package serialization

import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

class IntegrationTest {
    @Test
    fun primitivesRoundTrip() {
        checkWithPrimitives()
        checkWithPrimitives2()
    }

    @Test
    fun unsignedAndVarLen() {
        checkWithUnsigned(WithUnsigned(255u.toUByte(), 65535u.toUShort(), 3000000000u, 1UL shl 63))
        checkWithUnsigned(WithUnsigned(0u, 0u, 0u, 0u))
        checkWithVarLen(WithVarLen(123, 1L shl 40, 123u, 1UL shl 40))
    }

    @Test
    fun mapsAndSets() {
        checkWithMaps(WithMaps(mapOf("a" to 1, "b" to -1), mapOf(UUID.randomUUID() to "x"), mapOf(1 to "y")))
        checkWithMaps(WithMaps(emptyMap(), emptyMap(), emptyMap()))
        checkWithSets(WithSets(setOf("a", "b"), setOf(1, -1)))
        checkWithCollections(WithCollections(listOf(Inner(1, "a")), setOf(Inner(2, "b")), mapOf("k" to listOf("v")), mapOf(1 to Inner(3, "c"))))
    }

    @Test
    fun nullableAndEnum() {
        checkWithNullable(WithNullable(5, "note", mapOf("k" to "v")))
        checkWithNullable(WithNullable(5, null, null))
        checkNullableEmptyTag()
        checkEnumStorage(sampleWithPrimitives2())
        checkMapStringOptimized(WithMaps(mapOf("x" to 1), emptyMap(), emptyMap()))
    }
}
