// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Japanese (`ja`).
class AppLocalizationsJa extends AppLocalizations {
  AppLocalizationsJa([String locale = 'ja']) : super(locale);

  @override
  String get home => 'ホーム';

  @override
  String get settings => '設定';

  @override
  String get resolution => '解像度';

  @override
  String get theme => 'テーマ';

  @override
  String get system => 'システム';

  @override
  String get light => 'ライト';

  @override
  String get dark => 'ダーク';

  @override
  String get display => '表示';

  @override
  String get secureDisplay => 'セキュア表示';

  @override
  String get secureDisplayDescription => '保護されたコンテンツの表示を許可します';

  @override
  String get mirrorBackend => 'ディスプレイのミラーリング方式';

  @override
  String get mirrorBackendAuto => '自動（互換性優先）';

  @override
  String get mirrorBackendAutoDescription => 'この端末で利用可能な最適な方式を使用します';

  @override
  String get mirrorBackendWindowManager => 'WindowManager';

  @override
  String get mirrorBackendSurfaceControl => 'SurfaceControl';

  @override
  String get mirrorBackendVirtualDisplay => 'VirtualDisplay';

  @override
  String get updateAvailable => 'アップデートがあります';

  @override
  String get updateAvailableTitle => 'GitHubに最新リリースが公開されています！';

  @override
  String get checkForUpdates => '更新を確認';

  @override
  String get checkingForUpdates => '更新情報を取得しています';

  @override
  String get updateNotChecked => '更新情報をまだ取得していません';

  @override
  String get upToDate => '最新バージョンです';

  @override
  String get updateCheckFailed => '更新情報を取得できませんでした';

  @override
  String get currentVersion => '現在のバージョン';

  @override
  String get latestVersion => '最新バージョン';

  @override
  String get openOnGitHub => 'GitHubを開く';

  @override
  String get close => '閉じる';

  @override
  String get deviceInfo => '端末情報';

  @override
  String get desktopMode => '使用するデスクトップモード';

  @override
  String get accessibilitySettings => 'アクセシビリティ設定';

  @override
  String get accessibilityDescription => 'Dextopのサービス設定を開きます';

  @override
  String get appInfo => 'アプリ情報';

  @override
  String get licenses => 'オープンソースライセンス';

  @override
  String get licensesDescription => 'Flutterと使用ライブラリのライセンスを表示';

  @override
  String get landscape => '横向き';

  @override
  String get portrait => '縦向き';

  @override
  String get stopped => '停止中';

  @override
  String get running => '起動中';

  @override
  String get start => '起動';

  @override
  String get stop => '停止';

  @override
  String get customAdd => 'カスタム解像度を追加';

  @override
  String get editResolution => '解像度を編集';

  @override
  String get add => '追加';

  @override
  String get save => '保存';

  @override
  String get deleteResolution => 'この解像度を削除';

  @override
  String get width => '幅';

  @override
  String get height => '高さ';

  @override
  String get protectedContent => '保護されたコンテンツの表示を許可します';

  @override
  String get version => 'バージョン 1.0.0';

  @override
  String get setupWelcome => 'Dextopへようこそ。';

  @override
  String get setupTagline => 'スマホ単体で、完璧なデスクトップ環境を。';

  @override
  String get setupBegin => 'はじめる';

  @override
  String get setupPhaseTerms => 'ご利用にあたって';

  @override
  String get setupPhaseShizuku => 'Shizuku';

  @override
  String get setupPhaseDevice => '端末の確認';

  @override
  String get setupPhaseDemo => '操作を体験';

  @override
  String get back => '戻る';

  @override
  String get continueLabel => '続ける';

  @override
  String get done => '完了';

  @override
  String get incomplete => '未完了';

  @override
  String get setupSystemTitle => 'システム機能を利用します';

  @override
  String get setupSystemDescription =>
      'DextopはShizukuとADBを使用し、仮想ディスプレイ、画面方向、入力、システムUIなどの挙動を制御します。';

  @override
  String get setupDisclaimer =>
      '端末やOSの実装差、システム更新、他のアプリとの競合などにより生じた不具合、データ損失、端末機能への影響について、開発者は責任を負いません。内容を理解したうえで使用してください。';

  @override
  String get setupShizukuTitle => 'Shizukuを準備';

  @override
  String get setupShizukuDescription =>
      'Dextopがシステム機能へ安全にアクセスするためにShizukuを使用します。';

  @override
  String get setupInstallShizuku => 'Shizukuをインストール';

  @override
  String get setupConfigureShizuku => 'Shizukuを設定';

  @override
  String get setupShizukuHint =>
      'Shizukuを開き、「ペアリング」に表示される順序に従って設定し、Shizukuを開始してください。';

  @override
  String get setupOpenShizuku => 'Shizukuを開く';

  @override
  String get setupValidate => '設定が完了しましたか？ 有効性をチェック';

  @override
  String get setupDextopPermission => 'Dextopへの権限';

  @override
  String get setupInstallPlay => 'Google Playからインストール';

  @override
  String get setupAllowPermission => '権限を許可';

  @override
  String get setupProviderChoiceTitle => '特権サービスを選択';

  @override
  String get setupProviderChoiceDescription =>
      'StellarとShizukuの両方がインストールされています。Dextopで使用するサービスを選択してください。';

  @override
  String get setupUseStellar => 'Stellar（推奨）';

  @override
  String get setupUseShizuku => 'Shizuku';

  @override
  String get setupRunningAsRoot => 'rootでサービスを実行中です';

  @override
  String get setupRootVerified =>
      'Shizukuがrootで実行中であることを確認しました。Dextopへの権限を付与してください。';

  @override
  String get setupRootNotRunning =>
      'Shizukuがrootで実行中であることを確認できませんでした。rootで起動してから、もう一度確認してください。';

  @override
  String get setupQuestionOpen => 'Shizukuを開きましたか？';

  @override
  String get setupQuestionPair => '「ペアリング」に表示された手順をすべて完了しましたか？';

  @override
  String get setupQuestionStart => 'Shizukuで「開始」を押し、「Shizukuは実行中です」と表示されていますか？';

  @override
  String get yes => 'はい';

  @override
  String get no => 'いいえ';

  @override
  String get setupVerified => 'Shizukuの設定を確認しました';

  @override
  String get setupVerificationFailed =>
      'Shizukuの設定または開始を確認できません。Shizuku内の手順を完了してから、もう一度確認してください。';

  @override
  String get setupPermissionCheckFailed => 'Shizukuの権限を確認できませんでした';

  @override
  String get setupDeviceTitle => 'この端末での構成';

  @override
  String get model => '機種';

  @override
  String get vendor => 'ベンダー';

  @override
  String get desktopUi => 'デスクトップUI';

  @override
  String get detectedResolution => '自動検出解像度';

  @override
  String get loadingLabel => '取得中…';

  @override
  String get setupDeviceDescription => 'この情報をもとに、初回の解像度と端末固有のデスクトップ制御を設定します。';

  @override
  String get setupGestureTitle => 'ジェスチャーで操作パネルを呼び出しましょう';

  @override
  String get setupGestureDescription => '下の3つの円へ、3本の指を同時に置いてください。';

  @override
  String get uiTwoFingerTap => '2本指タップ';

  @override
  String get ui3FingerTap => '3本指タップ';

  @override
  String get ui4Divisions => '4分割';

  @override
  String get uiDextopIsReady => 'Dextopの準備ができました';

  @override
  String get uiStopDextop => 'Dextopを停止';

  @override
  String get uiDextopCanBeRestarted => 'Dextopを再開できます';

  @override
  String get uiOpenDextop => 'Dextopを開く';

  @override
  String get uiCreateADextopSession => 'Dextopセッション作成';

  @override
  String get uiDextopWorkspaceJson => 'DextopワークスペースJSON';

  @override
  String get uiPerformanceDisplayOnDextop => 'Dextop上にパフォーマンス表示';

  @override
  String get uiDoNotSleepWhileRunningDextop => 'Dextop実行中はスリープしない';

  @override
  String get uiRealTimeDisplayOfFpsMemoryPower => 'FPS、メモリ、消費電力、バッテリーをリアルタイム表示';

  @override
  String get uiCouldNotLoadJson => 'JSONを読み込めませんでした';

  @override
  String get uiSecureSettingsPermission => 'Secure Settings権限';

  @override
  String get uiAllowShizukuPermissions => 'Shizuku の権限を許可';

  @override
  String get uiInstallShizuku => 'Shizuku をインストール';

  @override
  String get uiCheckingShizukuConnection => 'Shizuku 接続確認中';

  @override
  String get uiShizukuConnection => 'Shizuku接続';

  @override
  String get uiCopy => 'のコピー';

  @override
  String get uiOthers => 'その他';

  @override
  String get uiAccessibilityOverlay => 'アクセシビリティオーバーレイ';

  @override
  String get uiAccessibilityServices => 'アクセシビリティサービス';

  @override
  String get uiAppNotFound => 'アプリが見つかりません';

  @override
  String get uiAppsAndWorkspace => 'アプリとワークスペース';

  @override
  String get uiLaunchTheAppAndConfigureYourWorkspace => 'アプリの起動とワークスペースの構成';

  @override
  String get uiRestartTheApp => 'アプリを再起動';

  @override
  String get uiSearchApp => 'アプリを検索';

  @override
  String get uiAppMemory => 'アプリメモリ';

  @override
  String get uiAppLauncher => 'アプリランチャー';

  @override
  String get uiAppLauncherSettings => 'アプリランチャー設定';

  @override
  String get uiAppLaunchFunction => 'アプリ起動機能';

  @override
  String get uiImport => 'インポート';

  @override
  String get uiExport => 'エクスポート';

  @override
  String get uiCursor => 'カーソル';

  @override
  String get uiCancel => 'キャンセル';

  @override
  String get uiQuickSettingsTile => 'クイック設定タイル';

  @override
  String get uiGesture => 'ジェスチャー';

  @override
  String get uiSecondaryIme => 'セカンダリIME';

  @override
  String get uiSecureDisplayFoldable => 'セキュア表示、ミラーリング方式、Foldable';

  @override
  String get uiSecurity => 'セキュリティ';

  @override
  String get uiTap => 'タップ';

  @override
  String get uiTapPressAndHoldMultiFingerOperation => 'タップ、長押し、複数指操作';

  @override
  String get uiOpenAppOnDesktop => 'デスクトップでアプリを開く';

  @override
  String get uiDesktopMode => 'デスクトップモード';

  @override
  String get uiDesktopFeatures => 'デスクトップ機能';

  @override
  String get uiTrackpad => 'トラックパッド';

  @override
  String get uiDrag => 'ドラッグ';

  @override
  String get uiBattery => 'バッテリー';

  @override
  String get uiPerformance => 'パフォーマンス';

  @override
  String get uiPerformanceCompatibility => 'パフォーマンス、互換性';

  @override
  String get uiItSupportsMultiTouchAndTheThree =>
      'マルチタッチに対応し、3本指ジェスチャーは画面左からに変更されます。';

  @override
  String get uiMainLarge2Sub => 'メイン大・サブ2枚';

  @override
  String get uiMainLeft => 'メイン（左）';

  @override
  String get uiLayout => 'レイアウト';

  @override
  String get uiWorkSpace => 'ワークスペース';

  @override
  String get uiCopiedWorkspaceJsonToClipboard => 'ワークスペースJSONをクリップボードへコピーしました';

  @override
  String get uiImportWorkspace => 'ワークスペースをインポート';

  @override
  String get uiSaveWorkspace => 'ワークスペースを保存';

  @override
  String get uiDeleteWorkspace => 'ワークスペースを削除';

  @override
  String get uiEditWorkspace => 'ワークスペースを編集';

  @override
  String get uiUp => '上へ';

  @override
  String get uiDividedIntoUpperAndLowerParts => '上下2分割';

  @override
  String get uiUpperHalf => '上半分';

  @override
  String get uiMoveDown => '下へ移動';

  @override
  String get uiLowerHalf => '下半分';

  @override
  String get uiCenter => '中央';

  @override
  String get uiCompatibilityDiagnosis => '互換性診断';

  @override
  String get uiVirtualDisplayCreation => '仮想ディスプレイ作成';

  @override
  String get uiOpenASavedAppConfiguration => '保存したアプリ構成を開く';

  @override
  String get uiNoSavedWorkspaces => '保存済みワークスペースはありません';

  @override
  String get uiInputAndGestures => '入力とジェスチャー';

  @override
  String get uiInputMode => '入力モード';

  @override
  String get uiCancelFullScreen => '全画面を解除';

  @override
  String get uiReDiagnosis => '再診断';

  @override
  String get uiRestart => '再開';

  @override
  String get uiAvailableMemory => '利用可能メモリ';

  @override
  String get uiDelete => '削除';

  @override
  String get uiYouCanRestoreYourPreviousSession => '前回のセッションを復旧できます';

  @override
  String get uiRight => '右';

  @override
  String get uiRight13 => '右1/3';

  @override
  String get uiRight23 => '右2/3';

  @override
  String get uiRightClick => '右クリック';

  @override
  String get uiUpperRight => '右上';

  @override
  String get uiLowerRight => '右下';

  @override
  String get uiRightHalf => '右半分';

  @override
  String get uiName => '名前';

  @override
  String get uiLargeScreenFoldable => '大画面・Foldable';

  @override
  String get uiActualFps => '実測FPS';

  @override
  String get uiExperimentalMultiTouch => '実験的なマルチタッチ';

  @override
  String get uiExperimentalFeatures => '実験的な機能';

  @override
  String get uiLeft => '左';

  @override
  String get uiLeft13 => '左1/3';

  @override
  String get uiLeft13Right23 => '左1/3・右2/3';

  @override
  String get uiLeft23 => '左2/3';

  @override
  String get uiLeft23Right13 => '左2/3・右1/3';

  @override
  String get uiLeftCenterRight => '左・中央・右';

  @override
  String get uiUpperLeft => '左上';

  @override
  String get uiUpperLeftUpperRightLowerHalf => '左上・右上・下半分';

  @override
  String get uiLowerLeft => '左下';

  @override
  String get uiLeftHalf => '左半分';

  @override
  String get uiDividedIntoLeftAndRight => '左右2分割';

  @override
  String get uiSwipeRightWithThreeFingersFromThe => '左端から3本指で右へスワイプ';

  @override
  String get uiRecoverySession => '復旧セッション';

  @override
  String get uiEstimatedPowerConsumption => '推定消費電力';

  @override
  String get uiOperationOverlay => '操作オーバーレイ';

  @override
  String get uiShowActionOverlay => '操作オーバーレイを表示';

  @override
  String get uiOperationMenu => '操作メニュー';

  @override
  String get uiThereIsAnExistingSession => '既存のセッションがあります';

  @override
  String get uiSaveConfiguration => '構成を保存';

  @override
  String get uiRestorePrivileges => '権限を復旧';

  @override
  String get uiChangeToHorizontalHold => '横持ちに変更';

  @override
  String get uiPreparationIsRequired => '準備が必要です';

  @override
  String get uiPhysicalKeyboard => '物理キーボード';

  @override
  String get uiPhysicalMouse => '物理マウス';

  @override
  String get uiConditionAndDiagnosis => '状態と診断';

  @override
  String get uiPreventsTheScreenFromTurningOffAutomatically => '画面の自動消灯を防止します';

  @override
  String get uiDestruction => '破棄';

  @override
  String get uiTerminalAndPermissions => '端末と権限';

  @override
  String get uiDeviceInformationDesktopModeAccessibility =>
      '端末情報、デスクトップモード、アクセシビリティ';

  @override
  String get uiTerminalResolution => '端末解像度';

  @override
  String get uiEnd => '終了';

  @override
  String get uiTerminationProcessingCompletedSuccessfully => '終了処理は正常に完了しました。';

  @override
  String get uiEdit => '編集';

  @override
  String get uiChangeToPortraitOrientation => '縦持ちに変更';

  @override
  String get uiVerticalHorizontalSwitching => '縦横切り替え';

  @override
  String get uiDisplayOptimization => '表示の最適化';

  @override
  String get uiDisplayRefreshRate => '表示リフレッシュレート';

  @override
  String get uiReproduction => '複製';

  @override
  String get uiManageLaunchedAppsAndConfigurations => '起動するアプリと構成の管理';

  @override
  String get uiCouldNotStart => '起動できませんでした';

  @override
  String get uiLongPress => '長押し';

  @override
  String get uiAutomaticallyUsesMeasuredResolutionForOpenAnd =>
      '開いた状態と閉じた状態の実測解像度を自動使用';

  @override
  String get uiStart => '開始';

  @override
  String get uiAutomaticSwitchingAccordingToOpenClosedState =>
      '開閉状態に合わせて自動切り替え';

  @override
  String get uiOpeningQuote => '「';

  @override
  String get uiDeleteWorkspaceQuestionSuffix => '」を削除しますか？';

  @override
  String get uiAbnormalSessionWarning =>
      '不正な状態でセッションが終了されたため、\n一部のAndroid側の機能が無効化されている可能性があります。';

  @override
  String get uiChecking => '確認中';

  @override
  String get uiIdle => '待機中';

  @override
  String get uiAvailable => 'Available';

  @override
  String get uiUnavailable => 'Unavailable';

  @override
  String get appName => 'Dextop';

  @override
  String get uiAndroid => 'Android';

  @override
  String get uiGitHub => 'GitHub';

  @override
  String get uiGitHubRepository => 'NarYuki/Dextop';

  @override
  String get diagnosticLog => '動作ログと端末診断';

  @override
  String get diagnosticLogDescription => 'アプリのログ、能力判定、端末の詳細スペックを表示します';

  @override
  String get loadDiagnosticLog => '診断レポートを読み込む';

  @override
  String get copyDiagnosticLog => 'コピー';

  @override
  String get shareDiagnosticLog => '共有';

  @override
  String get clearDiagnosticLog => 'ログを消去';

  @override
  String get deviceReport => '動作報告';

  @override
  String get uiCpuTemperature => 'CPU温度';

  @override
  String get deviceReportDescription => '端末情報と機能の対応状況をメールで報告';

  @override
  String get deviceReportIntro => '端末情報は自動収集されます。各機能の動作状況を選択してください。';

  @override
  String get reportWorking => '動作した';

  @override
  String get reportNotWorking => '動作しない';

  @override
  String get reportUntested => '未確認';

  @override
  String get reportOverall => '総合的な動作状況';

  @override
  String get reportNotes => 'その他・特記事項';

  @override
  String get sendDeviceReport => 'メールで動作報告を送る';

  @override
  String get reportEmailUnavailable => 'メールアプリを開けませんでした';

  @override
  String get reportTemplateTitle => 'Dextop端末動作報告';

  @override
  String get reportNoNotes => 'なし';

  @override
  String get reportFeatureStartup => 'アプリ起動と端末検出';

  @override
  String get reportFeatureSession => 'Dextopセッションの起動';

  @override
  String get reportFeatureVirtualDisplay => 'VirtualDisplayミラーリング';

  @override
  String get reportFeatureWindowManager => 'WindowManagerミラーリング';

  @override
  String get reportFeatureSurfaceControl => 'SurfaceControlミラーリング';

  @override
  String get reportFeatureLandscape => '横向きモード';

  @override
  String get reportFeaturePortrait => '縦向きモード';

  @override
  String get reportFeatureSecureDisplay => 'セキュア表示';

  @override
  String get reportFeatureLauncher => 'アプリランチャーとフリーフォームウィンドウ';

  @override
  String get reportFeatureWorkspace => 'ワークスペースの保存と復元';

  @override
  String get reportFeatureCursor => 'カーソル・タッチパッド入力';

  @override
  String get reportFeatureDirectTouch => 'ダイレクトタッチ入力';

  @override
  String get reportFeatureMultiTouch => 'マルチタッチのスクロールとピンチズーム';

  @override
  String get reportFeatureGesture => '3本指オーバーレイジェスチャー';

  @override
  String get reportFeatureMouse => '物理マウス';

  @override
  String get reportFeatureKeyboard => '物理キーボード';

  @override
  String get reportFeatureRouting => '物理マウス・キーボードのディスプレイルーティング';

  @override
  String get reportFeatureFoldable => '折りたたみ端末の自動解像度';

  @override
  String get reportFeaturePerformance => 'パフォーマンスオーバーレイ';

  @override
  String get reportFeatureCleanup => 'セッション終了処理とAndroid状態の復元';
}
