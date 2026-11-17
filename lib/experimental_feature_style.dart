import 'package:flutter/material.dart';

/// The compact row used by the maximum-refresh-rate experimental setting.
const experimentalFeatureTileTheme = ListTileThemeData(
  dense: true,
  minTileHeight: 64,
  minVerticalPadding: 6,
  contentPadding: EdgeInsets.symmetric(horizontal: 16),
  visualDensity: VisualDensity(vertical: -1),
);
