package game.shared.definition

/** Static configuration common to living, moving entity types. */
interface CharacterDefinition : EntityDefinition {
    val maxHealth: Float
    val movementSpeed: Float
    val collisionRadius: Float
}
