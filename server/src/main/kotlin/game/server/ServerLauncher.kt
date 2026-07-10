@file:JvmName("ServerLauncher")

package game.server

/** Launches the server application. */
fun main(args: Array<String>) {
    val maxTicks = args.firstNotNullOfOrNull { argument ->
        argument.removePrefix("--ticks=").takeIf { it != argument }?.toLong()
    }
    ServerApplication().run(maxTicks = maxTicks)
}
