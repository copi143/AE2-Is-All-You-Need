package allyouneed.parts.logger

import io.github.copi143.serialization.SerialName
import io.github.copi143.serialization.SerialOrdinal
import io.github.copi143.serialization.Serialize
import allyouneed.util.MODID
import net.minecraft.network.chat.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serialize
@JvmRecord
data class NetworkLogEntry(
    @SerialName("t") val utcMillis: Long,
    @SerialName("k") @SerialOrdinal val kind: NetworkLogKind,
    @SerialName("a") val args: List<String>,
) {
    fun formatLocalTime(): String = LOCAL_TIME.format(Instant.ofEpochMilli(utcMillis).atZone(ZoneId.systemDefault()))

    fun message(): Component = Component.translatable("gui.$MODID.log.${kind.langKey}", *args.toTypedArray())

    fun toComponent(): Component = Component.literal("[${formatLocalTime()}] ").append(message())

    fun toPlainLine(): String = "[${formatLocalTime()}] ${message().string}"

    companion object {
        private val LOCAL_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
