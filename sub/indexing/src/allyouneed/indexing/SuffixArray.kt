package allyouneed.indexing

/**
 * 后缀数组构造（SA-IS，诱导排序）。
 *
 * 约定：输入 `s` 的最后一个元素必须是最小且唯一的终结符（在 [FMIndex] 中恒为 0），
 * 其余元素取值 `>= 0`。返回的后缀数组长度为 `s.size`，按字典序排列后缀起始位置。
 */
object SuffixArray {

    fun build(s: IntArray): IntArray {
        if (s.isEmpty()) return IntArray(0)
        if (s.size == 1) return intArrayOf(0)
        var k = 0
        for (v in s) if (v > k) k = v
        return saIs(s, k)
    }

    private fun isLms(t: BooleanArray, i: Int): Boolean = i > 0 && t[i] && !t[i - 1]

    private fun saIs(s: IntArray, k: Int): IntArray {
        val n = s.size

        // 类型：true = S 型，false = L 型
        val t = BooleanArray(n)
        t[n - 1] = true
        for (i in n - 2 downTo 0) {
            t[i] = when {
                s[i] < s[i + 1] -> true
                s[i] > s[i + 1] -> false
                else -> t[i + 1]
            }
        }

        // LMS 位置（文本序）
        var m = 0
        for (i in 1 until n) if (isLms(t, i)) m++
        val lms = IntArray(m)
        var p = 0
        for (i in 1 until n) if (isLms(t, i)) lms[p++] = i

        // 首次诱导排序，得到按 LMS 子串排序的（粗略）后缀数组
        val sa = induce(s, t, lms, k)

        // 计算每个 LMS 位置的子串终点
        val lmsEnd = IntArray(n)
        var next = n - 1
        for (i in n - 1 downTo 0) {
            if (isLms(t, i)) {
                lmsEnd[i] = next
                next = i
            }
        }

        // 为 LMS 子串命名
        val name = IntArray(n) { -1 }
        var nameCnt = 0
        var prev = -1
        for (i in 0 until n) {
            val cur = sa[i]
            if (!isLms(t, cur)) continue
            if (prev == -1 || !lmsEqual(s, lmsEnd, prev, cur)) nameCnt++
            name[cur] = nameCnt
            prev = cur
        }

        // 得到按 LMS 子串排序的 LMS 位置
        val lmsSorted: IntArray
        if (nameCnt == m) {
            lmsSorted = IntArray(m)
            var j = 0
            for (i in 0 until n) if (isLms(t, sa[i])) lmsSorted[j++] = sa[i]
        } else {
            val reduced = IntArray(m + 1)
            for (j in 0 until m) reduced[j] = name[lms[j]]
            reduced[m] = 0
            val saR = saIs(reduced, nameCnt)
            lmsSorted = IntArray(m)
            for (i in 1..m) lmsSorted[i - 1] = lms[saR[i]]
        }

        // 最终诱导排序
        return induce(s, t, lmsSorted, k)
    }

    private fun lmsEqual(s: IntArray, lmsEnd: IntArray, a: Int, b: Int): Boolean {
        if (a == b) return true
        val la = lmsEnd[a] - a + 1
        val lb = lmsEnd[b] - b + 1
        if (la != lb) return false
        for (k in 0 until la) if (s[a + k] != s[b + k]) return false
        return true
    }

    private fun induce(s: IntArray, t: BooleanArray, lms: IntArray, k: Int): IntArray {
        val n = s.size
        val cnt = IntArray(k + 1)
        for (c in s) cnt[c]++
        val start = IntArray(k + 1)
        val end = IntArray(k + 1)
        var sum = 0
        for (i in 0..k) {
            start[i] = sum
            sum += cnt[i]
            end[i] = sum
        }

        val sa = IntArray(n) { -1 }

        // 把 LMS 后缀放入各桶的右端（逆序放置）
        val endCur = end.clone()
        for (i in lms.size - 1 downTo 0) {
            val c = s[lms[i]]
            sa[--endCur[c]] = lms[i]
        }

        // 从左往右诱导 L 型
        val startCur = start.clone()
        for (i in 0 until n) {
            val j = sa[i]
            if (j < 0 || j == 0) continue
            val jj = j - 1
            if (!t[jj]) {
                val c = s[jj]
                sa[startCur[c]++] = jj
            }
        }

        // 从右往左诱导 S 型
        val endCur2 = end.clone()
        for (i in n - 1 downTo 0) {
            val j = sa[i]
            if (j < 0 || j == 0) continue
            val jj = j - 1
            if (t[jj]) {
                val c = s[jj]
                sa[--endCur2[c]] = jj
            }
        }

        return sa
    }
}
