package allyouneed.core

class SampleKey @JvmOverloads constructor(val name: String, val extra: Int = 0) {
    override fun equals(other: Any?): Boolean =
        other is SampleKey && name == other.name && extra == other.extra

    override fun hashCode(): Int = 31 * name.hashCode() + extra
}

class SampleLongKey(val primary: Long, val secondary: Long = 0) {
    override fun equals(other: Any?): Boolean =
        other is SampleLongKey && primary == other.primary && secondary == other.secondary

    override fun hashCode(): Int = 31 * primary.hashCode() + secondary.hashCode()
}
