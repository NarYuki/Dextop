# Linux サービス監査5件の修正・検証

> 再監査で確認した清掃以外のリンク処理・未登録の保護指示・Bluetoothの全件列挙は追加修正済み。`linux-service-security-reaudit-2026-11-12.md` を参照。この文書の成功したテストだけで全経路が安全と判断しない。

2026-11-12（Asia/Tokyo）。対象は現行作業ツリー。既存の未コミット変更は保持。
APK のインストールや稼働中サービスの置き換えは行っていない。

## 変更

| 監査ID | 修正 |
|---|---|
| A1 | tmpfiles の未実装オプションを操作開始前に拒否。`--dry-run` / `--root` / `--image` / `--replace` / `--purge` / `--graceful` / `-E` を無視して実行する挙動を削除 |
| A2 | 清掃開始点の全パス要素を `O_DIRECTORY | O_NOFOLLOW` で固定。FD 相対で走査・削除し、子の差し替え・リンクをたどらない。除外を各階層に適用 |
| A3 | D-Bus のフレーム長・ヘッダ・本文を byte-order に従って読む。big-endian と little-endian の混在・分割受信に対応。16 MiBを超えるフレームや不正ヘッダは接続を閉じて再接続対象にする |
| A4 | `Protect*` / `Private*` / `Restrict*` ほか未対応の保護指示をバリデーションで拒否。空の `CapabilityBoundingSet=` も拒否。forking / notify 等の既存対応型は維持 |
| A5 | Bluetooth 書き込み前の `readText()` 比較を削除し、既存ファイルを読まず原子的に置換。要求は開いた FD を検証し、通常ファイルのみ・最大8,192バイト。`NOFOLLOW` / `NONBLOCK` と8,193バイトの固定バッファで差し替え・肥大化・FIFOによる無制限読み込みを防ぐ |

### 互換性の変更

未対応オプションや保護指示を指定した呼び出しは、成功したように見せずエラーにする。
そのような指示を持つパッケージの unit が起動しなくなる場合がある。
保護を外して自動実行するフォールバックは追加していない。
清掃対象やその親がシンボリックリンクの場合も、その参照先は清掃せずエラーを報告する。

Bluetooth の既存ファイル比較をなくしたため、同じ内容でも snapshot を置換する。
共有ワーカーがゲストの古い内容を読む必要はなくなる。

## 検証結果

- 新規セキュリティ回帰テスト **12/12**：ホストと実機 PRoot ゲストの双方で成功。
  未対応オプションの変更前拒否、通常の作成・清掃、開始点・親のリンク、除外階層、
  `stat` と `open` の間でディレクトリをリンクへ差し替える競合、保護指示、
  実 supervisor の拒否と通常 unit の起動、D-Bus の混在・分割・不正長を含む。
- 既存 parser / keyring テスト **14/14** 成功。古いテストの forking/notify 拒否という
  期待だけを現在の対応機能に合わせて分離し、保護指示の拒否テストは維持。
- Kotlin `LinuxBluetoothFilesTest` **2/2** 成功。2 GiB の sparse ファイルを小さな
  snapshot へ置換し、リンク先のファイルも変更されないことを検証。
- 監査で使用した独立 GLib/GIO エンコーダ＋実 dbus-daemon の実機隔離試験：
  176バイトの正規 big-endian 呼び出しと、その後の通常呼び出しに **両方とも返答**。
- 現行 Kotlin writer を抽出した **16 MiB ヒープ**の JVM で、64 MiB sparse ファイルを
  読み込まず置換でき、リンク先の sentinel が保たれることを確認。
- `:app:compileGithubDebugKotlin` と、指定した JVM テスト用の Gradle タスクが成功。
- Python 構文と変更箇所の whitespace を確認。

## 再実行

```sh
python3 -m unittest discover -s tool/tests -p test_linux_service_security.py
PYTHONPATH=tool/tests python3 -m unittest test_linux_systemd.UnitParserTests test_linux_keyring_unlock
cd android
./gradlew :app:compileGithubDebugKotlin
./gradlew :app:testGithubDebugUnitTest --tests moe.n4tsu.dextop.linux.LinuxBluetoothFilesTest
```

実機用には `test_linux_service_security.py` と3つの現行 Python リソースを隔離ディレクトリに
置き、`DEXTOP_SERVICE_TEST_RESOURCES` でその場所を指定して実行した。
実 D-Bus 試験は監査の `dbus_probe.py` の期待を「2件とも返答」に変更して使用した。
詳細ログと実行スクリプトはローカルの ignored `temp/service-fixes-20261112/` に保持。
テスト用実機プロセスとディレクトリは検証後に削除した。

## 範囲

監査時の5件への修正であり、PRoot を完全な同一UID分離へ変更するものではない。
同一 UID の keyring 接続先を本物の daemon と区別できないという設計上の制約は残る。
今回の tmpfiles の FD 化は清掃経路が対象で、他の tmpfiles 互換処理全体に完全な
サンドボックスを提供するものではない。
