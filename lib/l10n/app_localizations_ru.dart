// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Russian (`ru`).
class AppLocalizationsRu extends AppLocalizations {
  AppLocalizationsRu([String locale = 'ru']) : super(locale);

  @override
  String get home => 'Главная';

  @override
  String get settings => 'Настройки';

  @override
  String get resolution => 'Разрешение';

  @override
  String get theme => 'Тема';

  @override
  String get system => 'Системная';

  @override
  String get light => 'Светлая';

  @override
  String get dark => 'Тёмная';

  @override
  String get display => 'Экран';

  @override
  String get secureDisplay => 'Защищённый экран';

  @override
  String get secureDisplayDescription =>
      'Разрешить показ защищённого содержимого';

  @override
  String get mirrorBackend => 'Способ зеркалирования дисплея';

  @override
  String get mirrorBackendAuto => 'Автоматически (совместимость)';

  @override
  String get mirrorBackendAutoDescription =>
      'Использовать лучший доступный способ для устройства';

  @override
  String get mirrorBackendWindowManager => 'WindowManager';

  @override
  String get mirrorBackendSurfaceControl => 'SurfaceControl';

  @override
  String get mirrorBackendVirtualDisplay => 'VirtualDisplay';

  @override
  String get updateAvailable => 'Доступно обновление';

  @override
  String get updateAvailableTitle => 'На GitHub опубликована новая версия!';

  @override
  String get checkForUpdates => 'Проверить обновления';

  @override
  String get checkingForUpdates => 'Получение сведений об обновлении';

  @override
  String get updateNotChecked => 'Сведения об обновлении ещё не получены';

  @override
  String get upToDate => 'Установлена последняя версия';

  @override
  String get updateCheckFailed => 'Не удалось получить сведения об обновлении';

  @override
  String get currentVersion => 'Текущая версия';

  @override
  String get latestVersion => 'Последняя версия';

  @override
  String get openOnGitHub => 'Открыть на GitHub';

  @override
  String get close => 'Закрыть';

  @override
  String get deviceInfo => 'Информация об устройстве';

  @override
  String get desktopMode => 'Режим рабочего стола';

  @override
  String get accessibilitySettings => 'Специальные возможности';

  @override
  String get accessibilityDescription => 'Открыть настройки службы Dextop';

  @override
  String get appInfo => 'О приложении';

  @override
  String get licenses => 'Лицензии открытого ПО';

  @override
  String get licensesDescription => 'Лицензии Flutter и используемых библиотек';

  @override
  String get landscape => 'Альбомная';

  @override
  String get portrait => 'Портретная';

  @override
  String get stopped => 'Остановлено';

  @override
  String get running => 'Работает';

  @override
  String get start => 'Запустить';

  @override
  String get stop => 'Остановить';

  @override
  String get customAdd => 'Добавить разрешение';

  @override
  String get editResolution => 'Изменить разрешение';

  @override
  String get add => 'Добавить';

  @override
  String get save => 'Сохранить';

  @override
  String get deleteResolution => 'Удалить разрешение';

  @override
  String get width => 'Ширина';

  @override
  String get height => 'Высота';

  @override
  String get protectedContent => 'Разрешить показ защищённого содержимого';

  @override
  String get version => 'Версия 1.0.0';

  @override
  String get setupWelcome => 'Добро пожаловать в Dextop.';

  @override
  String get setupTagline =>
      'Идеальная среда рабочего стола на вашем смартфоне.';

  @override
  String get setupBegin => 'Начать';

  @override
  String get setupPhaseTerms => 'Перед началом работы';

  @override
  String get setupPhaseShizuku => 'Shizuku';

  @override
  String get setupPhaseDevice => 'Проверка устройства';

  @override
  String get setupPhaseDemo => 'Знакомство с управлением';

  @override
  String get back => 'Назад';

  @override
  String get continueLabel => 'Продолжить';

  @override
  String get done => 'Готово';

  @override
  String get incomplete => 'Не завершено';

  @override
  String get setupSystemTitle => 'Использование системных функций';

  @override
  String get setupSystemDescription =>
      'Dextop использует Shizuku и ADB для управления таким поведением, как виртуальные дисплеи, ориентация экрана, ввод и системный пользовательский интерфейс.';

  @override
  String get setupDisclaimer =>
      'Разработчик не несет ответственности за любые дефекты, потерю данных или влияние на функциональность устройства, вызванное различиями в реализации устройства или ОС, обновлениями системы, конфликтами с другими приложениями и т. д. Перед использованием ознакомьтесь с содержанием.';

  @override
  String get setupShizukuTitle => 'Подготовка Shizuku';

  @override
  String get setupShizukuDescription =>
      'Dextop использует Shizuku для безопасного доступа к функциям системы.';

  @override
  String get setupInstallShizuku => 'Установить Shizuku';

  @override
  String get setupConfigureShizuku => 'Настроить Shizuku';

  @override
  String get setupShizukuHint =>
      'Откройте Shizuku, установите его в порядке, указанном в разделе «Сопряжение», и запустите Shizuku.';

  @override
  String get setupOpenShizuku => 'Открыть Shizuku';

  @override
  String get setupValidate => 'Настройка завершена? Проверить';

  @override
  String get setupDextopPermission => 'Разрешения для Dextop';

  @override
  String get setupInstallPlay => 'Установить из Google Play';

  @override
  String get setupAllowPermission => 'Предоставить разрешение';

  @override
  String get setupProviderChoiceTitle => 'Выберите привилегированную службу';

  @override
  String get setupProviderChoiceDescription =>
      'Установлены Stellar и Shizuku. Выберите службу, которую будет использовать Dextop.';

  @override
  String get setupUseStellar => 'Stellar (рекомендуется)';

  @override
  String get setupUseShizuku => 'Shizuku';

  @override
  String get setupRunningAsRoot => 'Служба запущена с правами root';

  @override
  String get setupRootVerified =>
      'Подтверждено, что Shizuku работает с правами root. Теперь предоставьте разрешение Dextop.';

  @override
  String get setupRootNotRunning =>
      'Не удалось подтвердить, что Shizuku работает с правами root. Запустите его с правами root и повторите попытку.';

  @override
  String get setupQuestionOpen => 'Вы открыли Shizuku?';

  @override
  String get setupQuestionPair =>
      'Вы выполнили все шаги, перечисленные в разделе «Сопряжение»?';

  @override
  String get setupQuestionStart =>
      'Вы нажали «Запустить» в Shizuku и убедились, что отображается сообщение «Shizuku работает»?';

  @override
  String get yes => 'Да';

  @override
  String get no => 'Нет';

  @override
  String get setupVerified => 'Настройка Shizuku проверена';

  @override
  String get setupVerificationFailed =>
      'Невозможно подтвердить конфигурацию или запуск Shizuku. Пожалуйста, выполните действия в Shizuku, а затем проверьте еще раз.';

  @override
  String get setupPermissionCheckFailed =>
      'Не удалось проверить разрешения для Shizuku.';

  @override
  String get setupDeviceTitle => 'Конфигурация на этом устройстве';

  @override
  String get model => 'Модель';

  @override
  String get vendor => 'Производитель';

  @override
  String get desktopUi => 'Интерфейс рабочего стола';

  @override
  String get detectedResolution => 'Автоматически определённое разрешение';

  @override
  String get loadingLabel => 'Загрузка…';

  @override
  String get setupDeviceDescription =>
      'Эта информация используется для установки исходного разрешения и элементов управления рабочим столом для конкретного устройства.';

  @override
  String get setupGestureTitle => 'Вызов панели управления жестами';

  @override
  String get setupGestureDescription =>
      'Поместите три пальца одновременно на три круга внизу.';

  @override
  String get uiTwoFingerTap => 'Касание двумя пальцами';

  @override
  String get ui3FingerTap => 'Касание тремя пальцами';

  @override
  String get ui4Divisions => 'Разделение на четыре области';

  @override
  String get uiDextopIsReady => 'Dextop готов.';

  @override
  String get uiStopDextop => 'Остановить Dextop';

  @override
  String get uiDextopCanBeRestarted => 'Сеанс Dextop можно возобновить';

  @override
  String get uiOpenDextop => 'Открыть Dextop';

  @override
  String get uiCreateADextopSession => 'Создать сеанс Dextop';

  @override
  String get uiDextopWorkspaceJson => 'Рабочая область Dextop в формате JSON';

  @override
  String get uiPerformanceDisplayOnDextop =>
      'Показывать панель производительности в Dextop';

  @override
  String get uiDoNotSleepWhileRunningDextop =>
      'Не выключать экран во время работы Dextop';

  @override
  String get uiRealTimeDisplayOfFpsMemoryPower =>
      'Отображение в реальном времени FPS, памяти, энергопотребления и заряда батареи.';

  @override
  String get uiCouldNotLoadJson => 'Не удалось загрузить JSON';

  @override
  String get uiSecureSettingsPermission =>
      'Разрешение на настройки безопасности';

  @override
  String get uiAllowShizukuPermissions => 'Предоставить разрешение Shizuku';

  @override
  String get uiInstallShizuku => 'Установить Shizuku';

  @override
  String get uiCheckingShizukuConnection => 'Проверка подключения к Shizuku';

  @override
  String get uiShizukuConnection => 'Подключение к Shizuku';

  @override
  String get uiCopy => ' — копия';

  @override
  String get uiOthers => 'Другое';

  @override
  String get uiAccessibilityOverlay => 'Оверлей специальных возможностей';

  @override
  String get uiAccessibilityServices => 'Служба специальных возможностей';

  @override
  String get uiAppNotFound => 'Приложение не найдено';

  @override
  String get uiAppsAndWorkspace => 'Приложения и рабочее пространство';

  @override
  String get uiLaunchTheAppAndConfigureYourWorkspace =>
      'Запуск приложений и настройка рабочих областей';

  @override
  String get uiRestartTheApp => 'Перезапустить приложение';

  @override
  String get uiSearchApp => 'Поиск приложений';

  @override
  String get uiAppMemory => 'Память приложения';

  @override
  String get uiAppLauncher => 'Панель приложений';

  @override
  String get uiAppLauncherSettings => 'Настройки запуска приложений';

  @override
  String get uiAppLaunchFunction => 'Функция запуска приложения';

  @override
  String get uiImport => 'Импортировать';

  @override
  String get uiExport => 'Экспортировать';

  @override
  String get uiCursor => 'Курсор';

  @override
  String get uiCancel => 'Отмена';

  @override
  String get uiQuickSettingsTile => 'Плитка быстрых настроек';

  @override
  String get uiGesture => 'Жест';

  @override
  String get uiSecondaryIme => 'Вторичный IME';

  @override
  String get uiSecureDisplayFoldable =>
      'Защищённый экран, способ зеркалирования, складные устройства';

  @override
  String get uiSecurity => 'Безопасность';

  @override
  String get uiTap => 'Касание';

  @override
  String get uiTapPressAndHoldMultiFingerOperation =>
      'Касание, долгое нажатие и управление несколькими пальцами';

  @override
  String get uiOpenAppOnDesktop => 'Открыть приложение на рабочем столе';

  @override
  String get uiDesktopMode => 'Режим рабочего стола';

  @override
  String get uiDesktopFeatures => 'Функции рабочего стола';

  @override
  String get uiTrackpad => 'Трекпад';

  @override
  String get uiDrag => 'Перетаскивание';

  @override
  String get uiBattery => 'Батарея';

  @override
  String get uiPerformance => 'Производительность';

  @override
  String get uiPerformanceCompatibility => 'Производительность и совместимость';

  @override
  String get uiItSupportsMultiTouchAndTheThree =>
      'Включает мультитач и заменяет жест тремя пальцами на свайп от левого края экрана.';

  @override
  String get uiMainLarge2Sub => 'Большое основное окно + два дополнительных';

  @override
  String get uiMainLeft => 'Главный (слева)';

  @override
  String get uiLayout => 'Расположение';

  @override
  String get uiWorkSpace => 'Рабочая область';

  @override
  String get uiCopiedWorkspaceJsonToClipboard =>
      'JSON рабочей области скопирован в буфер обмена.';

  @override
  String get uiImportWorkspace => 'Импорт рабочей области';

  @override
  String get uiSaveWorkspace => 'Сохранить рабочее пространство';

  @override
  String get uiDeleteWorkspace => 'Удалить рабочую область';

  @override
  String get uiEditWorkspace => 'Изменить рабочую область';

  @override
  String get uiUp => 'Переместить вверх';

  @override
  String get uiDividedIntoUpperAndLowerParts => 'Разделение сверху и снизу';

  @override
  String get uiUpperHalf => 'Верхняя половина';

  @override
  String get uiMoveDown => 'Переместить вниз';

  @override
  String get uiLowerHalf => 'Нижняя половина';

  @override
  String get uiCenter => 'По центру';

  @override
  String get uiCompatibilityDiagnosis => 'Диагностика совместимости';

  @override
  String get uiVirtualDisplayCreation => 'Создание виртуального дисплея';

  @override
  String get uiOpenASavedAppConfiguration =>
      'Откройте сохраненную конфигурацию приложения';

  @override
  String get uiNoSavedWorkspaces => 'Нет сохраненных рабочих пространств';

  @override
  String get uiInputAndGestures => 'Ввод и жесты';

  @override
  String get uiInputMode => 'Режим ввода';

  @override
  String get uiCancelFullScreen => 'Выйти из полноэкранного режима';

  @override
  String get uiReDiagnosis => 'Повторить диагностику';

  @override
  String get uiRestart => 'Возобновить';

  @override
  String get uiAvailableMemory => 'Доступная память';

  @override
  String get uiDelete => 'Удалить';

  @override
  String get uiYouCanRestoreYourPreviousSession =>
      'Вы можете восстановить предыдущий сеанс';

  @override
  String get uiRight => 'Справа';

  @override
  String get uiRight13 => 'Правая 1/3';

  @override
  String get uiRight23 => 'Справа 2/3';

  @override
  String get uiRightClick => 'Щелчок правой кнопкой';

  @override
  String get uiUpperRight => 'Справа сверху';

  @override
  String get uiLowerRight => 'Справа снизу';

  @override
  String get uiRightHalf => 'Правая половина';

  @override
  String get uiName => 'Название';

  @override
  String get uiLargeScreenFoldable => 'Большой экран/складной';

  @override
  String get uiActualFps => 'Фактический FPS';

  @override
  String get uiExperimentalMultiTouch => 'Экспериментальный мультитач';

  @override
  String get uiExperimentalFeatures => 'Экспериментальные функции';

  @override
  String get uiLeft => 'Слева';

  @override
  String get uiLeft13 => 'Слева 1/3';

  @override
  String get uiLeft13Right23 => 'Слева 1/3 + справа 2/3';

  @override
  String get uiLeft23 => 'Слева 2/3';

  @override
  String get uiLeft23Right13 => 'Слева 2/3 + справа 1/3';

  @override
  String get uiLeftCenterRight => 'Слева / по центру / справа';

  @override
  String get uiUpperLeft => 'Слева сверху';

  @override
  String get uiUpperLeftUpperRightLowerHalf =>
      'Верхний левый, верхний правый, нижняя половина';

  @override
  String get uiLowerLeft => 'Слева снизу';

  @override
  String get uiLeftHalf => 'Левая половина';

  @override
  String get uiDividedIntoLeftAndRight => 'Разделение слева и справа';

  @override
  String get uiSwipeRightWithThreeFingersFromThe =>
      'Проведите тремя пальцами вправо от левого края';

  @override
  String get uiRecoverySession => 'Восстановление сеанса';

  @override
  String get uiEstimatedPowerConsumption => 'Расчетное энергопотребление';

  @override
  String get uiOperationOverlay => 'Панель управления';

  @override
  String get uiShowActionOverlay => 'Показать панель управления';

  @override
  String get uiOperationMenu => 'Меню управления';

  @override
  String get uiThereIsAnExistingSession => 'Обнаружен существующий сеанс';

  @override
  String get uiSaveConfiguration => 'Сохранить конфигурацию';

  @override
  String get uiRestorePrivileges => 'Восстановить разрешения';

  @override
  String get uiChangeToHorizontalHold => 'Переключить в альбомную ориентацию';

  @override
  String get uiPreparationIsRequired => 'Требуется настройка';

  @override
  String get uiPhysicalKeyboard => 'Физическая клавиатура';

  @override
  String get uiPhysicalMouse => 'Физическая мышь';

  @override
  String get uiConditionAndDiagnosis => 'Состояние и диагностика';

  @override
  String get uiPreventsTheScreenFromTurningOffAutomatically =>
      'Предотвращает автоматическое выключение экрана';

  @override
  String get uiDestruction => 'Удалить';

  @override
  String get uiTerminalAndPermissions => 'Устройство и разрешения';

  @override
  String get uiDeviceInformationDesktopModeAccessibility =>
      'Информация об устройстве, режим рабочего стола, специальные возможности';

  @override
  String get uiTerminalResolution => 'Разрешение устройства';

  @override
  String get uiEnd => 'Завершить';

  @override
  String get uiTerminationProcessingCompletedSuccessfully =>
      'Сеанс успешно завершён.';

  @override
  String get uiEdit => 'Изменить';

  @override
  String get uiChangeToPortraitOrientation =>
      'Переключиться на портретную ориентацию';

  @override
  String get uiVerticalHorizontalSwitching =>
      'Портретная / альбомная ориентация';

  @override
  String get uiDisplayOptimization => 'Оптимизация дисплея';

  @override
  String get uiDisplayRefreshRate => 'Частота обновления дисплея';

  @override
  String get uiReproduction => 'Создать копию';

  @override
  String get uiManageLaunchedAppsAndConfigurations =>
      'Управление запускаемыми приложениями и их расположением';

  @override
  String get uiCouldNotStart => 'Не удалось запустить';

  @override
  String get uiLongPress => 'Долгое нажатие';

  @override
  String get uiAutomaticallyUsesMeasuredResolutionForOpenAnd =>
      'Автоматически использует измеренное разрешение для открытых и закрытых состояний.';

  @override
  String get uiStart => 'Начать';

  @override
  String get uiAutomaticSwitchingAccordingToOpenClosedState =>
      'Автоматическое переключение в зависимости от открытого/закрытого состояния.';

  @override
  String get uiOpeningQuote => '«';

  @override
  String get uiDeleteWorkspaceQuestionSuffix =>
      '» — удалить это рабочее пространство?';

  @override
  String get uiAbnormalSessionWarning =>
      'Сеанс завершился в некорректном состоянии.\nНекоторые системные функции Android могут оставаться отключёнными.';

  @override
  String get uiChecking => 'Проверка';

  @override
  String get uiIdle => 'Ожидание';

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
  String get diagnosticLog => 'Журнал работы и диагностика устройства';

  @override
  String get diagnosticLogDescription =>
      'Журналы приложения, проверка возможностей и характеристики устройства';

  @override
  String get loadDiagnosticLog => 'Загрузить отчёт диагностики';

  @override
  String get copyDiagnosticLog => 'Копировать';

  @override
  String get shareDiagnosticLog => 'Поделиться';

  @override
  String get clearDiagnosticLog => 'Очистить журнал';

  @override
  String get deviceReport => 'Отчёт о работе устройства';

  @override
  String get uiCpuTemperature => 'Температура ЦП';

  @override
  String get deviceReportDescription =>
      'Отправить совместимость устройства и функций по электронной почте';

  @override
  String get deviceReportIntro =>
      'Данные устройства собираются автоматически. Выберите результат для каждой функции.';

  @override
  String get reportWorking => 'Работает';

  @override
  String get reportNotWorking => 'Не работает';

  @override
  String get reportUntested => 'Не проверено';

  @override
  String get reportOverall => 'Общий статус';

  @override
  String get reportNotes => 'Другие примечания';

  @override
  String get sendDeviceReport => 'Отправить отчёт по почте';

  @override
  String get reportEmailUnavailable => 'Не удалось открыть почтовое приложение';

  @override
  String get reportTemplateTitle => 'Отчёт о работе Dextop';

  @override
  String get reportNoNotes => 'Нет';

  @override
  String get reportFeatureStartup =>
      'Запуск приложения и обнаружение устройства';

  @override
  String get reportFeatureSession => 'Запуск сеанса Dextop';

  @override
  String get reportFeatureVirtualDisplay => 'Зеркалирование VirtualDisplay';

  @override
  String get reportFeatureWindowManager => 'Зеркалирование WindowManager';

  @override
  String get reportFeatureSurfaceControl => 'Зеркалирование SurfaceControl';

  @override
  String get reportFeatureLandscape => 'Альбомный режим';

  @override
  String get reportFeaturePortrait => 'Портретный режим';

  @override
  String get reportFeatureSecureDisplay => 'Защищённый дисплей';

  @override
  String get reportFeatureLauncher => 'Панель приложений и свободные окна';

  @override
  String get reportFeatureWorkspace =>
      'Сохранение и восстановление рабочих областей';

  @override
  String get reportFeatureCursor => 'Ввод курсором и сенсорной панелью';

  @override
  String get reportFeatureDirectTouch => 'Прямой сенсорный ввод';

  @override
  String get reportFeatureMultiTouch => 'Мультитач-прокрутка и масштабирование';

  @override
  String get reportFeatureGesture => 'Жест оверлея тремя пальцами';

  @override
  String get reportFeatureMouse => 'Физическая мышь';

  @override
  String get reportFeatureKeyboard => 'Физическая клавиатура';

  @override
  String get reportFeatureRouting =>
      'Маршрутизация мыши и клавиатуры между дисплеями';

  @override
  String get reportFeatureFoldable => 'Авторазрешение для складных устройств';

  @override
  String get reportFeaturePerformance => 'Оверлей производительности';

  @override
  String get reportFeatureCleanup =>
      'Завершение сеанса и восстановление Android';
}
