<p align="center">
  <img src="assets/dextop-readme-icon.png" alt="Dextop" width="192">
</p>

<h1 align="center">Dextop</h1>

<p align="center">
  <a href="README.md">English</a> | <a href="README.ja.md">日本語</a>
</p>

Dextopは、Android端末上に仮想ディスプレイを作成し、スマートフォンだけでデスクトップ風の作業環境を利用するためのオープンソースアプリです。ShizukuとAndroidのシステム機能を利用して、アプリの起動、ウィンドウ配置、タッチ操作、画面方向などを制御します。

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
- [x] 折りたたみ端末の開閉状態に応じた解像度の自動切り替え
- [x] FPS、リフレッシュレート、メモリ、バッテリー、推定消費電力のパフォーマンス表示
- [x] クイック設定タイルからの起動
- [x] 中断されたセッションとAndroid設定の復元
- [x] アプリログ、能力判定、端末仕様を含む詳細な診断レポート
- [x] 日本語、英語、中国語、韓国語、ロシア語UI
- [ ] 物理マウスの完全対応（現在は移動、基本クリック、スクロールなどに限定）
- [ ] 物理キーボードの完全対応（ショートカット、IME、外部画面への入力経路は端末依存）

## 対応状況

| 環境 | 対応状況 | 備考 |
| --- | --- | --- |
| Samsung DeX | ほぼ対応 | 現在もっとも完全な動作環境です。DeX側で管理される機能はSamsungの実装を利用します。 |
| Google Pixel | 限定的・不完全 | Androidのfreeform／desktop実装と非公開APIの状態に依存し、一部機能が動作しない場合があります。 |
| その他のAndroid端末 | 実験的 | メーカー、機種、OS更新によって仮想ディスプレイ、ミラーリング、freeformの対応状況が異なります。 |

Dextopは実行時に端末の能力を検査し、複数のバックエンドを順番に試します。ただし、Androidの非公開APIやOEM実装を利用するため、同じメーカーでも機種やOSバージョンによって結果が異なります。

## 動作要件

- Android 10以降
- [Shizuku](https://shizuku.rikka.app/)
- ワイヤレスデバッグまたはADBによるShizukuの起動
- DextopへのShizuku権限

Shizukuのセットアップでわからない点がある場合は、[Shizuku公式セットアップガイド](https://shizuku.rikka.app/guide/setup/)の **Start via wireless debugging** を参照してください。

## インストール

Google Play版は現在審査中です。

[GitHub Releases](https://github.com/NarYuki/Dextop/releases/latest)から最新のAPKをダウンロードし、インストールしてください。

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

このプロジェクトは開発中です。端末やAndroidの更新により、利用できる機能や動作が変わる場合があります。

## ライセンス

GPL-3.0-or-laterでライセンスされています。詳細は[LICENSE](LICENSE)を参照してください。
