package game.shared.definition

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefinitionLoaderTest {
    @Test
    fun `loads valid mob and item JSON`() {
        val registry = load(MOB_JSON, ITEM_JSON)

        assertEquals(1, registry.mobCount)
        assertEquals(1, registry.itemCount)
        assertEquals("Training Slime", registry.requireMob("training_slime").displayName)
        assertEquals(ItemType.MATERIAL, registry.requireItem("slime_gel").type)
    }

    @Test
    fun `rejects duplicate definition id`() {
        val duplicateItemJson = ITEM_JSON.replace("slime_gel", "training_slime")

        val exception = assertThrows(DefinitionValidationException::class.java) {
            load(MOB_JSON, duplicateItemJson)
        }

        assertTrue(exception.message.orEmpty().contains("Duplicate definition id 'training_slime'"))
    }

    @Test
    fun `rejects negative health or cooldown`() {
        listOf(
            MOB_JSON.replace("\"maxHealth\": 30.0", "\"maxHealth\": -1.0") to "maxHealth",
            MOB_JSON.replace("\"attackCooldown\": 1.25", "\"attackCooldown\": -0.5") to "attackCooldown",
        ).forEach { (mobJson, expectedField) ->
            val exception = assertThrows(DefinitionValidationException::class.java) {
                load(mobJson, ITEM_JSON)
            }
            assertTrue(exception.message.orEmpty().contains(expectedField))
        }
    }

    @Test
    fun `gets definition by id and reports an unknown id clearly`() {
        val registry = load(MOB_JSON, ITEM_JSON)

        assertSame(registry.requireMob("training_slime"), registry["training_slime"])
        val exception = assertThrows(IllegalStateException::class.java) {
            registry.requireMob("missing_mob")
        }
        assertEquals("Unknown mob definitionId 'missing_mob'", exception.message)
    }

    private fun load(mobsJson: String, itemsJson: String): DefinitionRegistry =
        DefinitionLoader().load(StringReader(mobsJson), StringReader(itemsJson))

    private companion object {
        val MOB_JSON =
            """
            [
              {
                "id": "training_slime",
                "displayName": "Training Slime",
                "maxHealth": 30.0,
                "movementSpeed": 2.0,
                "collisionRadius": 0.35,
                "aggroRadius": 6.0,
                "attackRadius": 0.8,
                "attackDamage": 5.0,
                "attackCooldown": 1.25
              }
            ]
            """.trimIndent()

        val ITEM_JSON =
            """
            [
              {
                "id": "slime_gel",
                "displayName": "Slime Gel",
                "type": "MATERIAL",
                "maxStackSize": 99
              }
            ]
            """.trimIndent()
    }
}
