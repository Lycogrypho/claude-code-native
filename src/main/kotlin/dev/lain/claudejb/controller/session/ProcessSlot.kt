package dev.lain.claudejb.controller.session

class ProcessSlot<T : Any> {

    private val lock = Any()

    @Volatile var generation = 0
        private set

    @Volatile var current: T? = null
        private set

    fun supersede(): Int = synchronized(lock) { ++generation }

    fun isCurrent(gen: Int): Boolean = gen == generation

    fun claim(gen: Int, value: T): Boolean = synchronized(lock) {
        if (gen != generation) return false
        current = value
        true
    }

    fun releaseIfStale(gen: Int, value: T): Boolean = synchronized(lock) {
        if (gen == generation) return false
        if (current === value) current = null
        true
    }

    fun release(value: T) = synchronized(lock) {
        if (current === value) current = null
    }

    fun take(): T? = synchronized(lock) { current.also { current = null } }

    fun <E> onlyWhileCurrent(gen: Int, deliver: (E) -> Unit): (E) -> Unit = { event ->
        if (isCurrent(gen)) deliver(event)
    }
}
