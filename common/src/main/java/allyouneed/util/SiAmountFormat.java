package allyouneed.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;

/**
 * SI short-amount formatting with support beyond {@code long} (up to Q = 10^30).
 * Values at or above 999Q are shown as {@code 999Q+}; use {@link #formatFull} for exact text.
 */
public final class SiAmountFormat {
    private static final int DIVISION_BASE = 1000;
    private static final char[] POSTFIXES = "KMGTPEZYRQ".toCharArray();
    private static final BigInteger BI_DIV = BigInteger.valueOf(DIVISION_BASE);
    /** Values strictly greater than 999Q are shown as {@code 999Q+}. */
    private static final BigInteger THRESHOLD_999Q =
            BigInteger.valueOf(999).multiply(BI_DIV.pow(POSTFIXES.length));

    private SiAmountFormat() {
    }

    public static String format(long number, int width) {
        if (number < 0) {
            throw new IllegalArgumentException("Non-negative numbers cannot be formatted by this method");
        }
        return format(BigInteger.valueOf(number), width);
    }

    public static String format(BigInteger number, int width) {
        if (number.signum() < 0) {
            throw new IllegalArgumentException("Non-negative numbers cannot be formatted by this method");
        }
        // Strictly above 999Q → capped display; exact 999Q still formats as 999Q
        if (number.compareTo(THRESHOLD_999Q) > 0) {
            if (width >= 5) {
                return "999Q+";
            }
            if (width >= 4) {
                return "999Q";
            }
            return "Q+";
        }

        String numberString = number.toString();
        int numberSize = numberString.length();
        if (numberSize <= width) {
            return numberString;
        }

        BigInteger base = number;
        BigInteger last = base.multiply(BI_DIV);
        int exponent = -1;
        char postFix = 0;

        while (numberSize > width) {
            last = base;
            base = base.divide(BI_DIV);
            exponent++;
            if (exponent >= POSTFIXES.length) {
                return "999Q+";
            }
            numberSize = base.toString().length() + 1;
            postFix = POSTFIXES[exponent];
        }

        String withPrecision = formatFractional(last, postFix);
        String withoutPrecision = base.toString() + postFix;
        String slimResult = withPrecision.length() <= width ? withPrecision : withoutPrecision;
        if (slimResult.length() > width) {
            // Fall back further if needed (e.g. width=3 with large mantissa)
            while (slimResult.length() > width && exponent + 1 < POSTFIXES.length) {
                last = base;
                base = base.divide(BI_DIV);
                exponent++;
                postFix = POSTFIXES[exponent];
                withPrecision = formatFractional(last, postFix);
                withoutPrecision = base.toString() + postFix;
                slimResult = withPrecision.length() <= width ? withPrecision : withoutPrecision;
            }
            if (slimResult.length() > width) {
                slimResult = withoutPrecision.length() <= width ? withoutPrecision : (base.toString() + postFix);
                if (slimResult.length() > width) {
                    slimResult = slimResult.substring(0, width);
                }
            }
        }
        return slimResult;
    }

    public static String format(double number, int width) {
        if (number < 0) {
            throw new IllegalArgumentException("Non-negative numbers cannot be formatted by this method");
        }
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            return "???";
        }
        // Prefer exact integer path when possible
        if (number <= Long.MAX_VALUE && number == Math.rint(number)) {
            return format((long) number, width);
        }
        if (number > Long.MAX_VALUE) {
            // Approximate via BigDecimal for SI path
            return format(BigDecimal.valueOf(number).toBigInteger(), width);
        }

        int integerDigits = (int) Math.max(0, Math.log10(number) + 1);
        int fractionalDigits = width - integerDigits - 1;
        double minFractional = Math.pow(10, -fractionalDigits);
        double fractional = number - Math.floor(number);

        if (fractional < 1e-9 || integerDigits > width - 1) {
            return format((long) number, width);
        }
        if (fractional + 1e-9 < minFractional && integerDigits - 1 <= width) {
            return "~" + format((long) number, width - 1);
        }
        DecimalFormat fmt = getFormat();
        fmt.setMaximumFractionDigits(Math.max(0, fractionalDigits));
        return fmt.format(number);
    }

    public static String formatFull(long amount) {
        return formatFull(BigInteger.valueOf(amount));
    }

    public static String formatFull(BigInteger amount) {
        return NumberFormat.getNumberInstance().format(amount);
    }

    public static long saturateToLong(BigInteger amount) {
        if (amount.signum() < 0) {
            return 0L;
        }
        if (amount.bitLength() > 63) {
            return Long.MAX_VALUE;
        }
        return amount.longValue();
    }

    private static String formatFractional(BigInteger lastTimes1000, char postFix) {
        // lastTimes1000 is previous base * 1000 before last division; value = last/1000
        BigDecimal value = new BigDecimal(lastTimes1000).divide(BigDecimal.valueOf(DIVISION_BASE), 1, RoundingMode.DOWN);
        DecimalFormat fmt = getFormat();
        fmt.setMaximumFractionDigits(1);
        return fmt.format(value) + postFix;
    }

    private static DecimalFormat getFormat() {
        var symbols = DecimalFormatSymbols.getInstance();
        var format = new DecimalFormat(".#;0.#");
        format.setDecimalSeparatorAlwaysShown(false);
        format.setDecimalFormatSymbols(symbols);
        format.setRoundingMode(RoundingMode.DOWN);
        return format;
    }
}
