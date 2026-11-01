# API ガイド

このドキュメントは `ShimePluginLibrary` の公開 API を機能ごとにまとめたものです。
すべての例は Kotlin を前提としていますが、公開クラスは Java からも利用できるように
`@JvmStatic` や `@JvmOverloads` を適切に付けています。

## 目次

1. [エントリーポイント](#エントリーポイント)
2. [ライフサイクルとサービス](#ライフサイクルとサービス)
3. [設定](#設定)
4. [キャッシュ](#キャッシュ)
5. [ストレージ](#ストレージ)
6. [データベース](#データベース)
7. [メッセージ](#メッセージ)
8. [スケジューラーとタスク](#スケジューラーとタスク)
9. [プレイヤー](#プレイヤー)
10. [アイテム](#アイテム)
11. [GUI](#gui)
12. [スコアボード](#スコアボード)
13. [経済](#経済)
14. [ワールドとエンティティ](#ワールドとエンティティ)
15. [インベントリ](#インベントリ)
16. [コマンド](#コマンド)
17. [モジュール](#モジュール)
18. [外部連携](#外部連携)
19. [NMS とパケット](#nms-とパケット)
20. [ユーティリティと検証](#ユーティリティと検証)

## エントリーポイント

### `ShimeLibrary`

`shimeserver.shimepluginlibrary.shimeapi.ShimeLibrary`

ライブラリの中心となるファサードです。`ShimePlugin` が起動時に構築し、初期化します。
利用側プラグインは `ShimeLibrary.get()` で取得してください。

```kotlin
val library = ShimeLibrary.get()

if (!library.isReady) {
    logger.warning("ShimePluginLibrary is not ready.")
    return
}
```

| メンバー | 説明 |
| --- | --- |
| `get()` | 初期化済みインスタンスを返す。未初期化なら `ShimeLifecycleException`。 |
| `getOrNull()` | 準備完了時のみインスタンスを返す。 |
| `state` | `ShimeState`。 |
| `isReady` | `state == READY`。 |
| `context` | サービスへ渡される `ShimeContext`。 |
| `registerService(service, replace)` | 初期化前に追加サービスを登録。 |
| `initialize()` / `shutdown()` | 通常は `ShimePlugin` が呼び出す。 |
| `requireReady(operation)` | 準備完了でなければ例外。 |
| `service(type)` / `serviceByName(name)` | 登録済みサービスを取得。 |
| `serviceStates()` | サービス名と状態のスナップショット。 |

### `ShimeContext`

`plugin`、`library`、`logger`、`scheduler`、`dataFolder` をまとめた不変のコンテキストです。
サービスやモジュールの初期化時に渡されます。

## ライフサイクルとサービス

### `ShimeService`

```kotlin
class MyService : ShimeAbstractService("my-service") {
    override val serviceDependencies: Set<String> = setOf("database")
    override val servicePriority: Int = 10

    override fun onInitialize(context: ShimeContext) {
        // 初期化処理
    }

    override fun onShutdown() {
        // 解放処理。冪等にすること。
    }
}
```

`ShimeServiceRegistry` は次を保証します。

- サービス名の重複を拒否する。
- 依存先が未登録なら初期化前に `ShimeServiceException` をスローする。
- 循環依存を検出して拒否する。
- 初期化は依存関係順、停止は逆順。
- 1 つのサービスの停止失敗が残りの停止を妨げない。

### `ShimeState`

`UNINITIALIZED`、`INITIALIZING`、`READY`、`STOPPING`、`STOPPED`、`FAILED`。
`isUsable` は `READY` のみ `true`、`isTerminal` は `STOPPED` のみ `true` です。

## 設定

### `ShimeConfigManager`

```kotlin
val config = library.configManager.create("sample", "sample.yml", loadNow = true)
val value = config.getString("greeting", "Hello") ?: "Hello"
config.set("greeting", "こんにちは")
config.save()
```

| メソッド | 説明 |
| --- | --- |
| `create(name, resourcePath, loadNow)` | 設定を作成し、JAR 内リソースを必要に応じてコピー。 |
| `register(config, replace)` | 既存の `ShimeConfig` を登録。 |
| `get(name)` / `require(name)` | 設定を取得。 |
| `reloadAll()` / `saveAll()` | すべての設定を再読み込み・保存。 |
| `names()` | 登録名の一覧。 |

### `ShimeConfig`

`getString`、`getInt`、`getLong`、`getDouble`、`getBoolean`、`getStringList`、
`getSection`、`getKeys`、`set`、`remove`、`setComments`、`getComments` を提供します。
`migrate(targetVersion, steps)` で `config-version` に基づく移行ができます。

```kotlin
config.migrate(2, mapOf(
    1 to { section -> section.set("new-key", "default") }
))
```

すべての読み書きは内部ロックで保護されていますが、複数ファイルにまたがる一貫性は保証しません。

## キャッシュ

### `ShimeCache`

```kotlin
interface ShimeCache<K : Any, V : Any> {
    fun get(key: K): V?
    fun put(key: K, value: V, ttlMillis: Long? = null): V?
    fun remove(key: K): V?
    fun contains(key: K): Boolean
    fun clear()
    fun size(): Int
    fun keys(): Set<K>
    fun stats(): ShimeCacheStats
}
```

`ShimeMemoryCache` は最大サイズと既定 TTL を持ちます。TTL が 0 の場合は無期限です。
`ShimeCacheStats` はヒット、ミス、追い出し、期限切れ、サイズ、容量、ヒット率を返します。

### `ShimeCacheManager`

```kotlin
val cache = library.cacheManager.create<String, Int>("balances", maxSize = 500, defaultTtlMillis = 60_000)
cache.put("alice", 100)

val loading = library.cacheManager.createLoading<String, String>("names", loader = { key ->
    // 重い処理。同じキーの同時リクエストは 1 回のロードに集約される。
    key.uppercase()
})
```

`ShimeLoadingCache.getOrLoadAsync(key, executor)` は `CompletableFuture` を返します。
`loadCount()` はローダーが成功した回数、`loadFailureCount()` は失敗回数です。

## ストレージ

### `ShimeStorage`

キーごとに 1 件のレコードを保存する抽象です。

```kotlin
interface ShimeStorage<T : Any> : AutoCloseable {
    val name: String
    fun load(key: String): T?
    fun save(key: String, value: T)
    fun delete(key: String): Boolean
    fun exists(key: String): Boolean
    fun keys(): Set<String>
}
```

### `ShimeStorageManager`

```kotlin
val profiles = library.storageManager.createYaml("profiles", ShimePlayerProfileCodec)
profiles.save("uuid", profile)

val json = library.storageManager.createJson("records", MyRecord::class.java)
```

- `ShimeYamlStorage` はキーごとに 1 ファイルを作成します。
- `ShimeJsonStorage` は Gson を使用します。
- キーは検証され、ディレクトリ外へのパス脱出は拒否されます。
- 書き込みは一時ファイル経由で行われ、可能な限り原子的に置き換えます。

## データベース

### `ShimeDatabaseSettings`

```kotlin
val settings = ShimeDatabaseSettings(
    type = ShimeDatabaseType.MYSQL,
    host = "127.0.0.1",
    port = 3306,
    database = "minecraft",
    username = "app",
    password = "secret",
    poolSize = 6,
    useSsl = true
)
```

`ShimeDatabaseType` は `SQLITE`、`MYSQL`、`MARIADB` を持ちます。SQLite では
`sqliteFile` を指定し、プールサイズは 1 に制限されます。

### `ShimeDatabase`

```kotlin
val database = library.databaseManager.connect("main", settings)

database.update("INSERT INTO players (uuid, name) VALUES (?, ?)", uuid.toString(), name)

val names = database.query("SELECT name FROM players WHERE uuid = ?", uuid.toString()) {
    it.getString("name")
}

val result = database.transaction { connection ->
    connection.prepareStatement("UPDATE ...").use { statement ->
        statement.executeUpdate()
    }
}
```

| メソッド | 説明 |
| --- | --- |
| `query(sql, vararg params, mapper)` | SELECT を実行し、行を変換して返す。 |
| `update(sql, vararg params)` | INSERT / UPDATE / DELETE を実行し、更新行数を返す。 |
| `batch(sql, batches)` | バッチ実行。 |
| `transaction(block)` | トランザクション内で任意の JDBC 操作。失敗時はロールバック。 |
| `queryAsync` / `updateAsync` / `transactionAsync` | 非同期版。 |
| `healthCheck()` | 接続の健全性確認。 |

### `ShimeSchemaManager`

```kotlin
val schema = ShimeSchemaManager(database)
schema.migrate(listOf(
    ShimeMigration(1, "create players", listOf(
        "CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, name TEXT NOT NULL)"
    )),
    ShimeMigration(2, "add balance", listOf(
        "ALTER TABLE players ADD COLUMN balance DECIMAL(20, 2) NOT NULL DEFAULT 0"
    ))
))
```

適用済みバージョンは履歴テーブルに記録され、未適用のものだけが順に実行されます。
SQL は開発者が用意したものだけを実行し、外部入力をそのまま実行しません。

## メッセージ

### `ShimeMessageManager`

```kotlin
library.messageManager.send(player, "command.reload.success")
library.messageManager.sendActionBar(player, "sample.action")
library.messageManager.sendTitle(player, "sample.title", "sample.subtitle")
library.messageManager.sendBossBar(player, "sample.bossbar", durationMillis = 3_000)
```

- メッセージは MiniMessage 形式です。
- ロケールはプレイヤーのロケール、言語コード、既定ロケールの順にフォールバックします。
- `configure(defaultLocale, usePrefix)` で既定ロケールとプレフィックスを設定します。
- `reload()` で `messages/` を再読み込みします。
- `missingKeys()` で未定義キーを確認できます。

## スケジューラーとタスク

### `ShimeScheduler`

```kotlin
library.scheduler.runSync { /* メインスレッド */ }
library.scheduler.runAsync { /* 非同期 */ }
library.scheduler.runLater(20L) { /* 1 秒後 */ }
library.scheduler.runRepeating(0L, 20L) { /* 毎秒 */ }
library.scheduler.runAsyncRepeating(0L, 100L) { /* 非同期で 5 秒ごと */ }

val value = library.scheduler.callSync { player.location }
```

`ShimeTaskHandle` は `cancel()`、`isCancelled`、`taskId` を提供します。
`ShimeScheduler.cancelAll()` はライブラリ停止時に自動で呼ばれます。

### `ShimeTaskManager`

名前とグループでタスクを管理します。

```kotlin
library.taskManager.repeat("scoreboard-update", periodTicks = 20L, group = "ui") {
    library.scoreboardManager.refreshAll()
}

library.taskManager.cancelGroup("ui")
val stats = library.taskManager.statistics()
```

## プレイヤー

### `ShimePlayerManager`

```kotlin
val profile = library.playerManager.requireProfile(player.uniqueId)
profile.setMetadata("rank", "vip")
library.playerManager.saveAsync(player.uniqueId)
```

- 参加時にプロフィールを読み込み、退出時に保存します。
- `loadProfileAsync(uuid, name)` で明示的に読み込むこともできます。
- `isDirty(uuid)` と `dirtyProfiles()` で未保存の変更を確認できます。
- `setLocale(uuid, locale)` でプレイヤーごとの表示ロケールを保存できます。
- `playTimeMillis(uuid)` は累積プレイ時間を返します。

`ShimePlayerProfile` は UUID、名前、初回参加、最終参加、最終退出、プレイ時間、ロケール、
スレッドセーフなメタデータを持ちます。

## アイテム

### `ShimeItemBuilder`

```kotlin
val item = ShimeItemBuilder(plugin, Material.DIAMOND_SWORD)
    .name("<gold>Shime Sword</gold>")
    .lore("<gray>A sample item.</gray>")
    .enchant("sharpness", 5)
    .flag(ItemFlag.HIDE_ATTRIBUTES)
    .customModelData(1001)
    .unbreakable()
    .persistentString("sample-id", "sword")
    .build()
```

名前と Lore は MiniMessage として解釈されます。永続データは
`PersistentDataContainer` に保存されるため、表示名の変更では偽装できません。

### `ShimeItemManager`

```kotlin
library.itemManager.register(
    ShimeItemDefinition(
        id = "sample-sword",
        provider = { player -> ShimeItemBuilder(plugin, Material.DIAMOND_SWORD).build() },
        rightClickHandler = { player, item ->
            player.sendMessage("Right clicked!")
            true
        },
        cooldownMillis = 1_000
    )
)

val item = library.itemManager.create("sample-sword", player)
val definition = library.itemManager.identify(item)
```

`rightClickHandler` / `leftClickHandler` が `true` を返すと、その操作はキャンセルされます。
クールダウンは `setCooldown` / `remainingCooldown` で管理できます。

### `ShimeItemIdentifier` / `ShimeItemSerializer`

`ShimeItemIdentifier` はアイテムにカスタム ID を付与・取得します。
`ShimeItemSerializer` は `ItemStack` を Map、YAML 文字列へ変換し、また復元します。

## GUI

### `ShimeGUI`

```kotlin
class SampleGUI(plugin: JavaPlugin) : ShimeGUI(
    id = "sample",
    title = ShimeText.component("<gold>Sample</gold>"),
    size = 27
) {
    override fun render(player: Player, inventory: Inventory) {
        inventory.setItem(13, ShimeItemBuilder(plugin, Material.EMERALD).name("Click me").build())
    }

    override fun handleClick(
        player: Player,
        slot: Int,
        clickType: ClickType,
        event: InventoryClickEvent
    ): ShimeGUIAction {
        if (slot == 13) {
            player.sendMessage("Clicked!")
            return ShimeGUIAction.CLOSE
        }
        return ShimeGUIAction.NONE
    }
}

library.guiManager.open(player, SampleGUI(plugin))
```

- サイズは 9 の倍数で 9〜54。
- 既定ではアイテムの移動とドラッグをキャンセルします。
- `allowItemPickup()` / `allowDrag()` をオーバーライドした場合のみ編集可能になります。
- `ShimeGUIManager.refresh(player)` で再描画、`updateSlot` で 1 スロット更新。
- `ShimePagedGUI` は前/次ページのナビゲーションを提供します。
- `ShimeConfirmGUI` ははい/いいえの確認ダイアログを提供します。

## スコアボード

### `ShimeSidebar`

```kotlin
val sidebar = ShimeSidebar(
    id = "main",
    titleProvider = { player -> ShimeText.component("<gold>Server</gold>") },
    lineProvider = { player ->
        listOf(
            ShimeText.component("Player: ${player.name}"),
            ShimeText.component("Online: ${Bukkit.getOnlinePlayers().size}")
        )
    },
    updateIntervalTicks = 20L
)

library.scoreboardManager.show(player, sidebar)
library.scoreboardManager.refreshAll()
library.scoreboardManager.hide(player)
```

サイドバーはプレイヤーごとに管理され、退出時に自動で解除されます。
描画はメインスレッドで行ってください。

## 経済

### `ShimeEconomyManager`

```kotlin
val account = player.uniqueId

library.economyManager.deposit(account, BigDecimal("100.00"), "reward")
library.economyManager.withdraw(account, BigDecimal("25.00"), "purchase")
library.economyManager.transfer(account, target, BigDecimal("10.00"), "trade")

val balance = library.economyManager.balance(account)
val history = library.economyManager.history(account, limit = 20)
```

既定では `ShimeInMemoryEconomyService` が使用されます。データベースを有効にした場合、
`ShimeDatabaseEconomyService` を `setProvider(provider, persistent = true)` で設定できます。
`ShimeTransactionResult` は成功可否、取引 ID、残高、理由、タイムスタンプを返します。

## ワールドとエンティティ

### `ShimeWorldService`

```kotlin
val location = ShimeLocation("world", 0.0, 64.0, 0.0)
library.worldService.teleport(player, location)
val nearby = library.worldService.nearbyEntities(player.location, 10.0)
```

`ShimeLocation` はシリアライズ可能な座標表現です。`toLocation()` はワールドが未ロードなら `null`、
`requireLocation()` は例外をスローします。

### `ShimeEntityService`

```kotlin
library.entityService.tag(entity, "shop-npc")
library.entityService.setOwner(entity, player.uniqueId)
val tagged = library.entityService.findTagged(world, "shop-npc")
library.entityService.removeSafely(entity)
```

タグと所有者は永続データに保存されます。

## インベントリ

### `ShimeInventoryService`

```kotlin
val snapshot = library.inventoryService.snapshot(player.inventory)
val encoded = library.inventoryService.serialize(player.inventory)
library.inventoryService.restore(player.inventory, encoded)
library.inventoryService.addOrDrop(player, item)
```

シリアライズ形式は Base64 でエンコードされたアイテム配列です。復元時はサイズを検証し、
不正なデータは例外として扱います。

## コマンド

### コマンドツリーの構築

```kotlin
val root = shimeCommand("sample") {
    alias("smp")
    permission("sample.use")
    description("Sample command.")
    executes { context, _ ->
        context.sender.sendMessage("Hello!")
        ShimeCommandResult.SilentSuccess
    }
    subcommand("give") {
        playerOnly()
        argument("amount", ShimeArgumentTypes.integer(min = 1, max = 64), optional = true, defaultValue = 1)
        executes { context, args ->
            val amount = args["amount"] as Int
            ShimeCommandResult.Success("Gave $amount item(s).")
        }
    }
}
```

### 登録

```kotlin
val commands = ShimeCommandManager(plugin, library.logger, library.scheduler)
library.registerService(commands)

// plugin.yml に宣言済みの場合
commands.register(root)

// plugin.yml にない場合
commands.register(root, dynamic = true)
```

`ShimeCommandManager` は次を行います。

- `plugin.yml` の `PluginCommand` へ executor / tab completer を設定。
- 動的登録時は Paper の `CommandMap` を使用。
- ノードごとの権限チェック（`CommandSender.hasPermission`）。
- 送信者種別（任意 / プレイヤー / コンソール）。
- クールダウンと期間内実行回数制限。
- 引数解析とタブ補完。
- 使用方法とヘルプの自動生成。

### `ShimeCommandResult`

| 結果 | 意味 |
| --- | --- |
| `Success(message)` | 成功。メッセージを送信。 |
| `SilentSuccess` | 成功。何も送信しない。 |
| `Failure(message, cause)` | 想定内の失敗。 |
| `Usage(message)` | 構文エラー。使用方法を付加。 |
| `NoPermission(permission)` | 権限不足。 |

### 標準引数

`ShimeArgumentTypes` は `string`、`integer`、`long`、`decimal`、`boolean`、`uuid`、
`choice`、`enumeration`、`player`、`offlinePlayer`、`world`、`custom` を提供します。
すべてのパーサーは不正入力を `ShimeCommandArgumentException` として報告します。

## モジュール

### `ShimeModule`

```kotlin
class SampleModule : ShimeAbstractModule(
    moduleId = "sample",
    modulePriority = 10,
    moduleDependencies = setOf("database")
) {
    override fun onInitialize(context: ShimeContext) {
        // 機能の初期化
    }

    override fun onShutdown() {
        // 機能の解放
    }
}

library.moduleManager.register(SampleModule())
library.moduleManager.enableAll(library.context)
```

`ShimeModuleManager` は依存関係を解決し、循環依存を拒否します。
`states()` で各モジュールの状態、`resolveOrder()` で解決済みの順序を確認できます。

## 外部連携

### `ShimeIntegration`

```kotlin
class PlaceholderIntegration : ShimeIntegration {
    override val id: String = "placeholderapi"

    override fun isAvailable(): Boolean =
        Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")

    override fun enable(context: ShimeContext) {
        // 外部 API の初期化
    }

    override fun disable() {
        // 冪等な解放
    }
}

library.integrationManager.register(PlaceholderIntegration())
library.integrationManager.enableAllAvailable(library.context)
```

外部プラグインが存在しない場合でも `disable()` は安全に呼び出せる必要があります。
LuckPerms、PlaceholderAPI、Vault、ProtocolLib、PacketEvents などは同梱していません。

## NMS とパケット

### `ShimeNmsManager`

```kotlin
val nms = library.service(ShimeNmsManager::class.java)
val version = nms.versionOrNull()
val status = nms.adapterStatus()
val report = nms.report()
```

### `ShimeNmsAdapter`

```kotlin
class CustomAdapter : ShimeNmsAdapter {
    override val adapterId = "custom-1.20.6"
    override val supportedMinecraftVersions = setOf(ShimeMinecraftVersion.PAPER_1_20_6)
    override val supportedFeatures = setOf(ShimeNmsFeature.VERSION_DETECTION)

    override fun initialize(context: ShimeNmsAdapterContext) { }
    override fun shutdown() { }
}
```

同梱の `ShimePaper1206Adapter` は `VERSION_DETECTION` のみをサポートします。
パケット転送を実装する場合は `ShimePacketTransport` を実装し、アダプターから公開してください。
検証済みのトランスポートがない状態でパケット操作を行うと
`ShimeNmsUnsupportedException` がスローされます。

### `ShimePacketManager`

```kotlin
val packets = ShimePacketManager(logger)
packets.transport = myVerifiedTransport // 検証済みの場合のみ

val registration = packets.register(filter, listener)
registration.close()
```

パケット API は抽象化のみを提供します。特定の外部ライブラリを必須にしません。

## ユーティリティと検証

### `ShimeText`

`component(input)`、`legacy(input)`、`legacyToComponent(input)`、`ampersand(input)`、
`plain(input)`、`replace(input, values)`、`truncate(input, maxLength)` を提供します。
MiniMessage とレガシー形式の両方を扱えます。

### `ShimeTime`

`formatDuration(millis)`、`parseDuration("30s")`、`formatTimestamp(epochMillis)` を提供します。

### `ShimeNumbers`

`parseInt`、`parseLong`、`parseDecimal`、`clamp`、`format` を提供します。

### `ShimeValidation`

`requireNotNull`、`requireNonBlank`、`requireLength`、`requireInRange`、`requirePositive`、
`requireSafeKey`、`requireSafeFileName`、`requireUuid`、`requireTrue` を提供します。
失敗時は `ShimeValidationException` をスローします。

### 例外

`ShimeException` を基底に、`ShimeLifecycleException`、`ShimeServiceException`、
`ShimeValidationException`、`ShimeConfigurationException`、`ShimeStorageException`、
`ShimeDatabaseException`、`ShimePermissionException`、`ShimeUnsupportedVersionException` があります。

## スレッド規則

| 対象 | スレッド |
| --- | --- |
| GUI、スコアボード、プレイヤーへの表示 | Paper メインスレッド |
| 設定、アイテム、ワールド、エンティティ | Paper メインスレッド |
| キャッシュ、サービスレジストリ、タスク管理 | スレッドセーフ |
| データベースの非同期 API | ワーカースレッド。Bukkit API を呼ぶ前に `callSync` で戻す。 |

## 制約

- Paper 1.20.6 を初期対象としています。他のバージョンでは NMS アダプターの追加が必要です。
- 同梱の NMS アダプターはバージョン検出のみです。
- 外部プラグイン連携、パケット転送、Boss API、独自権限、独自プレースホルダーは同梱しません。
- 実際のサーバーでの動作は、ビルドした JAR を Paper 1.20.6 に導入して確認してください。
