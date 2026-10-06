package kaptor

enum class SrcType(val extension: String) {
    Kt("kt"), Kts("kts");

    companion object {
        fun fromFileName(fileName: String): SrcType? {
            return when {
                fileName.endsWith(".kt") -> Kt
                fileName.endsWith(".kts") -> Kts
                else -> null
            }
        }
    }
}
