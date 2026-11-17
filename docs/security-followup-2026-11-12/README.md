# 追加点検で確認した4件

> 以下は修正前の調査記録。現在は追加修正済み。`../linux-service-followup-fixes-2026-11-12.md` を参照。reproduce.py は修正後の期待を確認する検証スクリプトに更新した。results.json は修正前、fixed-results.json は修正後の結果。

2026-11-12、現行作業ツリー。調査のみで製品コードは変更していない。
再現はホストの一時ディレクトリ内に限定。実機再検証・APK反映は今回行っていない。
優先度はデータ削除と設定の影響に基づく暫定評価。

## P1: tmpfiles設定の x 除外が清掃に反映されない

`d <cache> 0700 - - 0` と `x <cache>/keep - - - -` を同じ設定に置き、`--clean` を実行すると keep/sentinel が削除され、終了コードも0となる。
`main()` はCLIの `--exclude-prefix` だけを清掃へ渡し、解析した x/X エントリから清掃用の除外条件を作っていない。
CLI除外の前回修正とは別経路。保護したつもりの実データを失うおそれがある。
位置: dextop-tmpfiles:408–411。

## P1: boot限定の削除が通常実行でも行われる

`r! <sentinel> - - - -` を `--remove` だけで実行すると削除され、終了コード0となる。
`!` の判定は create() 内にしかなく、remove/cleanは判定を経由しない。
稼働中に消してはいけないロック・状態ファイルをパッケージ更新時などに削除するおそれがある。
位置: dextop-tmpfiles:404–411、create():294。

## P2: サービス用ディレクトリが指定配置先の外に作用する

`RuntimeDirectory=<一時ディレクトリ内の絶対パス>` をvalidate_service()が受理する。
ユーザー用XDG_RUNTIME_DIRとは別のそのディレクトリが、directories()で0700から0777へ変更される。
Path(root)/absolute がrootを捨てるのに、絶対パスを拒否せず `..` しか検査していない。
さらに CacheDirectory=alias のaliasを外部ディレクトリへのリンクにすると、参照先のmodeが変更される。
システム用でUser=があればchownも同じパスをたどる。ただし別UIDへの所有権変更や実機での権限昇格は未検証であり、主張しない。
位置: dextop-systemd:511–519。

## P2: f+ が既存ファイルを更新しない

OLDを格納したファイルに `f+ <file> 0600 - - - NEW` を適用してもOLDのままで、終了コード0となる。
既存fファイルを開く分岐で常にfresh=Falseとなり、+の有無にかかわらず内容の書き込みを省く。
設定で指定した初期化・上書きが反映されず、古い状態が残る。
位置: dextop-tmpfiles:306–320。

## 再現・基準

`python3 docs/security-followup-2026-11-12/reproduce.py`

5つのassertionで4件を確認する（ディレクトリ問題は絶対パスとリンクの2経路）。
現在のスクリプトは修正後の保持・拒否・更新動作を確認する。
既存セキュリティ回帰テスト17件も成功するため、既存テストでは今回の条件を検出できない。

仕様との照合:
- https://www.freedesktop.org/software/systemd/man/latest/tmpfiles.d.html （x/X、!、f+）
- https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html （RuntimeDirectory等の相対パス要件）

今回の範囲はtmpfiles、サービス起動時の保護・ディレクトリ処理、Bluetoothファイル処理の周辺。
Bluetoothの大量列挙について、新たな全件配列化は見つからなかった。結果清掃の走査時間がファイル数に比例する点は前回記録済み。
プロジェクト全体や同一UID攻撃すべての安全性を保証する監査ではない。
