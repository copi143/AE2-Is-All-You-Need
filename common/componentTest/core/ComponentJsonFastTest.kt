package core

import allyouneed.gsonfast.ComponentJsonFast
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ComponentJsonFastTest {
    @Test
    fun duplicatePropertiesMatchVanilla() {
        assertTrue(ComponentJsonFast.isAvailable())
        val inputs = listOf(
            """{"text":"x","bold":true,"bold":false}""",
            """{"text":"x","italic":true,"italic":false,"text":"y"}""",
            """{"text":"x","extra":[{"text":"a"}],"extra":[{"text":"b"}]}""",
            """{"translate":"key","with":["a"],"with":["b"]}""",
            """{"score":{"name":"old","objective":"a"},"score":{"name":"new","objective":"b"}}""",
            """{"text":"x","extra":[{"text":"y","bold":true,"bold":false}]}""",
        )
        for (input in inputs) {
            assertEquals(
                Component.Serializer.toJson(requireNotNull(Component.Serializer.fromJson(input))),
                Component.Serializer.toJson(ComponentJsonFast.fromJson(input, false)), input,
            )
        }
    }

    @Test
    fun replacementScoreCannotInheritPreviousFields() {
        val input = """{"score":{"name":"old","objective":"o"},"score":{"name":"new"}}"""
        val vanilla = assertThrows(com.google.gson.JsonParseException::class.java) { Component.Serializer.fromJson(input) }
        val fast = assertThrows(com.google.gson.JsonParseException::class.java) { ComponentJsonFast.fromJson(input, false) }
        assertEquals(vanilla.message, fast.message)
    }
}
