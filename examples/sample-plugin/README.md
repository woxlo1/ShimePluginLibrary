# サンプルプラグイン

`ShimePluginLibrary` を利用する最小構成のサンプルです。ルートの Gradle ビルドには含まれません。

## ビルド

先にルートでライブラリをビルドし、`ShimePluginLibrary` と `ShimePluginCommand` の JAR を生成します。

```bash
./gradlew :ShimePluginLibrary:jar :ShimePluginCommand:jar
```

次にサンプルをビルドします。

```bash
cd examples/sample-plugin
../../gradlew -p . build
```

Windows の場合:

```powershell
cd examples\sample-plugin
..\..\gradlew.bat -p . build
```

成果物は `build/libs/ShimeSamplePlugin-1.0.0.jar` です。サーバーへ導入する際は、
`ShimePlugin/build/libs/ShimePlugin-1.0.0.jar` も `plugins/` に配置してください。

## 内容

- `ShimeSamplePlugin`: 起動時に `ShimeLibrary.getOrNull()` でライブラリを取得します。
- `shimeCommand("shimesample")`: コマンド DSL で `/shimesample` を構築します。
- `ShimeCommandManager.register(root)`: `plugin.yml` に宣言したコマンドへバインドします。
- `onDisable`: 登録したコマンドを解除します。

このサンプルはコマンド API のみを扱います。GUI、経済、データベースなどの例は
[API ガイド](../../docs/API.md) を参照してください。
