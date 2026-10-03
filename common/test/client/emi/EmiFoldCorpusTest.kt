package allyouneed.client.integration.emi.fold

import allyouneed.client.integration.emi.fold.model.FoldClassifier
import allyouneed.client.integration.emi.fold.model.FoldFeature
import allyouneed.client.integration.emi.fold.model.FoldKind
import allyouneed.client.integration.emi.fold.model.FoldTree
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.assertEquals

/** 可选本地大样本验收：Python 原型导出特征和预期家族，不依赖启动 Minecraft。 */
class EmiFoldCorpusTest {
    @Test
    fun `Kotlin 分类家族与 Python 多模组样本一致`() {
        val file = System.getenv("AE2INYA_FOLD_FIXTURE")?.let(Path::of)
        assumeTrue(file != null && Files.isRegularFile(file), "未提供本地分类样本")
        val rows = Files.readAllLines(file!!).map { line ->
            line.split('\t').map { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }
        }
        fun split(s: String): List<String> = if (s.isEmpty()) emptyList() else s.split('\u0000')
        val features = rows.map { r ->
            FoldFeature(r[0], FoldKind.valueOf(r[1]), r[2], r[3], split(r[4]).toSet(), r[5], split(r[6]), r[7].ifEmpty { null },
                split(r[8]).chunked(2).associate { it[0] to it[1] })
        }
        val tree = FoldClassifier.build(features)
        val mismatches = rows.indices.mapNotNull { i ->
            val family = tree.pathFor(i).filter { it.stage == FoldTree.Stage.FAMILY }.lastOrNull()?.label.orEmpty()
            if (family == rows[i][9]) null else "${rows[i][0]}\t${rows[i][9]}\t$family"
        }
        if (mismatches.isNotEmpty()) Files.write(file.resolveSibling("emi-fold-port-mismatches.tsv"), mismatches)
        assertEquals(0, mismatches.size, "分类家族不一致，见样本旁的 emi-fold-port-mismatches.tsv；前几项：${mismatches.take(8)}")
        for (kind in FoldKind.entries) {
            val projection = tree.project(IntArray(features.size) { it }, kind)
            val actual = projection.entries.flatMap { when (it) {
                is FoldTree.Entry.Stack -> listOf(it.index)
                is FoldTree.Entry.Group -> it.members.toList()
            } }
            assertEquals(features.indices.filter { features[it].kind == kind }, actual.sorted())
        }
    }
}
