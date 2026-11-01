# アーキテクチャ

## 全体方針

ShimePluginLibrary は、Paper プラグイン開発で繰り返し必要になる機能を 1 つのライブラリにまとめ、
利用側プラグインが同じ初期化・保存・解放処理を再実装しなくて済むようにすることを目的としています。

設計上の優先順位は次のとおりです。

1. **ライフサイクルの一元管理** — 生成したサービス、リスナー、タスク、接続を確実に解放する。
2. **Paper API の優先** — バージョン依存処理は `ShimePluginNMS` に隔離し、コアは Paper API で完結させる。
3. **任意連携の分離** — 外部プラグインやパケットライブラリを必須依存にしない。
4. **失敗を隠さない** — 未対応機能は例外または明示的な結果として返し、成功を装わない。
5. **過剰な抽象化の回避** — 実際に責務が異なるものだけをクラス・モジュールに分ける。

## Gradle モジュール

```text
ShimePlugin
├── ShimePluginLibrary
├── ShimePluginCommand
└── ShimePluginNMS
```

| モジュール | 依存先 | 責務 |
| --- | --- | --- |
| `ShimePluginLibrary` | Paper API（`compileOnly`） | 汎用 API、サービス基盤、設定、DB、キャッシュ、GUI、アイテム、経済など。 |
| `ShimePluginCommand` | `ShimePluginLibrary`、Paper API | コマンドツリー、引数解析、実行、補完、権限、クールダウン。 |
| `ShimePluginNMS` | `ShimePluginLibrary`、Paper API | バージョン検出、NMS アダプター SPI、パケット抽象化。 |
| `ShimePlugin` | 上記 3 つ、Paper API | エントリーポイント、起動・停止の調整、管理コマンド、Shadow JAR。 |

`ShimePluginLibrary` 内の機能は Gradle モジュールではなくパッケージで分割します。機能ごとに
独立したリリース単位やクラスローダー分離が必要になった場合にのみ、モジュール分割を検討します。

## 起動と停止

`ShimePlugin` は Paper の `onEnable` / `onDisable` を `ShimePluginBootstrap` に転送するだけです。

```text
ShimePlugin.onEnable()
└── ShimePluginBootstrap.enable()
    ├── ShimeLibrary を生成（コアサービスを登録）
    ├── config.yml を読み込み
    ├── データベースを接続（有効な場合）
    ├── ShimeCommandManager / ShimeNmsManager を登録
    ├── ShimeLibrary.initialize()
    │   └── 依存関係順にサービスを初期化
    └── 管理コマンドを登録

ShimePlugin.onDisable()
└── ShimePluginBootstrap.disable()
    ├── 管理コマンドを解除
    └── ShimeLibrary.shutdown()
        ├── 依存関係の逆順にサービスを停止
        └── スケジューラー経由のタスクをすべてキャンセル
```

起動に失敗した場合、`ShimePlugin` は例外をログに記録したうえで `disable()` を呼び、
部分的に初期化されたリソースを解放します。`disable()` は冪等です。

## サービスレジストリ

`ShimeService` は次の契約を持ちます。

- `serviceName`: 一意な名前。
- `serviceDependencies`: 初期化前に存在していなければならないサービス名。
- `servicePriority`: 依存関係が同じ場合の順序。
- `state`: `ShimeState`。
- `initialize(context)` / `shutdown()`。

`ShimeServiceRegistry` は登録時に名前の重複を検証し、初期化時に依存関係を解決します。
依存先が未登録の場合や循環依存がある場合は、初期化前に例外をスローします。
停止は初期化の逆順で行われ、1 つのサービスの失敗が残りの解放を妨げないようにログへ記録します。

`ShimeAbstractService` は状態遷移の共通実装を提供します。コアのマネージャーはすべてこれを継承します。

## ライフサイクル状態

`ShimeState` は `UNINITIALIZED`、`INITIALIZING`、`READY`、`STOPPING`、`STOPPED`、`FAILED` を表します。
`ShimeLifecycle` は許可された遷移だけを受け付け、`requireReady(...)` で早期に失敗させます。

`ShimeLibrary.get()` は初期化済みかつ `READY` のインスタンスだけを返します。利用側プラグインは
`plugin.yml` の `depend` または `softdepend` で読み込み順を保証してください。

## パッケージ構成

### `ShimePluginLibrary`

| パッケージ | 内容 |
| --- | --- |
| `shimeapi` | `ShimeLibrary`、`ShimeContext`、`ShimeLifecycle`、`ShimeState`。 |
| `shimeservice` | サービス SPI と依存解決レジストリ。 |
| `shimeconfig` | YAML 設定、再読み込み、バックアップ、バージョン移行。 |
| `shimecache` | TTL 付きメモリキャッシュ、ローディングキャッシュ、統計。 |
| `shimestorage` | YAML / JSON ストレージとコーデック。 |
| `shimedatabase` | HikariCP 接続、SQL 実行、トランザクション、スキーマ移行。 |
| `shimemessage` | MiniMessage ベースの多言語メッセージ。 |
| `shimeplayer` | プレイヤープロフィールと非同期保存。 |
| `shimeitem` | アイテムビルダー、カスタムアイテム、識別、シリアライズ。 |
| `shimegui` | チェスト GUI、ページ GUI、確認 GUI、クリック処理。 |
| `shimescoreboard` | サイドバースコアボード。 |
| `shimeeconomy` | インメモリ / DB 経済、取引履歴。 |
| `shimeworld` | ワールド、座標、テレポート、範囲検索。 |
| `shimeentity` | エンティティのタグと所有者。 |
| `shimeinventory` | インベントリのスナップショットとシリアライズ。 |
| `shimeevent` | Paper リスナーの登録・解除。 |
| `shimescheduler` / `shimetask` | 同期・非同期タスクと名前付きタスク管理。 |
| `shimemodule` | 機能モジュールの依存関係順ライフサイクル。 |
| `shimeintegration` | 外部プラグイン連携の任意登録。 |
| `shimeutil` | テキスト、数値、時間、ファイル操作。 |
| `shimevalidation` | 入力検証ヘルパー。 |
| `shimeexception` | ライブラリ共通例外。 |
| `shimelogging` | カテゴリ付きロガー。 |

### `ShimePluginCommand`

| クラス | 役割 |
| --- | --- |
| `ShimeCommandNode` | コマンドツリーのノード。名前、エイリアス、権限、引数、実行制限を保持。 |
| `ShimeCommandBuilder` / `shimeCommand` | Kotlin DSL。 |
| `ShimeCommandDsl` | Java 向け DSL。 |
| `ShimeArgument` / `ShimeArgumentTypes` | 引数パーサーと標準実装。 |
| `ShimeCommandManager` | `plugin.yml` または動的登録によるコマンドのバインド、実行、補完、権限、クールダウン。 |
| `ShimeCommandContext` | 1 回の実行コンテキスト。 |
| `ShimeCommandResult` | 成功、失敗、使用方法エラー、権限不足を表す結果。 |

### `ShimePluginNMS`

| クラス | 役割 |
| --- | --- |
| `ShimeVersionDetector` | サーバーの Minecraft バージョンを検出。 |
| `ShimeNmsAdapter` | バージョン別実装の SPI。 |
| `ShimeNmsAdapterRegistry` | アダプターの登録、選択、状態管理。 |
| `ShimeNmsManager` | アダプターの初期化、停止、レポート。 |
| `ShimePacketManager` | パケットリスナーとトランスポートの抽象化。 |

## 設計上の判断

### 削除した機能

- **Boss API** — コアの責務ではなく、将来 `ShimePluginBoss` のような任意モジュールで実装できるため。
- **独自権限システム** — Paper の `CommandSender.hasPermission` と `plugin.yml` の権限宣言で十分なため。
- **独自プレースホルダーシステム** — 外部の PlaceholderAPI 連携を任意の `ShimeIntegration` とするため。
- **独自イベントバス** — Paper のイベントシステムと `ShimeEventManager` で足りるため。

### 残した抽象化

- `ShimeService` と `ShimeModule` は責務が異なります。前者はライブラリ基盤、後者は機能単位の
  ライフサイクルです。両方を残しています。
- `ShimeIntegration` は外部プラグインの存在確認と有効化をカプセル化するため残しています。
- `ShimeCacheManager` は名前付きキャッシュのライフサイクル管理を提供するため残しています。

### NMS とパケット

コアは NMS の内部クラスを参照しません。`ShimePluginNMS` も、同梱アダプターはバージョン検出のみを
実装し、未検証のリフレクションやパケット転送を提供しません。パケット操作は `ShimePacketTransport`
を実装した検証済みアダプターがある場合にのみ利用できます。

## スレッド安全性

- `ShimeLibrary` のマネージャーは、Paper のメインスレッドからの利用を基本とします。
- キャッシュ、サービスレジストリ、タスク管理、データベースの内部状態はスレッドセーフな構造を使用します。
- データベースの非同期 API は `CompletableFuture` を返します。コールバックで Bukkit API を呼ぶ場合は
  `ShimeScheduler.callSync` でメインスレッドへ戻してください。
- GUI とスコアボードは Paper のメインスレッドからのみ操作してください。

## テスト

- `ShimePluginLibrary`: サービスレジストリ、キャッシュ、SQL、経済、検証、ライフサイクル。
- `ShimePluginCommand`: コマンドノード、ビルダー、引数パーサー。
- `ShimePluginNMS`: アダプターレジストリ、バージョン判定。

Paper の `JavaPlugin` を必要とするクラスは、モックまたは初期化前の状態のみをテストします。
実際のサーバーでの動作確認は、ビルドした JAR を Paper 1.20.6 に導入して行ってください。
