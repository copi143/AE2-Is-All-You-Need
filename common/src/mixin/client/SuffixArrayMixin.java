package allyouneed.mixin.client;

import allyouneed.client.search.FmSuffixArray;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.searchtree.SuffixArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * 把原版搜索树引擎换成 FM-index。
 * <p>
 * 直接 mixin {@code SuffixArray} 类本身，三个方法在 HEAD 处整体转交给
 * {@link FmSuffixArray}（注入 + cancel，本仓库替换原版方法体的既有模式，
 * 见 CompoundTagMixin）。{@code PlainTextSearchTree.create} /
 * {@code ResourceLocationSearchTree.create} 里的 {@code new SuffixArray} 不用动，
 * {@code suffixarray::search} 方法引用经虚分派自然走到新实现；上层的合并/排序逻辑
 *（IdSearchTree/FullTextSearchTree）原样保留。
 */
@Mixin(SuffixArray.class)
public abstract class SuffixArrayMixin<T> {

    @Unique
    private static final IntArrayList allyouneed$emptyArray = new IntArrayList();

    @Unique
    private final FmSuffixArray<T> allyouneed$delegate = new FmSuffixArray<>();

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;", remap = false))
    private ArrayList<Object> allyouneed$init$1() {
        return null;
    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/ints/IntArrayList", remap = false))
    private IntArrayList allyouneed$init$2() {
        return allyouneed$emptyArray;
    }

    /**
     * @author copi143
     * @reason 使用 FM-Index 替换后缀数组
     */
    @Overwrite
    public void add(T object, String text) {
        allyouneed$delegate.add(object, text);
    }

    /**
     * @author copi143
     * @reason 使用 FM-Index 替换后缀数组
     */
    @Overwrite
    public void generate() {
        allyouneed$delegate.generate();
    }

    /**
     * @author copi143
     * @reason 使用 FM-Index 替换后缀数组
     */
    @Overwrite
    public List<T> search(String query) {
        return allyouneed$delegate.search(query);
    }
}
