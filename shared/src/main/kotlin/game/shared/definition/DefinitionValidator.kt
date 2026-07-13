package game.shared.definition

/** Fails startup validation before invalid static gameplay data can enter a world. */
class DefinitionValidator {
    fun validate(mobs: List<MobDefinition>, items: List<ItemDefinition>) {
        mobs.forEach(::validateMob)
        items.forEach(::validateItem)
        validateUniqueIds(mobs, items)
    }

    private fun validateMob(definition: MobDefinition) {
        validateIdentity(definition, "mob")
        requirePositive(definition.id, "maxHealth", definition.maxHealth)
        requirePositive(definition.id, "movementSpeed", definition.movementSpeed)
        requirePositive(definition.id, "collisionRadius", definition.collisionRadius)
        requireNonNegative(definition.id, "aggroRadius", definition.aggroRadius)
        requirePositive(definition.id, "attackRadius", definition.attackRadius)
        requireNonNegative(definition.id, "attackDamage", definition.attackDamage)
        requirePositive(definition.id, "attackCooldown", definition.attackCooldown)
    }

    private fun validateItem(definition: ItemDefinition) {
        validateIdentity(definition, "item")
        val type: ItemType? = definition.type
        if (type == null) invalid(definition.id, "type must be specified")
        if (definition.maxStackSize <= 0) {
            invalid(definition.id, "maxStackSize must be greater than zero, was ${definition.maxStackSize}")
        }
    }

    private fun validateIdentity(definition: EntityDefinition, kind: String) {
        val id: String? = definition.id
        if (id.isNullOrBlank()) throw DefinitionValidationException("$kind definition id must not be blank")
        val displayName: String? = definition.displayName
        if (displayName.isNullOrBlank()) invalid(id, "displayName must not be blank")
    }

    private fun validateUniqueIds(mobs: List<MobDefinition>, items: List<ItemDefinition>) {
        val firstKindById = mutableMapOf<String, String>()
        val definitions = mobs.map { it.id to "mob" } + items.map { it.id to "item" }
        definitions.forEach { (id, kind) ->
            val previousKind = firstKindById.putIfAbsent(id, kind)
            if (previousKind != null) {
                throw DefinitionValidationException(
                    "Duplicate definition id '$id' ($previousKind and $kind definitions)",
                )
            }
        }
    }

    private fun requirePositive(id: String, field: String, value: Float) {
        if (!value.isFinite() || value <= 0f) invalid(id, "$field must be finite and greater than zero, was $value")
    }

    private fun requireNonNegative(id: String, field: String, value: Float) {
        if (!value.isFinite() || value < 0f) invalid(id, "$field must be finite and non-negative, was $value")
    }

    private fun invalid(id: String, reason: String): Nothing =
        throw DefinitionValidationException("Invalid definition '$id': $reason")
}

class DefinitionValidationException(message: String) : IllegalArgumentException(message)
