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
10. [イベント](#イベント)
11. [アイテム](#アイテム)
12. [GUI](#gui)
13. [スコアボード](#スコアボード)
14. [経済](#経済)
15. [ワールドとエンティティ](#ワールドとエンティティ)
16. [インベントリ](#インベントリ)
17. [コマンド](#コマンド)
18. [モジュール](#モジュール)
19. [外部連携](#外部連携)
20. [NMS とパケット](#nms-とパケット)
21. [ユーティリティと検証](#ユーティリティと検証)

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

#### スキーマ検証と整合性チェック

```kotlin
val schema = ShimeSchemaManager(database)

val validation = schema.validate(listOf(
    ShimeTableSpec("players", setOf("uuid", "name", "balance"))
))
if (!validation.isValid) {
    logger.warning(validation.summary())
}

val integrity = schema.integrityCheck(listOf("players"))
if (!integrity.isHealthy) {
    integrity.failedChecks().forEach { logger.warning("${it.name}: ${it.details}") }
}
```

- `validate` はテーブルとカラムの欠落・型の不一致を `ShimeSchemaValidationReport` として返します。
- `integrityCheck` は NULL、重複主キー、孤立行などを検査し、`ShimeDatabaseIntegrityReport` を返します。
- どちらも検査のみを行い、データを変更しません。

#### キャッシュ付きリポジトリ

```kotlin
val repository = ShimeCachedRepository(
    delegate = playerRepository,
    cache = library.cacheManager.create("players"),
    keyExtractor = { it.uuid }
)

repository.findById(uuid)   // 初回のみ委譲先へ問い合わせる
repository.save(profile)    // 保存と同時にキャッシュを更新する
repository.invalidate(uuid) // 個別破棄
```

`findAll` と `findPage` はメモリ圧迫を避けるため常に委譲先へ問い合わせます。
取得した値を呼び出し側で変更した場合は、保存するか `invalidate` を呼んでください。

#### 健全性レポート

```kotlin
val reports = library.databaseManager.healthReports()
if (!library.databaseManager.isHealthy()) {
    reports.filterNot { it.healthy }.forEach { logger.warning(it.summary()) }
}
```

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

#### テンプレートと検証

```kotlin
library.messageManager.registerTemplate(
    ShimeMessageTemplate(
        key = "sample.welcome",
        defaultText = "<green>Welcome, {player}!</green>",
        requiredPlaceholders = setOf("player")
    )
)

val report = library.messageManager.validate(
    key = "sample.welcome",
    required = setOf("player")
)
if (!report.isValid) {
    logger.warning(report.summary())
}

library.messageManager.sendWithSound(
    player,
    "sample.welcome",
    placeholders = mapOf("player" to player.name),
    sound = "minecraft:entity.experience_orb.pickup"
)
```

- `template(locale, key)` は登録済みテンプレート、または `{name}` / `%name%` から抽出した
  プレースホルダーを返します。
- `validate` は不足キー、未置換プレースホルダー、空メッセージを検出します。
- `sendWithSound` はメッセージ送信と効果音再生をまとめて行います。
- 描画済みコンポーネントはロケール・キー・プレースホルダーの組み合わせでキャッシュされます。
  `reload()` と `clearCache()` で破棄できます。

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

#### 依存関係・タイムアウト・結果 Future

```kotlin
val first = library.taskManager.submitAsyncFuture("load", group = "startup") {
    loadFromDisk()
}

val second = library.taskManager.submitAsyncFuture(
    name = "publish",
    group = "startup",
    dependsOn = listOf("load"),
    timeoutTicks = 200L
) {
    publishLoadedData()
}

second.thenAccept { result ->
    when (result.state) {
        ShimeTaskState.COMPLETED -> logger.info("Published: ${result.value}")
        ShimeTaskState.TIMED_OUT -> logger.warning("Timed out.", result.error)
        ShimeTaskState.SKIPPED -> logger.warning("Skipped.", result.error)
        else -> Unit
    }
}
```

- `dependsOn` のタスクは先に登録されている必要があります。依存先が失敗・キャンセル・
  タイムアウトした場合、後続は `SKIPPED` になります。
- `timeoutTicks` を超えると `TIMED_OUT` と `ShimeTaskTimeoutException` が返ります。
- `ShimeTaskResult` は `state`、`value`、`error`、開始・終了時刻、実行時間を持ちます。
- `snapshot(name)` / `snapshots()` で実行中タスクの状態、`statistics()` で累計を確認できます。
- 非同期タスクから Bukkit API を呼ぶ場合は `callSync { ... }` でメインスレッドへ戻してください。

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
スレッドセーフなメタデータ、セッション数、上限付きログイン履歴を持ちます。

#### セッションとログイン履歴

```kotlin
val session = library.playerManager.session(player.uniqueId)
session?.register(AutoCloseable { cleanup(player.uniqueId) })
session?.put("temporary-tool", tool)

val history = library.playerManager.loginHistory(player.uniqueId, limit = 10)
val stats = library.playerManager.statistics(player.uniqueId)
logger.info("Sessions: ${stats?.sessionCount}, average: ${stats?.averageSessionMillis} ms")
```

- セッションは参加時に開始され、退出時に登録済みリソースを逆順で閉じます。
- リソースのクローズで例外が発生しても残りは閉じられ、例外は `suppressed` に集約されます。
- `ShimeLoginRecord` は参加・退出時刻と継続時間を表します。履歴は上限を超えると古い順に破棄されます。
- `ShimePlayerStatistics` はセッション数、合計プレイ時間、平均セッション時間を返します。

#### プロフィールの移行

```kotlin
val exported = library.playerManager.exportProfile(uuid).join()
library.playerManager.importProfile(exported).join()

library.playerManager.exportProfileToFile(uuid, file).join()
val restored = library.playerManager.importProfileFromFile(file).join()
```

`ShimePlayerProfileCodec` はプロフィールを Map へ変換し、必須項目と型を検証して復元します。
不正なデータは `ShimeValidationException` または `IllegalArgumentException` として拒否されます。

## イベント

### `ShimeEventManager`

`registerListener` / `unregisterListener` に加え、条件付きの `subscribe` を提供します。

```kotlin
val subscription = library.eventManager.subscribe(
    PlayerAdvancementDoneEvent::class.java,
    priority = EventPriority.MONITOR,
    ignoreCancelled = true,
    requirePrimaryThread = true,
    filter = { event -> event.advancement.key.key.startsWith("sample:") }
) { event ->
    logger.info("${event.player.name} completed ${event.advancement.key}")
}

logger.info("Invocations: ${subscription.invocationCount}")
logger.info("Filtered: ${subscription.filteredCount}")

subscription.unregister()
```

- `filter` が `false` を返した場合、ハンドラーは呼ばれず `filteredCount` が増えます。
- `requirePrimaryThread` が `true` の場合、非同期イベントは無視されます。
- ハンドラーの例外はログに記録され、他のリスナーへは伝播しません。`failureCount` に集計されます。
- `subscriptions()` と `statistics()` で購読ごとの実行回数・失敗数・平均時間を確認できます。
- 購読は `unregister()` または `ShimeLibrary.shutdown()` で解除されます。

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

#### アクセス制御

```kotlin
val gui = SampleGUI(plugin)
gui.setOwner(player)
gui.grantAccess(friend)
gui.revokeAccess(other)

library.guiManager.open(player, gui) // アクセス権がない場合は開かない
```

`hasAccess(player)` は所有者、明示的な許可、許可リストを確認します。
`ShimeGUIManager.open` は非同期スレッドから呼ばれた場合、自動でメインスレッドへ委譲します。

#### テンプレート

```kotlin
val template = shimeGuiTemplate(id = "shop", title = "<gold>Shop</gold>", size = 27) {
    render { player, inventory, _ ->
        inventory.setItem(13, ShimeItemBuilder(plugin, Material.EMERALD).name("Buy").build())
    }
    onClick { _, slot, _, _, _ ->
        if (slot == 13) ShimeGUIAction.CLOSE else ShimeGUIAction.NONE
    }
}

val gui = template.instantiate()
library.guiManager.open(player, gui)
```

`ShimeGUITemplate` は画面定義を再利用可能にします。`instantiate()` のたびに独立した
`ShimeGUI` を生成します。

#### 一覧 GUI

`ShimeListGUI` は `ShimeGUIEntry` の一覧を検索・グループ絞り込み・並べ替え・ページ送り付きで表示します。

```kotlin
class PlayerListGUI(plugin: JavaPlugin) : ShimeListGUI("players", ShimeText.component("Players"), 54) {
    override fun entries(player: Player): List<ShimeGUIEntry> =
        Bukkit.getOnlinePlayers().map { target ->
            ShimeGUIEntry(
                key = target.uniqueId.toString(),
                item = ShimeItemBuilder(plugin, Material.PLAYER_HEAD).name(target.name).build(),
                searchText = target.name,
                sortKey = target.name.lowercase(),
                group = target.world.name
            )
        }

    override fun onSelect(player: Player, entry: ShimeGUIEntry, clickType: ClickType) {
        player.sendMessage("Selected ${entry.key}")
    }
}
```

状態はプレイヤーごとに保持され、`handleClose` で破棄されます。

#### 定期更新と非同期読み込み

```kotlin
library.guiManager.scheduleRefresh(player, intervalTicks = 20L)
library.guiManager.cancelScheduledRefresh(player)

library.guiManager.updateAfter(player, loader = { loadRowsAsync().join() }) { rows ->
    // メインスレッドで GUI に反映する
}
```

`refreshAfter` / `updateAfter` は読み込みを非同期で行い、結果をメインスレッドで反映します。
読み込みに失敗した場合はログに記録され、GUI は変更されません。

`history(player)` はプレイヤーが開いた GUI ID の履歴を返し、`clearHistory(player)` で破棄できます。

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

### アノテーション定義

DSL を使わず、クラスとメソッドのアノテーションからコマンドツリーを構築できます。

```kotlin
@ShimeCommandDefinition(
    name = "sample",
    aliases = ["smp"],
    permission = "sample.use",
    description = "Sample command."
)
class SampleCommands {
    @ShimeCommandHandler
    fun root(context: ShimeCommandContext): ShimeCommandResult {
        context.sender.sendMessage("Hello!")
        return ShimeCommandResult.SilentSuccess
    }

    @ShimeCommandHandler(path = ["give"], permission = "sample.give")
    fun give(
        context: ShimeCommandContext,
        @ShimeCommandArg(name = "amount", type = ShimeCommandArgumentType.INTEGER, min = "1", max = "64")
        amount: Int
    ): ShimeCommandResult {
        return ShimeCommandResult.Success("Gave $amount item(s).")
    }
}

val registration = commands.registerAnnotated(SampleCommands())
```

- ハンドラーには `ShimeCommandContext`、`CommandSender`、`Map<String, Any?>` を注入できます。
- 戻り値は `ShimeCommandResult`、`String`、`Boolean`、`Unit`、`null` を扱えます。
- `AUTO` はメソッド引数の型から解決します。`CHOICE`、`ENUM`、`CUSTOM` も利用できます。
- `ShimeCommandAnnotations.registerArgumentType(name, argument)` でカスタム型を登録できます。
- `ShimeCommandRegistration.unregister()` で登録を解除します。

### フック・監査ログ・統計

```kotlin
commands.addHook(object : ShimeCommandHook {
    override fun beforeExecution(context: ShimeCommandContext): Boolean {
        return context.sender.hasPermission("sample.extra")
    }

    override fun afterExecution(context: ShimeCommandContext, result: ShimeCommandResult) {
        logger.info("${context.sender.name}: $result")
    }
})

val recent = commands.auditLog.recent(limit = 20)
val stats = commands.statistics("sample give")
commands.resetStatistics()
```

- フックは登録順に `beforeExecution` が呼ばれ、`false` を返した場合は実行されません。
- `afterExecution` は実行された場合のみ、登録と逆順に呼ばれます。フックの例外はログに記録されます。
- `auditLog` は上限付きのインメモリ監査ログです。`addListener` で永続化できます。
- `statistics(path)` は実行回数、成功・失敗数、平均・最小・最大実行時間を返します。
  拒否された呼び出し（権限不足、クールダウン、引数エラー）は含みません。
- 引数エラーには引数名・位置・入力値が含まれ、使用方法の表示に利用されます。

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
    override val adapterId = "custom-1.21.8"
    override val supportedMinecraftVersions = setOf(ShimeMinecraftVersion.PAPER_1_21_8)

    private var currentStatus = ShimeNmsAdapterStatus.CREATED

    override fun supports(feature: ShimeNmsFeature): Boolean {
        return currentStatus == ShimeNmsAdapterStatus.COMPATIBLE && feature == ShimeNmsFeature.VERSION_DETECTION
    }

    override fun initialize(context: ShimeNmsAdapterContext) {
        currentStatus = ShimeNmsAdapterStatus.COMPATIBLE
    }

    override fun shutdown() {
        currentStatus = ShimeNmsAdapterStatus.CLOSED
    }

    override fun status(): ShimeNmsAdapterStatus = currentStatus
}
```

同梱の `ShimePaper1206Adapter`（Paper 1.20.6）と `ShimePaper1218Adapter`（Paper 1.21.8）は
`VERSION_DETECTION` のみをサポートします。1.21.8 の内部クラスやパケット形式は検証なしに使用しません。
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

- Paper 1.20.6 をビルドと実行の基準とし、Paper 1.21.8 では NMS のバージョン検出を利用できます。
  それ以外のバージョンでは、NMS アダプターの追加が必要です。
- 同梱の NMS アダプターはバージョン検出のみです。1.21.8 の内部クラスやパケット形式は同梱しません。
- 外部プラグイン連携、パケット転送、Boss API、独自権限、独自プレースホルダーは同梱しません。
- 実際のサーバーでの動作は、ビルドした JAR を Paper 1.20.6 または Paper 1.21.8 に導入して確認してください。
