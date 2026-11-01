package shimeserver.shimepluginlibrary.shimemodule

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeapi.ShimeState
import shimeserver.shimepluginlibrary.shimeexception.ShimeServiceException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.concurrent.ConcurrentHashMap

/**
 * 任意機能モジュールのライフサイクルを調整する。
 *
 * 登録と有効化は別操作である。マネージャーはモジュールを初期化する前に不足している依存関係と
 * 循環を検出し、他の有効なモジュールからまだ必要とされているモジュールの無効化を拒否する。
 */
class ShimeModuleManager(
    private val logger: ShimeLogger
) : ShimeAbstractService(SERVICE_NAME) {
    private val modules = ConcurrentHashMap<String, ShimeModule>()

    override fun onInitialize(context: ShimeContext) {
        enableAll(context)
    }

    override fun onShutdown() {
        disableAll()
        modules.clear()
    }

    fun register(module: ShimeModule, replace: Boolean = false) {
        val id = module.moduleId
        if (id.isBlank()) {
            throw ShimeServiceException("A module with a blank id cannot be registered.")
        }
        val previous = modules.put(id, module)
        if (previous != null && !replace) {
            modules[id] = previous
            throw ShimeServiceException("Module '$id' is already registered.")
        }
        if (previous != null && previous.state == ShimeState.READY) {
            modules[id] = previous
            throw ShimeServiceException("Module '$id' is already enabled and cannot be replaced safely.")
        }
    }

    fun unregister(id: String): Boolean = modules.remove(id) != null

    fun get(id: String): ShimeModule? = modules[id]

    fun ids(): Set<String> = modules.keys.toSet()

    fun states(): Map<String, ShimeState> = modules.mapValues { (_, module) -> module.state }

    /** 登録済みのすべてのモジュールを依存順に有効化する。 */
    fun enableAll(context: ShimeContext) {
        resolveOrder().forEach { module ->
            if (module.state == ShimeState.READY) {
                return@forEach
            }
            try {
                logger.info(ShimeLogCategory.MODULE, "Initializing module '${module.moduleId}'.")
                module.initialize(context)
            } catch (throwable: Throwable) {
                logger.error(ShimeLogCategory.MODULE, "Module '${module.moduleId}' failed to initialize.", throwable)
                throw ShimeServiceException("Module '${module.moduleId}' failed to initialize.", throwable)
            }
        }
    }

    /** 1 つのモジュールとその依存関係を有効化する。 */
    fun enable(id: String, context: ShimeContext): Boolean {
        val ordered = resolveFor(id)
        var enabledAny = false
        ordered.forEach { module ->
            if (module.state != ShimeState.READY) {
                module.initialize(context)
                enabledAny = true
            }
        }
        return enabledAny
    }

    /** 有効なモジュールが依存していない場合に、1 つのモジュールを無効化する。 */
    fun disable(id: String): Boolean {
        val module = modules[id] ?: return false
        val dependants = modules.values.filter { other ->
            other.moduleId != id && other.state == ShimeState.READY && id in other.moduleDependencies
        }
        if (dependants.isNotEmpty()) {
            throw ShimeServiceException(
                "Module '$id' cannot be disabled while required by " +
                    dependants.joinToString { it.moduleId } + "."
            )
        }
        if (module.state != ShimeState.READY && module.state != ShimeState.FAILED) {
            return false
        }
        module.shutdown()
        return true
    }

    /** すべてのモジュールを依存関係の逆順で無効化する。個々の失敗はログに記録される。 */
    fun disableAll() {
        val ordered = try {
            resolveOrder()
        } catch (exception: ShimeServiceException) {
            logger.warn(ShimeLogCategory.MODULE, "Could not resolve module order during shutdown; using registration order.")
            modules.values.toList()
        }
        ordered.asReversed().forEach { module ->
            if (module.state != ShimeState.READY && module.state != ShimeState.FAILED) {
                return@forEach
            }
            try {
                module.shutdown()
            } catch (throwable: Throwable) {
                logger.error(ShimeLogCategory.MODULE, "Module '${module.moduleId}' failed to shut down.", throwable)
            }
        }
    }

    /** 登録済みのすべてのモジュールについて、安定した初期化順序を解決する。 */
    fun resolveOrder(): List<ShimeModule> {
        val snapshot = LinkedHashMap(modules)
        val result = ArrayList<ShimeModule>(snapshot.size)
        val visiting = LinkedHashSet<String>()
        val visited = HashSet<String>()

        fun visit(module: ShimeModule) {
            if (module.moduleId in visited) {
                return
            }
            if (!visiting.add(module.moduleId)) {
                throw ShimeServiceException(
                    "Circular module dependency detected: ${visiting.joinToString(" -> ")} -> ${module.moduleId}."
                )
            }
            module.moduleDependencies.sorted().forEach { dependency ->
                val dependencyModule = snapshot[dependency] ?: throw ShimeServiceException(
                    "Module '${module.moduleId}' depends on '$dependency', which is not registered."
                )
                visit(dependencyModule)
            }
            visiting.remove(module.moduleId)
            visited.add(module.moduleId)
            result.add(module)
        }

        snapshot.values
            .sortedWith(compareByDescending<ShimeModule> { it.modulePriority }.thenBy { it.moduleId })
            .forEach(::visit)
        return result
    }

    private fun resolveFor(id: String): List<ShimeModule> {
        modules[id] ?: throw ShimeServiceException("Module '$id' is not registered.")
        val closure = LinkedHashSet<String>()
        collectDependencies(id, closure)
        val ordered = resolveOrder()
        return ordered.filter { it.moduleId in closure }
    }

    private fun collectDependencies(id: String, target: MutableSet<String>) {
        if (!target.add(id)) {
            return
        }
        val module = modules[id] ?: return
        module.moduleDependencies.forEach { dependency -> collectDependencies(dependency, target) }
    }

    companion object {
        const val SERVICE_NAME = "module"
    }
}
