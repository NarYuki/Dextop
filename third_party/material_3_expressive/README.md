# Material 3 Expressive

A faithful Flutter implementation of the
[Material 3](https://m3.material.io/components) **Expressive** component set.

Every widget is exposed as a direct `M3E*` class with spring-driven press
feedback, shape morphing, and hover/focus/press state layers. Design tokens
(color, typography, motion, shapes, elevation) are provided through
`M3ETheme`.

Runtime dependencies are intentionally small — see
[Dependencies](#dependencies) for the packages declared in
[`pubspec.yaml`](pubspec.yaml).

### Samples

|                                                                      Actions                                                                      |                                                                            Selection                                                                             |
|:-------------------------------------------------------------------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Actions — buttons, FABs, and button groups](https://raw.githubusercontent.com/paadevelopments/material_3_expressive/main/assets/asset_1_v2.gif) | ![Selection — segmented controls, chips, menus, and sliders](https://raw.githubusercontent.com/paadevelopments/material_3_expressive/main/assets/asset_2_v2.gif) |

|                                                                       Containment                                                                       |                                                                        Navigation                                                                         |
|:-------------------------------------------------------------------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Containment — pickers, cards, lists, and dialogs](https://raw.githubusercontent.com/paadevelopments/material_3_expressive/main/assets/asset_3_v2.gif) | ![Navigation — app bars, tabs, nav bar, and toolbars](https://raw.githubusercontent.com/paadevelopments/material_3_expressive/main/assets/asset_4_v2.gif) |

|                                                                            Feedback                                                                            |
|:--------------------------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Feedback — progress, badges, text fields, and snackbars](https://raw.githubusercontent.com/paadevelopments/material_3_expressive/main/assets/asset_5_v2.gif) |

## Example app

Try the live gallery on the web:
[paadevelopments.github.io/material_3_expressive](https://paadevelopments.github.io/material_3_expressive/).

An interactive gallery demonstrating **all 44 widgets** also lives in the
[`example/`](example/) directory (same build as the live demo). It groups
components the same way as the official Material 3 catalog, with a live
playground per component under [`example/lib/pages/playground/`](example/lib/pages/playground/).
Each tab lists its components (a card grid on wide windows). Opening one shows
its preview screen: the component sits in its real place (app bars on top, nav
bars at the bottom, FABs in the FAB slot), next to a controls pane on wide
windows or behind a **Tap for controls** banner that opens a full-height
bottom sheet on narrow ones. Only the controls that apply to the selected
variant appear, and a **Code** tab shows paste-ready Dart that tracks them
(copy to clipboard).

| Tab | Playgrounds | Components |
| --- | ----------- | ---------- |
| **Do** | [`playground/do/`](example/lib/pages/playground/do/) | Buttons, FABs, FAB menu, groups, segmented & split buttons |
| **Pick** | [`playground/pick/`](example/lib/pages/playground/pick/) | Checkbox, radio, switch, chips, dropdown, slider (incl. wavy), pickers |
| **View** | [`playground/view/`](example/lib/pages/playground/view/) | Cards, carousel, lists, selection, divider, dialogs, sheets, shapes, typography |
| **Nav** | [`playground/nav/`](example/lib/pages/playground/nav/) | App bars (incl. search), tabs, nav bar/rail/drawer, toolbar, menu |
| **Find** | [`playground/find/`](example/lib/pages/playground/find/) | Badges, progress, refresh, tooltip, snackbar, inputs |

The gallery shell in [`example/lib/main.dart`](example/lib/main.dart) uses
`M3EMaterialApp` with adaptive theming, a light/dark toggle, and a palette
action that opens [`theme_config_page.dart`](example/lib/pages/theme_config_page.dart)
(auto theming, dynamic color, five seed colors, and font family — default
**Google Sans Flex**). For type scale, variable-font axes, and style
conversion, use the **Typography** playground under the View tab.

```bash
cd example
flutter run
```

## Features

- **44 widgets** across 39 component modules, covering Actions, Selection,
  Containment, Navigation, and Feedback (communication + text input).
- **Direct component API** — construct each `M3E*` widget directly; enums and
  models are exported from a single library import. Action surfaces accept
  optional gradient decorations (fill, foreground, overlay, outline).
- **Expressive motion & interaction** — spring physics (via [`motor`](https://pub.dev/packages/motor)),
  shape morphing, per-destination selection indicators, shared haptics (`M3EHaptics`), and
  proper state layers on every interactive surface.
- **Design token foundations** — color schemes, typography, motion, shapes
  (including [`material_new_shapes`](https://pub.dev/packages/material_new_shapes)
  morph polygons), spacing and radius via `M3EDimensions`, elevation, haptics,
  and state layers via the `M3ETheme` inherited widget.
- **Interactive example gallery** — run locally from [`example/`](example/), or
  open the [live web demo](https://paadevelopments.github.io/material_3_expressive/).

## Requirements

| Tool    | Version    |
| ------- | ---------- |
| Flutter | `>= 3.47.0` |
| Dart    | `^3.13.0`  |

## Migrating to `material_ui`

This package uses [`material_ui`](https://pub.dev/packages/material_ui) `^1.8.0`
for Material widgets (`MaterialApp`, `ThemeData`, `ColorScheme`, and the rest of
the Material library). **Do not import** `package:flutter/material.dart`.

```dart
import 'package:material_ui/material_ui.dart';
import 'package:material_3_expressive/material_3_expressive.dart';
```

Apps that still import `package:flutter/material.dart` should switch those
imports to `package:material_ui/material_ui.dart`. Flutter **3.47.0 or newer**
(Dart **3.13.0+**) is required (`material_ui` will not resolve on older SDKs).

`material_ui` exports its own `RoundedPolygon` and `CornerRounding`, which clash
with the [`material_new_shapes`](https://pub.dev/packages/material_new_shapes)
ones this package re-exports. If you use either name, hide it from the
`material_ui` import:

```dart
import 'package:material_ui/material_ui.dart' hide CornerRounding, RoundedPolygon;
```

`ColorScheme.harmonized()` / `Color.harmonizeWith()` come from
[`dynamic_color`](https://pub.dev/packages/dynamic_color) `^2.1.0` (re-exported
through this package). Prefer those APIs rather than a local duplicate.

## What's new in 1.2.0

Summary of updates since 1.1.7 (details in [`CHANGELOG.md`](CHANGELOG.md)):

- **Dependencies** — `material_ui` raised to `^1.8.0`. If you import both
  packages and use `RoundedPolygon` or `CornerRounding`, add
  `hide CornerRounding, RoundedPolygon` to the `material_ui` import.
- **Buttons** — an `M3EButton` with only an `icon` or only a `label` renders
  that content.
- **Button group, segmented buttons** — nothing takes focus on mount unless an
  action sets `autofocus`. Icon-only overflow menu items use their
  `semanticLabel` or `tooltip` as the title, and tapping a connected group no
  longer shifts its gaps.
- **Search** — the view field takes focus as soon as the view starts opening.
- **Date pickers** — `autofocus` on `M3EInputDatePickerFormField` focuses the
  field.
- **Theme** — iOS status bar text now contrasts correctly with the theme and
  with scrims.

## Installation

Add the package to your `pubspec.yaml`:

```yaml
dependencies:
  material_3_expressive: ^1.2.0
```

Then fetch it:

```bash
flutter pub get
```

Or add it from the command line:

```bash
flutter pub add material_3_expressive
```

## Dependencies

External packages declared in [`pubspec.yaml`](pubspec.yaml):

| Package | Role in this library |
| ------- | -------------------- |
| [`flutter`](https://api.flutter.dev/) | SDK — widgets, painting, gestures |
| [`material_ui`](https://pub.dev/packages/material_ui) | Official Material widget library (`MaterialApp`, `ThemeData`, `ColorScheme`) |
| [`collection`](https://pub.dev/packages/collection) | Small collection helpers used by component logic |
| [`dynamic_color`](https://pub.dev/packages/dynamic_color) | Platform dynamic / Material You seed colors for `M3EMaterialApp` (`dynamicColoring`); `ColorScheme.harmonized` / `Color.harmonizeWith` (2.x, `material_ui`) |
| [`motor`](https://pub.dev/packages/motor) | Unified motion API — physics springs and curves that drive expressive morphs and selection indicators |
| [`material_new_shapes`](https://pub.dev/packages/material_new_shapes) | Expressive `RoundedPolygon` morph shapes (`M3EMaterialNewShapes`, `M3EShapeKind`, `M3EShapeClipper`, `M3EShapeContainer`) used by loading / shape-driven surfaces |

Dev-only: [`flutter_lints`](https://pub.dev/packages/flutter_lints), [`flutter_test`](https://api.flutter.dev/flutter/flutter_test/flutter_test-library.html), and [`custom_lint`](https://pub.dev/packages/custom_lint).

## Quick start

Import the library — a single import exposes every component and foundation:

```dart
import 'package:material_3_expressive/material_3_expressive.dart';
```

### Recommended: `M3EMaterialApp`

Wrap your app in `M3EMaterialApp` for adaptive theming, dynamic color, and
Material `ThemeMode` alignment (same pattern as the example gallery):

```dart
void main() => runApp(const MyApp());

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return M3EMaterialApp(
      title: 'My App',
      data: M3EThemeData.light(seedColor: const Color(0xFF6750A4)),
      autoTheming: true,
      dynamicColoring: true,
      drawUnderSystemBars: true, // transparent system bars, edge-to-edge layout
      home: const HomePage(),
    );
  }
}
```

Surfaces draw behind the OS navigation bar; components such as `M3ENavigationBar`
keep interactive content above the gesture area via `viewPadding`.

Actionable controls draw an outset **keyboard focus ring** when focused via
Tab (hidden after pointer interaction). Override globally with
`focusRingTheme` / `keyboardFocusIndicators` on `M3EThemeData`, or try the
example **Focus rings** playground (View tab):

```dart
M3EThemeData.light(seedColor: seed).copyWith(
  keyboardFocusIndicators: true,
  focusRingTheme: const M3EFocusRingTheme(
    color: Color(0xFF6750A4),
    width: 2,
    gap: 2,
  ),
);
```

### Alternative: `M3ETheme` subtree

If you already have an app shell, wrap any subtree in `M3ETheme`:

```dart
M3ETheme(
  data: M3EThemeData.light(seedColor: const Color(0xFF6750A4)),
  child: myApp,
);
```

Use `M3EThemeData.dark(...)` for a dark scheme. If no `M3ETheme` is found,
components fall back to a default light theme. Unlike `M3EMaterialApp`, this
path does not set a system bar style, so wrap your app in an
`AnnotatedRegion<SystemUiOverlayStyle>` (for example
`SystemUiOverlayStyle.dark` for a light theme) to restore it after modals close.

### Accessing theme tokens

```dart
final theme = M3ETheme.of(context);
final scheme = theme.colorScheme;
final type = theme.typeScale;

// Toggle brightness at runtime (requires adaptive M3EMaterialApp / M3EThemeScope)
M3ETheme.controllerOf(context)?.toggleBrightness(
  fallback: theme.brightness,
  autoTheming: true,
);
```

## Theming

`M3EThemeData` bundles expressive tokens and per-component themes:

```dart
final theme = M3EThemeData.light(
  seedColor: const Color(0xFF6750A4),
);

// Override a single component theme
final custom = theme.copyWith(
  buttonTheme: M3EButtonTheme.defaults.copyWith(/* ... */),
);
```

Key properties on `M3EThemeData`:

- `colorScheme` — `M3EColorScheme` with M3 semantic roles
- `typography` — `M3ETypography` with baseline and emphasized scales (30 styles)
- `typeScale` — baseline alias for `typography.baseline` (used by components)
- `spacing`, `visualDensity`, per-component `*Theme` extensions
- `focusRingTheme` / `keyboardFocusIndicators` — keyboard focus chrome
- Per-component `M3ESpring` motion fields on themes such as `switchTheme`,
  `fabMenuTheme`, `navigationRailTheme`, `listTheme`, `toolbarTheme`,
  `sliderTheme`, `iconButtonTheme`, `checkboxTheme`, and
  `refreshIndicatorTheme` (defaults preserve prior hard-coded springs)

The M3 type system has 15 baseline and 15 emphasized roles. Use emphasized
styles for selection, actions, and editorial hierarchy:

```dart
final theme = M3ETheme.of(context);
Text('Headline', style: theme.typography.emphasized.headlineSmall);
```

Shared typography (font family, fallback, package, size factor/delta, color,
decoration, brand/plain typefaces, and variable-font axes) is applied through
`M3ETypography.apply` or theme `copyWith` — not a full `TextStyle`:

```dart
final themed = M3EThemeData.light(seedColor: seed).copyWith(
  typography: M3ETypography.material3().apply(
    fontFamily: 'Roboto Flex',
    fontVariations: M3ETypeVariations.graded.variations,
  ),
);

// Sugar: same result without building typography by hand
final alsoThemed = M3EThemeData.light(seedColor: seed).copyWith(
  fontFamily: 'Roboto Flex',
  fontVariations: M3ETypeVariations.graded.variations,
);

// Per-role variable-font axes (opsz, wght, split ROND, emphasized GRAD)
final variable = M3EThemeData.light(seedColor: seed).copyWith(
  fontFamily: 'Roboto Flex',
  typeScaleMode: M3ETypeScaleMode.variable,
  variableFont: const M3EVariableFontConfig(
    global: M3EVariableFontAxes(wght: 500, opsz: 16),
    brand: M3EVariableFontAxes(rond: 25),
    body: M3EVariableFontAxes(rond: 50),
  ),
);

// Convert any TextStyle to a spec variant
final converted = M3ETypeStyleConversion.toVariant(
  Theme.of(context).textTheme.bodyLarge!,
  variant: M3ETypeScaleVariant.emphasized,
  role: M3ETypeRole.bodyLarge,
);

// Customize token fields before building a TextStyle
final tokens = M3ETypeStyleTokens.fromTextStyle(style).copyWith(
  letterSpacing: 0.25,
);
final custom = tokens.toTextStyle(fontFamily: 'Roboto Flex');
```

`M3EMaterialApp` additionally supports `autoTheming` (platform brightness) and
`dynamicColoring` (OS seed color on supported platforms — Material You primary
on Android 12+, accent color on desktop — with schemes generated via
`ColorScheme.fromSeed`). Pass `fontFamily` / `fontFamilyFallback` /
`fontVariations`, `typeScaleMode`, `typeface`, or `variableFont` to apply
type-scale knobs at the shell. Use [`buildM3EThemeDefaults()`](lib/foundations/theme/m3e_theme_defaults.dart)
to assemble a full [`M3EThemeData`](lib/foundations/theme/m3e_theme_data.dart)
from core tokens, or [`M3EDynamicColorHost`](lib/foundations/theme/m3e_dynamic_color_host.dart)
when you need device dynamic color outside `M3EMaterialApp`:

```dart
M3EMaterialApp(
  data: M3EThemeData.light(seedColor: seed),
  fontFamily: 'Roboto Flex',
  typeScaleMode: M3ETypeScaleMode.variable,
  variableFont: const M3EVariableFontConfig(),
  home: const HomePage(),
);
```

`M3ETypeVariations` is an enum of Roboto Flex axis presets (`.variations`):
regular, graded (alias for the weight+grade preset formerly named emphasized),
condensed, extra condensed, wide, extra wide, and round. This is not the M3
emphasized type scale — use `theme.typography.emphasized` for that. Static
and mono fonts ignore axes they do not define. The shell projects the baseline
type scale onto `ThemeData.textTheme` and `DefaultTextStyle`.

## Components

<!-- markdownlint-disable MD051 -->

- [Actions](#actions)
- [Selection](#selection)
- [Containment](#containment)
- [Navigation](#navigation)
- [Feedback](#feedback)
- [Modal surfaces](#modal-surfaces)

<!-- markdownlint-enable MD051 -->

Every component is a widget you construct directly. Snippets below use a single
import. Stateful controls show `// in State` where a `setState` wrapper is
needed.

---

### Actions

> See also: [`example/lib/pages/playground/do/`](example/lib/pages/playground/do/) (Do tab)

#### M3EButton

Text button in five sizes with a press shape morph and the sparkle ripple.
Styles are elevated, filled, tonal, outlined and text. `isSelected` turns it
into a toggle that morphs round to square, with optional `selectedIcon` and
`selectedLabel` (not on text buttons). State layers are **0.08** hover and
**0.1** focus and press. Gradients and per-state colors go on
`M3EButtonDecoration`; customize defaults on `M3EButtonTheme`.

```dart
M3EButton(
  style: M3EButtonStyle.elevated,
  onPressed: () {},
  child: const Text('Elevated'),
);

// Toggle (in State)
M3EButton.filled(
  icon: const Icon(M3EIcons.favorite_border),
  selectedIcon: const Icon(M3EIcons.favorite),
  isSelected: isFavorite,
  onPressed: () => setState(() => isFavorite = !isFavorite),
);
```

Keyboard: Tab, then Space or Enter.

#### M3EIconButton

Icon-only button in filled (default), tonal, outlined and standard variants.
Toggle it with `isSelected` and `selectedIcon`. Customize it on
`M3EIconButtonTheme`.

```dart
M3EIconButton(
  icon: const Icon(M3EIcons.edit),
  onPressed: () {},
);

// Toggle (in State)
M3EIconButton(
  icon: const Icon(M3EIcons.add),
  selectedIcon: const Icon(M3EIcons.check),
  isSelected: isFavorite,
  onPressed: () => setState(() => isFavorite = !isFavorite),
);
```

Keyboard: Tab, then Space or Enter.

#### M3EFab

Floating action button in `small` **40**, `regular` **56**, `medium` **80**
(default) and `large` **96**. `M3EFabController` with
`M3EFabScrollVisibility` hides it on scroll, and `openBuilder` opens a
full-screen container transform. Customize it on `M3EFabTheme`.

```dart
M3EFab(
  icon: const Icon(M3EIcons.add),
  size: M3EFabSize.large,
  color: M3EFabColor.tertiary,
  openBuilder: (context) => const ComposePage(), // optional transform
  onPressed: () {},
);
```

Keyboard: Tab, then Space or Enter.

#### M3EExtendedFab

FAB with a required label, in `small` **56** (default), `medium` **80** and
`large` **96**. `M3EExtendedFabController` with
`M3EExtendedFabScrollVisibility` hides it on scroll, and `openBuilder` opens a
container transform. Customize it on `M3EExtendedFabTheme`.

```dart
M3EExtendedFab(
  label: 'Compose',
  icon: const Icon(M3EIcons.edit),
  size: M3EExtendedFabSize.medium,
  onPressed: () {},
);
```

Keyboard: Tab, then Space or Enter.

#### M3EFabMenu

Speed-dial menu of **2–6** items. The trigger morphs into a **56** close
button, and back closes the menu before the route. Customize it on
`M3EFabMenuTheme`.

```dart
M3EFabMenu(
  expandIcon: const Icon(M3EIcons.add),
  collapseIcon: const Icon(M3EIcons.close),
  items: [
    M3EFabMenuItem(icon: const Icon(M3EIcons.edit), label: 'Note', onPressed: () {}),
    M3EFabMenuItem(icon: const Icon(M3EIcons.schedule), label: 'Reminder', onPressed: () {}),
  ],
);
```

Keyboard: Tab walks the items. Escape closes.

#### M3EButtonGroup

Standard or connected group of `M3EButton` actions with single or multi
selection. Density changes the height, not the gap. Customize it on
`M3EButtonGroupTheme`.

```dart
// in State
M3EButtonGroup(
  type: M3EButtonGroupType.connected,
  selectedIndex: groupIndex,
  onSelectedIndexChanged: (i) => setState(() => groupIndex = i),
  actions: const [
    M3EButtonGroupAction(label: Text('Day')),
    M3EButtonGroupAction(label: Text('Week')),
    M3EButtonGroupAction(label: Text('Month')),
  ],
);
```

Keyboard: Tab, then Space or Enter.

#### M3ESegmentedButton

Outlined single or multi-select control with **2–5** segments, **40** tall
with a **48** target, and a **1** **outline** border. Density lowers the
height. Customize it on `M3ESegmentedButtonTheme`.

```dart
// in State
M3ESegmentedButton<String>(
  segments: const [
    M3ESegment(value: 'list', label: 'List'),
    M3ESegment(value: 'grid', label: 'Grid'),
  ],
  selected: viewMode,
  onSelectionChanged: (v) => setState(() => viewMode = v),
);
```

Keyboard: Tab, then Space or Enter.

#### M3ESplitButton

A primary action and a menu trigger, **2** apart. The menu comes from `items`
or `m3eMenuBuilder`, and back closes it before the route. Customize it on
`M3ESplitButtonTheme`.

```dart
M3ESplitButton<String>(
  label: 'Save',
  leadingIcon: M3EIcons.check,
  onPressed: () {},
  onSelected: (value) {},
  items: const [
    M3ESplitButtonItem(value: 'draft', child: Text('Save as draft')),
    M3ESplitButtonItem(value: 'copy', child: Text('Save a copy')),
  ],
);
```

Keyboard: Tab, then Space or Enter. Escape closes the menu.

---

### Selection

> See also: [`example/lib/pages/playground/pick/`](example/lib/pages/playground/pick/) (Pick tab)

#### M3ECheckbox

Checkbox with an **18** box, **2** corners, a **40** state layer and a **48**
target, with optional `tristate` and `label`. Customize it on
`M3ECheckboxTheme`.

```dart
// in State
M3ECheckbox(
  value: checked,
  label: const Text('Remember me'),
  onChanged: (v) => setState(() => checked = v),
);
```

Keyboard: Tab, then Space or Enter.

#### M3ERadio

Radio with a **20** icon, a **40** state layer and a **48** target. Wrap
options in `M3ERadioGroup` for arrow-key selection. Customize it on
`M3ERadioTheme`.

```dart
// in State
M3ERadioGroup<String>(
  groupValue: plan,
  groupLabel: 'Plan',
  onChanged: (v) => setState(() => plan = v),
  child: M3ERadio<String>(
    value: 'pro',
    groupValue: plan,
    label: const Text('Pro'),
    onChanged: (v) => setState(() => plan = v),
  ),
);
```

Keyboard: Tab enters the selected radio. Arrows move, select and wrap.

#### M3ESwitch

Switch with a **52×32** track. The handle is **16** off, **24** on or with an
icon, and **28** pressed. Dragging past the midpoint toggles it. Customize it
on `M3ESwitchTheme`.

```dart
// in State
M3ESwitch(
  value: wifiEnabled,
  selectedIcon: const Icon(M3EIcons.check),
  onChanged: (v) => setState(() => wifiEnabled = v),
);
```

Keyboard: Tab, then Space or Enter.

#### M3EChip

Assist, filter, input and suggestion chips, **32** tall with **8** corners.
`M3EChipGroup` moves focus between chips with the arrow keys. Customize them
on `M3EChipTheme`.

```dart
M3EChip(
  label: 'Assist',
  leading: const Icon(M3EIcons.edit),
  onPressed: () {},
);

// Filter chip (in State)
M3EChip(
  label: 'Flutter',
  type: M3EChipType.filter,
  selected: chips.contains('flutter'),
  onPressed: () => toggleChip('flutter'),
);
```

Keyboard: Tab, then Space or Enter. In a group, arrows move. Backspace or
Delete removes an input chip.

#### M3EDropdownMenu

Dropdown for one or many values, with optional search, a selection `limit`
and async items (`.future`). Back closes the panel before the route. Customize
it on `M3EDropdownMenuTheme`.

```dart
M3EDropdownMenu<String>(
  singleSelect: true,
  items: const [
    M3EDropdownItem(label: 'Flutter', value: 'flutter'),
    M3EDropdownItem(label: 'Dart', value: 'dart'),
  ],
  fieldStyle: const M3EDropdownFieldStyle(hintText: 'Choose a framework'),
  onSelectionChanged: (items) {},
);
```

Keyboard: Space or Enter opens. Arrows move in the panel. Escape closes.

#### M3ESlider

Value or range slider, also centered, wavy and vertical. Sizes `xs`–`xl` scale
the track (**16–96**) and handle (**44–108**) together. The active track and
handle are **primary**, the inactive track **secondary container**, and
`divisions` adds stops. Customize it on `M3ESliderTheme`.

```dart
// in State
M3ESlider(
  value: volume,
  size: M3ESliderSize.l,
  divisions: 5,
  onChanged: (v) => setState(() => volume = v),
);

M3ERangeSlider(
  values: range,
  onChanged: (v) => setState(() => range = v),
);
```

Keyboard: arrows step. Page Up and Page Down jump. Home and End go to the ends.

#### M3EDatePicker

Inline calendar (`M3ECalendarDatePicker`) and dialogs for one date or a range,
with a year grid and text input. The inline calendar is **328** wide.
Customize it on `M3EDatePickerTheme`.

```dart
// Inline (in State)
M3ECalendarDatePicker(
  initialDate: date,
  firstDate: DateTime(2020),
  lastDate: DateTime(2030),
  onDateChanged: (v) => setState(() => date = v),
);

// Dialog; use showRange for a range
final picked = await M3EDatePicker.show(
  context,
  initialDate: date,
  firstDate: DateTime(2020),
  lastDate: DateTime(2030),
);
```

Keyboard: Tab, then Space or Enter. Left and Right change the month.

#### M3ETimePicker

Dialog and inline dial (**256**) for a time of day. Customize it on
`M3ETimePickerTheme`.

```dart
final M3ETime? picked = await M3ETimePicker.show(context, initialTime: time);

// Inline (in State)
M3EDialTimePicker(
  value: time,
  onChanged: (v) => setState(() => time = v),
);
```

Keyboard: Tab, then Enter moves focus on.

---

### Containment

> See also: [`example/lib/pages/playground/view/`](example/lib/pages/playground/view/) (View tab)

#### M3ECard

Elevated, filled and outlined card with **16** content padding and a
**secondary** **3** focus ring. It has optional media, headline, supporting
text, actions and overflow slots, a swipe action, and an `openBuilder`
container transform. `M3ECardGroup` lays out and reorders a set of cards.
Customize it on `M3ECardTheme`.

```dart
M3ECard(
  variant: M3ECardVariant.outlined,
  media: Image.network(imageUrl, fit: BoxFit.cover),
  headline: const Text('Weekend trip'),
  supportingText: const Text('12 photos · 3 people'),
  actions: M3EButton.text(onPressed: () {}, child: const Text('Share')),
  onPressed: () {},
);
```

Keyboard: Tab, then Space or Enter. Left and Right reveal a swipe action, and
Escape hides it.

#### M3ECarousel

Multi-browse, uncontained, multi-aspect, hero and full-screen carousels,
horizontal or vertical (`axis`). Items are `M3ECarouselItem`, `onChange`
reports the focal item, and `M3ECarouselController` steps, jumps or opens the
show-all list. Customize it on `M3ECarouselTheme`.

```dart
M3ECarousel(
  type: M3ECarouselType.hero,
  children: [
    for (final url in imageUrls)
      M3ECarouselItem(
        image: Image.network(url, fit: BoxFit.cover),
        title: const Text('Title'),
        showScrim: const M3ECarouselScrim.gradient(), // fades under the text
        onTap: () {},
      ),
  ],
);
```

Keyboard: Tab or arrows move between items. Space or Enter opens one.

#### M3EListItem

List row with a headline, supporting text, and leading and trailing slots.
Rows are **56**, **72** and **88** tall for one, two and three lines, with
**16** side padding. `variant` and `border` style a standalone row. Customize
it on `M3EListItemTheme`.

```dart
M3EListItem(
  headline: 'Wireless charging',
  supportingText: 'On · Fast charge enabled',
  leading: const Icon(M3EIcons.schedule),
  trailing: const Icon(M3EIcons.chevron_right),
  onTap: () {},
);
```

Keyboard: Tab, then Space or Enter.

#### M3EList

The list widget, with segmented (default) or standard corners. Rows opt into
swipe actions, expansion, sub-lists (a nested `M3EList`) and container
transforms, and the list adds selection and drag-to-reorder. Expanded rows
fill **surface container high**. Use `.scrollable` for a lazy list and
`.sliver` in a `CustomScrollView`. Customize it on `M3EListTheme`.

```dart
M3EList(
  variant: M3ECardVariant.outlined,
  itemCount: 3,
  onTap: (index) {},
  itemBuilder: (context, index) => M3EListItem(
    headline: 'Inbox $index',
    leading: const Icon(M3EIcons.schedule),
  ),
);
```

Keyboard: the list is one Tab stop. Arrows move through rows, row actions and
sub-rows. Space or Enter activates.

#### M3ESelection

Multi-select host: an `M3ESelectionAppBar` swaps its `idle` bar for a
contextual bar with a count, select-all and `actions` while rows are selected,
over any list body. Selected rows use `selectedColor`. Customize it on
`M3ESelectionTheme`.

```dart
final selection = M3ESelectionController();

M3ESelection(
  controller: selection,
  itemCount: items.length,
  appBar: M3ESelectionAppBar(
    idle: M3EAppBar.top(titleText: 'Files'),
    actions: [
      M3EIconButton(icon: const Icon(M3EIcons.delete), onPressed: () {}),
    ],
  ),
  body: M3EList.scrollable(
    itemCount: items.length,
    selection: true,
    selectionController: selection,
    itemBuilder: (context, i) => M3EListItem(headline: items[i]),
  ),
);
```

Keyboard: Tab, then Space or Enter selects a row.

#### M3EDivider

**1** **outline variant** line, full width unless `inset` or `outerMargin` is
set, horizontal or vertical. Customize it on `M3EDividerTheme`.

```dart
const M3EDivider();

const M3EDivider(axis: M3EDividerAxis.vertical);
```

#### M3EDialog

Basic and full-screen dialogs. A basic dialog is **280–560** wide on
**surface container high**, with **28** corners and **24** padding, over a
**0.32** scrim. The headline and actions stay pinned while content scrolls.
`showAdaptive` is full-screen below **600**. Customize it on `M3EDialogTheme`.

```dart
M3EDialog.show<void>(
  context,
  dialog: M3EDialog(
    title: 'Reset settings?',
    content: const Text('This restores default values.'),
    actions: [
      M3EButton.text(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
      M3EButton.text(onPressed: () => Navigator.pop(context), child: const Text('Reset')),
    ],
  ),
);

// Full screen below 600dp, basic dialog above.
M3EDialog.showAdaptive<void>(
  context,
  title: 'Create album',
  content: const Text('Album details'),
  confirmLabel: 'Save',
  onConfirm: () => Navigator.pop(context),
);
```

Keyboard: Tab and Shift+Tab stay inside. Space or Enter activates. Escape
closes.

#### M3EBottomSheet

Modal and standard bottom sheets, full width up to **640**, on **surface
container low**, with **28** top corners and a **32×4** drag handle. A modal
sheet opens over a **0.32** scrim at up to half the screen and can be pulled
up to its content height. `.standard` sits next to the content without a
scrim. Customize it on `M3EBottomSheetTheme`.

```dart
// Modal
M3EBottomSheet.show<void>(
  context,
  builder: (context) => const ShareTargets(),
);

// Standard, driven by a controller
final controller = M3EBottomSheetController();
M3EBottomSheet.standard(
  controller: controller,
  previewHeight: 64,
  child: const PlacesList(),
);
```

Keyboard: Tab focuses the handle, and Space or Enter cycles the heights.
Escape closes a modal sheet.

#### M3ESideSheet

Modal and standard side sheets on the end edge, **256** wide (up to **400**).
A modal sheet is on **surface container low** with **16** corners over a
**0.32** scrim. A standard sheet is on **surface** and pushes the content.
`M3ESideSheetLayout` makes it modal below **600**. Customize it on
`M3ESideSheetTheme`.

```dart
// Modal
M3ESideSheet.show<void>(
  context,
  title: 'Filters',
  body: const FilterList(),
);

// Standard next to your content, modal on compact windows
M3ESideSheetLayout(
  controller: controller,
  body: const Inbox(),
  sheet: const M3ESideSheet.standard(title: 'Details', body: Details()),
);
```

Keyboard: Tab moves through the icons, content and actions. Escape closes a
modal sheet.

---

### Navigation

> See also: [`example/lib/pages/playground/nav/`](example/lib/pages/playground/nav/) (Nav tab)

#### M3EAppBar

Top, search, sliver and bottom app bars. The small bar is **64**, flexible
medium **112** and large **120** (taller with a subtitle), and docked bars pad
for the system bars (`safeArea`). `hideMode` and `M3EAppBarController`
collapse or hide the bar on scroll. For a `.top` or `.search` bar that
collapses, set `extendBodyBehindAppBar: true` on the `Scaffold` so the bar
moves with the content. Customize it on `M3EAppBarTheme`.

```dart
Scaffold(
  extendBodyBehindAppBar: true,
  appBar: M3EAppBar.top(
    titleText: 'Inbox',
    variant: M3EAppBarVariant.mediumFlexible,
    actions: const [Icon(M3EIcons.search)],
  ),
  body: ListView(children: messages),
);

// In a CustomScrollView
M3EAppBar.sliver(titleText: 'Inbox');
```

Keyboard: Tab, then Space or Enter on the actions.

#### M3ETabs

Primary and secondary tab bars, **48** tall (**64** for primary icon and
label), with a **3** (primary) or **2** (secondary) indicator. `M3ETabsView`
keeps a swipeable body in sync, and `.sliver` scrolls away with content.
Customize it on `M3ETabTheme`.

```dart
// in State
M3ETabs(
  selectedIndex: tabIndex,
  onTabSelected: (i) => setState(() => tabIndex = i),
  tabs: const [
    M3ETab(label: 'Overview'),
    M3ETab(label: 'Specs'),
  ],
);
```

Keyboard: Tab enters. Left and Right move. Space or Enter selects.

#### M3ENavigationBar

Bottom navigation with a selection pill that scales in place. Wide windows get
a row of icon-and-label chips, and `hideOnScroll` with
`M3ENavigationBarController` hides it on scroll. Customize it on
`M3ENavigationBarTheme`.

```dart
// in State
M3ENavigationBar(
  destinations: const [
    M3ENavigationBarDestination(icon: Icon(M3EIcons.home), label: 'Home'),
    M3ENavigationBarDestination(icon: Icon(M3EIcons.search), label: 'Search'),
  ],
  selectedIndex: barIndex,
  onDestinationSelected: (i) => setState(() => barIndex = i),
);
```

Keyboard: Tab enters. Left and Right move. Space or Enter selects.

#### M3ENavigationRail

Vertical navigation, **96** wide collapsed (**80** narrow) and **220–360**
expanded, with an optional FAB slot. A modal rail closes on the scrim, Escape
or back. `M3ENavigationRailController` expands, collapses and selects.
Customize it on `M3ENavigationRailTheme`.

```dart
// in State
M3ENavigationRail(
  sections: const [
    M3ENavigationRailSection(
      destinations: [
        M3ENavigationRailDestination(icon: Icon(M3EIcons.home), label: 'Home'),
        M3ENavigationRailDestination(icon: Icon(M3EIcons.search), label: 'Search'),
      ],
    ),
  ],
  selectedIndex: railIndex,
  onDestinationSelected: (i) => setState(() => railIndex = i),
);
```

Keyboard: Tab enters. Arrows move. Space or Enter selects. Escape closes a
modal rail.

#### M3ENavigationDrawer

Standard (default) or modal drawer, **360** wide with **16** end corners. A
modal drawer closes on a destination, the scrim, a drag or back, and
`M3ENavigationDrawerController` opens and closes it. Customize it on
`M3ENavigationDrawerTheme`.

```dart
// in State
M3ENavigationDrawer(
  headline: 'Mail',
  destinations: const [
    M3ENavigationDestination(icon: Icon(M3EIcons.inbox), label: 'Inbox'),
    M3ENavigationDestination(icon: Icon(M3EIcons.favorite), label: 'Starred'),
  ],
  selectedIndex: drawerIndex,
  onDestinationSelected: (i) => setState(() => drawerIndex = i),
);
```

Keyboard: Tab enters. Up and Down move. Space or Enter selects.

#### M3EToolbar

Floating (default) or docked toolbar, **64** tall. A floating toolbar is a
pill placed by `alignment`, **16** off the edge (`screenOffset`), with an
optional paired FAB. `M3EToolbar.docked` spans the width and lays out actions
at **600** and wider with `contentAlignment`. Customize it on
`M3EToolbarTheme`.

```dart
M3EToolbar(
  actions: <M3EToolbarItem>[
    M3EToolbarAction(icon: M3EIcons.edit, onPressed: () {}),
    M3EToolbarAction(icon: M3EIcons.share, onPressed: () {}),
  ],
);

M3EToolbar.docked(
  actions: <M3EToolbarItem>[
    M3EToolbarAction(icon: M3EIcons.search, onPressed: () {}),
    M3EToolbarAction(icon: M3EIcons.delete, onPressed: () {}),
  ],
);
```

Keyboard: Tab or arrows move between actions. Space or Enter activates.

#### M3EMenu

Vertical and baseline menus, **112–280** wide with **48** entries, plus
groups, submenus and multi-select. Opening focuses the first enabled item,
and back closes a submenu, then the menu. Customize it on `M3EMenuTheme`.

```dart
M3EMenu(
  anchorBuilder: (context, open) => M3EButton(
    onPressed: open,
    child: const Text('Open menu'),
  ),
  children: [
    M3EMenuGroup.entries(
      entries: [
        M3EMenuEntry(label: 'Edit', onPressed: () {}),
        M3EMenuEntry(label: 'Copy', trailingText: '⌘C', onPressed: () {}),
      ],
    ),
  ],
);
```

Keyboard: Up and Down move. Left and Right open or close a submenu. Letters
jump. Enter or Space activates. Escape closes.

---

### Feedback

> See also: [`example/lib/pages/playground/find/`](example/lib/pages/playground/find/) (Find tab)

#### M3EBadge

A **6** dot or a label badge (at least **16**) in **error** and **on error**,
drawn over its child without moving it. Counts cap at **999**. Customize it on
`M3EBadgeTheme`.

```dart
const M3EBadge(
  count: 8,
  child: Icon(M3EIcons.mail),
);
```

#### M3EProgressIndicator

Circular and linear progress, flat or wavy, determinate or indeterminate. The
indicator is **primary** on a **secondary container** track (`showTrack:
false` hides it). Customize it on `M3EProgressIndicatorTheme`.

```dart
const M3EProgressIndicator.circular(); // indeterminate

M3EProgressIndicator.linearWavy(value: 0.6);
```

#### M3ELoadingIndicator

Indeterminate morphing shape, **48** outer and **38** active by default,
plain or `contained`. `size` scales both together. Customize it on
`M3ELoadingIndicatorTheme`.

```dart
const M3ELoadingIndicator();

const M3ELoadingIndicator(
  variant: M3ELoadingIndicatorVariant.contained,
  size: 96,
);
```

#### M3ERefreshIndicator

Pull-to-refresh for scrollables that shows a contained `M3ELoadingIndicator`.
It arms once fully revealed, and `M3ERefreshIndicatorController.show()`
refreshes from code. Customize it on `M3ERefreshIndicatorTheme`.

```dart
M3ERefreshIndicator(
  onRefresh: () async => reload(),
  child: ListView(children: items),
);
```

#### M3ETooltip

Plain tooltip (up to **200** wide, above the target) or rich tooltip (up to
**320**, with a title and actions). `persistent` keeps a rich tooltip open.
Customize it on `M3ETooltipTheme`.

```dart
M3ETooltip(
  message: 'Compose a new message',
  child: M3EIconButton(
    icon: const Icon(M3EIcons.edit),
    onPressed: () {},
  ),
);
```

Keyboard: the tooltip shows when its child gets keyboard focus.

#### M3ESnackbar

Short message at the bottom, at least **48** tall and up to **600** wide, with
an optional action and close button. A bar with either stays until dismissed.
Customize it on `M3ESnackbarTheme`.

```dart
M3ESnackbar.show(
  context,
  message: 'Draft saved',
  actionLabel: 'Undo',
  onAction: () {},
);
```

Keyboard: Escape dismisses it when focused.

#### M3ETextField

Filled or outlined field, **56** tall with **4** corners, and a label that
springs between the middle and the top. It supports icons, prefix and suffix
text, a placeholder, supporting or error text, a counter, required and
read-only fields, clear and password buttons, multi-line input and `density`
(**0** to **-3**). Focus is **primary** and errors are **error**. Customize it
on `M3ETextFieldTheme` and `M3ETextFieldColorTheme`.

```dart
M3ETextField(
  controller: nameController,
  label: 'Full name',
  supportingText: 'As it appears on your ID',
  showClearButton: true,
);

const M3ETextField(
  label: 'Password',
  variant: M3ETextFieldVariant.outlined,
  obscureText: true,
  showPasswordToggle: true,
);
```

Keyboard: Tab focuses enabled fields. Escape unfocuses.

#### M3ESearchBar / M3ESearchAnchor / M3ESliverSearchBar

**56** pill search bar on **surface container high**, **24** from its pane and
**12** when focused, and a search view (contained or divided) that is
full-screen below **600** and docked above. `M3ESliverSearchBar` scrolls away
with content. Customize it on `M3ESearchBarTheme` and `M3ESearchViewTheme`.

```dart
final controller = M3ESearchController();

M3ESearchAnchor.bar(
  searchController: controller,
  barHintText: 'Search messages',
  suggestionsBuilder: (context, controller) => [
    for (final name in names)
      M3EListItem(headline: name, onTap: () => controller.closeView(name)),
  ],
);
```

Keyboard: Tab, then Space or Enter opens the view. Arrows move through
results. Escape closes.

---

### Modal surfaces

Several components present transient UI over the app. They all require a
`BuildContext` with a `Navigator` / `Overlay` ancestor (any `MaterialApp` or
`WidgetsApp` provides this):

| Component | API |
| --------- | --- |
| `M3EDialog` | `M3EDialog.show`, `.showSelectionScreen`, `.showFullScreen`, `.showAdaptive` |
| `M3EBottomSheet` | `M3EBottomSheet.show`, `.showAdaptive`, `.standard` |
| `M3ESideSheet` | `M3ESideSheet.show`, `M3ESideSheetLayout` |
| `M3ESnackbar` | `M3ESnackbar.show` |

## Example app (detailed)

Live web build:
[paadevelopments.github.io/material_3_expressive](https://paadevelopments.github.io/material_3_expressive/).

The [`example/`](example/) project is a full gallery app:

- **Entry point:** [`example/lib/main.dart`](example/lib/main.dart) —
  `M3EMaterialApp` with `autoTheming`, `dynamicColoring`, and a five-tab
  catalog-driven gallery shell. The home app bar palette action opens
  [`theme_config_page.dart`](example/lib/pages/theme_config_page.dart) to
  toggle auto theming and dynamic color, pick one of five seed colors when
  dynamic color is off, and choose a font family (Google Sans Flex default,
  system, Roboto Flex, or Roboto Mono). Open **View → Typography** for type
  scale, variable-font axes, and conversion demos.
- **Pages:** playgrounds under
  [`example/lib/pages/playground/`](example/lib/pages/playground/), grouped
  by tab (`do/`, `pick/`, `view/`, `nav/`, `find/`). Each opens on
  [`preview_screen.dart`](example/lib/pages/preview/preview_screen.dart); the
  shared layout and controls live in
  [`example/lib/widgets/playground/`](example/lib/widgets/playground/).
- **Theme toggle:** app-bar `M3EIconButton` calls
  `M3ETheme.controllerOf(context)?.toggleBrightness(...)`.

```bash
cd example
flutter pub get
flutter run
```

Pick a device or simulator when prompted. Use the bottom navigation bar to
switch between component groups, the palette icon for theme settings, and the
brightness icon to toggle light/dark mode.

## Development

Static analysis and tests:

```bash
flutter analyze
flutter test
```

Custom lint rules live in `tools/klin_dart` (path dependency, omitted from the published archive):

```bash
export PATH="$PWD/.fvm/flutter_sdk/bin:$PATH"
dart run custom_lint
```

## Support

If this package helps your project:

- **Star** it on [pub.dev](https://pub.dev/packages/material_3_expressive)
- **Star or fork** the repo on [GitHub](https://github.com/paadevelopments/material_3_expressive)
- Share it with others building Material 3 Expressive UIs

## Credits

Several components were ported or vendored from earlier Material 3 Expressive
implementations. Thanks to the original authors:

| Author | Components | Source |
| ------ | ---------- | ------ |
| [Mudit Purohit](https://github.com/Mudit200408) | Dropdown menus | [m3e_dropdown_menu](https://github.com/Mudit200408/m3e_dropdown_menu) |
| [Emily](https://github.com/EmilyMoonstone) | Loading indicator (Flutter package) | [loading_indicator_m3e](https://github.com/EmilyMoonstone/material_3_expressive/tree/main/packages/loading_indicator_m3e) |
| [The Android Open Source Project](https://source.android.com/) | Linear / circular wavy progress (Compose reference) | [`LinearWavyProgressIndicator`](https://developer.android.com/reference/kotlin/androidx/compose/material3/LinearWavyProgressIndicator.composable) / [`CircularWavyProgressIndicator`](https://developer.android.com/reference/kotlin/androidx/compose/material3/CircularWavyProgressIndicator.composable) |
| [The Flutter Authors](https://github.com/flutter/flutter) | Carousel view layout (`CarouselView`) | Flutter SDK / [m3_carousel](https://pub.dev/packages/m3_carousel) |
| [pub.dev](https://pub.dev/) | Spring motion (`motor`), expressive morph polygons (`material_new_shapes`), dynamic color (`dynamic_color`) | See [Dependencies](#dependencies) |

Copyright notices and licenses from those sources are retained in the
corresponding source files where applicable, and summarized in
[`NOTICE`](NOTICE).

## License

Distributed under the MIT License. See [`LICENSE`](LICENSE) for details.

Copyright (c) 2026 Paa Developments <paa.code.me@gmail.com>
