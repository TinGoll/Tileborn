package game.shared.definition

/** Read-only lookup for validated gameplay definitions. */
class DefinitionRegistry(
    mobs: List<MobDefinition>,
    items: List<ItemDefinition>,
    validator: DefinitionValidator = DefinitionValidator(),
) {
    private val mobsById: Map<String, MobDefinition>
    private val itemsById: Map<String, ItemDefinition>
    private val definitionsById: Map<String, EntityDefinition>

    init {
        validator.validate(mobs, items)
        mobsById = mobs.associateBy(MobDefinition::id)
        itemsById = items.associateBy(ItemDefinition::id)
        definitionsById = mobsById + itemsById
    }

    val mobCount: Int get() = mobsById.size
    val itemCount: Int get() = itemsById.size

    operator fun get(definitionId: String): EntityDefinition? = definitionsById[definitionId]

    fun getMob(definitionId: String): MobDefinition? = mobsById[definitionId]

    fun requireMob(definitionId: String): MobDefinition =
        getMob(definitionId) ?: error("Unknown mob definitionId '$definitionId'")

    fun getItem(definitionId: String): ItemDefinition? = itemsById[definitionId]

    fun requireItem(definitionId: String): ItemDefinition =
        getItem(definitionId) ?: error("Unknown item definitionId '$definitionId'")

    companion object {
        fun empty(): DefinitionRegistry = DefinitionRegistry(emptyList(), emptyList())
    }
}
