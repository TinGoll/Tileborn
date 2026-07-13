package game.shared.definition

import com.badlogic.gdx.files.FileHandle
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.Reader

/** Loads JSON definitions and returns a registry only after the whole data set is valid. */
class DefinitionLoader {
    private val gson = Gson()
    fun load(mobsFile: FileHandle, itemsFile: FileHandle): DefinitionRegistry =
        mobsFile.reader(Charsets.UTF_8.name()).use { mobsReader ->
            itemsFile.reader(Charsets.UTF_8.name()).use { itemsReader ->
                load(mobsReader, itemsReader)
            }
        }

    fun load(mobsReader: Reader, itemsReader: Reader): DefinitionRegistry =
        DefinitionRegistry(
            mobs = readDefinitions(
                reader = mobsReader,
                kind = "mob",
                type = MobDefinition::class.java,
                requiredFields = MOB_FIELDS,
            ),
            items = readDefinitions(
                reader = itemsReader,
                kind = "item",
                type = ItemDefinition::class.java,
                requiredFields = ITEM_FIELDS,
            ),
        )

    private fun <T : Any> readDefinitions(
        reader: Reader,
        kind: String,
        type: Class<T>,
        requiredFields: Set<String>,
    ): List<T> {
        val root = try {
            JsonParser.parseReader(reader)
        } catch (exception: JsonParseException) {
            throw DefinitionLoadException("Could not parse $kind definitions JSON", exception)
        }
        if (!root.isJsonArray) throw DefinitionLoadException("$kind definitions JSON must be an array")

        return root.asJsonArray.mapIndexed { index, element ->
            if (!element.isJsonObject) {
                throw DefinitionLoadException("$kind definition at index $index must be a JSON object")
            }
            val jsonObject = element.asJsonObject
            requiredFields.forEach { field ->
                if (!jsonObject.has(field) || jsonObject[field].isJsonNull) {
                    throw DefinitionLoadException("$kind definition at index $index is missing required field '$field'")
                }
            }
            try {
                gson.fromJson(jsonObject, type)
                    ?: throw DefinitionLoadException("$kind definition at index $index must not be null")
            } catch (exception: DefinitionLoadException) {
                throw exception
            } catch (exception: RuntimeException) {
                throw DefinitionLoadException("Could not parse $kind definition at index $index", exception)
            }
        }
    }

    private companion object {
        val MOB_FIELDS = setOf(
            "id",
            "displayName",
            "maxHealth",
            "movementSpeed",
            "collisionRadius",
            "aggroRadius",
            "attackRadius",
            "attackDamage",
            "attackCooldown",
        )
        val ITEM_FIELDS = setOf("id", "displayName", "type", "maxStackSize")
    }
}

class DefinitionLoadException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
