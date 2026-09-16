package allyouneed.mixin

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageHotPathContractTest {

    @Test
    fun `item stack cache invalidates tag and damage mutations`() {
        val cn = read("allyouneed/mixin/minecraft/ItemStackMixin")
        val targets = injectTargets(cn)
        assertTrue("setTag" in targets)
        assertTrue("getOrCreateTag" in targets)
        assertTrue("addTagElement" in targets)
        assertTrue("removeTagKey" in targets)
        assertTrue("setDamageValue" in targets)
        val mixinOwners = classOwners(cn)
        assertTrue("allyouneed/util/ItemStackCaps" in mixinOwners)
    }

    @Test
    fun `of ItemStack does not use Item plain key`() {
        val cn = read("allyouneed/mixin/ae2/AEItemKeyOfMixin")
        val stackHit = cn.methods.first { it.name == "allyouneed\$stackHit" }
        val stackStore = cn.methods.first { it.name == "allyouneed\$stackStore" }
        assertFalse(I_ITEM_KEY_HOLDER in owners(stackHit))
        assertFalse(I_ITEM_KEY_HOLDER in owners(stackStore))
        assertTrue(I_ITEM_STACK_KEY_HOLDER in owners(stackHit))
        assertTrue(I_ITEM_STACK_KEY_HOLDER in owners(stackStore))
    }

    @Test
    fun `cell mounts require StorageCell not NetworkStorage`() {
        val refs = classOwners(read("allyouneed/api/BigStackSource")) +
            classOwners(read("allyouneed/api/BigStackSource\$Companion"))
        assertTrue("appeng/api/storage/cells/StorageCell" in refs)
        assertFalse("appeng/me/storage/NetworkStorage" in refs)
    }

    @Test
    fun `plain stack cache helper exists`() {
        val cl = javaClass.classLoader
        assertTrue(cl.getResource("allyouneed/util/ItemStackCaps.class") != null)
    }

    private fun read(internalName: String): ClassNode {
        val path = "$internalName.class"
        val bytes = javaClass.classLoader.getResourceAsStream(path)!!.readBytes()
        val cn = ClassNode()
        ClassReader(bytes).accept(cn, 0)
        return cn
    }

    private fun injectTargets(cn: ClassNode): Set<String> {
        val out = HashSet<String>()
        for (method in cn.methods) {
            val annos = mutableListOf<AnnotationNode>()
            method.visibleAnnotations?.let(annos::addAll)
            method.invisibleAnnotations?.let(annos::addAll)
            for (anno in annos) {
                if (anno.desc != "Lorg/spongepowered/asm/mixin/injection/Inject;") continue
                val values = anno.values ?: continue
                var i = 0
                while (i < values.size) {
                    val key = values[i] as String
                    val value = values[i + 1]
                    i += 2
                    if (key != "method") continue
                    when (value) {
                        is String -> out.add(value)
                        is List<*> -> value.filterIsInstance<String>().forEach(out::add)
                    }
                }
            }
        }
        return out
    }

    private fun owners(method: MethodNode): Set<String> {
        val refs = HashSet<String>()
        for (insn in method.instructions) {
            when (insn) {
                is org.objectweb.asm.tree.TypeInsnNode -> refs.add(insn.desc)
                is org.objectweb.asm.tree.FieldInsnNode -> refs.add(insn.owner)
                is org.objectweb.asm.tree.MethodInsnNode -> refs.add(insn.owner)
            }
        }
        return refs
    }

    private fun classOwners(cn: ClassNode): Set<String> {
        val refs = HashSet<String>()
        cn.superName?.let(refs::add)
        cn.interfaces?.forEach(refs::add)
        for (method in cn.methods) {
            refs.addAll(owners(method))
        }
        return refs
    }

    companion object {
        private const val I_ITEM_KEY_HOLDER = "allyouneed/api/IItemKeyHolder"
        private const val I_ITEM_STACK_KEY_HOLDER = "allyouneed/api/IItemStackKeyHolder"
    }
}
