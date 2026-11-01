# ShimePluginLibrary

Paper 1.20.6 / Java 21 を対象とした、Minecraft プラグイン開発用の共通ライブラリです。
設定、データベース、キャッシュ、プレイヤー、アイテム、GUI、コマンド、スコアボード、経済、ワールド、
モジュール、外部プラグイン連携などを、再利用可能な API として提供します。

Gradle のサブプロジェクトは次の 4 つだけです。

| モジュール | 役割 |
| --- | --- |
| `ShimePlugin` | サーバーへ導入するプラグイン本体。`plugin.yml`、メインクラス、管理コマンドを含みます。 |
| `ShimePluginLibrary` | 汎用 API 本体。設定、DB、キャッシュ、GUI、アイテム、経済などのパッケージを含みます。 |
| `ShimePluginCommand` | コマンド DSL、引数パーサー、実行・補完・権限・クールダウンを扱うコマンド API。 |
| `ShimePluginNMS` | バージョン検出と NMS アダプター SPI。パケット転送は同梱しません。 |

GUI や Config などを個別の Gradle モジュールには分割していません。これらは
`ShimePluginLibrary` 内のパッケージとして実装されています。

## 必要環境

- Paper 1.20.6
- Java 21
- Gradle 8.5（同梱の Gradle Wrapper を使用）

## ビルド

```bash
./gradlew build
```

Windows の場合:

```powershell
.\gradlew.bat build
```

ビルド成果物は `ShimePlugin/build/libs/ShimePlugin-1.0.0.jar` です。この JAR は
`ShimePluginLibrary`、`ShimePluginCommand`、`ShimePluginNMS` と Kotlin ランタイムを内包します。
Paper API は `compileOnly` のため JAR には含まれません。

テストのみ実行する場合:

```bash
./gradlew test
```

> 日本語などの非 ASCII 文字を含むパスで Gradle のテスト実行が失敗する環境では、
> ASCII のみのパス（ジャンクションやシンボリックリンク）からビルドしてください。
> これは Gradle のワーカー引数ファイルの文字コードに起因する環境依存の問題です。

## GitHub Packages からの利用

`ShimePluginLibrary`、`ShimePluginCommand`、`ShimePluginNMS` は GitHub Packages の Maven リポジトリへ
公開できます。公開は `v*` タグの push、または GitHub Actions の手動実行で行われます。
バージョンはタグ名から `v` を除いた値（例: `v1.2.3` → `1.2.3`）になります。

利用側の `build.gradle`:

```groovy
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/woxlo1/ShimePluginLibrary')
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation 'shimeserver:ShimePluginLibrary:1.2.3'
    implementation 'shimeserver:ShimePluginCommand:1.2.3'
    implementation 'shimeserver:ShimePluginNMS:1.2.3'
}
```

GitHub Packages から取得するには `read:packages` 権限を持つトークンが必要です。
ローカルでバージョンを指定してビルドする場合は `--project-prop releaseVersion=1.2.3` を付けます。

## 導入

1. `ShimePlugin/build/libs/ShimePlugin-1.0.0.jar` をサーバーの `plugins/` に配置します。
2. サーバーを起動すると `plugins/ShimePluginLibrary/` に `config.yml` と `messages/` が生成されます。
3. ライブラリを利用するプラグインは `plugin.yml` で依存を宣言します。

```yaml
depend: [ShimePluginLibrary]
# または、ライブラリがなくても動作できる場合
softdepend: [ShimePluginLibrary]
```

## クイックスタート

ライブラリの初期化は `ShimePlugin` が行います。利用側プラグインは `ShimeLibrary.get()` で
初期化済みのインスタンスを取得してください。独自に `ShimeLibrary` を構築すると、サービスグラフと
データベースプールが二重に作られるため避けてください。

```kotlin
import org.bukkit.plugin.java.JavaPlugin
import shimeserver.shimepluginlibrary.shimeapi.ShimeLibrary
import shimeserver.shimepluginlibrary.shimelogging.ShimeLogCategory

class SamplePlugin : JavaPlugin() {
    override fun onEnable() {
        val library = ShimeLibrary.get()

        library.logger.info(ShimeLogCategory.CORE, "SamplePlugin started.")

        val config = library.configManager.create("sample", "sample.yml")
        val greeting = config.getString("greeting", "Hello!") ?: "Hello!"

        library.scheduler.runRepeating(0L, 20L * 60L) {
            logger.info(greeting)
        }
    }
}
```

Java から利用する場合:

```java
ShimeLibrary library = ShimeLibrary.get();
ShimeConfig config = library.getConfigManager().create("sample", "sample.yml", true);
```

## 主な API

`ShimeLibrary` は次のマネージャーとサービスを公開します。すべてライフサイクルに統合されており、
`ShimeLibrary.shutdown()` で依存関係の逆順に解放されます。

| プロパティ | 用途 |
| --- | --- |
| `logger` | カテゴリ付きログ出力。デバッグ切り替えと例外レポートに対応。 |
| `scheduler` | 同期・非同期の遅延実行、繰り返し実行、同期呼び出し。 |
| `services` | 依存関係順のサービスレジストリ。 |
| `configManager` | YAML 設定の作成、再読み込み、バージョン移行。 |
| `cacheManager` | 名前付きメモリキャッシュとローディングキャッシュ。 |
| `storageManager` | YAML / JSON ストレージ。 |
| `databaseManager` | HikariCP による SQLite / MySQL / MariaDB 接続。 |
| `eventManager` | Paper リスナーの登録・解除とライフサイクル管理。 |
| `taskManager` | 名前とグループで管理できるタスク。 |
| `messageManager` | MiniMessage ベースの多言語メッセージ。 |
| `itemManager` | カスタムアイテム定義、識別、クリック処理、クールダウン。 |
| `guiManager` | チェスト GUI、ページ GUI、確認 GUI。 |
| `playerManager` | プレイヤープロフィールの読み込み・保存。 |
| `scoreboardManager` | サイドバースコアボード。 |
| `worldService` | ワールド・座標・テレポート・範囲検索。 |
| `entityService` | エンティティのタグ付けと所有者管理。 |
| `economyManager` | インメモリまたはデータベース永続化の経済 API。 |
| `moduleManager` | 機能モジュールの依存関係順ライフサイクル。 |
| `integrationManager` | 外部プラグイン連携の任意登録。 |
| `inventoryService` | インベントリのスナップショット、シリアライズ、復元。 |

詳細は [docs/API.md](docs/API.md) を参照してください。

## コマンド

管理コマンドは `plugin.yml` で宣言され、`ShimeCommandManager` がバインドします。

| コマンド | 説明 |
| --- | --- |
| `/shimepluginlibrary status` | ライブラリ、データベース、NMS の状態を表示します。 |
| `/shimepluginlibrary reload` | 設定と言語ファイルを再読み込みします。 |
| `/shimepluginlibrary nms` | NMS の対応状況レポートを表示します。 |
| `/shimepluginlibrary services` | 登録済みサービスと状態を一覧表示します。 |

エイリアスは `/spl` と `/shimelib` です。必要な権限は `shimepluginlibrary.admin`（既定: OP）です。

## 設定

`config.yml` の主な項目:

| キー | 既定値 | 説明 |
| --- | --- | --- |
| `language` | `en_US` | コンソールとプレイヤーのフォールバックロケール。 |
| `use-prefix` | `true` | メッセージの先頭に `prefix` を付けるか。 |
| `debug` | `false` | 詳細ログの有効化。 |
| `database.enabled` | `false` | データベースを使用するか。 |
| `database.type` | `sqlite` | `sqlite`、`mysql`、`mariadb`。 |
| `database.sqlite-file` | `data.db` | データフォルダーからの相対パス。外部パスは拒否されます。 |

言語ファイルは `plugins/ShimePluginLibrary/messages/<locale>.yml` に配置します。
同梱の `en_US.yml` と `ja_JP.yml` を参考に追加できます。

## データベース

`ShimeDatabaseSettings` と `ShimeDatabaseManager` を使用します。

```kotlin
val settings = ShimeDatabaseSettings(
    type = ShimeDatabaseType.SQLITE,
    sqliteFile = File(library.context.dataFolder, "sample.db")
)
val database = library.databaseManager.connect("sample", settings)

database.update("CREATE TABLE IF NOT EXISTS sample (id TEXT PRIMARY KEY, value TEXT NOT NULL)")
database.update("INSERT INTO sample (id, value) VALUES (?, ?)", "a", "1")

val rows = database.query("SELECT value FROM sample WHERE id = ?", "a") { it.getString("value") }
```

SQLite は接続プールを 1 接続に制限します。MySQL / MariaDB では `pool-size` を調整できます。
スキーマ移行には `ShimeSchemaManager` と `ShimeMigration` を使用してください。

## NMS とパケット

`ShimePluginNMS` は NMS 依存処理をコアから分離するためのモジュールです。

- 同梱の `ShimePaper1206Adapter` は **バージョン検出のみ** を実装しています。
- 生のパケット転送は同梱していません。パケット操作が必要な場合は、外部ライブラリを任意の
  `ShimeIntegration` として接続するか、独自の `ShimeNmsAdapter` を登録してください。
- 検証済みのトランスポートがない状態でパケット操作を呼ぶと
  `ShimeNmsUnsupportedException` がスローされます。未対応機能を黙って成功扱いにはしません。

## 任意の外部連携

LuckPerms、PlaceholderAPI、Vault、ProtocolLib、PacketEvents などは必須依存ではありません。
`ShimeIntegration` を実装し、`ShimeIntegrationManager.register(...)` で登録してください。
外部プラグインが存在しない場合、`isAvailable()` が `false` を返すように実装します。

## ライフサイクル

- サービスは `ShimeService` を実装し、`serviceDependencies` で依存関係を宣言します。
- `ShimeLibrary.registerService(...)` は初期化前にのみ呼び出せます。
- 初期化は依存関係順、停止はその逆順です。循環依存は検出され、例外になります。
- 機能単位のモジュールは `ShimeModule` と `ShimeModuleManager` を使用します。
- すべてのマネージャーは `ShimeAbstractService` を継承し、Paper のリスナー登録も自身で解除します。

## サンプル

`examples/sample-plugin/` に、ライブラリを利用する最小構成のサンプルプラグインがあります。
サンプルはルートビルドには含まれず、単体でビルドできます。

```bash
cd examples/sample-plugin
../../gradlew -p . build
```

## 開発

- Kotlin 2.0.21、Java 21、Groovy Gradle DSL。
- テストは JUnit 5 と kotlin-test を使用します。
- 公開 API には KDoc を付け、入力検証・ライフサイクル・スレッド安全性・リソース解放を明示します。
- コメントと KDoc は日本語で記述します。
- コミットメッセージは英語の Conventional Commits に従います。

### モジュール構成

```text
ShimePluginLibrary/
├── ShimePlugin/          # プラグイン本体（Shadow JAR）
├── ShimePluginLibrary/   # 汎用 API
├── ShimePluginCommand/   # コマンド API
├── ShimePluginNMS/       # NMS アダプター SPI
├── docs/                 # 設計・API ドキュメント
└── examples/             # サンプルプラグイン
```

## ライセンス

MIT License。詳細は [LICENSE](LICENSE) を参照してください。
