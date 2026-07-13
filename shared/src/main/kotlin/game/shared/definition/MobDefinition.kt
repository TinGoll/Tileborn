package game.shared.definition

/** Static gameplay configuration for a mob type. */
data class MobDefinition(
    override val id: String,
    override val displayName: String,
    override val maxHealth: Float,
    override val movementSpeed: Float,
    override val collisionRadius: Float,
    val aggroRadius: Float,
    val attackRadius: Float,
    val attackDamage: Float,
    val attackCooldown: Float,
) : CharacterDefinition
