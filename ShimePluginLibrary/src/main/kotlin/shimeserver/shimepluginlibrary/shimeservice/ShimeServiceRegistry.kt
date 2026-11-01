package shimeserver.shimepluginlibrary.shimeservice

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeapi.ShimeState
import shimeserver.shimepluginlibrary.shimeexception.ShimeServiceException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * サービスのレジストリ兼ライフサイクルコーディネーター。
 *
 * 登録と初期化は意図的に分離されている。まずすべてのコアサービスが登録され、
 * その後レジストリが依存関係を解決し、循環を検出して、決定的な順序で初期化する。
 */
class ShimeServiceRegistry(private val logger: ShimeLogger) {
    private val lock = ReentrantLock()
    private val services = LinkedHashMap<String, ShimeService>()

    fun register(service: ShimeService, replace: Boolean = false) {
        val name = service.serviceName
        if (name.isBlank()) {
            throw ShimeServiceException("A service with a blank name cannot be registered.")
        }
        lock.withLock {
            val existing = services[name]
            if (existing != null && !replace) {
                throw ShimeServiceException(
                    "Service '$name' is already registered by ${existing.javaClass.name}. " +
                        "Use replace=true only when replacing it is intentional."
                )
            }
            if (existing != null && existing.state == ShimeState.READY) {
                throw ShimeServiceException(
                    "Service '$name' is already initialized and cannot be replaced safely. Shut it down first."
                )
            }
            services[name] = service
        }
        logger.debug(ShimeLogCategory.SERVICE, "Registered service '$name' (${service.javaClass.simpleName}).")
    }

    fun unregister(name: String): Boolean {
        lock.withLock {
            return services.remove(name) != null
        }
    }

    fun getByName(name: String): ShimeService? = lock.withLock { services[name] }

    fun <T : ShimeService> get(type: Class<T>): T? {
        val candidates = lock.withLock { services.values.toList() }
        return candidates.firstOrNull { type.isInstance(it) }?.let(type::cast)
    }

    fun <T : ShimeService> require(type: Class<T>): T {
        return get(type) ?: throw ShimeServiceException(
            "Service ${type.name} is not registered. Available services: ${names().joinToString().ifBlank { "none" }}."
        )
    }

    fun requireByName(name: String): ShimeService {
        return getByName(name) ?: throw ShimeServiceException(
            "Service '$name' is not registered. Available services: ${names().joinToString().ifBlank { "none" }}."
        )
    }

    fun isRegistered(name: String): Boolean = lock.withLock { services.containsKey(name) }

    fun names(): List<String> = lock.withLock { services.keys.toList() }

    fun snapshot(): Map<String, ShimeState> = lock.withLock {
        services.mapValues { (_, service) -> service.state }
    }

    /**
     * 登録済みのすべてのサービスを依存関係順に初期化する。
     *
     * 失敗すると初期化は停止し、すでに初期化されたサービスは READY のまま残る。呼び出し元は
     * それらを解放するために [shutdownAll] を呼び出すべきである。スローされる例外には失敗したサービスの名前が含まれる。
     */
    fun initializeAll(context: ShimeContext) {
        val ordered = resolveOrder()
        for (service in ordered) {
            logger.info(ShimeLogCategory.SERVICE, "Initializing service '${service.serviceName}'.")
            try {
                service.initialize(context)
            } catch (throwable: Throwable) {
                logger.error(
                    ShimeLogCategory.SERVICE,
                    "Service '${service.serviceName}' failed to initialize. " +
                        "The library cannot continue with an inconsistent service graph.",
                    throwable
                )
                throw ShimeServiceException("Service '${service.serviceName}' failed to initialize.", throwable)
            }
        }
    }

    /** サービスを初期化の逆順で停止する。個々の失敗はログに記録され、クリーンアップを止めない。 */
    fun shutdownAll() {
        val ordered = try {
            resolveOrder()
        } catch (exception: ShimeServiceException) {
            logger.warn(ShimeLogCategory.SERVICE, "Could not resolve service order during shutdown; using registration order.")
            lock.withLock { services.values.toList() }
        }
        for (service in ordered.asReversed()) {
            if (service.state == ShimeState.STOPPED || service.state == ShimeState.UNINITIALIZED) {
                continue
            }
            try {
                service.shutdown()
            } catch (throwable: Throwable) {
                logger.error(ShimeLogCategory.SERVICE, "Service '${service.serviceName}' failed to shut down.", throwable)
            }
        }
    }

    /**
     * 安定した初期化順序を解決する。依存先は依存元より先に訪問され、優先度が等しい
     * サービスは登録順を保つ。
     */
    fun resolveOrder(): List<ShimeService> {
        val registered = lock.withLock { LinkedHashMap(services) }
        val result = ArrayList<ShimeService>(registered.size)
        val visiting = LinkedHashSet<String>()
        val visited = HashSet<String>()

        fun visit(service: ShimeService) {
            if (service.serviceName in visited) {
                return
            }
            if (!visiting.add(service.serviceName)) {
                throw ShimeServiceException(
                    "Circular service dependency detected: ${visiting.joinToString(" -> ")} -> ${service.serviceName}."
                )
            }
            for (dependency in service.serviceDependencies.sorted()) {
                val dependencyService = registered[dependency] ?: throw ShimeServiceException(
                    "Service '${service.serviceName}' depends on '$dependency', which is not registered."
                )
                visit(dependencyService)
            }
            visiting.remove(service.serviceName)
            visited.add(service.serviceName)
            result.add(service)
        }

        registered.values
            .sortedWith(compareByDescending<ShimeService> { it.servicePriority }.thenBy { it.serviceName })
            .forEach(::visit)
        return result
    }
}
