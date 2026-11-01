package shimeserver.shimepluginlibrary.shimeintegration

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.concurrent.ConcurrentHashMap

/**
 * 任意インテグレーションのレジストリ。
 *
 * 失敗したインテグレーションは分離される。例外はログに記録され、ステータスは
 * [ShimeIntegrationStatus.FAILED] になり、他のインテグレーションは動作を続ける。インテグレーションは
 * 設計上任意であるため、これは重要である。
 */
class ShimeIntegrationManager(
    private val logger: ShimeLogger,
    private val autoEnable: Boolean = false
) : ShimeAbstractService(SERVICE_NAME) {
    private val integrations = ConcurrentHashMap<String, ShimeIntegration>()
    private val statuses = ConcurrentHashMap<String, ShimeIntegrationStatus>()

    override fun onInitialize(context: ShimeContext) {
        if (autoEnable) {
            enableAllAvailable(context)
        }
    }

    override fun onShutdown() {
        disableAll()
        integrations.clear()
        statuses.clear()
    }

    fun register(integration: ShimeIntegration, replace: Boolean = false) {
        val id = integration.id
        if (id.isBlank()) {
            throw IllegalArgumentException("Integration id must not be blank.")
        }
        val previous = integrations.put(id, integration)
        if (previous != null && !replace) {
            integrations[id] = previous
            throw IllegalArgumentException("Integration '$id' is already registered.")
        }
        if (previous != null && previous !== integration) {
            safeDisable(previous)
        }
        statuses[id] = if (integration.isAvailable()) {
            ShimeIntegrationStatus.DISABLED
        } else {
            ShimeIntegrationStatus.UNAVAILABLE
        }
    }

    fun unregister(id: String): Boolean {
        val integration = integrations.remove(id) ?: return false
        safeDisable(integration)
        statuses.remove(id)
        return true
    }

    fun get(id: String): ShimeIntegration? = integrations[id]

    fun ids(): Set<String> = integrations.keys.toSet()

    fun status(id: String): ShimeIntegrationStatus? = statuses[id]

    fun statuses(): Map<String, ShimeIntegrationStatus> = statuses.toMap()

    fun enabledIds(): Set<String> {
        return statuses.filterValues { it == ShimeIntegrationStatus.ENABLED }.keys
    }

    /** 対象が利用可能な場合に [id] を有効化する。その後インテグレーションが有効であれば true を返す。 */
    fun enable(id: String, context: ShimeContext): Boolean {
        val integration = integrations[id]
            ?: throw IllegalArgumentException("Integration '$id' is not registered.")
        if (statuses[id] == ShimeIntegrationStatus.ENABLED) {
            return true
        }
        if (!integration.isAvailable()) {
            statuses[id] = ShimeIntegrationStatus.UNAVAILABLE
            logger.debug(ShimeLogCategory.INTEGRATION, "Integration '$id' is unavailable; skipping enable.")
            return false
        }
        return try {
            integration.enable(context)
            statuses[id] = ShimeIntegrationStatus.ENABLED
            logger.info(ShimeLogCategory.INTEGRATION, "Enabled integration '$id'.")
            true
        } catch (throwable: Throwable) {
            statuses[id] = ShimeIntegrationStatus.FAILED
            logger.error(ShimeLogCategory.INTEGRATION, "Integration '$id' failed to enable.", throwable)
            false
        }
    }

    /** 利用可能なすべてのインテグレーションを有効化する。この呼び出しで有効化されたインテグレーションの数を返す。 */
    fun enableAllAvailable(context: ShimeContext): Int {
        var enabled = 0
        integrations.keys.sorted().forEach { id ->
            if (statuses[id] != ShimeIntegrationStatus.ENABLED && enable(id, context)) {
                enabled += 1
            }
        }
        return enabled
    }

    /** [id] を無効化する。呼び出し前に有効だった場合は true を返す。 */
    fun disable(id: String): Boolean {
        val integration = integrations[id] ?: return false
        val wasEnabled = statuses[id] == ShimeIntegrationStatus.ENABLED
        safeDisable(integration)
        statuses[id] = if (integration.isAvailable()) {
            ShimeIntegrationStatus.DISABLED
        } else {
            ShimeIntegrationStatus.UNAVAILABLE
        }
        return wasEnabled
    }

    fun disableAll() {
        integrations.keys.sorted().forEach { id ->
            val integration = integrations[id] ?: return@forEach
            if (statuses[id] == ShimeIntegrationStatus.ENABLED) {
                safeDisable(integration)
                statuses[id] = if (integration.isAvailable()) {
                    ShimeIntegrationStatus.DISABLED
                } else {
                    ShimeIntegrationStatus.UNAVAILABLE
                }
            }
        }
    }

    private fun safeDisable(integration: ShimeIntegration) {
        try {
            integration.disable()
        } catch (throwable: Throwable) {
            logger.error(ShimeLogCategory.INTEGRATION, "Integration '${integration.id}' failed to disable.", throwable)
        }
    }

    companion object {
        const val SERVICE_NAME = "integration"
    }
}
