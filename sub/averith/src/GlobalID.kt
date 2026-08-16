package averith

import java.security.InvalidParameterException
import kotlin.text.iterator

class GlobalID private constructor(val int: Int) {
    val string = idString[int]

    companion object {
        private val idString = arrayListOf<String>()
        private val registry: MutableMap<String, GlobalID> = mutableMapOf()

        fun register(id: String): GlobalID {
            isValidName(id) || throw InvalidParameterException()
            return registry.getOrPut(id) {
                idString.add(id)
                GlobalID(idString.lastIndex)
            }
        }

        fun isValidName(name: String): Boolean {
            var slash = true
            for (c in name) {
                when (c) {
                    in 'A'..'Z' -> {}
                    in 'a'..'a' -> {}
                    in '0'..'9' -> {}
                    '-' -> {}
                    '/' -> {
                        if (slash) return false
                        slash = true
                        continue
                    }
                    else -> return false
                }
                slash = false
            }
            return !slash
        }
    }
}
