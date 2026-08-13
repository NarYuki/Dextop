# 端末対応を追加する

[English](ADDING_DEVICE_SUPPORT.en.md) | [日本語](ADDING_DEVICE_SUPPORT.md) | [简体中文](ADDING_DEVICE_SUPPORT.zh-CN.md)

この文書は、新しいAndroid端末やOEM実装への対応をPull Requestで追加するための実装ガイドです。Dextopでは、1台のための修正が他の端末へ影響しないことを最優先にします。

## 先に診断情報を取得する

1. Dextopで **設定 → アプリ情報 → 動作ログと端末診断** を開きます。
2. セッション停止中と、問題を再現した直後のレポートを保存します。
3. `manufacturer`、`model`、`device`、`fingerprint`、`sdk`、`environmentId`を確認します。
4. `probe.*`と動作ログ内の`strategy=... success=...`から、失敗した方式を特定します。
5. IssueやPRへ添付する前に、不要な識別情報を削除します。

ADBから基本情報だけ取得する場合:

```sh
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.product.device
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
```

## コード構成

| ファイル | 役割 |
| --- | --- |
| `DeviceProfiles.kt` | 端末・メーカーのマッチ条件と戦略順 |
| `DesktopEnvironment.kt` | `DeviceIdentity`、`DeviceMatch`、端末能力モデル |
| `CapabilityProbe.kt` | 状態を変更しない実行時能力検査 |
| `DisplayMirrorBackend.kt` | 仮想画面作成とミラー方式 |
| `DesktopModeConfigurator.kt` | freeform設定とウィンドウモード戦略 |
| `SessionJournal.kt` | 一時変更したAndroid設定の復元 |
| `DeviceMatchTest.kt` | 端末ルールが他機種へ波及しないためのテスト |

## 既存方式で動く端末を追加する

既存バックエンドで動作し、表示名や方針だけを追加する場合は`DeviceProfiles.rules`へルールを追加します。

具体的な機種ルールは、メーカー全体のルールより前に置きます。`firstOrNull`で最初に一致したルールが採用されるためです。

```kotlin
DesktopEnvironmentRule(
    id = "example_phone_2_android_16",
    match = DeviceMatch(
        manufacturers = setOf("example"),
        models = setOf("example phone 2"),
        devices = setOf("example2"),
        fingerprintPrefixes = setOf("example/example2/"),
        minSdk = 36,
        maxSdk = 36
    )
) { identity ->
    DesktopEnvironmentRegistry.aospFreeform(identity).copy(
        id = "example_phone_2_android_16",
        displayName = "Example Desktop",
        mirrorStrategies = listOf("window_manager", "surface_control"),
        windowingStrategies = listOf("wm", "activity_task_manager")
    )
}
```

条件は実際に必要な範囲まで絞ります。

- 1機種だけの回避策を`manufacturers`だけで指定しない
- Android特定版だけの問題には`minSdk`と`maxSdk`を指定する
- モデル名が地域版で変わる場合は、安定している`device`やfingerprint prefixを使う
- fingerprint全体はビルド更新で変わるため、必要最小限のprefixにする
- 無関係な端末の既定戦略順を変更しない

## 新しいミラーバックエンドを追加する

既存の`window_manager`と`surface_control`で動作しない場合は、`MirrorAttachBackend`を実装します。

```kotlin
private class ExampleMirrorBackend : MirrorAttachBackend {
    override val id = "example_mirror"

    override fun isSupported(): Boolean = runCatching {
        // APIの存在確認だけを行う。ここで設定変更や画面作成をしない。
        Class.forName("example.hidden.MirrorApi")
    }.isSuccess

    override fun createLayer(displayId: Int): SurfaceControl {
        // 失敗時は理由が分かる例外を投げる。
        return createExampleMirror(displayId)
    }
}
```

次に`DisplayMirrorBackend.attachBackends`へ登録します。

```kotlin
listOf(
    WindowManagerMirrorBackend(privilegedAccess),
    SurfaceControlMirrorBackend(),
    ExampleMirrorBackend()
).associateBy { it.id }
```

対象端末のルールだけで新方式を先頭にします。既存端末には追加方式を適用しません。

```kotlin
mirrorStrategies = listOf(
    "example_mirror",
    "window_manager",
    "surface_control"
)
```

バックエンドは以下を守る必要があります。

- `isSupported()`は読み取り専用にする
- 失敗時に次の戦略へ進めるよう、プロセスを終了させない
- `SurfaceControl`、ParcelFileDescriptor、プロセスを必ず解放する
- 失敗理由をログで判別できる例外にする
- 一時設定を変更する場合は`SessionJournal`へ変更前の値を記録する
- 成功、失敗、タイムアウト、強制停止のすべてで設定を復元する

## 能力プローブを追加する

APIの有無をSDK番号やメーカー名だけで推測せず、`CapabilityProbe.run()`へ読み取り専用プローブを追加します。

```kotlin
"exampleMirror" to probe("Example mirror API") {
    Class.forName("example.hidden.MirrorApi")
        .getDeclaredMethod("mirror", Int::class.javaPrimitiveType)
}
```

プローブは診断レポートへ`probe.exampleMirror`として出力されます。プローブ内ではシステム設定の書き換え、画面作成、アプリ起動を行わないでください。

## 必須テスト

対象端末への一致だけでなく、似た端末と無関係な端末に一致しないことをテストします。

```kotlin
@Test
fun exampleRuleIsLimitedToTargetDevice() {
    val match = DeviceMatch(
        manufacturers = setOf("example"),
        devices = setOf("example2"),
        minSdk = 36,
        maxSdk = 36
    )
    assertTrue(match.matches(targetIdentity))
    assertFalse(match.matches(targetIdentity.copy(device = "example1")))
    assertFalse(match.matches(targetIdentity.copy(manufacturer = "other")))
    assertFalse(match.matches(targetIdentity.copy(sdk = 35)))
}
```

実行するチェック:

```sh
flutter analyze
flutter test
cd android
./gradlew testDebugUnitTest assembleDebug
```

## 実機確認

- 初回起動、StellarまたはShizukuの許可、セッション開始と停止
- 仮想ディスプレイの作成、ミラー表示、HOME起動
- 縦横切り替え、解像度とDPI変更
- セキュア表示のオン／オフとスクリーンショット
- 画面ロック、ロック解除、アプリ再起動
- タッチ、カーソル、基本ジェスチャー
- 失敗させた第1戦略から第2戦略へのフォールバック
- 停止後に`overlay_display_devices`などの一時設定が復元されること
- 対象外の端末で従来と同じ戦略が選択されること

## Pull Requestに含めるもの

- メーカー、正確なモデル名、コードネーム、Android版、SDK
- 個人情報を除いた診断レポート
- 問題発生時と修正後のログ
- 追加・変更した戦略と、その順序にした理由
- 対象端末への肯定テストと対象外端末への否定テスト
- 実機確認結果

端末固有修正をメーカー共通ルールへ入れる、既存フォールバックを削除する、失敗を握り潰す、復元されないシステム設定を追加するPRは受け入れられません。
