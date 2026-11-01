package shimeserver.shimepluginlibrary.shimeworld

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import shimeserver.shimepluginlibrary.shimeexception.ShimeValidationException

/** World オブジェクトへの強い参照を保持しない、シリアライズ可能なワールド座標。 */
data class ShimeLocation(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0.0f,
    val pitch: Float = 0.0f
) {
    init {
        require(world.isNotBlank()) { "World name must not be blank." }
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Coordinates must be finite numbers." }
        require(yaw.isFinite() && pitch.isFinite()) { "Rotation must be finite numbers." }
    }

    fun toLocation(): Location? {
        val bukkitWorld = Bukkit.getWorld(world) ?: return null
        return Location(bukkitWorld, x, y, z, yaw, pitch)
    }

    fun requireLocation(): Location {
        return toLocation() ?: throw ShimeValidationException("World '$world' is not loaded.")
    }

    fun distanceSquared(other: ShimeLocation): Double {
        if (world != other.world) {
            return Double.POSITIVE_INFINITY
        }
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }

    fun distance(other: ShimeLocation): Double = kotlin.math.sqrt(distanceSquared(other))

    fun withWorld(world: String): ShimeLocation = copy(world = world)

    fun serialize(): Map<String, Any?> = linkedMapOf(
        "world" to world,
        "x" to x,
        "y" to y,
        "z" to z,
        "yaw" to yaw,
        "pitch" to pitch
    )

    companion object {
        @JvmStatic
        fun fromLocation(location: Location): ShimeLocation {
            val world = location.world
                ?: throw ShimeValidationException("Cannot serialize a location without a world.")
            return ShimeLocation(
                world = world.name,
                x = location.x,
                y = location.y,
                z = location.z,
                yaw = location.yaw,
                pitch = location.pitch
            )
        }

        @JvmStatic
        fun deserialize(data: Map<String, Any?>): ShimeLocation {
            return ShimeLocation(
                world = data["world"] as? String ?: throw ShimeValidationException("Location is missing 'world'."),
                x = (data["x"] as? Number)?.toDouble() ?: throw ShimeValidationException("Location is missing 'x'."),
                y = (data["y"] as? Number)?.toDouble() ?: throw ShimeValidationException("Location is missing 'y'."),
                z = (data["z"] as? Number)?.toDouble() ?: throw ShimeValidationException("Location is missing 'z'."),
                yaw = (data["yaw"] as? Number)?.toFloat() ?: 0.0f,
                pitch = (data["pitch"] as? Number)?.toFloat() ?: 0.0f
            )
        }
    }
}
