package allyouneed.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("SpellCheckingInspection")
const val MODID = "ae2isallyouneed"

@Suppress("SpellCheckingInspection")
const val MODNAME = "AE2 Is All You Need"

@JvmField
val logger: Logger = LoggerFactory.getLogger(MODNAME)

val Double.Ki get() = this * (1024.0)
val Double.Mi get() = this * (1024.0 * 1024.0)
val Double.Gi get() = this * (1024.0 * 1024.0 * 1024.0)
val Double.Ti get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Pi get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Ei get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Zi get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Yi get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Ri get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)
val Double.Qi get() = this * (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0)

fun formatScaledUnit(exp: Int, name: String) = when {
    exp >= 100 -> "${1 shl (exp - 100)}q_$name"
    exp >= 90 -> "${1 shl (exp - 90)}r_$name"
    exp >= 80 -> "${1 shl (exp - 80)}y_$name"
    exp >= 70 -> "${1 shl (exp - 70)}z_$name"
    exp >= 60 -> "${1 shl (exp - 60)}e_$name"
    exp >= 50 -> "${1 shl (exp - 50)}p_$name"
    exp >= 40 -> "${1 shl (exp - 40)}t_$name"
    exp >= 30 -> "${1 shl (exp - 30)}g_$name"
    exp >= 20 -> "${1 shl (exp - 20)}m_$name"
    exp >= 10 -> "${1 shl (exp - 10)}k_$name"
    else -> "${1 shl exp}b_$name"
}

val Float.floatingExp get() = (this.toBits() shr 23) - 127

val Double.floatingExp get() = (this.toBits() shr 52).toInt() - 1023
