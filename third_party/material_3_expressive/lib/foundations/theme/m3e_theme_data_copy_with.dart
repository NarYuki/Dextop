part of 'm3e_theme_data.dart';

M3EThemeData _m3eDeriveDarkTemplate(M3EThemeData source) {
  return M3EThemeData.dark(seedColor: source.colorScheme.primary).copyWith(
    typography: source.typography,
    iconTheme: source.iconTheme,
    spacing: source.spacing,
    visualDensity: source.visualDensity,
    platform: source.platform,
    useMaterial3: source.useMaterial3,
    splashColor: source.splashColor,
    highlightColor: source.highlightColor,
    keyboardFocusIndicators: source.keyboardFocusIndicators,
    focusRingTheme: source.focusRingTheme,
    appBarTheme: source.appBarTheme,
    badgeTheme: source.badgeTheme,
    bottomSheetTheme: source.bottomSheetTheme,
    buttonTheme: source.buttonTheme,
    cardTheme: source.cardTheme,
    carouselTheme: source.carouselTheme,
    checkboxTheme: source.checkboxTheme,
    chipTheme: source.chipTheme,
    datePickerTheme: source.datePickerTheme,
    dialogTheme: source.dialogTheme,
    dividerTheme: source.dividerTheme,
    dropdownMenuTheme: source.dropdownMenuTheme,
    fabTheme: source.fabTheme,
    fabMenuTheme: source.fabMenuTheme,
    iconButtonTheme: source.iconButtonTheme,
    listTheme: source.listTheme,
    loadingIndicatorTheme: source.loadingIndicatorTheme,
    menuTheme: source.menuTheme,
    navigationBarTheme: source.navigationBarTheme,
    navigationDrawerTheme: source.navigationDrawerTheme,
    navigationRailTheme: source.navigationRailTheme,
    progressIndicatorTheme: source.progressIndicatorTheme,
    radioTheme: source.radioTheme,
    refreshIndicatorTheme: source.refreshIndicatorTheme,
    searchBarTheme: source.searchBarTheme,
    searchViewTheme: source.searchViewTheme,
    segmentedButtonTheme: source.segmentedButtonTheme,
    selectionTheme: source.selectionTheme,
    sideSheetTheme: source.sideSheetTheme,
    sliderTheme: source.sliderTheme,
    snackBarTheme: source.snackBarTheme,
    splitButtonTheme: source.splitButtonTheme,
    switchTheme: source.switchTheme,
    tabTheme: source.tabTheme,
    textFieldTheme: source.textFieldTheme,
    timePickerTheme: source.timePickerTheme,
    buttonGroupTheme: source.buttonGroupTheme,
    toolbarTheme: source.toolbarTheme,
    tooltipTheme: source.tooltipTheme,
  );
}

M3EThemeData _m3eCopyThemeData(
  M3EThemeData source, {
  M3EColorScheme? colorScheme,
  M3ETypography? typography,
  M3ETypeScale? typeScale,
  M3ETypefaceConfig? typeface,
  M3EVariableFontConfig? variableFont,
  String? fontFamily,
  List<String>? fontFamilyFallback,
  String? package,
  List<FontVariation>? fontVariations,
  IconThemeData? iconTheme,
  M3ESpacing? spacing,
  double? visualDensity,
  TargetPlatform? platform,
  bool? useMaterial3,
  Color? splashColor,
  Color? highlightColor,
  bool? keyboardFocusIndicators,
  M3EFocusRingTheme? focusRingTheme,
  M3EAppBarTheme? appBarTheme,
  M3EBadgeTheme? badgeTheme,
  M3EBottomSheetTheme? bottomSheetTheme,
  M3EButtonTheme? buttonTheme,
  M3ECardTheme? cardTheme,
  M3ECarouselTheme? carouselTheme,
  M3ECheckboxTheme? checkboxTheme,
  M3EChipTheme? chipTheme,
  M3EDatePickerTheme? datePickerTheme,
  M3EDialogTheme? dialogTheme,
  M3EDividerTheme? dividerTheme,
  M3EDropdownMenuTheme? dropdownMenuTheme,
  M3EFabTheme? fabTheme,
  M3EFabMenuTheme? fabMenuTheme,
  M3EIconButtonTheme? iconButtonTheme,
  M3EListTheme? listTheme,
  M3ELoadingIndicatorTheme? loadingIndicatorTheme,
  M3EMenuTheme? menuTheme,
  M3ENavigationBarTheme? navigationBarTheme,
  M3ENavigationDrawerTheme? navigationDrawerTheme,
  M3ENavigationRailTheme? navigationRailTheme,
  M3EProgressIndicatorTheme? progressIndicatorTheme,
  M3ERadioTheme? radioTheme,
  M3ERefreshIndicatorTheme? refreshIndicatorTheme,
  M3ESearchBarTheme? searchBarTheme,
  M3ESearchViewTheme? searchViewTheme,
  M3ESegmentedButtonTheme? segmentedButtonTheme,
  M3ESelectionTheme? selectionTheme,
  M3ESideSheetTheme? sideSheetTheme,
  M3ESliderTheme? sliderTheme,
  M3ESnackbarTheme? snackBarTheme,
  M3ESplitButtonTheme? splitButtonTheme,
  M3ESwitchTheme? switchTheme,
  M3ETabTheme? tabTheme,
  M3ETextFieldTheme? textFieldTheme,
  M3ETimePickerTheme? timePickerTheme,
  M3EButtonGroupTheme? buttonGroupTheme,
  M3EToolbarTheme? toolbarTheme,
  M3ETooltipTheme? tooltipTheme,
}) {
  final M3ETypography nextTypography = _resolveCopyWithTypography(
    source,
    typography: typography,
    typeScale: typeScale,
    typeface: typeface,
    variableFont: variableFont,
    fontFamily: fontFamily,
    fontFamilyFallback: fontFamilyFallback,
    package: package,
    fontVariations: fontVariations,
  );

  return M3EThemeData(
    colorScheme: colorScheme ?? source.colorScheme,
    typography: nextTypography,
    iconTheme: iconTheme ?? source.iconTheme,
    spacing: spacing ?? source.spacing,
    visualDensity: visualDensity ?? source.visualDensity,
    platform: platform ?? source.platform,
    useMaterial3: useMaterial3 ?? source.useMaterial3,
    splashColor: splashColor ?? source.splashColor,
    highlightColor: highlightColor ?? source.highlightColor,
    keyboardFocusIndicators:
        keyboardFocusIndicators ?? source.keyboardFocusIndicators,
    focusRingTheme: focusRingTheme ?? source.focusRingTheme,
    appBarTheme: appBarTheme ?? source.appBarTheme,
    badgeTheme: badgeTheme ?? source.badgeTheme,
    bottomSheetTheme: bottomSheetTheme ?? source.bottomSheetTheme,
    buttonTheme: buttonTheme ?? source.buttonTheme,
    cardTheme: cardTheme ?? source.cardTheme,
    carouselTheme: carouselTheme ?? source.carouselTheme,
    checkboxTheme: checkboxTheme ?? source.checkboxTheme,
    chipTheme: chipTheme ?? source.chipTheme,
    datePickerTheme: datePickerTheme ?? source.datePickerTheme,
    dialogTheme: dialogTheme ?? source.dialogTheme,
    dividerTheme: dividerTheme ?? source.dividerTheme,
    dropdownMenuTheme: dropdownMenuTheme ?? source.dropdownMenuTheme,
    fabTheme: fabTheme ?? source.fabTheme,
    fabMenuTheme: fabMenuTheme ?? source.fabMenuTheme,
    iconButtonTheme: iconButtonTheme ?? source.iconButtonTheme,
    listTheme: listTheme ?? source.listTheme,
    loadingIndicatorTheme:
        loadingIndicatorTheme ?? source.loadingIndicatorTheme,
    menuTheme: menuTheme ?? source.menuTheme,
    navigationBarTheme: navigationBarTheme ?? source.navigationBarTheme,
    navigationDrawerTheme:
        navigationDrawerTheme ?? source.navigationDrawerTheme,
    navigationRailTheme: navigationRailTheme ?? source.navigationRailTheme,
    progressIndicatorTheme:
        progressIndicatorTheme ?? source.progressIndicatorTheme,
    radioTheme: radioTheme ?? source.radioTheme,
    refreshIndicatorTheme:
        refreshIndicatorTheme ?? source.refreshIndicatorTheme,
    searchBarTheme: searchBarTheme ?? source.searchBarTheme,
    searchViewTheme: searchViewTheme ?? source.searchViewTheme,
    segmentedButtonTheme: segmentedButtonTheme ?? source.segmentedButtonTheme,
    selectionTheme: selectionTheme ?? source.selectionTheme,
    sideSheetTheme: sideSheetTheme ?? source.sideSheetTheme,
    sliderTheme: sliderTheme ?? source.sliderTheme,
    snackBarTheme: snackBarTheme ?? source.snackBarTheme,
    splitButtonTheme: splitButtonTheme ?? source.splitButtonTheme,
    switchTheme: switchTheme ?? source.switchTheme,
    tabTheme: tabTheme ?? source.tabTheme,
    textFieldTheme: textFieldTheme ?? source.textFieldTheme,
    timePickerTheme: timePickerTheme ?? source.timePickerTheme,
    buttonGroupTheme: buttonGroupTheme ?? source.buttonGroupTheme,
    toolbarTheme: toolbarTheme ?? source.toolbarTheme,
    tooltipTheme: tooltipTheme ?? source.tooltipTheme,
  );
}

M3ETypography _resolveCopyWithTypography(
  M3EThemeData source, {
  M3ETypography? typography,
  M3ETypeScale? typeScale,
  M3ETypefaceConfig? typeface,
  M3EVariableFontConfig? variableFont,
  String? fontFamily,
  List<String>? fontFamilyFallback,
  String? package,
  List<FontVariation>? fontVariations,
}) {
  M3ETypography nextTypography = typography ?? source.typography;
  if (typeScale != null && typography == null) {
    nextTypography = M3ETypography(
      baseline: typeScale,
      emphasized: nextTypography.emphasized,
    );
  }

  if (variableFont != null) {
    final M3EVariableFontConfig config = fontVariations == null
        ? variableFont
        : variableFont.copyWith(
            extraVariations: <FontVariation>[
              ...variableFont.extraVariations,
              ...fontVariations,
            ],
          );
    return nextTypography
        .apply(
          fontFamily: fontFamily,
          typeface: typeface,
          fontFamilyFallback: fontFamilyFallback,
          package: package,
        )
        .applyVariableFont(config);
  }

  if (fontFamily != null ||
      typeface != null ||
      fontFamilyFallback != null ||
      package != null ||
      fontVariations != null) {
    return nextTypography.apply(
      fontFamily: fontFamily,
      typeface: typeface,
      fontFamilyFallback: fontFamilyFallback,
      package: package,
      fontVariations: fontVariations,
    );
  }

  return nextTypography;
}
