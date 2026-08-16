package allyouneed.pattern.term

enum class EncodingKind {
    MACHINE,
    PROCESSING,
    PROBABILITY,
    PSEUDO,
    ;

    companion object {
        fun byName(name: String): EncodingKind =
            entries.firstOrNull { it.name == name } ?: MACHINE
    }
}
