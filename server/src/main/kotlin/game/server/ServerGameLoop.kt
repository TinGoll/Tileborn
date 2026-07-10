package game.server

import kotlin.math.min

/** Runs authoritative server simulation with a fixed timestep. */
class ServerGameLoop(
    tickRate: Int = DEFAULT_TICK_RATE,
    private val logTicks: Boolean = false,
    private val clock: TickClock = SystemTickClock,
    private val logger: (String) -> Unit = ::println,
) {
    val fixedTimeStepSeconds: Float = 1f / tickRate
    val fixedTimeStepNanos: Long = NANOS_PER_SECOND / tickRate

    @Volatile
    var isRunning: Boolean = false
        private set

    var serverTick: Long = 0L
        private set

    fun run(maxTicks: Long? = null, update: (Float) -> Unit) {
        isRunning = true
        var previousTime = clock.nanoTime()
        var accumulatedNanos = 0L

        while (isRunning && (maxTicks == null || serverTick < maxTicks)) {
            val now = clock.nanoTime()
            val elapsedNanos = (now - previousTime).coerceAtLeast(0L)
            previousTime = now
            accumulatedNanos += min(elapsedNanos, MAX_FRAME_NANOS)

            val ticksToRun = calculateTicksToRun(accumulatedNanos)
            if (ticksToRun == 0) {
                clock.sleepNanos(fixedTimeStepNanos - accumulatedNanos)
                continue
            }

            repeat(ticksToRun) {
                if (!isRunning || (maxTicks != null && serverTick >= maxTicks)) return@repeat
                tick(update)
                accumulatedNanos -= fixedTimeStepNanos
            }
        }

        isRunning = false
    }

    fun tick(update: (Float) -> Unit) {
        update(fixedTimeStepSeconds)
        serverTick += 1
        if (logTicks) {
            logger("server tick=$serverTick")
        }
    }

    fun stop() {
        isRunning = false
    }

    fun calculateTicksToRun(accumulatedNanos: Long): Int =
        (accumulatedNanos / fixedTimeStepNanos).toInt()

    interface TickClock {
        fun nanoTime(): Long
        fun sleepNanos(nanos: Long)
    }

    object SystemTickClock : TickClock {
        override fun nanoTime(): Long = System.nanoTime()

        override fun sleepNanos(nanos: Long) {
            if (nanos <= 0L) return
            val millis = nanos / 1_000_000L
            val extraNanos = (nanos % 1_000_000L).toInt()
            try {
                Thread.sleep(millis, extraNanos)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    companion object {
        const val DEFAULT_TICK_RATE = 20
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val MAX_FRAME_NANOS = 250_000_000L
    }
}
