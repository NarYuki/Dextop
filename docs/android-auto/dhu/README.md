# Dextop Android Auto / DHU profiles

停車中の Android Auto（Parked App）として Dextop を検証するための
Desktop Head Unit (DHU) プロファイルです。画面形状ごとにプロファイルを
分けています。

## 起動

`SDK_LOCATION` は Android SDK の場所に置き換えてください。

```sh
DHU="$ANDROID_SDK_ROOT/extras/google/auto/desktop-head-unit"
"$DHU" -c "$PWD/docs/android-auto/dhu/dhu-parked-small-landscape.ini"
```

DHU 起動後、ターミナルで次のコマンドを入力すると停車中の制限なし状態を
再現できます。走行中の制限を再現する場合は `restrict all` を使います。

```text
restrict none
```

各プロファイルは `driving_status`、`parking_brake`、`gear`、`speed` センサーを
有効にしています。これらは DHU のセンサーコマンドで停車・走行状態を切り替える
ためのもので、ini を読み込んだだけで車両状態を固定するものではありません。

## プロファイル

| ファイル | 想定画面 |
| --- | --- |
| `dhu-parked-small-landscape.ini` | 標準の 800x480 横画面 |
| `dhu-parked-wide-landscape.ini` | ワイド横画面（1920x1080、実効 1920x484） |
| `dhu-parked-portrait.ini` | 縦長カーナビ（1920x1080から中央の1042x1080領域を使用） |
| `dhu-parked-subaru-portrait.ini` | Subaru型の縦長カーナビ（中央の600x1080領域を使用） |

DHUが受け付ける動画解像度は `800x480`、`1280x720`、`1920x1080` の3種類です。
縦長プロファイルは仕様外の縦解像度を要求せず、対応する `1920x1080` の左右に
マージンを設定し、`cropmargins=true` で中央の縦長領域だけを表示します。

## Dextop の確認ポイント

- `CAR_LAUNCHER` から Parked App として起動できること
- 横長・縦長の両方で VirtualDisplay のサイズと黒帯が追従すること
- Android Auto 側の画面タップが Dextop のミラー元へ転送されること
- DHU の `restrict all` / `restrict none` 切り替えで Dextop が安全に停止・再開すること
