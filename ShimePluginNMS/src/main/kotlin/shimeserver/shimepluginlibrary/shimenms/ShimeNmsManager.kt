package shimeserver.shimepluginlibrary.shimenms

import shimeserver.shimepluginlibrary.shimeapi.ShimeContext
import shimeserver.shimepluginlibrary.shimeexception.ShimeLifecycleException
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogger
import shimeserver.shimepluginlibrary.shimepacket.ShimePacketManager
import shimeserver.shimepluginlibrary.shimeservice.ShimeAbstractService
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import java.util.concurrent.CopyOnWriteArrayList

/**
 * サーバーバージョンを検出し、互換性のある NMS アダプターを選択し、誠実な機能レポートを公開する
 * ライブラリサービス。
 *
 * NMS の可用性は、ライブラリのその他の部分が起動するために決して必須ではない。一致するアダプターが
 * ない場合、マネージャーは理由をログに記録し、[isAvailable] を false のままにする。その後パケット操作は、
 * 黙って何もしないのではなく、明確な [ShimeNmsUnsupportedException] で失敗する。
 *
 * アダプターはライブラリの初期化前に [registerAdapter] で追加するか、プラグインのクラスローダーから
 * [ServiceLoader] を通じて検出できる。すべてのアダプターはこのマネージャーによって初期化および
 * シャットダウンされる。
 */
class ShimeNmsManager(private val logger: ShimeLogger) : ShimeAbstractService(SERVICE_NAME) {
    private val registry = ShimeNmsAdapterRegistry(logger)
    private val reflection = ShimeReflection(logger)

    /** この NMS モジュールが所有するパケットフィルターとリスナー。 */
    val packets: ShimePacketManager = ShimePacketManager(logger)

    private val warnings = CopyOnWriteArrayList<String>()

    @Volatile
    private var detectedVersion: ShimeNmsVersion? = null

    @Volatile
    private var activeAdapter: ShimeNmsAdapter? = null

    override fun onInitialize(context: ShimeContext) {
        val version = ShimeVersionDetector.detect(context.plugin.server)
        detectedVersion = version
        logger.info(ShimeLogCategory.NMS, "Detected ${version.summary()}.")

        if (!version.isDetected) {
            addWarning("The Minecraft version could not be parsed from the server strings.")
        }
        if (!version.isSupported) {
            addWarning(
                "Minecraft ${version.minecraftVersion ?: "unknown"} is not the initially supported version " +
                    "${ShimeMinecraftVersion.PAPER_1_20_6}; only adapters that declare it can be used."
            )
        }

        registerBuiltInAdapter()
        loadExternalAdapters(context)

        val adapterContext = ShimeNmsAdapterContext(
            plugin = context.plugin,
            logger = logger,
            version = version,
            reflection = reflection,
            packets = packets
        )
        val candidates = registry.findCompatible(version)
        if (candidates.isEmpty()) {
            logger.warn(
                ShimeLogCategory.NMS,
                "No NMS adapter declares support for Minecraft ${version.minecraftVersion ?: "unknown"}. " +
                    "Version detection is available, but NMS-specific features are disabled."
            )
        }
        for (candidate in candidates) {
            if (tryInitialize(candidate, adapterContext)) {
                activeAdapter = candidate
                break
            }
        }

        val active = activeAdapter
        if (active == null) {
            logger.warn(
                ShimeLogCategory.NMS,
                "NMS features are unavailable. The library continues with Paper API features only."
            )
            return
        }
        packets.setTransport(active.packetTransport())
        val features = ShimeNmsFeature.entries.filter(active::supports)
        logger.info(
            ShimeLogCategory.NMS,
            "NMS adapter '${active.adapterId}' is active. Features: " +
                (if (features.isEmpty()) "none" else features.joinToString { it.label }) + "."
        )
    }

    override fun onShutdown() {
        packets.close()
        val adapter = activeAdapter
        activeAdapter = null
        if (adapter != null) {
            try {
                adapter.shutdown()
            } catch (throwable: Throwable) {
                logger.error(
                    ShimeLogCategory.NMS,
                    "NMS adapter '${adapter.adapterId}' failed to shut down cleanly.",
                    throwable
                )
            }
        }
        registry.clear()
        reflection.clearCache()
        detectedVersion = null
        warnings.clear()
    }

    /**
     * ライブラリの初期化前にアダプターを登録する。
     *
     * @throws ShimeLifecycleException 初期化後に呼ばれた場合。稼働中のアダプターを置き換えると、
     * すでに初期化済みの機能が古い状態を参照したままになるため。
     */
    @JvmOverloads
    fun registerAdapter(adapter: ShimeNmsAdapter, replace: Boolean = false) {
        if (state != shimeserver.shimepluginlibrary.shimeapi.ShimeState.UNINITIALIZED) {
            throw ShimeLifecycleException(
                "NMS adapters must be registered before the library is initialized; current state is $state."
            )
        }
        registry.register(adapter, replace)
    }

    /** 登録済みアダプターのスナップショットを返す。 */
    fun adapters(): List<ShimeNmsAdapter> = registry.adapters()

    /** 検出されたバージョンを返す。初期化前は `null`。 */
    fun versionOrNull(): ShimeNmsVersion? = detectedVersion

    /** 検出されたバージョンを返す。マネージャーが初期化されていない場合は例外を投げる。 */
    fun requireVersion(): ShimeNmsVersion {
        return detectedVersion ?: throw ShimeNmsException(
            "NMS version detection has not run. Initialize the library before requesting the version."
        )
    }

    /** 互換性のあるアダプターが稼働しているかどうかを返す。 */
    fun isAvailable(): Boolean = activeAdapter?.status() == ShimeNmsAdapterStatus.COMPATIBLE

    /** [feature] が稼働中のアダプターを通じて利用可能かどうかを返す。 */
    fun supports(feature: ShimeNmsFeature): Boolean = activeAdapter?.supports(feature) == true

    /** 稼働中のアダプターを返す。互換性のあるアダプターがない場合は例外を投げる。 */
    fun requireAdapter(): ShimeNmsAdapter {
        val adapter = activeAdapter
        if (adapter == null || adapter.status() != ShimeNmsAdapterStatus.COMPATIBLE) {
            throw ShimeNmsUnsupportedException(
                "No compatible NMS adapter is active. Check the server version and installed adapters."
            )
        }
        return adapter
    }

    /** 稼働中のアダプターのステータスを返す。アダプターが稼働していない場合は `null`。 */
    fun adapterStatus(): ShimeNmsAdapterStatus? = activeAdapter?.status()

    /**
     * 診断用スナップショットを構築する。
     *
     * @throws ShimeNmsException マネージャーがバージョン検出を完了していない場合。
     */
    fun report(): ShimeNmsReport {
        val version = requireVersion()
        val adapter = activeAdapter
        val features = if (adapter == null) {
            emptySet()
        } else {
            ShimeNmsFeature.entries.filter(adapter::supports).toSet()
        }
        return ShimeNmsReport(
            version = version,
            adapterId = adapter?.adapterId,
            adapterStatus = adapter?.status(),
            supportedFeatures = features,
            packetTransportAvailable = packets.supportsSending() || packets.supportsReceiving(),
            warnings = warnings.toList()
        )
    }

    private fun registerBuiltInAdapter() {
        try {
            registry.register(ShimePaper1206Adapter())
        } catch (exception: IllegalArgumentException) {
            logger.warn(ShimeLogCategory.NMS, "The built-in NMS adapter could not be registered.", exception)
        }
    }

    private fun loadExternalAdapters(context: ShimeContext) {
        val loader = context.plugin.javaClass.classLoader
        try {
            val iterator = ServiceLoader.load(ShimeNmsAdapter::class.java, loader).iterator()
            while (iterator.hasNext()) {
                val adapter = try {
                    iterator.next()
                } catch (error: ServiceConfigurationError) {
                    logger.error(ShimeLogCategory.NMS, "An external NMS adapter could not be loaded.", error)
                    addWarning("An external NMS adapter could not be loaded: ${error.message}")
                    continue
                }
                try {
                    registry.register(adapter)
                } catch (exception: IllegalArgumentException) {
                    logger.warn(
                        ShimeLogCategory.NMS,
                        "An external NMS adapter was ignored: ${exception.message}",
                        exception
                    )
                    addWarning("An external NMS adapter was ignored: ${exception.message}")
                }
            }
        } catch (error: ServiceConfigurationError) {
            logger.error(ShimeLogCategory.NMS, "The NMS adapter service list could not be read.", error)
            addWarning("The NMS adapter service list could not be read: ${error.message}")
        } catch (exception: SecurityException) {
            logger.error(ShimeLogCategory.NMS, "Access to NMS adapter services was denied.", exception)
            addWarning("Access to NMS adapter services was denied.")
        }
    }

    private fun tryInitialize(adapter: ShimeNmsAdapter, context: ShimeNmsAdapterContext): Boolean {
        return try {
            adapter.initialize(context)
            val status = adapter.status()
            if (status == ShimeNmsAdapterStatus.COMPATIBLE) {
                logger.debug(ShimeLogCategory.NMS, "NMS adapter '${adapter.adapterId}' initialized successfully.")
                true
            } else {
                logger.warn(
                    ShimeLogCategory.NMS,
                    "NMS adapter '${adapter.adapterId}' is not compatible (status $status)."
                )
                addWarning("NMS adapter '${adapter.adapterId}' is not compatible (status $status).")
                shutdownQuietly(adapter)
                false
            }
        } catch (throwable: Throwable) {
            logger.error(ShimeLogCategory.NMS, "NMS adapter '${adapter.adapterId}' failed to initialize.", throwable)
            addWarning("NMS adapter '${adapter.adapterId}' failed to initialize: ${throwable.message}")
            shutdownQuietly(adapter)
            false
        }
    }

    private fun shutdownQuietly(adapter: ShimeNmsAdapter) {
        try {
            adapter.shutdown()
        } catch (throwable: Throwable) {
            logger.warn(
                ShimeLogCategory.NMS,
                "NMS adapter '${adapter.adapterId}' failed to shut down after a rejected initialization.",
                throwable
            )
        }
    }

    private fun addWarning(message: String) {
        warnings += message
        logger.debug(ShimeLogCategory.NMS, "NMS warning: $message")
    }

    companion object {
        /** [shimeserver.shimepluginlibrary.shimeservice.ShimeServiceRegistry] で使用されるサービス名。 */
        const val SERVICE_NAME = "nms"
    }
}
