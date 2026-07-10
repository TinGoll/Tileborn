@file:JvmName("ServerLauncher")

package game.server

/** Launches the server application. */
fun main(args: Array<String>) {
    val maxTicks = args.firstNotNullOfOrNull { argument ->
        argument.removePrefix("--ticks=").takeIf { it != argument }?.toLong()
    }
    val logTicks = "--log-ticks" in args
    ServerApplication(logTicks = logTicks).run(maxTicks = maxTicks)
}
