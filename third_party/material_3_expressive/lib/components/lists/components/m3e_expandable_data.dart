import 'package:flutter/widgets.dart';
import 'package:material_ui/material_ui.dart' show CircleAvatar;

import 'm3e_expandable_expanded.dart';

/// A data container used to configure items for `M3EExpandableList`.
class M3EExpandableData {
  /// The main title text shown in the header.
  final String title;

  /// Optional custom text styles for the title.
  ///
  /// When multiple styles are provided, the first is used (header text does
  /// not change on expand/collapse).
  final List<TextStyle>? titleStyle;

  /// An optional text-only subtitle shown in the header.
  final String? subtitle;

  /// Optional custom text styles for the subtitle.
  ///
  /// When multiple styles are provided, the first is used.
  final List<TextStyle>? subtitleStyle;

  /// Maximum number of lines for the subtitle.
  final int? subtitleMaxLines;

  /// Expanded content: card-styled list or a single freeform child.
  final M3EExpandableExpanded? expanded;

  /// An optional leading widget for the header (e.g., an [Icon] or [CircleAvatar]).
  final Widget? leading;

  /// An optional trailing widget for the header.
  final Widget? trailing;

  /// Creates a data configuration for an expandable item.
  const M3EExpandableData({
    required this.title,
    this.titleStyle,
    this.subtitle,
    this.subtitleStyle,
    this.subtitleMaxLines,
    this.expanded,
    this.leading,
    this.trailing,
  }) : assert(
         subtitle != null || expanded != null,
         'Provide a subtitle and/or expanded content.',
       );
}
