<p align="center">
  <img src="assets/dextop-readme-icon.png" alt="Dextop" width="192">
</p>

<h1 align="center">Dextop</h1>

<p align="center">
  <a href="README.md">English</a> | <a href="README.ja.md">日本語</a> | <a href="README.zh-CN.md">简体中文</a> | <a href="README.ko.md">한국어</a>
</p>

Dextopは、Android端末上に仮想ディスプレイを作成し、スマートフォンだけでデスクトップ風の作業環境を利用するためのオープンソースアプリです。StellarまたはShizukuとAndroidのシステム機能を利用して、アプリの起動、ウィンドウ配置、タッチ操作、画面方向などを制御します。

## コミュニティとフィードバック

公式Discordサーバーに参加してください: [ここから参加](https://discord.com/invite/444YG3srK)

バグ報告、動作報告、機能リクエストを行うことができます。

## スクリーンショットとデモ

<table>
  <tr>
    <td width="20%" align="center"><img src="docs/media/home.jpg" alt="Dextopホーム画面"><br><sub>ホームとワークスペース</sub></td>
    <td width="20%" align="center"><img src="docs/media/desktop.jpg" alt="Dextopデスクトップ"><br><sub>デスクトップ</sub></td>
    <td width="20%" align="center"><img src="docs/media/control-overlay.jpg" alt="Dextop操作オーバーレイ"><br><sub>操作オーバーレイ</sub></td>
    <td width="20%" align="center"><img src="docs/media/multi-window.jpg" alt="Dextopマルチウィンドウ"><br><sub>マルチウィンドウ</sub></td>
    <td width="20%" align="center"><a href="docs/media/dextop-demo.mp4"><img src="docs/media/demo-poster.jpg" alt="Dextopデモ動画を再生"></a><br><sub>▶ デモ動画</sub></td>
  </tr>
</table>

## 機能

- [x] 解像度、DPI、縦向き／横向きを指定した仮想ディスプレイ
- [x] セキュア表示とAndroidシステム装飾の切り替え
- [x] デスクトップ上でのアプリランチャー
- [x] 複数アプリの位置を保存・再現するワークスペース
- [x] 2分割、3分割、4分割などのウィンドウレイアウト
- [x] ワークスペースのJSONインポート／エクスポート
- [x] カーソルモードと直接タッチモード
- [x] タップ、長押し、ドラッグ、右クリック、2本指／3本指ジェスチャー
- [x] スクロールやピンチズームを含むマルチタッチ入力
- [x] 物理マウス対応
- [x] 物理キーボード対応
- [x] 対応端末でのDextopと外部ディスプレイ間のマウス／キーボード入力ルーティング切り替え
- [x] モニター配置の保存とディスプレイ間のポインタールーティングに対応したマルチディスプレイトポロジー
- [x] デスクトップタスクバーの自動非表示と内蔵ディスプレイの120 Hz維持
- [x] US配列キーボード、トラックパッド、手動呼び出し、ヒンジ角度の自動検知に対応した折りたたみ端末向けラップトップモード
- [x] 折りたたみ端末のメイン／サブディスプレイ切り替え
- [x] Android 15以降の停車中Android Auto向けミラーリングActivity（車載解像度の自動適用、Dextop／端末画面の切り替え、タッチ転送）
- [x] FPS、リフレッシュレート、メモリ、バッテリー、推定消費電力のパフォーマンス表示
- [x] クイック設定タイルからの起動
- [x] 中断されたセッションとAndroid設定の復元
- [x] アプリログ、能力判定、端末仕様を含む詳細な診断レポート
- [x] 機能別の結果とメール作成に対応したローカライズ済み動作報告
- [x] 日本語、英語、中国語、韓国語、ロシア語UI

## 対応状況

| 環境 | 対応状況 | 備考 |
| --- | --- | --- |
| Samsung DeX（One UI 8以降） | 完全対応 | 現在もっとも完全な動作環境です。DeX側で管理される機能はSamsungの実装を利用します。 |
| Samsung DeX（One UI 8未満） | 限定的・非互換の可能性が高い | 古いDeX実装では、Dextopが必要とするディスプレイおよびウィンドウ管理の動作を利用できない場合があります。 |
| Google Pixel | 限定的・不完全 | Androidのfreeform／desktop実装と非公開APIの状態に依存し、一部機能が動作しない場合があります。 |
| OPPO ColorOSデスクトップ | 限定的・不完全 | デスクトップは表示できますが、タスクバーなどのシステムUIが表示されない場合があります。 |
| HyperOS以降を実行しているXiaomi端末 | 無効 | MIUIおよびHyperOSはサポート対象外です。 |
| その他のAndroid端末 | 実験的 | メーカー、機種、OS更新によって仮想ディスプレイ、ミラーリング、freeformの対応状況が異なります。 |

Android Auto向けはAndroid 15以降の停車中アプリ用`CAR_LAUNCHER` Activityとして公開しています。実際に車載ランチャーへ表示するかはAndroid Auto側が決定し、現在の公開仕様では対象カテゴリが限定されています。

Dextopは実行時に端末の能力を検査し、複数のバックエンドを順番に試します。ただし、Androidの非公開APIやOEM実装を利用するため、同じメーカーでも機種やOSバージョンによって結果が異なります。

<details>
<summary><strong>対応デバイス一覧</strong></summary>

以下は実際に検証したファームウェアだけを対象とした対応状況です。ベンダー名をクリックすると端末一覧を展開できます。詳しい機能別結果は[デバイス対応状況Wiki](https://github.com/NarYuki/Dextop/wiki/Device-Compatibility)を参照してください。

<details>
<summary><strong>Samsung</strong></summary>

| デバイス | モデル | 検証済みソフトウェア | 対応状況 |
| --- | --- | --- | --- |
| Galaxy S26 | SM-S942Z (`m1q`) | Android 16 / One UI 8.5 / `S942ZSCS1AZF2` | ✅ Confirmed working |
| Galaxy Z TriFold | SM-F968N (`q7mq`) | Android 16 (API 36) / One UI 8.0 / `F968NKSS6BZG3` | ✅ Confirmed working |
| Galaxy Z Fold8 | SM-F971Q (`h8q`) | Android 17 (API 37) / One UI 9.0 / `F971QOPU1AZGI` | ✅ Confirmed working |
| Galaxy Z Fold7 | SM-F966Q (`q7q`) | Android 16 (API 36) / One UI 8.0 / `F966QOPU1BZF1` | ✅ Confirmed working |
| Galaxy Z Fold3 5G | SCG11 (`SCG11`) | Android 15 (API 35) / One UI 7.0 / `SCG11KDS1EZB8` | ❌ Not working at this time |

_コミュニティから提出され、レビューされた動作報告_

</details>

<details>
<summary><strong>Google</strong></summary>

| デバイス | モデル | 検証済みソフトウェア | 対応状況 |
| --- | --- | --- | --- |
| Pixel 9a | Pixel 9a (`tegu`) | Android 17 (API 37) / `15641320` | 🟡 Partial |

_コミュニティから提出され、レビューされた動作報告_

</details>

<details>
<summary><strong>HONOR</strong></summary>

| デバイス | モデル | 検証済みソフトウェア | 対応状況 |
| --- | --- | --- | --- |
| HONOR Magic 8 Pro | BKQ-AN10 (`HNBKQ`) | Android 16 (API 36) / `10DLDLD170SP5C00E167` | 🧪 Experimental |

_コミュニティから提出され、レビューされた動作報告_

</details>

<details>
<summary><strong>OPPO</strong></summary>

> ColorOSではデスクトップを表示できますが、対応は不完全で、タスクバーが表示されない場合があります。

| デバイス | モデル | 検証済みソフトウェア | 対応状況 |
| --- | --- | --- | --- |
| Find X9 | OPG07 (`OP5E8BL1`) | Android 16 (API 36) / ColorOS 16 / `B.R4T3.1287153_118ce71_119cc78` | 🧪 Experimental |

_コミュニティから提出され、レビューされた動作報告_

</details>

<details>
<summary><strong>Sony</strong></summary>

| デバイス | モデル | 検証済みソフトウェア | 対応状況 |
| --- | --- | --- | --- |
| Xperia 1 III | XQ-BC42 (`XQ-BC42`) | Android 13 (API 33) / `061002A0000472A1434898470` | ❌ Not working at this time |

_コミュニティから提出され、レビューされた動作報告_

</details>

<details>
<summary><strong>Xiaomi</strong></summary>

> HyperOS以降を実行しているXiaomi端末ではデスクトップ環境は無効です。MIUIおよびHyperOSはサポート対象外です。

| デバイス | モデル | 検証済みソフトウェア | 対応状況 |
| --- | --- | --- | --- |
| POCO X7 Pro 5G | 2412DPC0AG (`rodin`) | Android 16 (API 36) / HyperOS 3.0 / `OS3.0.301.0.WOJMIXM` | ❌ Not working at this time |
| POCO X7 Pro | 2412DPC0AG (`rodin`) | Android 16 (API 36) / HyperOS 3.0 / `OS3.0.301.0.WOJMIXM` | ❌ Not working at this time |

_コミュニティから提出され、レビューされた動作報告_

</details>

</details>

## 動作要件

- Android 10以降。大半の端末では実用的なデスクトップ環境にAndroid 14以降が必要です。
- [Stellar](https://github.com/roro2239/Stellar/releases)（標準。特にAndroid 16以降で推奨）または[Shizuku](https://github.com/RikkaApps/Shizuku/releases)
- ワイヤレスデバッグ、ADB、またはrootによる選択サービスの起動
- DextopへのStellarまたはShizuku権限

Dextopの「GitHubからダウンロード」ボタンからStellarを入手できます。Android 16以降ではStellarを推奨します。Android 16以降でShizukuを使う場合はPlay Store版ではなく[GitHub Releases版](https://github.com/RikkaApps/Shizuku/releases)を使用してください。Android 15以下ではPlay Store版も使用できます。両方が入っている場合は使用するサービスを選択し、どちらかがアンインストールされるまで選択を保存します。

ワイヤレスデバッグの設定でわからない点がある場合は、[Shizuku公式セットアップガイド](https://shizuku.rikka.app/guide/setup/)の **Start via wireless debugging** を参照してください。Stellarをワイヤレス起動する場合もAndroid側のペアリング手順は共通です。

## インストール

Google Play版は現在審査中です。

[GitHub Releases](https://github.com/NarYuki/Dextop/releases/latest)から最新のAPKをダウンロードし、インストールしてください。

### Nightlyビルド

[GitHub Actions](https://github.com/NarYuki/Dextop/actions)から、最新の変更が適用された開発版を利用できます。最新の成功した**Debug APK**ワークフローを開き、NightlyのArtifactをダウンロードしてください。Artifactには同じビルドのDextop本体とDextop Car CompanionのデバッグAPKが含まれます。Nightlyは最新ソースから自動生成されるベータ版であり、未完成の機能や安定版にはない不具合を含む場合があります。

Android Auto対応を含むGitHub Releasesには、Dextop本体APKと対応する**Dextop Car Companion** APKを両方掲載します。署名とリレー仕様を一致させるため、必ず同じリリースに含まれる2つのAPKを使用してください。

## Android Autoの簡単な使い方

DextopはAndroid 15以降で、**Dextop Car Companion**を使用して対応する停車中のAndroid Auto画面へ専用デスクトップを表示できます。

1. 同じリリースに含まれるDextopと**Dextop Car Companion**のAPKを両方インストールします。
2. StellarまたはShizukuを起動し、Dextopへ権限を付与して端末側の初期設定を完了します。
3. 停車中にAndroid Autoへ接続し、車載ランチャーから**Dextop Car Companion**を開きます。
4. **Start**を選択します。車載画面の解像度に合わせたAuto専用Dextopが作成され、タッチ操作が直接転送されます。
5. 車載画面の左端から右へスワイプすると、ワークスペース、映像の再接続、停止を行うAuto専用操作パネルが開きます。

Android Auto側が、サイドロードされた停車中アプリを車載ランチャーへ表示するかを決定します。標準の互換表示方式では端末側にAuto用仮想ディスプレイのオーバーレイが表示される場合があります。実験的な非表示方式は**Dextop → 設定 → Auto**から変更できます。インストール条件、表示方式、ジェスチャー、各操作、制限事項、DHU検証、問題の対処方法は[Android Auto Wiki](https://github.com/NarYuki/Dextop/wiki/Android-Auto)を参照してください。

## 開発

```sh
git clone https://github.com/NarYuki/Dextop.git
cd Dextop
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

新しい端末への対応を追加する場合は、[端末対応の追加ガイド](docs/ADDING_DEVICE_SUPPORT.md)を参照してください。英語版は[こちら](docs/ADDING_DEVICE_SUPPORT.en.md)です。

## 診断情報

**設定 → アプリ情報 → 動作ログと端末診断**から、端末仕様、能力プローブ、フォールバック結果、Dextopの動作ログを表示・コピー・共有できます。不具合報告には、必要に応じて個人情報を取り除いた診断レポートを添付してください。

## 動作報告

**設定 → 動作報告**から、特定の端末とファームウェアにおけるDextopの動作状況を報告できます。総合結果と各機能について**動作した**、**動作しない**、**未確認**のいずれかを選び、必要なら特記事項を入力して、**メールで動作報告を送る**をタップしてください。Dextopが構造化されたMarkdown本文を作成し、宛先を`dextop-device@n4t.su`に設定してメールアプリを開きます。

報告には端末モデル、コードネーム、Android/APIバージョン、ファームウェア識別情報、セキュリティパッチ、Dextopバージョン、検出能力、選択した動作結果が含まれます。送信前にメール本文を確認してください。詳細は[動作報告Wiki](https://github.com/NarYuki/Dextop/wiki/Device-Reports)を参照してください。

このプロジェクトは開発中です。端末やAndroidの更新により、利用できる機能や動作が変わる場合があります。

## ライセンス

GPL-3.0-or-laterでライセンスされています。詳細は[LICENSE](LICENSE)を参照してください。
