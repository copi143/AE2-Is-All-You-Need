package kaptor.a2s.runtime

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * post 事件队列。`post` 入队，flush 时 drain 并逐个分发，新 post 进入下一刻。
 */
class A2sEventQueue {
    private val queue = ConcurrentLinkedQueue<A2sEventObject>()

    fun post(event: A2sEventObject) {
        queue.add(event)
    }

    fun drain(): List<A2sEventObject> {
        val batch = mutableListOf<A2sEventObject>()
        while (true) {
            val e = queue.poll() ?: break
            batch.add(e)
        }
        return batch
    }

    fun isEmpty(): Boolean = queue.isEmpty()
}
