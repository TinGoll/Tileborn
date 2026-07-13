package game.shared.protocol

/** Shared nickname validation used by both the join UI and the authoritative server. */
object NicknameRules {
    const val MIN_LENGTH: Int = 3
    const val MAX_LENGTH: Int = 20

    // The MVP uses libGDX's bundled bitmap font, whose glyph set is Latin-only.
    private val allowedCharacters = Regex("^[A-Za-z0-9_-]+$")

    fun normalize(value: String): String = value.trim()

    fun validationError(value: String): String? {
        val nickname = normalize(value)
        return when {
            nickname.length < MIN_LENGTH -> "Nickname must contain at least $MIN_LENGTH characters."
            nickname.length > MAX_LENGTH -> "Nickname must contain at most $MAX_LENGTH characters."
            !allowedCharacters.matches(nickname) ->
                "Nickname may contain only Latin letters, numbers, '_' and '-'."
            else -> null
        }
    }

    fun isValid(value: String): Boolean = validationError(value) == null
}
