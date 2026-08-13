import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_ja.dart';
import 'app_localizations_ko.dart';
import 'app_localizations_ru.dart';
import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('ja'),
    Locale('ko'),
    Locale('ru'),
    Locale('zh'),
  ];

  /// No description provided for @home.
  ///
  /// In ja, this message translates to:
  /// **'ホーム'**
  String get home;

  /// No description provided for @settings.
  ///
  /// In ja, this message translates to:
  /// **'設定'**
  String get settings;

  /// No description provided for @resolution.
  ///
  /// In ja, this message translates to:
  /// **'解像度'**
  String get resolution;

  /// No description provided for @theme.
  ///
  /// In ja, this message translates to:
  /// **'テーマ'**
  String get theme;

  /// No description provided for @system.
  ///
  /// In ja, this message translates to:
  /// **'システム'**
  String get system;

  /// No description provided for @light.
  ///
  /// In ja, this message translates to:
  /// **'ライト'**
  String get light;

  /// No description provided for @dark.
  ///
  /// In ja, this message translates to:
  /// **'ダーク'**
  String get dark;

  /// No description provided for @display.
  ///
  /// In ja, this message translates to:
  /// **'表示'**
  String get display;

  /// No description provided for @secureDisplay.
  ///
  /// In ja, this message translates to:
  /// **'セキュア表示'**
  String get secureDisplay;

  /// No description provided for @secureDisplayDescription.
  ///
  /// In ja, this message translates to:
  /// **'保護されたコンテンツの表示を許可します'**
  String get secureDisplayDescription;

  /// No description provided for @deviceInfo.
  ///
  /// In ja, this message translates to:
  /// **'端末情報'**
  String get deviceInfo;

  /// No description provided for @desktopMode.
  ///
  /// In ja, this message translates to:
  /// **'使用するデスクトップモード'**
  String get desktopMode;

  /// No description provided for @accessibilitySettings.
  ///
  /// In ja, this message translates to:
  /// **'アクセシビリティ設定'**
  String get accessibilitySettings;

  /// No description provided for @accessibilityDescription.
  ///
  /// In ja, this message translates to:
  /// **'Dextopのサービス設定を開きます'**
  String get accessibilityDescription;

  /// No description provided for @appInfo.
  ///
  /// In ja, this message translates to:
  /// **'アプリ情報'**
  String get appInfo;

  /// No description provided for @licenses.
  ///
  /// In ja, this message translates to:
  /// **'オープンソースライセンス'**
  String get licenses;

  /// No description provided for @licensesDescription.
  ///
  /// In ja, this message translates to:
  /// **'Flutterと使用ライブラリのライセンスを表示'**
  String get licensesDescription;

  /// No description provided for @landscape.
  ///
  /// In ja, this message translates to:
  /// **'横向き'**
  String get landscape;

  /// No description provided for @portrait.
  ///
  /// In ja, this message translates to:
  /// **'縦向き'**
  String get portrait;

  /// No description provided for @stopped.
  ///
  /// In ja, this message translates to:
  /// **'停止中'**
  String get stopped;

  /// No description provided for @running.
  ///
  /// In ja, this message translates to:
  /// **'起動中'**
  String get running;

  /// No description provided for @start.
  ///
  /// In ja, this message translates to:
  /// **'起動'**
  String get start;

  /// No description provided for @stop.
  ///
  /// In ja, this message translates to:
  /// **'停止'**
  String get stop;

  /// No description provided for @customAdd.
  ///
  /// In ja, this message translates to:
  /// **'カスタム解像度を追加'**
  String get customAdd;

  /// No description provided for @editResolution.
  ///
  /// In ja, this message translates to:
  /// **'解像度を編集'**
  String get editResolution;

  /// No description provided for @add.
  ///
  /// In ja, this message translates to:
  /// **'追加'**
  String get add;

  /// No description provided for @save.
  ///
  /// In ja, this message translates to:
  /// **'保存'**
  String get save;

  /// No description provided for @deleteResolution.
  ///
  /// In ja, this message translates to:
  /// **'この解像度を削除'**
  String get deleteResolution;

  /// No description provided for @width.
  ///
  /// In ja, this message translates to:
  /// **'幅'**
  String get width;

  /// No description provided for @height.
  ///
  /// In ja, this message translates to:
  /// **'高さ'**
  String get height;

  /// No description provided for @protectedContent.
  ///
  /// In ja, this message translates to:
  /// **'保護されたコンテンツの表示を許可します'**
  String get protectedContent;

  /// No description provided for @version.
  ///
  /// In ja, this message translates to:
  /// **'バージョン 1.0.0'**
  String get version;

  /// No description provided for @setupWelcome.
  ///
  /// In ja, this message translates to:
  /// **'Dextopへようこそ。'**
  String get setupWelcome;

  /// No description provided for @setupTagline.
  ///
  /// In ja, this message translates to:
  /// **'スマホ単体で、完璧なデスクトップ環境を。'**
  String get setupTagline;

  /// No description provided for @setupBegin.
  ///
  /// In ja, this message translates to:
  /// **'はじめる'**
  String get setupBegin;

  /// No description provided for @setupPhaseTerms.
  ///
  /// In ja, this message translates to:
  /// **'ご利用にあたって'**
  String get setupPhaseTerms;

  /// No description provided for @setupPhaseShizuku.
  ///
  /// In ja, this message translates to:
  /// **'Shizuku'**
  String get setupPhaseShizuku;

  /// No description provided for @setupPhaseDevice.
  ///
  /// In ja, this message translates to:
  /// **'端末の確認'**
  String get setupPhaseDevice;

  /// No description provided for @setupPhaseDemo.
  ///
  /// In ja, this message translates to:
  /// **'操作を体験'**
  String get setupPhaseDemo;

  /// No description provided for @back.
  ///
  /// In ja, this message translates to:
  /// **'戻る'**
  String get back;

  /// No description provided for @continueLabel.
  ///
  /// In ja, this message translates to:
  /// **'続ける'**
  String get continueLabel;

  /// No description provided for @done.
  ///
  /// In ja, this message translates to:
  /// **'完了'**
  String get done;

  /// No description provided for @incomplete.
  ///
  /// In ja, this message translates to:
  /// **'未完了'**
  String get incomplete;

  /// No description provided for @setupSystemTitle.
  ///
  /// In ja, this message translates to:
  /// **'システム機能を利用します'**
  String get setupSystemTitle;

  /// No description provided for @setupSystemDescription.
  ///
  /// In ja, this message translates to:
  /// **'DextopはShizukuとADBを使用し、仮想ディスプレイ、画面方向、入力、システムUIなどの挙動を制御します。'**
  String get setupSystemDescription;

  /// No description provided for @setupDisclaimer.
  ///
  /// In ja, this message translates to:
  /// **'端末やOSの実装差、システム更新、他のアプリとの競合などにより生じた不具合、データ損失、端末機能への影響について、開発者は責任を負いません。内容を理解したうえで使用してください。'**
  String get setupDisclaimer;

  /// No description provided for @setupShizukuTitle.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuを準備'**
  String get setupShizukuTitle;

  /// No description provided for @setupShizukuDescription.
  ///
  /// In ja, this message translates to:
  /// **'Dextopがシステム機能へ安全にアクセスするためにShizukuを使用します。'**
  String get setupShizukuDescription;

  /// No description provided for @setupInstallShizuku.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuをインストール'**
  String get setupInstallShizuku;

  /// No description provided for @setupConfigureShizuku.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuを設定'**
  String get setupConfigureShizuku;

  /// No description provided for @setupShizukuHint.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuを開き、「ペアリング」に表示される順序に従って設定し、Shizukuを開始してください。'**
  String get setupShizukuHint;

  /// No description provided for @setupOpenShizuku.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuを開く'**
  String get setupOpenShizuku;

  /// No description provided for @setupValidate.
  ///
  /// In ja, this message translates to:
  /// **'設定が完了しましたか？ 有効性をチェック'**
  String get setupValidate;

  /// No description provided for @setupDextopPermission.
  ///
  /// In ja, this message translates to:
  /// **'Dextopへの権限'**
  String get setupDextopPermission;

  /// No description provided for @setupInstallPlay.
  ///
  /// In ja, this message translates to:
  /// **'Google Playからインストール'**
  String get setupInstallPlay;

  /// No description provided for @setupAllowPermission.
  ///
  /// In ja, this message translates to:
  /// **'権限を許可'**
  String get setupAllowPermission;

  /// No description provided for @setupProviderChoiceTitle.
  ///
  /// In ja, this message translates to:
  /// **'特権サービスを選択'**
  String get setupProviderChoiceTitle;

  /// No description provided for @setupProviderChoiceDescription.
  ///
  /// In ja, this message translates to:
  /// **'StellarとShizukuの両方がインストールされています。Dextopで使用するサービスを選択してください。選択は、どちらかをアンインストールするまで保存されます。'**
  String get setupProviderChoiceDescription;

  /// No description provided for @setupUseStellar.
  ///
  /// In ja, this message translates to:
  /// **'Stellar（推奨）'**
  String get setupUseStellar;

  /// No description provided for @setupUseShizuku.
  ///
  /// In ja, this message translates to:
  /// **'Shizuku'**
  String get setupUseShizuku;

  /// No description provided for @setupRunningAsRoot.
  ///
  /// In ja, this message translates to:
  /// **'rootでサービスを実行中です'**
  String get setupRunningAsRoot;

  /// No description provided for @setupRootVerified.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuがrootで実行中であることを確認しました。Dextopへの権限を付与してください。'**
  String get setupRootVerified;

  /// No description provided for @setupRootNotRunning.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuがrootで実行中であることを確認できませんでした。rootで起動してから、もう一度確認してください。'**
  String get setupRootNotRunning;

  /// No description provided for @setupQuestionOpen.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuを開きましたか？'**
  String get setupQuestionOpen;

  /// No description provided for @setupQuestionPair.
  ///
  /// In ja, this message translates to:
  /// **'「ペアリング」に表示された手順をすべて完了しましたか？'**
  String get setupQuestionPair;

  /// No description provided for @setupQuestionStart.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuで「開始」を押し、「Shizukuは実行中です」と表示されていますか？'**
  String get setupQuestionStart;

  /// No description provided for @yes.
  ///
  /// In ja, this message translates to:
  /// **'はい'**
  String get yes;

  /// No description provided for @no.
  ///
  /// In ja, this message translates to:
  /// **'いいえ'**
  String get no;

  /// No description provided for @setupVerified.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuの設定を確認しました'**
  String get setupVerified;

  /// No description provided for @setupVerificationFailed.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuの設定または開始を確認できません。Shizuku内の手順を完了してから、もう一度確認してください。'**
  String get setupVerificationFailed;

  /// No description provided for @setupPermissionCheckFailed.
  ///
  /// In ja, this message translates to:
  /// **'Shizukuの権限を確認できませんでした'**
  String get setupPermissionCheckFailed;

  /// No description provided for @setupDeviceTitle.
  ///
  /// In ja, this message translates to:
  /// **'この端末での構成'**
  String get setupDeviceTitle;

  /// No description provided for @model.
  ///
  /// In ja, this message translates to:
  /// **'機種'**
  String get model;

  /// No description provided for @vendor.
  ///
  /// In ja, this message translates to:
  /// **'ベンダー'**
  String get vendor;

  /// No description provided for @desktopUi.
  ///
  /// In ja, this message translates to:
  /// **'デスクトップUI'**
  String get desktopUi;

  /// No description provided for @detectedResolution.
  ///
  /// In ja, this message translates to:
  /// **'自動検出解像度'**
  String get detectedResolution;

  /// No description provided for @loadingLabel.
  ///
  /// In ja, this message translates to:
  /// **'取得中…'**
  String get loadingLabel;

  /// No description provided for @setupDeviceDescription.
  ///
  /// In ja, this message translates to:
  /// **'この情報をもとに、初回の解像度と端末固有のデスクトップ制御を設定します。'**
  String get setupDeviceDescription;

  /// No description provided for @setupGestureTitle.
  ///
  /// In ja, this message translates to:
  /// **'ジェスチャーで操作パネルを呼び出しましょう'**
  String get setupGestureTitle;

  /// No description provided for @setupGestureDescription.
  ///
  /// In ja, this message translates to:
  /// **'下の3つの円へ、3本の指を同時に置いてください。'**
  String get setupGestureDescription;

  /// No description provided for @uiTwoFingerTap.
  ///
  /// In ja, this message translates to:
  /// **'2本指タップ'**
  String get uiTwoFingerTap;

  /// No description provided for @ui3FingerTap.
  ///
  /// In ja, this message translates to:
  /// **'3本指タップ'**
  String get ui3FingerTap;

  /// No description provided for @ui4Divisions.
  ///
  /// In ja, this message translates to:
  /// **'4分割'**
  String get ui4Divisions;

  /// No description provided for @uiDextopIsReady.
  ///
  /// In ja, this message translates to:
  /// **'Dextopの準備ができました'**
  String get uiDextopIsReady;

  /// No description provided for @uiStopDextop.
  ///
  /// In ja, this message translates to:
  /// **'Dextopを停止'**
  String get uiStopDextop;

  /// No description provided for @uiDextopCanBeRestarted.
  ///
  /// In ja, this message translates to:
  /// **'Dextopを再開できます'**
  String get uiDextopCanBeRestarted;

  /// No description provided for @uiOpenDextop.
  ///
  /// In ja, this message translates to:
  /// **'Dextopを開く'**
  String get uiOpenDextop;

  /// No description provided for @uiCreateADextopSession.
  ///
  /// In ja, this message translates to:
  /// **'Dextopセッション作成'**
  String get uiCreateADextopSession;

  /// No description provided for @uiDextopWorkspaceJson.
  ///
  /// In ja, this message translates to:
  /// **'DextopワークスペースJSON'**
  String get uiDextopWorkspaceJson;

  /// No description provided for @uiPerformanceDisplayOnDextop.
  ///
  /// In ja, this message translates to:
  /// **'Dextop上にパフォーマンス表示'**
  String get uiPerformanceDisplayOnDextop;

  /// No description provided for @uiDoNotSleepWhileRunningDextop.
  ///
  /// In ja, this message translates to:
  /// **'Dextop実行中はスリープしない'**
  String get uiDoNotSleepWhileRunningDextop;

  /// No description provided for @uiRealTimeDisplayOfFpsMemoryPower.
  ///
  /// In ja, this message translates to:
  /// **'FPS、メモリ、消費電力、バッテリーをリアルタイム表示'**
  String get uiRealTimeDisplayOfFpsMemoryPower;

  /// No description provided for @uiCouldNotLoadJson.
  ///
  /// In ja, this message translates to:
  /// **'JSONを読み込めませんでした'**
  String get uiCouldNotLoadJson;

  /// No description provided for @uiSecureSettingsPermission.
  ///
  /// In ja, this message translates to:
  /// **'Secure Settings権限'**
  String get uiSecureSettingsPermission;

  /// No description provided for @uiAllowShizukuPermissions.
  ///
  /// In ja, this message translates to:
  /// **'Shizuku の権限を許可'**
  String get uiAllowShizukuPermissions;

  /// No description provided for @uiInstallShizuku.
  ///
  /// In ja, this message translates to:
  /// **'Shizuku をインストール'**
  String get uiInstallShizuku;

  /// No description provided for @uiCheckingShizukuConnection.
  ///
  /// In ja, this message translates to:
  /// **'Shizuku 接続確認中'**
  String get uiCheckingShizukuConnection;

  /// No description provided for @uiShizukuConnection.
  ///
  /// In ja, this message translates to:
  /// **'Shizuku接続'**
  String get uiShizukuConnection;

  /// No description provided for @uiCopy.
  ///
  /// In ja, this message translates to:
  /// **'のコピー'**
  String get uiCopy;

  /// No description provided for @uiOthers.
  ///
  /// In ja, this message translates to:
  /// **'その他'**
  String get uiOthers;

  /// No description provided for @uiAccessibilityOverlay.
  ///
  /// In ja, this message translates to:
  /// **'アクセシビリティオーバーレイ'**
  String get uiAccessibilityOverlay;

  /// No description provided for @uiAccessibilityServices.
  ///
  /// In ja, this message translates to:
  /// **'アクセシビリティサービス'**
  String get uiAccessibilityServices;

  /// No description provided for @uiAppNotFound.
  ///
  /// In ja, this message translates to:
  /// **'アプリが見つかりません'**
  String get uiAppNotFound;

  /// No description provided for @uiAppsAndWorkspace.
  ///
  /// In ja, this message translates to:
  /// **'アプリとワークスペース'**
  String get uiAppsAndWorkspace;

  /// No description provided for @uiLaunchTheAppAndConfigureYourWorkspace.
  ///
  /// In ja, this message translates to:
  /// **'アプリの起動とワークスペースの構成'**
  String get uiLaunchTheAppAndConfigureYourWorkspace;

  /// No description provided for @uiRestartTheApp.
  ///
  /// In ja, this message translates to:
  /// **'アプリを再起動'**
  String get uiRestartTheApp;

  /// No description provided for @uiSearchApp.
  ///
  /// In ja, this message translates to:
  /// **'アプリを検索'**
  String get uiSearchApp;

  /// No description provided for @uiAppMemory.
  ///
  /// In ja, this message translates to:
  /// **'アプリメモリ'**
  String get uiAppMemory;

  /// No description provided for @uiAppLauncher.
  ///
  /// In ja, this message translates to:
  /// **'アプリランチャー'**
  String get uiAppLauncher;

  /// No description provided for @uiAppLauncherSettings.
  ///
  /// In ja, this message translates to:
  /// **'アプリランチャー設定'**
  String get uiAppLauncherSettings;

  /// No description provided for @uiAppLaunchFunction.
  ///
  /// In ja, this message translates to:
  /// **'アプリ起動機能'**
  String get uiAppLaunchFunction;

  /// No description provided for @uiImport.
  ///
  /// In ja, this message translates to:
  /// **'インポート'**
  String get uiImport;

  /// No description provided for @uiExport.
  ///
  /// In ja, this message translates to:
  /// **'エクスポート'**
  String get uiExport;

  /// No description provided for @uiCursor.
  ///
  /// In ja, this message translates to:
  /// **'カーソル'**
  String get uiCursor;

  /// No description provided for @uiCancel.
  ///
  /// In ja, this message translates to:
  /// **'キャンセル'**
  String get uiCancel;

  /// No description provided for @uiQuickSettingsTile.
  ///
  /// In ja, this message translates to:
  /// **'クイック設定タイル'**
  String get uiQuickSettingsTile;

  /// No description provided for @uiGesture.
  ///
  /// In ja, this message translates to:
  /// **'ジェスチャー'**
  String get uiGesture;

  /// No description provided for @uiSecondaryIme.
  ///
  /// In ja, this message translates to:
  /// **'セカンダリIME'**
  String get uiSecondaryIme;

  /// No description provided for @uiSecureDisplayFoldable.
  ///
  /// In ja, this message translates to:
  /// **'セキュア表示、Foldable'**
  String get uiSecureDisplayFoldable;

  /// No description provided for @uiSecurity.
  ///
  /// In ja, this message translates to:
  /// **'セキュリティ'**
  String get uiSecurity;

  /// No description provided for @uiTap.
  ///
  /// In ja, this message translates to:
  /// **'タップ'**
  String get uiTap;

  /// No description provided for @uiTapPressAndHoldMultiFingerOperation.
  ///
  /// In ja, this message translates to:
  /// **'タップ、長押し、複数指操作'**
  String get uiTapPressAndHoldMultiFingerOperation;

  /// No description provided for @uiOpenAppOnDesktop.
  ///
  /// In ja, this message translates to:
  /// **'デスクトップでアプリを開く'**
  String get uiOpenAppOnDesktop;

  /// No description provided for @uiDesktopMode.
  ///
  /// In ja, this message translates to:
  /// **'デスクトップモード'**
  String get uiDesktopMode;

  /// No description provided for @uiDesktopFeatures.
  ///
  /// In ja, this message translates to:
  /// **'デスクトップ機能'**
  String get uiDesktopFeatures;

  /// No description provided for @uiTrackpad.
  ///
  /// In ja, this message translates to:
  /// **'トラックパッド'**
  String get uiTrackpad;

  /// No description provided for @uiDrag.
  ///
  /// In ja, this message translates to:
  /// **'ドラッグ'**
  String get uiDrag;

  /// No description provided for @uiBattery.
  ///
  /// In ja, this message translates to:
  /// **'バッテリー'**
  String get uiBattery;

  /// No description provided for @uiPerformance.
  ///
  /// In ja, this message translates to:
  /// **'パフォーマンス'**
  String get uiPerformance;

  /// No description provided for @uiPerformanceCompatibility.
  ///
  /// In ja, this message translates to:
  /// **'パフォーマンス、互換性'**
  String get uiPerformanceCompatibility;

  /// No description provided for @uiItSupportsMultiTouchAndTheThree.
  ///
  /// In ja, this message translates to:
  /// **'マルチタッチに対応し、3本指ジェスチャーは画面左からに変更されます。'**
  String get uiItSupportsMultiTouchAndTheThree;

  /// No description provided for @uiMainLarge2Sub.
  ///
  /// In ja, this message translates to:
  /// **'メイン大・サブ2枚'**
  String get uiMainLarge2Sub;

  /// No description provided for @uiMainLeft.
  ///
  /// In ja, this message translates to:
  /// **'メイン（左）'**
  String get uiMainLeft;

  /// No description provided for @uiLayout.
  ///
  /// In ja, this message translates to:
  /// **'レイアウト'**
  String get uiLayout;

  /// No description provided for @uiWorkSpace.
  ///
  /// In ja, this message translates to:
  /// **'ワークスペース'**
  String get uiWorkSpace;

  /// No description provided for @uiCopiedWorkspaceJsonToClipboard.
  ///
  /// In ja, this message translates to:
  /// **'ワークスペースJSONをクリップボードへコピーしました'**
  String get uiCopiedWorkspaceJsonToClipboard;

  /// No description provided for @uiImportWorkspace.
  ///
  /// In ja, this message translates to:
  /// **'ワークスペースをインポート'**
  String get uiImportWorkspace;

  /// No description provided for @uiSaveWorkspace.
  ///
  /// In ja, this message translates to:
  /// **'ワークスペースを保存'**
  String get uiSaveWorkspace;

  /// No description provided for @uiDeleteWorkspace.
  ///
  /// In ja, this message translates to:
  /// **'ワークスペースを削除'**
  String get uiDeleteWorkspace;

  /// No description provided for @uiEditWorkspace.
  ///
  /// In ja, this message translates to:
  /// **'ワークスペースを編集'**
  String get uiEditWorkspace;

  /// No description provided for @uiUp.
  ///
  /// In ja, this message translates to:
  /// **'上へ'**
  String get uiUp;

  /// No description provided for @uiDividedIntoUpperAndLowerParts.
  ///
  /// In ja, this message translates to:
  /// **'上下2分割'**
  String get uiDividedIntoUpperAndLowerParts;

  /// No description provided for @uiUpperHalf.
  ///
  /// In ja, this message translates to:
  /// **'上半分'**
  String get uiUpperHalf;

  /// No description provided for @uiMoveDown.
  ///
  /// In ja, this message translates to:
  /// **'下へ移動'**
  String get uiMoveDown;

  /// No description provided for @uiLowerHalf.
  ///
  /// In ja, this message translates to:
  /// **'下半分'**
  String get uiLowerHalf;

  /// No description provided for @uiCenter.
  ///
  /// In ja, this message translates to:
  /// **'中央'**
  String get uiCenter;

  /// No description provided for @uiCompatibilityDiagnosis.
  ///
  /// In ja, this message translates to:
  /// **'互換性診断'**
  String get uiCompatibilityDiagnosis;

  /// No description provided for @uiVirtualDisplayCreation.
  ///
  /// In ja, this message translates to:
  /// **'仮想ディスプレイ作成'**
  String get uiVirtualDisplayCreation;

  /// No description provided for @uiOpenASavedAppConfiguration.
  ///
  /// In ja, this message translates to:
  /// **'保存したアプリ構成を開く'**
  String get uiOpenASavedAppConfiguration;

  /// No description provided for @uiNoSavedWorkspaces.
  ///
  /// In ja, this message translates to:
  /// **'保存済みワークスペースはありません'**
  String get uiNoSavedWorkspaces;

  /// No description provided for @uiInputAndGestures.
  ///
  /// In ja, this message translates to:
  /// **'入力とジェスチャー'**
  String get uiInputAndGestures;

  /// No description provided for @uiInputMode.
  ///
  /// In ja, this message translates to:
  /// **'入力モード'**
  String get uiInputMode;

  /// No description provided for @uiCancelFullScreen.
  ///
  /// In ja, this message translates to:
  /// **'全画面を解除'**
  String get uiCancelFullScreen;

  /// No description provided for @uiReDiagnosis.
  ///
  /// In ja, this message translates to:
  /// **'再診断'**
  String get uiReDiagnosis;

  /// No description provided for @uiRestart.
  ///
  /// In ja, this message translates to:
  /// **'再開'**
  String get uiRestart;

  /// No description provided for @uiAvailableMemory.
  ///
  /// In ja, this message translates to:
  /// **'利用可能メモリ'**
  String get uiAvailableMemory;

  /// No description provided for @uiDelete.
  ///
  /// In ja, this message translates to:
  /// **'削除'**
  String get uiDelete;

  /// No description provided for @uiYouCanRestoreYourPreviousSession.
  ///
  /// In ja, this message translates to:
  /// **'前回のセッションを復旧できます'**
  String get uiYouCanRestoreYourPreviousSession;

  /// No description provided for @uiRight.
  ///
  /// In ja, this message translates to:
  /// **'右'**
  String get uiRight;

  /// No description provided for @uiRight13.
  ///
  /// In ja, this message translates to:
  /// **'右1/3'**
  String get uiRight13;

  /// No description provided for @uiRight23.
  ///
  /// In ja, this message translates to:
  /// **'右2/3'**
  String get uiRight23;

  /// No description provided for @uiRightClick.
  ///
  /// In ja, this message translates to:
  /// **'右クリック'**
  String get uiRightClick;

  /// No description provided for @uiUpperRight.
  ///
  /// In ja, this message translates to:
  /// **'右上'**
  String get uiUpperRight;

  /// No description provided for @uiLowerRight.
  ///
  /// In ja, this message translates to:
  /// **'右下'**
  String get uiLowerRight;

  /// No description provided for @uiRightHalf.
  ///
  /// In ja, this message translates to:
  /// **'右半分'**
  String get uiRightHalf;

  /// No description provided for @uiName.
  ///
  /// In ja, this message translates to:
  /// **'名前'**
  String get uiName;

  /// No description provided for @uiLargeScreenFoldable.
  ///
  /// In ja, this message translates to:
  /// **'大画面・Foldable'**
  String get uiLargeScreenFoldable;

  /// No description provided for @uiActualFps.
  ///
  /// In ja, this message translates to:
  /// **'実測FPS'**
  String get uiActualFps;

  /// No description provided for @uiExperimentalMultiTouch.
  ///
  /// In ja, this message translates to:
  /// **'実験的なマルチタッチ'**
  String get uiExperimentalMultiTouch;

  /// No description provided for @uiExperimentalFeatures.
  ///
  /// In ja, this message translates to:
  /// **'実験的な機能'**
  String get uiExperimentalFeatures;

  /// No description provided for @uiLeft.
  ///
  /// In ja, this message translates to:
  /// **'左'**
  String get uiLeft;

  /// No description provided for @uiLeft13.
  ///
  /// In ja, this message translates to:
  /// **'左1/3'**
  String get uiLeft13;

  /// No description provided for @uiLeft13Right23.
  ///
  /// In ja, this message translates to:
  /// **'左1/3・右2/3'**
  String get uiLeft13Right23;

  /// No description provided for @uiLeft23.
  ///
  /// In ja, this message translates to:
  /// **'左2/3'**
  String get uiLeft23;

  /// No description provided for @uiLeft23Right13.
  ///
  /// In ja, this message translates to:
  /// **'左2/3・右1/3'**
  String get uiLeft23Right13;

  /// No description provided for @uiLeftCenterRight.
  ///
  /// In ja, this message translates to:
  /// **'左・中央・右'**
  String get uiLeftCenterRight;

  /// No description provided for @uiUpperLeft.
  ///
  /// In ja, this message translates to:
  /// **'左上'**
  String get uiUpperLeft;

  /// No description provided for @uiUpperLeftUpperRightLowerHalf.
  ///
  /// In ja, this message translates to:
  /// **'左上・右上・下半分'**
  String get uiUpperLeftUpperRightLowerHalf;

  /// No description provided for @uiLowerLeft.
  ///
  /// In ja, this message translates to:
  /// **'左下'**
  String get uiLowerLeft;

  /// No description provided for @uiLeftHalf.
  ///
  /// In ja, this message translates to:
  /// **'左半分'**
  String get uiLeftHalf;

  /// No description provided for @uiDividedIntoLeftAndRight.
  ///
  /// In ja, this message translates to:
  /// **'左右2分割'**
  String get uiDividedIntoLeftAndRight;

  /// No description provided for @uiSwipeRightWithThreeFingersFromThe.
  ///
  /// In ja, this message translates to:
  /// **'左端から3本指で右へスワイプ'**
  String get uiSwipeRightWithThreeFingersFromThe;

  /// No description provided for @uiRecoverySession.
  ///
  /// In ja, this message translates to:
  /// **'復旧セッション'**
  String get uiRecoverySession;

  /// No description provided for @uiEstimatedPowerConsumption.
  ///
  /// In ja, this message translates to:
  /// **'推定消費電力'**
  String get uiEstimatedPowerConsumption;

  /// No description provided for @uiOperationOverlay.
  ///
  /// In ja, this message translates to:
  /// **'操作オーバーレイ'**
  String get uiOperationOverlay;

  /// No description provided for @uiShowActionOverlay.
  ///
  /// In ja, this message translates to:
  /// **'操作オーバーレイを表示'**
  String get uiShowActionOverlay;

  /// No description provided for @uiOperationMenu.
  ///
  /// In ja, this message translates to:
  /// **'操作メニュー'**
  String get uiOperationMenu;

  /// No description provided for @uiThereIsAnExistingSession.
  ///
  /// In ja, this message translates to:
  /// **'既存のセッションがあります'**
  String get uiThereIsAnExistingSession;

  /// No description provided for @uiSaveConfiguration.
  ///
  /// In ja, this message translates to:
  /// **'構成を保存'**
  String get uiSaveConfiguration;

  /// No description provided for @uiRestorePrivileges.
  ///
  /// In ja, this message translates to:
  /// **'権限を復旧'**
  String get uiRestorePrivileges;

  /// No description provided for @uiChangeToHorizontalHold.
  ///
  /// In ja, this message translates to:
  /// **'横持ちに変更'**
  String get uiChangeToHorizontalHold;

  /// No description provided for @uiPreparationIsRequired.
  ///
  /// In ja, this message translates to:
  /// **'準備が必要です'**
  String get uiPreparationIsRequired;

  /// No description provided for @uiPhysicalKeyboard.
  ///
  /// In ja, this message translates to:
  /// **'物理キーボード'**
  String get uiPhysicalKeyboard;

  /// No description provided for @uiPhysicalMouse.
  ///
  /// In ja, this message translates to:
  /// **'物理マウス'**
  String get uiPhysicalMouse;

  /// No description provided for @uiConditionAndDiagnosis.
  ///
  /// In ja, this message translates to:
  /// **'状態と診断'**
  String get uiConditionAndDiagnosis;

  /// No description provided for @uiPreventsTheScreenFromTurningOffAutomatically.
  ///
  /// In ja, this message translates to:
  /// **'画面の自動消灯を防止します'**
  String get uiPreventsTheScreenFromTurningOffAutomatically;

  /// No description provided for @uiDestruction.
  ///
  /// In ja, this message translates to:
  /// **'破棄'**
  String get uiDestruction;

  /// No description provided for @uiTerminalAndPermissions.
  ///
  /// In ja, this message translates to:
  /// **'端末と権限'**
  String get uiTerminalAndPermissions;

  /// No description provided for @uiDeviceInformationDesktopModeAccessibility.
  ///
  /// In ja, this message translates to:
  /// **'端末情報、デスクトップモード、アクセシビリティ'**
  String get uiDeviceInformationDesktopModeAccessibility;

  /// No description provided for @uiTerminalResolution.
  ///
  /// In ja, this message translates to:
  /// **'端末解像度'**
  String get uiTerminalResolution;

  /// No description provided for @uiEnd.
  ///
  /// In ja, this message translates to:
  /// **'終了'**
  String get uiEnd;

  /// No description provided for @uiTerminationProcessingCompletedSuccessfully.
  ///
  /// In ja, this message translates to:
  /// **'終了処理は正常に完了しました。'**
  String get uiTerminationProcessingCompletedSuccessfully;

  /// No description provided for @uiEdit.
  ///
  /// In ja, this message translates to:
  /// **'編集'**
  String get uiEdit;

  /// No description provided for @uiChangeToPortraitOrientation.
  ///
  /// In ja, this message translates to:
  /// **'縦持ちに変更'**
  String get uiChangeToPortraitOrientation;

  /// No description provided for @uiVerticalHorizontalSwitching.
  ///
  /// In ja, this message translates to:
  /// **'縦横切り替え'**
  String get uiVerticalHorizontalSwitching;

  /// No description provided for @uiDisplayOptimization.
  ///
  /// In ja, this message translates to:
  /// **'表示の最適化'**
  String get uiDisplayOptimization;

  /// No description provided for @uiDisplayRefreshRate.
  ///
  /// In ja, this message translates to:
  /// **'表示リフレッシュレート'**
  String get uiDisplayRefreshRate;

  /// No description provided for @uiReproduction.
  ///
  /// In ja, this message translates to:
  /// **'複製'**
  String get uiReproduction;

  /// No description provided for @uiManageLaunchedAppsAndConfigurations.
  ///
  /// In ja, this message translates to:
  /// **'起動するアプリと構成の管理'**
  String get uiManageLaunchedAppsAndConfigurations;

  /// No description provided for @uiCouldNotStart.
  ///
  /// In ja, this message translates to:
  /// **'起動できませんでした'**
  String get uiCouldNotStart;

  /// No description provided for @uiLongPress.
  ///
  /// In ja, this message translates to:
  /// **'長押し'**
  String get uiLongPress;

  /// No description provided for @uiAutomaticallyUsesMeasuredResolutionForOpenAnd.
  ///
  /// In ja, this message translates to:
  /// **'開いた状態と閉じた状態の実測解像度を自動使用'**
  String get uiAutomaticallyUsesMeasuredResolutionForOpenAnd;

  /// No description provided for @uiStart.
  ///
  /// In ja, this message translates to:
  /// **'開始'**
  String get uiStart;

  /// No description provided for @uiAutomaticSwitchingAccordingToOpenClosedState.
  ///
  /// In ja, this message translates to:
  /// **'開閉状態に合わせて自動切り替え'**
  String get uiAutomaticSwitchingAccordingToOpenClosedState;

  /// No description provided for @uiOpeningQuote.
  ///
  /// In ja, this message translates to:
  /// **'「'**
  String get uiOpeningQuote;

  /// No description provided for @uiDeleteWorkspaceQuestionSuffix.
  ///
  /// In ja, this message translates to:
  /// **'」を削除しますか？'**
  String get uiDeleteWorkspaceQuestionSuffix;

  /// No description provided for @uiAbnormalSessionWarning.
  ///
  /// In ja, this message translates to:
  /// **'不正な状態でセッションが終了されたため、\n一部のAndroid側の機能が無効化されている可能性があります。'**
  String get uiAbnormalSessionWarning;

  /// No description provided for @uiChecking.
  ///
  /// In ja, this message translates to:
  /// **'確認中'**
  String get uiChecking;

  /// No description provided for @uiIdle.
  ///
  /// In ja, this message translates to:
  /// **'待機中'**
  String get uiIdle;

  /// No description provided for @uiAvailable.
  ///
  /// In ja, this message translates to:
  /// **'Available'**
  String get uiAvailable;

  /// No description provided for @uiUnavailable.
  ///
  /// In ja, this message translates to:
  /// **'Unavailable'**
  String get uiUnavailable;

  /// No description provided for @appName.
  ///
  /// In ja, this message translates to:
  /// **'Dextop'**
  String get appName;

  /// No description provided for @uiAndroid.
  ///
  /// In ja, this message translates to:
  /// **'Android'**
  String get uiAndroid;

  /// No description provided for @uiGitHub.
  ///
  /// In ja, this message translates to:
  /// **'GitHub'**
  String get uiGitHub;

  /// No description provided for @uiGitHubRepository.
  ///
  /// In ja, this message translates to:
  /// **'NarYuki/Dextop'**
  String get uiGitHubRepository;

  /// No description provided for @diagnosticLog.
  ///
  /// In ja, this message translates to:
  /// **'動作ログと端末診断'**
  String get diagnosticLog;

  /// No description provided for @diagnosticLogDescription.
  ///
  /// In ja, this message translates to:
  /// **'アプリのログ、能力判定、端末の詳細スペックを表示します'**
  String get diagnosticLogDescription;

  /// No description provided for @loadDiagnosticLog.
  ///
  /// In ja, this message translates to:
  /// **'診断レポートを読み込む'**
  String get loadDiagnosticLog;

  /// No description provided for @copyDiagnosticLog.
  ///
  /// In ja, this message translates to:
  /// **'コピー'**
  String get copyDiagnosticLog;

  /// No description provided for @shareDiagnosticLog.
  ///
  /// In ja, this message translates to:
  /// **'共有'**
  String get shareDiagnosticLog;

  /// No description provided for @clearDiagnosticLog.
  ///
  /// In ja, this message translates to:
  /// **'ログを消去'**
  String get clearDiagnosticLog;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'ja', 'ko', 'ru', 'zh'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'ja':
      return AppLocalizationsJa();
    case 'ko':
      return AppLocalizationsKo();
    case 'ru':
      return AppLocalizationsRu();
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
