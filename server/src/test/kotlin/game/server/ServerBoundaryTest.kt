package game.server

import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ServerBoundaryTest {
    @Test
    fun `server source does not import client or rendering packages`() {
        val serverSources = Files.walk(Path.of("src/main/kotlin")).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .filter { it.toString().endsWith(".kt") }
                .toList()
        }

        val forbiddenImports = serverSources.flatMap { source ->
            Files.readAllLines(source)
                .filter { line ->
                    line.startsWith("import game.client") ||
                        line.startsWith("import game.desktop") ||
                        line.startsWith("import com.badlogic.gdx.graphics") ||
                        line.startsWith("import com.badlogic.gdx.scenes")
                }
                .map { "${source}: $it" }
        }

        assertFalse(forbiddenImports.joinToString("\n"), forbiddenImports.isNotEmpty())
    }
}
