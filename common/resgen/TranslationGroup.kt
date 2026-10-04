package allyouneed.resgen

/** A fixed translation prefix; nested groups append to it without changing the parent scope. */
@AssetGenDsl
class TranslationGroup internal constructor(
    private val prefix: String,
    private val write: (String, String) -> Unit,
) {
    fun text(key: String, value: String) {
        write("$prefix.$key", value)
    }

    fun group(prefix: String, init: TranslationGroup.() -> Unit) {
        TranslationGroup("${this.prefix}.$prefix", write).apply(init)
    }
}
