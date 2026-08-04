package allyouneed.mac

import allyouneed.mac.MacAddress.MASK


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
    fun isValid(mac: Long): Boolean = mac != NONE && (mac and MASK) == mac

    @JvmStatic
    fun normalize(mac: Long): Long = mac and MASK

    @JvmStatic
    fun format(mac: Long): String = if (isValid(mac)) {
        "%02X:%02X:%02X:%02X:%02X:%02X".format(
            ((mac shr 40) and 0xFF).toInt(),
            ((mac shr 32) and 0xFF).toInt(),
            ((mac shr 24) and 0xFF).toInt(),
            ((mac shr 16) and 0xFF).toInt(),
            ((mac shr 8) and 0xFF).toInt(),
            (mac and 0xFF).toInt(),
        )
    } else if (mac == 0L) {
        "N/A"
    } else {
        "Error MAC Address"
    }

    @JvmStatic
    fun parse(text: String): Long {
        val hex = text.filter { it != ':' && it != '-' && !it.isWhitespace() }
        require(hex.length == 12) { "MAC must be 12 hex digits: $text" }
        return normalize(hex.toLong(16))
    }
}
