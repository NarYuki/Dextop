// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Korean (`ko`).
class AppLocalizationsKo extends AppLocalizations {
  AppLocalizationsKo([String locale = 'ko']) : super(locale);

  @override
  String get home => '홈';

  @override
  String get settings => '설정';

  @override
  String get resolution => '해상도';

  @override
  String get theme => '테마';

  @override
  String get system => '시스템';

  @override
  String get light => '라이트';

  @override
  String get dark => '다크';

  @override
  String get display => '디스플레이';

  @override
  String get secureDisplay => '보안 표시';

  @override
  String get secureDisplayDescription => '보호된 콘텐츠 표시를 허용합니다';

  @override
  String get mirrorBackend => '디스플레이 미러링 방식';

  @override
  String get mirrorBackendAuto => '자동(호환성 우선)';

  @override
  String get mirrorBackendAutoDescription => '이 기기에서 사용 가능한 최적의 방식을 사용합니다';

  @override
  String get mirrorBackendWindowManager => 'WindowManager';

  @override
  String get mirrorBackendSurfaceControl => 'SurfaceControl';

  @override
  String get mirrorBackendVirtualDisplay => 'VirtualDisplay';

  @override
  String get updateAvailable => '업데이트가 있습니다';

  @override
  String get updateAvailableTitle => 'GitHub에 새 릴리스가 공개되었습니다!';

  @override
  String get playUpdateAvailableTitle => 'Google Play에 업데이트가 있습니다';

  @override
  String get playUpdateAvailableDescription =>
      'Google Play에서 최신 버전으로 업데이트할 수 있습니다.';

  @override
  String get updateNow => '지금 업데이트';

  @override
  String get checkForUpdates => '업데이트 확인';

  @override
  String get checkingForUpdates => '업데이트 정보를 가져오는 중입니다';

  @override
  String get updateNotChecked => '업데이트 정보를 아직 가져오지 않았습니다';

  @override
  String get upToDate => '최신 버전입니다';

  @override
  String get updateCheckFailed => '업데이트 정보를 가져올 수 없습니다';

  @override
  String get currentVersion => '현재 버전';

  @override
  String get latestVersion => '최신 버전';

  @override
  String get openOnGitHub => 'GitHub에서 열기';

  @override
  String get close => '닫기';

  @override
  String get deviceInfo => '기기 정보';

  @override
  String get desktopMode => '사용할 데스크톱 모드';

  @override
  String get accessibilitySettings => '접근성 설정';

  @override
  String get accessibilityDescription => 'Dextop 서비스 설정을 엽니다';

  @override
  String get appInfo => '앱 정보';

  @override
  String get licenses => '오픈 소스 라이선스';

  @override
  String get licensesDescription => 'Flutter 및 사용 라이브러리 라이선스 표시';

  @override
  String get landscape => '가로';

  @override
  String get portrait => '세로';

  @override
  String get stopped => '중지됨';

  @override
  String get running => '실행 중';

  @override
  String get start => '시작';

  @override
  String get stop => '중지';

  @override
  String get customAdd => '사용자 지정 해상도 추가';

  @override
  String get editResolution => '해상도 편집';

  @override
  String get add => '추가';

  @override
  String get save => '저장';

  @override
  String get deleteResolution => '이 해상도 삭제';

  @override
  String get width => '너비';

  @override
  String get height => '높이';

  @override
  String get protectedContent => '보호된 콘텐츠 표시를 허용합니다';

  @override
  String get version => '버전 1.0.0';

  @override
  String get setupWelcome => 'Dextop에 오신 것을 환영합니다.';

  @override
  String get setupTagline => '스마트폰 하나로 완벽한 데스크톱 환경을.';

  @override
  String get setupBegin => '시작하기';

  @override
  String get setupPhaseTerms => '이용에 있어서';

  @override
  String get setupPhaseShizuku => 'Shizuku';

  @override
  String get setupPhaseDevice => '기기 확인';

  @override
  String get setupPhaseDemo => '조작 체험';

  @override
  String get back => '뒤로';

  @override
  String get continueLabel => '계속';

  @override
  String get done => '완료';

  @override
  String get incomplete => '미완료';

  @override
  String get setupSystemTitle => '시스템 기능을 이용';

  @override
  String get setupSystemDescription =>
      'Dextop은 Shizuku와 ADB를 사용하여 가상 디스플레이, 화면 방향, 입력, 시스템 UI 등의 동작을 제어합니다.';

  @override
  String get setupDisclaimer =>
      '단말기와 OS의 구현 차이, 시스템 업데이트, 다른 앱과의 충돌 등으로 인한 문제, 데이터 손실, 단말기 기능에 미치는 영향에 대해서는 개발자는 책임을 지지 않습니다. 내용을 이해한 후 사용하십시오.';

  @override
  String get setupShizukuTitle => 'Shizuku 준비';

  @override
  String get setupShizukuDescription =>
      'Dextop이 시스템 기능에 안전하게 액세스하기 위해 Shizuku를 사용합니다.';

  @override
  String get setupInstallShizuku => 'Shizuku 설치';

  @override
  String get setupConfigureShizuku => 'Shizuku 설정';

  @override
  String get setupShizukuHint =>
      'Shizuku를 열고 \"페어링\"에 나타나는 순서에 따라 설정하고 Shizuku를 시작하십시오.';

  @override
  String get setupOpenShizuku => 'Shizuku 열기';

  @override
  String get setupValidate => '설정이 완료되었습니까? 유효성 확인';

  @override
  String get setupDextopPermission => 'Dextop에 대한 권한';

  @override
  String get setupInstallPlay => 'Google Play에서 설치';

  @override
  String get setupAllowPermission => '권한 부여';

  @override
  String get setupProviderChoiceTitle => '권한 서비스 선택';

  @override
  String get setupProviderChoiceDescription =>
      'Stellar와 Shizuku가 모두 설치되어 있습니다. Dextop에서 사용할 서비스를 선택하세요.';

  @override
  String get setupUseStellar => 'Stellar(권장)';

  @override
  String get setupUseShizuku => 'Shizuku';

  @override
  String get setupRunningAsRoot => '서비스를 root로 실행 중입니다';

  @override
  String get setupRootVerified =>
      'Shizuku가 root 권한으로 실행 중임을 확인했습니다. 다음으로 Dextop 권한을 부여하세요.';

  @override
  String get setupRootNotRunning =>
      'Shizuku가 root 권한으로 실행 중인지 확인할 수 없습니다. root로 시작한 후 다시 시도하세요.';

  @override
  String get setupQuestionOpen => 'Shizuku를 열었나요?';

  @override
  String get setupQuestionPair => '\'페어링\'에 표시된 모든 단계를 완료했습니까?';

  @override
  String get setupQuestionStart =>
      'Shizuku에서 \"시작\"을 누르고 \"Shizuku가 실행 중입니다.\"라고 표시됩니까?';

  @override
  String get yes => '예';

  @override
  String get no => '아니오';

  @override
  String get setupVerified => 'Shizuku 설정을 확인했습니다';

  @override
  String get setupVerificationFailed =>
      'Shizuku 설정 또는 시작을 확인할 수 없습니다. Shizuku의 절차를 완료한 후 다시 확인하십시오.';

  @override
  String get setupPermissionCheckFailed => 'Shizuku의 권한을 확인할 수 없습니다.';

  @override
  String get setupDeviceTitle => '이 단말기의 구성';

  @override
  String get model => '기종';

  @override
  String get vendor => '벤더';

  @override
  String get desktopUi => '데스크톱 UI';

  @override
  String get detectedResolution => '자동 감지 해상도';

  @override
  String get loadingLabel => '불러오는 중…';

  @override
  String get setupDeviceDescription => '이 정보를 바탕으로 초기 해상도와 기기별 데스크톱 제어를 설정합니다.';

  @override
  String get setupGestureTitle => '제스처로 조작 패널 호출';

  @override
  String get setupGestureDescription => '아래의 3개의 원에 3개의 손가락을 동시에 놓으십시오.';

  @override
  String get uiTwoFingerTap => '두 손가락 탭';

  @override
  String get ui3FingerTap => '세 손가락 탭';

  @override
  String get ui4Divisions => '4분할';

  @override
  String get uiDextopIsReady => 'Dextop 준비됨';

  @override
  String get uiStopDextop => 'Dextop 중지';

  @override
  String get uiDextopCanBeRestarted => 'Dextop을 재개할 수 있습니다.';

  @override
  String get uiOpenDextop => 'Dextop 열기';

  @override
  String get uiCreateADextopSession => 'Dextop 세션 만들기';

  @override
  String get uiDextopWorkspaceJson => 'Dextop 작업 공간 JSON';

  @override
  String get uiPerformanceDisplayOnDextop => 'Dextop에 성능 오버레이 표시';

  @override
  String get uiDoNotSleepWhileRunningDextop => 'Dextop 실행 중 화면 켜짐 유지';

  @override
  String get uiRealTimeDisplayOfFpsMemoryPower => 'FPS, 메모리, 전력 소비, 배터리 실시간 표시';

  @override
  String get uiCouldNotLoadJson => 'JSON을 로드할 수 없습니다.';

  @override
  String get uiSecureSettingsPermission => 'Secure Settings 권한';

  @override
  String get uiAllowShizukuPermissions => 'Shizuku의 권한 부여';

  @override
  String get uiInstallShizuku => 'Shizuku 설치';

  @override
  String get uiCheckingShizukuConnection => 'Shizuku 연결 확인 중';

  @override
  String get uiShizukuConnection => 'Shizuku 연결';

  @override
  String get uiCopy => '사본';

  @override
  String get uiOthers => '기타';

  @override
  String get uiAccessibilityOverlay => '접근성 오버레이';

  @override
  String get uiAccessibilityServices => '내게 필요한 옵션 서비스';

  @override
  String get uiAppNotFound => '앱을 찾을 수 없음';

  @override
  String get uiAppsAndWorkspace => '앱 및 작업공간';

  @override
  String get uiLaunchTheAppAndConfigureYourWorkspace => '앱 시작 및 작업 공간 구성';

  @override
  String get uiRestartTheApp => '앱 다시 시작';

  @override
  String get uiSearchApp => '앱 검색';

  @override
  String get uiAppMemory => '앱 메모리';

  @override
  String get uiAppLauncher => '앱 런처';

  @override
  String get uiAppLauncherSettings => '앱 런처 설정';

  @override
  String get uiAppLaunchFunction => '앱 시작 기능';

  @override
  String get uiImport => '가져오기';

  @override
  String get uiExport => '내보내기';

  @override
  String get uiCursor => '커서';

  @override
  String get uiCancel => '취소';

  @override
  String get uiQuickSettingsTile => '빠른 설정 타일';

  @override
  String get uiGesture => '제스처';

  @override
  String get uiSecondaryIme => '보조 IME';

  @override
  String get uiSecureDisplayFoldable => '보안 디스플레이, 미러링 방식, Foldable';

  @override
  String get uiSecurity => '보안';

  @override
  String get uiTap => '탭';

  @override
  String get uiTapPressAndHoldMultiFingerOperation => '탭, 길게 누르기, 여러 손가락 조작';

  @override
  String get uiOpenAppOnDesktop => '데스크톱에서 앱 열기';

  @override
  String get uiDesktopMode => '데스크톱 모드';

  @override
  String get uiDesktopFeatures => '데스크톱 기능';

  @override
  String get uiTrackpad => '트랙패드';

  @override
  String get uiDrag => '드래그';

  @override
  String get uiBattery => '배터리';

  @override
  String get uiPerformance => '공연';

  @override
  String get uiPerformanceCompatibility => '성능 및 호환성';

  @override
  String get uiItSupportsMultiTouchAndTheThree =>
      '멀티터치를 활성화하면 세 손가락 제스처가 화면 왼쪽 가장자리 스와이프로 변경됩니다.';

  @override
  String get uiMainLarge2Sub => '큰 메인 창 + 보조 창 2개';

  @override
  String get uiMainLeft => '메인(왼쪽)';

  @override
  String get uiLayout => '레이아웃';

  @override
  String get uiWorkSpace => '작업 공간';

  @override
  String get uiCopiedWorkspaceJsonToClipboard => '작업 공간 JSON을 클립 보드에 복사했습니다.';

  @override
  String get uiImportWorkspace => '작업 공간 가져오기';

  @override
  String get uiSaveWorkspace => '작업 공간 저장';

  @override
  String get uiDeleteWorkspace => '작업공간 삭제';

  @override
  String get uiEditWorkspace => '작업공간 편집';

  @override
  String get uiUp => '위로';

  @override
  String get uiDividedIntoUpperAndLowerParts => '상하 2분할';

  @override
  String get uiUpperHalf => '상반부';

  @override
  String get uiMoveDown => '아래로 이동';

  @override
  String get uiLowerHalf => '하반부';

  @override
  String get uiCenter => '중앙';

  @override
  String get uiCompatibilityDiagnosis => '호환성 진단';

  @override
  String get uiVirtualDisplayCreation => '가상 디스플레이 생성';

  @override
  String get uiOpenASavedAppConfiguration => '저장된 앱 구성 열기';

  @override
  String get uiNoSavedWorkspaces => '저장된 작업공간 없음';

  @override
  String get uiInputAndGestures => '입력과 제스처';

  @override
  String get uiInputMode => '입력 모드';

  @override
  String get uiCancelFullScreen => '전체 화면 해제';

  @override
  String get uiReDiagnosis => '재진단';

  @override
  String get uiRestart => '재개';

  @override
  String get uiAvailableMemory => '사용 가능한 메모리';

  @override
  String get uiDelete => '삭제';

  @override
  String get uiYouCanRestoreYourPreviousSession => '마지막 세션을 복구할 수 있습니다.';

  @override
  String get uiRight => '오른쪽';

  @override
  String get uiRight13 => '오른쪽 1/3';

  @override
  String get uiRight23 => '오른쪽 2/3';

  @override
  String get uiRightClick => '오른쪽 클릭';

  @override
  String get uiUpperRight => '오른쪽 상단';

  @override
  String get uiLowerRight => '오른쪽 하단';

  @override
  String get uiRightHalf => '오른쪽 절반';

  @override
  String get uiName => '이름';

  @override
  String get uiLargeScreenFoldable => '큰 화면 · Foldable';

  @override
  String get uiActualFps => '실측 FPS';

  @override
  String get uiExperimentalMultiTouch => '실험적인 멀티 터치';

  @override
  String get uiExperimentalFeatures => '실험 기능';

  @override
  String get uiLeft => '왼쪽';

  @override
  String get uiLeft13 => '왼쪽 1/3';

  @override
  String get uiLeft13Right23 => '왼쪽 1/3 · 오른쪽 2/3';

  @override
  String get uiLeft23 => '왼쪽 2/3';

  @override
  String get uiLeft23Right13 => '왼쪽 2/3 · 오른쪽 1/3';

  @override
  String get uiLeftCenterRight => '왼쪽, 중앙, 오른쪽';

  @override
  String get uiUpperLeft => '왼쪽 상단';

  @override
  String get uiUpperLeftUpperRightLowerHalf => '좌상, 우상, 하반부';

  @override
  String get uiLowerLeft => '왼쪽 하단';

  @override
  String get uiLeftHalf => '왼쪽 절반';

  @override
  String get uiDividedIntoLeftAndRight => '좌우 2분할';

  @override
  String get uiSwipeRightWithThreeFingersFromThe =>
      '왼쪽 끝에서 3 손가락으로 오른쪽으로 스 와이프';

  @override
  String get uiRecoverySession => '복구 세션';

  @override
  String get uiEstimatedPowerConsumption => '예상 소비 전력';

  @override
  String get uiOperationOverlay => '조작 오버레이';

  @override
  String get uiShowActionOverlay => '조작 오버레이 표시';

  @override
  String get uiOperationMenu => '조작 메뉴';

  @override
  String get uiThereIsAnExistingSession => '기존 세션이 있습니다.';

  @override
  String get uiSaveConfiguration => '구성 저장';

  @override
  String get uiRestorePrivileges => '권한 복구';

  @override
  String get uiChangeToHorizontalHold => '가로로 변경';

  @override
  String get uiPreparationIsRequired => '준비가 필요';

  @override
  String get uiPhysicalKeyboard => '물리적 키보드';

  @override
  String get uiPhysicalMouse => '물리 마우스';

  @override
  String get uiConditionAndDiagnosis => '상태 및 진단';

  @override
  String get uiPreventsTheScreenFromTurningOffAutomatically => '화면 자동 꺼짐 방지';

  @override
  String get uiDestruction => '삭제';

  @override
  String get uiTerminalAndPermissions => '터미널 및 권한';

  @override
  String get uiDeviceInformationDesktopModeAccessibility =>
      '터미널 정보, 데스크톱 모드, 접근성';

  @override
  String get uiTerminalResolution => '터미널 해상도';

  @override
  String get uiEnd => '종료';

  @override
  String get uiTerminationProcessingCompletedSuccessfully =>
      '세션이 정상적으로 종료되었습니다.';

  @override
  String get uiEdit => '편집';

  @override
  String get uiChangeToPortraitOrientation => '세로로 변경';

  @override
  String get uiVerticalHorizontalSwitching => '종횡 전환';

  @override
  String get uiDisplayOptimization => '디스플레이 최적화';

  @override
  String get uiDisplayRefreshRate => '디스플레이 새로고침 속도';

  @override
  String get uiReproduction => '복사본 만들기';

  @override
  String get uiManageLaunchedAppsAndConfigurations => '시작할 앱 및 구성 관리';

  @override
  String get uiCouldNotStart => '시작할 수 없습니다.';

  @override
  String get uiLongPress => '길게 누르기';

  @override
  String get uiAutomaticallyUsesMeasuredResolutionForOpenAnd =>
      '열린 상태와 닫힌 상태의 실측 해상도 자동 사용';

  @override
  String get uiStart => '시작';

  @override
  String get uiAutomaticSwitchingAccordingToOpenClosedState =>
      '개폐 상태에 따라 자동 전환';

  @override
  String get uiOpeningQuote => '“';

  @override
  String get uiDeleteWorkspaceQuestionSuffix => '” 작업 공간을 삭제하시겠습니까?';

  @override
  String get uiAbnormalSessionWarning =>
      '세션이 비정상 상태로 종료되었습니다.\n일부 Android 시스템 기능이 여전히 비활성화되어 있을 수 있습니다.';

  @override
  String get uiChecking => '확인 중';

  @override
  String get uiIdle => '대기 중';

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
  String get diagnosticLog => '작동 로그 및 기기 진단';

  @override
  String get diagnosticLogDescription => '앱 로그, 기능 감지 및 상세 기기 사양을 표시합니다';

  @override
  String get loadDiagnosticLog => '진단 보고서 불러오기';

  @override
  String get copyDiagnosticLog => '복사';

  @override
  String get shareDiagnosticLog => '공유';

  @override
  String get clearDiagnosticLog => '로그 지우기';

  @override
  String get deviceReport => '기기 작동 보고';

  @override
  String get uiCpuTemperature => 'CPU 온도';

  @override
  String get deviceReportDescription => '기기 및 기능 호환성을 이메일로 보고';

  @override
  String get deviceReportIntro => '기기 정보는 자동으로 수집됩니다. 각 기능의 결과를 선택하세요.';

  @override
  String get reportWorking => '작동함';

  @override
  String get reportNotWorking => '작동 안 함';

  @override
  String get reportUntested => '미확인';

  @override
  String get reportOverall => '전체 상태';

  @override
  String get reportNotes => '기타 특이사항';

  @override
  String get sendDeviceReport => '이메일로 보고서 보내기';

  @override
  String get reportEmailUnavailable => '이메일 앱을 열 수 없습니다';

  @override
  String get reportTemplateTitle => 'Dextop 기기 작동 보고';

  @override
  String get reportNoNotes => '없음';

  @override
  String get reportNoSessionLog => '완료된 Dextop 세션 로그가 아직 없습니다.';

  @override
  String get reportFeatureStartup => '앱 시작 및 기기 감지';

  @override
  String get reportFeatureSession => 'Dextop 세션 시작';

  @override
  String get reportFeatureVirtualDisplay => 'VirtualDisplay 미러링';

  @override
  String get reportFeatureWindowManager => 'WindowManager 미러링';

  @override
  String get reportFeatureSurfaceControl => 'SurfaceControl 미러링';

  @override
  String get reportFeatureLandscape => '가로 모드';

  @override
  String get reportFeaturePortrait => '세로 모드';

  @override
  String get reportFeatureSecureDisplay => '보안 디스플레이';

  @override
  String get reportFeatureLauncher => '앱 런처 및 자유 형식 창';

  @override
  String get reportFeatureWorkspace => '작업 공간 저장 및 복원';

  @override
  String get reportFeatureCursor => '커서 및 터치패드 입력';

  @override
  String get reportFeatureDirectTouch => '직접 터치 입력';

  @override
  String get reportFeatureMultiTouch => '멀티터치 스크롤 및 핀치 줌';

  @override
  String get reportFeatureGesture => '세 손가락 오버레이 제스처';

  @override
  String get reportFeatureMouse => '물리 마우스';

  @override
  String get reportFeatureKeyboard => '물리 키보드';

  @override
  String get reportFeatureRouting => '물리 마우스 및 키보드 디스플레이 라우팅';

  @override
  String get reportFeatureFoldable => '폴더블 자동 해상도';

  @override
  String get reportFeaturePerformance => '성능 오버레이';

  @override
  String get reportFeatureCleanup => '세션 정리 및 Android 상태 복원';

  @override
  String get samsungExperimentalTitle => '실험적 Samsung 데스크톱 설정';

  @override
  String get samsungUnavailable => 'Samsung 기기에서만 사용할 수 있습니다';

  @override
  String get samsungExperimentalDescription =>
      '기본 DeX 설정 화면에서 숨겨진 항목을 Dextop에서 변경합니다';

  @override
  String get samsungSettingsTitle => 'Samsung 데스크톱 설정';

  @override
  String get samsungSettingsSummary => '화면, 입력 및 작업 표시줄 설정';

  @override
  String get samsungRestoreSuccess => 'Samsung 설정을 복원했습니다';

  @override
  String get samsungConfirmTitle => '설정 변경 확인';

  @override
  String get samsungPermanentWarning =>
      '이 항목은 초기화할 때까지 Dextop 및 평소 사용하는 데스크톱 환경에 영구적인 영향을 줄 수 있습니다.';

  @override
  String get samsungAcceptEnable => '동의하고 활성화';

  @override
  String get samsungAboutSetting => '이 설정 정보';

  @override
  String get samsungRestoreEnvironment => '환경 복원';

  @override
  String get samsungSettingsIntro =>
      'Samsung 설정이 외부 디스플레이 미연결 상태에서 숨기는 DeX 값을 직접 변경합니다. 변경 사항은 Samsung DeX 및 해당 Dextop 기능에 적용됩니다.';

  @override
  String get samsungResolution => '외부 화면 해상도';

  @override
  String get samsungScreenZoom => '화면 확대/축소(DPI)';

  @override
  String get samsungFontScale => '글자 크기';

  @override
  String get samsungScreenTimeout => '화면 시간 제한';

  @override
  String get samsungAudioOutput => '외부 화면으로 오디오 출력';

  @override
  String get samsungDisplayOrientation => '외부 화면 회전';

  @override
  String get samsungDisplayArrangement => '화면 배치';

  @override
  String get samsungSectionInput => '입력';

  @override
  String get samsungSectionDesktop => '데스크톱';

  @override
  String get samsungInputLockedWhileRunning =>
      'Dextop 실행 중에는 충돌하는 Samsung 입력 설정을 변경할 수 없습니다.';

  @override
  String get samsungAutorunTouchpad => '터치패드 자동 실행';

  @override
  String get samsungTouchpadScrollDirection => '스크롤 방향 반전';

  @override
  String get samsungTouchKeyboard => '연결 시 화면 키보드 표시';

  @override
  String get samsungKeyboardDex => '물리 키보드 사용 중에도 키보드 표시';

  @override
  String get samsungSpenInputMode => 'S Pen을 마우스로 사용';

  @override
  String get samsungThreeFingerGesture => '세 손가락 제스처';

  @override
  String get samsungFourFingerGesture => '네 손가락 제스처';

  @override
  String get samsungAutoHideTaskbar => '작업 표시줄 자동 숨기기';

  @override
  String get samsungDexCommandArrow => '명령 화살표 표시';

  @override
  String get samsungIncludePhoneDisplay => '휴대전화 화면을 디스플레이 토폴로지에 포함';

  @override
  String get samsungMirrorPhoneDisplay => '내장 화면 미러링';

  @override
  String get samsungReviewEnable => '경고를 확인하고 설정 변경 활성화';

  @override
  String get samsungSeconds15 => '15초';

  @override
  String get samsungSeconds30 => '30초';

  @override
  String get samsungMinute1 => '1분';

  @override
  String get samsungMinutes2 => '2분';

  @override
  String get samsungMinutes5 => '5분';

  @override
  String get samsungMinutes10 => '10분';

  @override
  String get samsungMinutes20 => '20분';

  @override
  String get samsungMinutes30 => '30분';

  @override
  String get samsungHour1 => '1시간';

  @override
  String get samsungLeft => '왼쪽';

  @override
  String get samsungRight => '오른쪽';

  @override
  String get samsungAutomatic => '자동';

  @override
  String get samsungGestureNone => '없음';

  @override
  String get samsungGestureApps => '앱 목록';

  @override
  String get samsungGestureRecents => '최근 앱';

  @override
  String get samsungGestureNotifications => '알림';

  @override
  String get samsungGestureQuickSettings => '빠른 설정';

  @override
  String get samsungHelp_resolution =>
      'Samsung 데스크톱 환경에서 사용하는 외부 화면 해상도이며 Dextop 해상도와 별도로 저장됩니다.';

  @override
  String get samsungHelp_screenZoom => 'Samsung 데스크톱 환경의 표시 밀도(DPI)를 변경합니다.';

  @override
  String get samsungHelp_fontScale => 'Samsung 데스크톱 환경의 글자 배율을 변경합니다.';

  @override
  String get samsungHelp_screenTimeout => '외부 데스크톱 화면이 꺼질 때까지의 시간입니다.';

  @override
  String get samsungHelp_audioOutput => '오디오 출력을 외부 화면으로 전환합니다.';

  @override
  String get samsungHelp_displayOrientation => '외부 데스크톱 화면의 회전 방향을 고정합니다.';

  @override
  String get samsungHelp_displayArrangement => '휴대전화와 외부 화면의 상대 위치를 지정합니다.';

  @override
  String get samsungHelp_autorunTouchpad =>
      '연결 시 Samsung 터치패드를 자동 표시하며 Dextop 입력과 충돌합니다.';

  @override
  String get samsungHelp_touchpadScrollDirection =>
      'Samsung 터치패드의 스크롤 방향을 반전합니다.';

  @override
  String get samsungHelp_touchKeyboard => '화면 키보드를 표시하며 Dextop IME 제어와 충돌합니다.';

  @override
  String get samsungHelp_keyboardDex =>
      '물리 키보드 연결 중에도 화면 키보드를 표시하며 Dextop IME 제어와 충돌합니다.';

  @override
  String get samsungHelp_spenInputMode => 'S Pen을 포인팅 장치로 사용합니다.';

  @override
  String get samsungHelp_threeFingerGesture =>
      'Samsung 세 손가락 동작이며 Dextop 제스처와 충돌합니다.';

  @override
  String get samsungHelp_fourFingerGesture =>
      'Samsung 네 손가락 동작이며 Dextop 멀티터치와 충돌합니다.';

  @override
  String get samsungHelp_autoHideTaskbar => 'Samsung 데스크톱 작업 표시줄을 자동으로 숨깁니다.';

  @override
  String get samsungHelp_dexCommandArrow => 'Samsung 데스크톱 명령 화살표를 표시합니다.';

  @override
  String get samsungHelp_includePhoneDisplay =>
      '휴대전화 화면을 데스크톱 디스플레이 토폴로지에 포함합니다.';

  @override
  String get samsungHelp_mirrorPhoneDisplay => '내장 화면을 데스크톱 환경에 미러링합니다.';
}
