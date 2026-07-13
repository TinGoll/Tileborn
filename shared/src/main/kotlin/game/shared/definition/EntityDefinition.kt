package game.shared.definition

/** Immutable, data-driven configuration shared by all instances of an entity type. */
interface EntityDefinition {
    val id: String
    val displayName: String
}
