package kaptor.a2s.runtime

/**
 * A2S Range：实现 `a..b` 语法，支持 for-in 迭代。
 * 所有值以 boxed Long 存储，支持 Iterable<Object> 接口。
 */
class A2sRange(
    private val start: Long,
    private val endInclusive: Long,
) : Iterable<Any?> {

    override fun iterator(): Iterator<Any?> = object : Iterator<Any?> {
        private var current = start
        override fun hasNext(): Boolean = current <= endInclusive
        override fun next(): Any? {
            if (!hasNext()) throw NoSuchElementException()
            return current++
        }
    }

    override fun toString(): String = "$start..$endInclusive"
}
