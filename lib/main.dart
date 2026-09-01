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
import 'package:free_dextop/embedded_binder_setup.dart';
import 'package:in_app_update/in_app_update.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:archive/archive.dart';
import 'package:file_picker/file_picker.dart';
import 'package:share_plus/share_plus.dart';

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
part 'keyboard_themes.dart';
part 'keyboard_settings.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // DPI and pointer acceleration were removed from the virtual-mouse
  // implementation. Clear values from older installations before any screen
  // or service can read them, including users who never open Mouse settings.
  final preferences = await SharedPreferences.getInstance();
  await preferences.remove('virtual_mouse_dpi');
  await preferences.remove('virtual_mouse_acceleration');
  // Keep the host display awake unless the user has explicitly opted out.
  if (!preferences.containsKey('keep_awake_during_session')) {
    await preferences.setBool('keep_awake_during_session', true);
  }
  await AppAnalytics.initialize();
  runApp(const DextopApp());
}
