package game.shared.definition

/** Static gameplay configuration for an item type. */
data class ItemDefinition(
    override val id: String,
    override val displayName: String,
    val type: ItemType,
    val maxStackSize: Int,
) : EntityDefinition

enum class ItemType {
    CONSUMABLE,
    EQUIPMENT,
    MATERIAL,
    QUEST,
}
