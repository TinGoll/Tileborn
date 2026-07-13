package game.shared.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NicknameRulesTest {
    @Test
    fun `nickname is trimmed and accepts supported characters`() {
        assertEquals("Player_1", NicknameRules.normalize("  Player_1  "))
        assertTrue(NicknameRules.isValid("Player_1"))
    }

    @Test
    fun `nickname length is validated`() {
        assertFalse(NicknameRules.isValid("ab"))
        assertFalse(NicknameRules.isValid("a".repeat(NicknameRules.MAX_LENGTH + 1)))
    }

    @Test
    fun `nickname rejects whitespace and punctuation`() {
        assertFalse(NicknameRules.isValid("two players"))
        assertFalse(NicknameRules.isValid("player!"))
        assertFalse(NicknameRules.isValid("Игрок"))
    }
}
