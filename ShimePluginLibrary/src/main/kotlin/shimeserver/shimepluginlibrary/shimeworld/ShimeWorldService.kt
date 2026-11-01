package shimeserver.shimepluginlibrary.shimeworld

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeexception.ShimeValidationException
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.concurrent.CompletableFuture

/** Paper の非同期テレポート API に配慮したワールドおよび座標のヘルパー。 */
class ShimeWorldService : ShimeAbstractService(SERVICE_NAME) {
    override fun onInitialize(context: ShimeContext) {
        // ワールドの状態は Bukkit が管理する。このサービスはステートレスである。
    }

    fun world(name: String): World? = Bukkit.getWorld(name)

    fun requireWorld(name: String): World {
        return world(name) ?: throw ShimeValidationException("World '$name' is not loaded.")
    }

    fun worlds(): List<World> = Bukkit.getWorlds().toList()

    fun isLoaded(name: String): Boolean = world(name) != null

    fun location(location: ShimeLocation): Location = location.requireLocation()

    fun teleport(player: Player, location: Location): CompletableFuture<Boolean> {
        return player.teleportAsync(location)
    }

    fun teleport(player: Player, location: ShimeLocation): CompletableFuture<Boolean> {
        return teleport(player, location.requireLocation())
    }

    fun nearbyEntities(location: Location, radius: Double): Collection<Entity> {
        if (radius <= 0) {
            return emptyList()
        }
        val world = location.world ?: return emptyList()
        return world.getNearbyEntities(location, radius, radius, radius)
    }

    fun distance(first: Location, second: Location): Double {
        if (first.world !== second.world) {
            return Double.POSITIVE_INFINITY
        }
        return first.distance(second)
    }

    fun isChunkLoaded(location: Location): Boolean {
        val world = location.world ?: return false
        return world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)
    }

    companion object {
        const val SERVICE_NAME = "world"
    }
}
