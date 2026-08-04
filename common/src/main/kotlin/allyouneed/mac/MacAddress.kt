package allyouneed.mac

/**
 * 48-bit MAC helpers. Values are stored as [Long] masked with [MASK].
 * Bit 1 of the first octet is set (locally administered) for newly allocated addresses.
 */
object MacAddress {
    const val MASK = 0xFFFF_FFFF_FFFFL
    const val NONE = 0L

    /** Locally administered unicast: second-least-significant bit of first octet. */
    const val LOCALLY_ADMINISTERED = 0x02_00_00_00_00_00L

    @JvmStatic
    fun isValid(mac: Long): Boolean = mac != NONE && (mac and MASK.inv()) == 0L && mac != 0L

    @JvmStatic
    fun normalize(mac: Long): Long = mac and MASK

    @JvmStatic
    fun format(mac: Long): String {
        val m = normalize(mac)
        return "%02X:%02X:%02X:%02X:%02X:%02X".format(
            ((m shr 40) and 0xFF).toInt(),
            ((m shr 32) and 0xFF).toInt(),
            ((m shr 24) and 0xFF).toInt(),
            ((m shr 16) and 0xFF).toInt(),
            ((m shr 8) and 0xFF).toInt(),
            (m and 0xFF).toInt(),
        )
    }

    @JvmStatic
    fun parse(text: String): Long {
        val hex = text.filter { it != ':' && it != '-' && !it.isWhitespace() }
        require(hex.length == 12) { "MAC must be 12 hex digits: $text" }
        return normalize(hex.toLong(16))
    }
}
