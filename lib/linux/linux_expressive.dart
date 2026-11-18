import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart' as mui;

import '../expressive/dextop_expressive.dart';

/// Hosts Material 3 Expressive widgets inside Dextop's app.
class LinuxExpressive extends StatelessWidget {
  const LinuxExpressive({super.key, required this.child});

  final Widget child;

  static Color get seed => DextopExpressive.seed;
  static set seed(Color value) => DextopExpressive.seed = value;

  @override
  Widget build(BuildContext context) => DextopExpressive(child: child);
}

bool linuxWide(BuildContext context) => dextopWide(context);

Route<T> linuxRoute<T>(Widget page) => dextopRoute<T>(page);

const LocalizationsDelegate<mui.MaterialLocalizations>
linuxMaterialUiLocalizations = dextopMaterialUiLocalizations;

String linuxText(BuildContext context, String key) => dextopText(context, key);
