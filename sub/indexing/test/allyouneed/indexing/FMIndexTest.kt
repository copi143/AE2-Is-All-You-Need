package allyouneed.indexing

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuffixArrayTest {

    private fun bruteForce(s: IntArray): IntArray {
        val n = s.size
        val idx = (0 until n).toList()
        val sorted = idx.sortedWith { a, b ->
            var i = a
            var j = b
            while (i < n && j < n) {
                if (s[i] != s[j]) return@sortedWith s[i].compareTo(s[j])
                i++
                j++
            }
            (n - a).compareTo(n - b)
        }
        return sorted.toIntArray()
    }

    private fun randomText(len: Int, alphabet: Int, rng: Random): IntArray {
        val s = IntArray(len)
        for (i in 0 until len - 1) s[i] = 1 + rng.nextInt(alphabet)
        s[len - 1] = 0
        return s
    }

    @Test
    fun matchesBruteForce() {
        val rng = Random(12345)
        repeat(300) {
            val len = 2 + rng.nextInt(40)
            val s = randomText(len, rng.nextInt(1, 8), rng)
            val expected = bruteForce(s)
            val actual = SuffixArray.build(s)
            assertTrue(expected.contentEquals(actual), "len=$len s=${s.toList()} exp=${expected.toList()} act=${actual.toList()}")
        }
    }

    @Test
    fun handlesTinyInputs() {
        assertEquals(intArrayOf(0).toList(), SuffixArray.build(intArrayOf(0)).toList())
        assertEquals(intArrayOf(1, 0).toList(), SuffixArray.build(intArrayOf(1, 0)).toList())
        assertEquals(intArrayOf(2, 0, 1).toList(), SuffixArray.build(intArrayOf(1, 2, 0)).toList())
        assertTrue(intArrayOf(2, 1, 0).contentEquals(SuffixArray.build(intArrayOf(1, 1, 0))))
    }
}

class WaveletMatrixTest {

    @Test
    fun rankMatchesBruteForce() {
        val rng = Random(777)
        repeat(200) {
            val n = rng.nextInt(1, 200)
            val sigma = rng.nextInt(1, 10)
            val values = IntArray(n) { rng.nextInt(sigma) }
            val bits = 32 - Integer.numberOfLeadingZeros(maxOf(sigma - 1, 1))
            val wm = WaveletMatrix.build(values, bits)
            for (v in 0 until sigma) {
                var running = 0
                for (i in 0..n) {
                    val expected = running
                    assertEquals(expected, wm.rank(v, i), "v=$v i=$i values=${values.toList()}")
                    if (i < n && values[i] == v) running++
                }
            }
        }
    }

    @Test
    fun accessReturnsOriginal() {
        val rng = Random(31337)
        repeat(200) {
            val n = rng.nextInt(1, 300)
            val sigma = rng.nextInt(1, 100)
            val values = IntArray(n) { rng.nextInt(sigma) }
            val bits = 32 - Integer.numberOfLeadingZeros(maxOf(sigma - 1, 1))
            val wm = WaveletMatrix.build(values, bits)
            for (i in 0 until n) {
                assertEquals(values[i], wm.access(i), "i=$i values=${values.toList()}")
            }
        }
    }
}

class FMIndexTest {

    private val codecs = listOf(Utf8Codec, Utf16Codec, Utf32Codec)

    private val alphabet = "abc XYZ123中文𠀀😀🚀"

    private fun randomString(rng: Random, maxLen: Int): String {
        val len = rng.nextInt(maxLen + 1)
        val sb = StringBuilder()
        repeat(len) {
            val idx = rng.nextInt(alphabet.length)
            val cp = alphabet.codePointAt(idx)
            sb.appendCodePoint(cp)
        }
        return sb.toString()
    }

    private fun bruteSearch(encoded: List<IntArray>, pattern: IntArray): List<Match> {
        val out = mutableListOf<Match>()
        for ((doc, text) in encoded.withIndex()) {
            for (i in 0..text.size - pattern.size) {
                var ok = true
                for (j in pattern.indices) if (text[i + j] != pattern[j]) { ok = false; break }
                if (ok) out.add(Match(doc, i, i + pattern.size))
            }
        }
        return out
    }

    private fun brutePrefix(encoded: List<IntArray>, prefix: IntArray): List<Match> =
        encoded.mapIndexedNotNull { doc, t ->
            if (t.size >= prefix.size && prefix.indices.all { t[it] == prefix[it] }) Match(doc, 0, prefix.size) else null
        }

    private fun bruteSuffix(encoded: List<IntArray>, suffix: IntArray): List<Match> =
        encoded.mapIndexedNotNull { doc, t ->
            if (t.size >= suffix.size && suffix.indices.all { t[t.size - suffix.size + it] == suffix[it] })
                Match(doc, t.size - suffix.size, t.size) else null
        }

    private fun assertSameMatches(expected: List<Match>, actual: List<Match>) {
        val e = expected.sortedWith(compareBy({ it.document }, { it.start }))
        val a = actual.sortedWith(compareBy({ it.document }, { it.start }))
        assertEquals(e, a)
    }

    @Test
    fun searchMatchesBruteForce() {
        val rng = Random(2024)
        for (codec in codecs) {
            repeat(40) {
                val texts = (0 until rng.nextInt(1, 12)).map { randomString(rng, 6) }
                val index = FMIndex.build(texts, codec)
                val encoded = texts.map { codec.encode(it) }
                repeat(20) {
                    val pattern = randomString(rng, 5)
                    val pat = codec.encode(pattern)
                    if (pat.isEmpty()) {
                        assertTrue(index.search(pattern).isEmpty())
                        assertEquals(0, index.count(pattern))
                    } else {
                        assertSameMatches(bruteSearch(encoded, pat), index.search(pattern))
                        assertEquals(bruteSearch(encoded, pat).size, index.count(pattern))
                        assertEquals(bruteSearch(encoded, pat).isNotEmpty(), index.contains(pattern))
                    }
                }
            }
        }
    }

    @Test
    fun prefixAndSuffixMatchBruteForce() {
        val rng = Random(99)
        for (codec in codecs) {
            repeat(40) {
                val texts = (0 until rng.nextInt(1, 12)).map { randomString(rng, 6) }
                val index = FMIndex.build(texts, codec)
                val encoded = texts.map { codec.encode(it) }
                repeat(15) {
                    val p = randomString(rng, 5)
                    val pp = codec.encode(p)
                    if (pp.isNotEmpty()) {
                        assertSameMatches(brutePrefix(encoded, pp), index.searchPrefix(p))
                        assertSameMatches(bruteSuffix(encoded, pp), index.searchSuffix(p))
                    }
                }
            }
        }
    }

    @Test
    fun noCrossDocumentMatch() {
        val texts = listOf("ab", "bc", "abc", "b")
        for (codec in codecs) {
            val index = FMIndex.build(texts, codec)
            assertSameMatches(listOf(Match(2, 0, 3)), index.search("abc"))
            assertEquals(1, index.count("abc"))
        }
    }

    @Test
    fun prefixSuffixBasics() {
        val texts = listOf("apple", "banana", "apricot", "grape", "application")
        val index = FMIndex.build(texts)
        assertSameMatches(listOf(Match(0, 0, 2), Match(2, 0, 2), Match(4, 0, 2)), index.searchPrefix("ap"))
        assertSameMatches(listOf(Match(0, 3, 5)), index.searchSuffix("le"))
        assertSameMatches(listOf(Match(0, 4, 5), Match(3, 4, 5)), index.searchSuffix("e"))
        assertSameMatches(listOf(Match(4, 7, 11)), index.searchSuffix("tion"))
        assertSameMatches(emptyList<Match>(), index.searchPrefix("zz"))
        assertSameMatches(emptyList<Match>(), index.searchSuffix("zz"))
    }

    @Test
    fun positionsAreConsistent() {
        val rng = Random(7)
        for (codec in codecs) {
            val texts = (0 until 10).map { randomString(rng, 8) }
            val index = FMIndex.build(texts, codec)
            val encoded = texts.map { codec.encode(it) }
            for (pattern in listOf("a", "b", "中", "😀", "abc")) {
                val pat = codec.encode(pattern)
                if (pat.isEmpty()) continue
                for (m in index.search(pattern)) {
                    assertTrue(pat.contentEquals(encoded[m.document].copyOfRange(m.start, m.end)), "codec=$codec pattern=$pattern m=$m")
                }
            }
        }
    }

    @Test
    fun emptyTextsAndPatterns() {
        for (codec in codecs) {
            val index = FMIndex.build(listOf("", "", "a", ""), codec)
            assertTrue(index.search("").isEmpty())
            assertEquals(0, index.count(""))
            assertFalse(index.contains(""))
            assertSameMatches(listOf(Match(2, 0, 1)), index.search("a"))
            assertSameMatches(listOf(Match(2, 0, 1)), index.searchPrefix("a"))
            assertSameMatches(listOf(Match(2, 0, 1)), index.searchSuffix("a"))
        }
        val empty = FMIndex.build(emptyList())
        assertEquals(0, empty.documentCount)
        assertTrue(empty.search("x").isEmpty())
    }

    @Test
    fun defaultCodecIsUtf8() {
        assertEquals("utf8", FMIndex.build(listOf("a")).codecName)
    }

    @Test
    fun chineseAndEmojiSearch() {
        val texts = listOf("你好世界", "世界你好", "😀😃😄", "hello 世界")
        val idx32 = FMIndex.build(texts, Utf32Codec)
        assertSameMatches(listOf(Match(0, 2, 4), Match(1, 0, 2), Match(3, 6, 8)), idx32.search("世界"))
        assertSameMatches(listOf(Match(2, 0, 1)), idx32.search("😀"))
    }
}
