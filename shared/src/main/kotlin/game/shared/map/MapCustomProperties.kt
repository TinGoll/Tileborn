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

    fun requireInt(name: String, minimum: Int = Int.MIN_VALUE): Int {
        val value = properties.get(name)?.toString()?.trim()?.toIntOrNull()
        require(value != null && value >= minimum) {
            "$context requires an integer '$name' custom property >= $minimum"
        }
        return value
    }

    fun requireFloat(name: String, minimum: Float = -Float.MAX_VALUE): Float {
        val value = properties.get(name)?.toString()?.trim()?.toFloatOrNull()
        require(value != null && value.isFinite() && value >= minimum) {
            "$context requires a finite number '$name' custom property >= $minimum"
        }
        return value
    }
}
