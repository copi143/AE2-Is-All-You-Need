package allyouneed.mixin.ae2;

import allyouneed.util.MetricFormat;
import appeng.util.ReadableNumberConverter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Extend SI suffixes past E (Z/Y/R/Q) and support values that format as 999Q+.
 */
@Mixin(value = ReadableNumberConverter.class, remap = false)
public class ReadableNumberConverterMixin {

    /**
     * @author AE2 Is All You Need
     * @reason Support SI suffixes beyond E and BigInteger-scale amounts via shared formatter
     */
    @Overwrite
    public static String format(long number, int width) {
        return MetricFormat.siFormat(number, width);
    }

    /**
     * @author AE2 Is All You Need
     * @reason Keep double path consistent with extended SI formatting
     */
    @Overwrite
    public static String format(double number, int width) {
        return MetricFormat.siFormat(number, width);
    }
}
