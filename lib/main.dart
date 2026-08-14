import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:free_dextop/l10n/app_localizations.dart';
import 'package:free_dextop/analytics_service.dart';
import 'package:free_dextop/app_strings.dart';
import 'package:free_dextop/features_page.dart';
import 'package:free_dextop/setup_page.dart';
import 'package:in_app_update/in_app_update.dart';
import 'package:shared_preferences/shared_preferences.dart';

part 'app_info.dart';
part 'samsung_desktop_settings.dart';
part 'overlay_entry.dart';
part 'app_shell.dart';
part 'home_screen.dart';
part 'home_content.dart';
part 'resolution_ui.dart';
part 'settings_screen.dart';
part 'display_topology.dart';
part 'device_report.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppAnalytics.initialize();
  runApp(const DextopApp());
}
