package allyouneed.util;

import java.math.BigInteger;

/**
 * @deprecated Use {@link SiFormat} (Kotlin). Kept as a thin Java façade for existing call sites.
 */
@Deprecated
public final class SiAmountFormat {
    private SiAmountFormat() {
    }

    public static String format(long number, int width) {
        return SiFormat.format(number, width);
    }

    public static String format(BigInteger number, int width) {
        return SiFormat.format(number, width);
    }

    public static String format(double number, int width) {
        return SiFormat.format(number, width);
    }

    public static String formatFull(long amount) {
        return SiFormat.formatFull(amount);
    }

    public static String formatFull(BigInteger amount) {
        return SiFormat.formatFull(amount);
    }

    public static long saturateToLong(BigInteger amount) {
        return SiFormat.saturateToLong(amount);
    }
}
