import 'package:material_ui/material_ui.dart';

import '../../button_group/models/m3e_button_group_action.dart';
import '../enums/m3e_button_enums.dart';
import '../styles/m3e_button_decoration.dart';
import 'm3e_overflow_strategy.dart';

/// Overflow strategy that renders every action inline without a trigger.
class M3ENoOverflowStrategy extends M3EOverflowStrategy {
  /// M3ENoOverflowStrategy.
  const M3ENoOverflowStrategy();

  @override
  String get id => 'none';

  @override
  Widget buildLayout({
    required BuildContext context,
    required List<M3EButtonGroupAction> actions,
    required int visibleCount,
    required double spacing,
    required Axis direction,
    required M3EButtonStyle style,
    required M3EButtonSize size,
    required M3EButtonDecoration? decoration,
    required bool connected,
    required bool isRtl,
    required Widget Function(
      int index, {
      required bool isFirst,
      required bool isLast,
    })
    buildButton,
  }) {
    final children = M3EOverflowStrategy.buildVisibleButtons(
      count: actions.length,
      isRtl: isRtl,
      buildButton: buildButton,
    );
    return M3EOverflowStrategy.axisFlex(children, direction);
  }

  @override
  Widget? buildOverflowTrigger({
    required BuildContext context,
    required int hiddenCount,
    required M3EButtonStyle style,
    required M3EButtonSize size,
    required M3EButtonDecoration? decoration,
    required bool connected,
    required bool isFirst,
    required bool isLast,
    required VoidCallback onPressed,
    required bool isSelected,
  }) => null;

  @override
  Future<int?> showOverflowMenu({
    required BuildContext context,
    required List<M3EButtonGroupAction> actions,
    required int firstHiddenIndex,
    required int? selectedIndex,
  }) async => null;
}
