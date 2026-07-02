package game.shared.map

import com.badlogic.gdx.maps.MapProperties

/** Strict custom-property access keeps malformed Tiled gameplay data from being ignored. */
class MapCustomProperties(
    private val properties: MapProperties,
    private val context: String,
) {
    fun requireString(name: String): String {
        val value = properties.get(name)?.toString()?.trim()
        require(!value.isNullOrEmpty()) {
            "$context requires a non-empty '$name' custom property"
        }
        return value
    }

    fun string(name: String, defaultValue: String): String =
        properties.get(name)?.toString()?.trim()?.takeIf(String::isNotEmpty) ?: defaultValue
}
