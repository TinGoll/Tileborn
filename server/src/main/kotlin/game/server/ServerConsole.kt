package game.server

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/** Reads simple server console commands without blocking the authoritative tick loop. */
class ServerConsole(
    input: InputStream = System.`in`,
    private val onStopRequested: () -> Unit,
    private val logger: (String) -> Unit = ::println,
) {
    private val reader = BufferedReader(InputStreamReader(input))

    fun start(): Thread =
        Thread({ readCommands() }, "server-console").apply {
            isDaemon = true
            start()
        }

    private fun readCommands() {
        while (true) {
            val command = reader.readLine() ?: return
            when (command.trim().lowercase()) {
                "stop", "quit", "exit" -> {
                    logger("Stop requested from server console")
                    onStopRequested()
                    return
                }
            }
        }
    }
}
