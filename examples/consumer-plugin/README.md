# ShimeConsumerPlugin

GitHub Packages に公開された `ShimePluginLibrary`、`ShimePluginCommand`、`ShimePluginNMS` を
解決し、コンパイルできることを確認するための利用者サンプルです。

ルートビルドには含まれません。

## 前提

- Java 21
- GitHub Packages の Maven リポジトリを読むための `read:packages` 権限を持つトークン
- Paper API を取得するための `https://repo.papermc.io/repository/maven-public/` への接続

GitHub Packages の Maven リポジトリは、公開リポジトリのパッケージでも認証を要求します。
`GITHUB_ACTOR` と `GITHUB_TOKEN` を設定してください。

## 公開成果物を直接解決する

```bash
cd examples/consumer-plugin
../../gradlew -p . build -PshimeArtifactSource=github -PshimeVersion=1.1.0
```

PowerShell の場合:

```powershell
$env:GITHUB_ACTOR = "your-account"
$env:GITHUB_TOKEN = "token-with-read-packages"
cd examples\consumer-plugin
..\..\gradlew.bat -p . build -PshimeArtifactSource=github -PshimeVersion=1.1.0
```

PowerShell では `-P...` が分割されることがあるため、次のように `--project-prop` を使用してください。

```powershell
..\..\gradlew.bat -p . build --project-prop shimeArtifactSource=github --project-prop shimeVersion=1.1.0
```

## ローカル公開物で座標と POM を検証する

GitHub Packages の認証がない環境では、同じ座標をローカル Maven リポジトリへ公開して
消費側の解決を検証できます。

```bash
./gradlew publishToMavenLocal --project-prop releaseVersion=1.1.0
cd examples/consumer-plugin
../../gradlew -p . build -PshimeArtifactSource=local -PshimeVersion=1.1.0
```

このモードは公開リポジトリへの到達性までは検証しません。POM の推移的依存関係と
コンパイル時の API 解決を検証します。

## GitHub Actions で公開成果物を検証する

`Verify Published Artifacts` ワークフローを手動実行すると、同じ利用者サンプルを
GitHub Packages の公開成果物に対してビルドします。`GITHUB_TOKEN` の `packages: read`
権限を使用するため、ローカルに `read:packages` トークンがない場合でも検証できます。

1. Actions の `Verify Published Artifacts` を選択
2. `version` に `1.1.0` などを入力して実行
3. 利用者サンプルのビルドが成功すれば、公開座標と推移的依存関係が解決できている

## 確認できること

- 3 つの artifactId が Maven 座標として解決できること
- `ShimePluginCommand` と `ShimePluginNMS` が `ShimePluginLibrary` を推移的に解決できること
- 公開 API を参照する Kotlin コードがコンパイルできること
