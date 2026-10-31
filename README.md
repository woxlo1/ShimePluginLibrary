# ShimePluginLibrary

Paper 1.20.6 / Java 21 を初期対象とする Gradle マルチプロジェクトです。

## モジュール

- `ShimePlugin`: サーバーに導入するプラグイン本体。メインクラスと `plugin.yml` を配置します。
- `ShimePluginLibrary`: GUI、Config、Database、Player、Item、Message、Event、Task、Permission、Economy、Cache、Storage、Scoreboard、Integration などの共通ライブラリ機能をまとめます。
- `ShimePluginCommand`: コマンドAPIを独立して管理します。
- `ShimePluginNMS`: NMSおよびパケットなどのバージョン依存処理をまとめます。

GUI / Config / Database などを個別のGradleモジュールには分割しません。これらは `ShimePluginLibrary` 内のパッケージとして実装します。

## ビルド

Gradle 8.7 以降と Java 21 が必要です。

```bash
./gradlew build
```

このリポジトリは初期構成です。API本体の実装およびビルド検証はこれから行います。
