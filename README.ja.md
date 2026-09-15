<p align="center">
  <img src="assets/dextop-readme-icon.png" alt="Dextop" width="192">
</p>

<h1 align="center">Dextop</h1>

<p align="center">
  <a href="README.md">English</a> | <a href="README.ja.md">日本語</a> | <a href="README.zh-CN.md">简体中文</a> | <a href="README.ko.md">한국어</a>
</p>

<p align="center">
  <a href="https://github.com/NarYuki/Dextop/releases/latest"><img src="docs/media/get-it-on-github.png" alt="GitHubから入手" height="56"></a>
  <a href="https://play.google.com/store/apps/details?id=moe.n4tsu.gpdextop"><img src="docs/media/get-it-on-google-play.png" alt="Google Playで手に入れよう" height="56"></a>
</p>

Dextopは、Android端末上に仮想ディスプレイを作成し、スマートフォンだけでデスクトップ風の作業環境を利用するためのオープンソースアプリです。Dextop 1.5.0以降には特権アクセス機能が内蔵されており、Androidのシステム機能と連携して、アプリの起動、ウィンドウ配置、タッチ操作、画面方向などを制御します。

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
- [x] 「スタイル」メニューから切り替えられる仮想ゲームパッド（ABXY、L/R、トリガー、スティック、方向パッド、Start/Select/Home）
- [x] 折りたたみ端末のメイン／サブディスプレイ切り替え
- [x] Android Auto向けParking／Driving Companion（車載解像度の自動適用、停車中の完全なタッチ操作、走行用のスマホ操作UI）
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

Android Auto向けには、Android 15以降で停車中用`CAR_LAUNCHER` Activityと、Android for Cars App Libraryを使用する別のDrivingサービスを提供します。各エントリーを表示するか、どの入力イベントを渡すかはAndroid Autoホスト側が決定します。

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
- 現行リリース：**Dextop 1.7.0**
- Android Auto Companion：**Dextop・Parking 2.0.0**／**Dextop・Driving 2.0.0**

Dextop 1.5.0以降には通常の動作に必要なアクセス機能が内蔵されているため、外部アプリや別の特権サービスは必須ではありません。セットアップから利用開始までDextopアプリだけで完結できます。端末によっては、Android側の権限許可やワイヤレスデバッグのペアリング画面が表示される場合があります。

root環境を利用している場合や、Stellar、Shizukuなどの互換性がある特権サービスをすでに利用している場合も使用できます。Dextopが利用可能な環境を自動検出し、その環境に合わせて動作するため、既存の構成を移行したり置き換えたりする必要はありません。

## インストール

[Google Play](https://play.google.com/store/apps/details?id=moe.n4tsu.gpdextop)からDextopをインストールできます。

または、[GitHub Releases](https://github.com/NarYuki/Dextop/releases/latest)から最新の署名済みAPKをダウンロードできます。

### Nightlyビルド

[GitHub Actions](https://github.com/NarYuki/Dextop/actions)から、最新の変更が適用された開発版を利用できます。最新の成功した**Debug APK**ワークフローを開き、NightlyのArtifactをダウンロードしてください。Artifactには同じビルドのDextop本体とDextop Car CompanionのデバッグAPKが含まれます。Nightlyは最新ソースから自動生成されるベータ版であり、未完成の機能や安定版にはない不具合を含む場合があります。

Android Auto対応は、**Dextop・Parking**（`moe.n4tsu.dextop.cardex`）と**Dextop・Driving**（`moe.n4tsu.dextop.cardex.driving`）の2つのCompanionアプリに分かれています。必要なCompanionを対応するDextopと一緒にインストールしてください。Dextopはリレーを有効にする前に、各Companionのパッケージ名と署名証明書を検証します。

**Dextop・Driving**は、[AAEnabler](https://github.com/malebuffy/AAEnabler)を通してインストールします。対応するDextopリリースからDrivingのAPKをダウンロードし、AAEnablerを開いて**Select local APK**からAPKを選択した後、**Install app**をタップしてAndroidのインストール画面を完了してください。**Dextop・Parking**は通常どおりインストールできますが、Android AutoにParkingが表示されない場合は、ParkingのAPKについても同じ手順でAAEnablerを通してインストールまたは再インストールしてください。AAEnablerは元のAPKや署名を変更せずにインストールします。

## Android Autoの簡単な使い方

Dextopは対応Androidバージョンの異なる2種類のAndroid Autoエントリーを提供します。

- **Dextop・Parking**：停車中用の全画面Activityを使用します。停車中は車載画面からタップ、ドラッグ、スクロール、スワイプを含む完全なタッチ操作ができます。
- **Dextop・Driving**：Android for Cars App LibraryのSurfaceを使用します。走行対応のホストフローで表示を継続でき、車載画面側の入力はAndroid Autoの制限を受けます。スマホ側のトラックパッドとキーボードから、カーソル、スクロール、スワイプ、文字入力を操作できます。

**Dextop・Parking**にはAndroid 15（API 35）以降が必要です。**Dextop・Driving**はAndroid 15の停車中アプリ用ランチャーを使用しないため、Android 13（API 33）以降に対応します。

1. Dextop本体と**Dextop・Parking**は通常どおりインストールし、**Dextop・Driving**は上記の手順でAAEnablerを通してインストールします。ParkingがAndroid Autoに表示されない場合は、ParkingもAAEnablerを通してインストールしてください。
2. 端末側でDextopの初期設定を完了します。1.5.0以降ではDextopの内蔵アクセスが自動的に使用され、root環境やStellar、Shizukuなどの互換性がある特権サービスがすでに利用可能な場合は、Dextopが自動検出して使用します。
3. Android Autoへ接続し、用途に応じて車載ランチャーから**Dextop・Parking**または**Dextop・Driving**を開きます。
4. **Start**を選択します。Parkingでは車載画面の完全なタッチ操作を転送します。DrivingではAndroid Autoが許可するクリック入力に加え、スマホ側のトラックパッドとキーボードを使用します。
5. Parkingでは車載画面の左端から右へスワイプしてAuto専用操作パネルを開きます。Drivingの操作項目はCar Appの画面から開きます。

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
