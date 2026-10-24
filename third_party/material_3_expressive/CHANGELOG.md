## 1.2.0

### Changed

* Raise `material_ui` to `^1.8.0`. It now exports its own `RoundedPolygon`
  and `CornerRounding`, which clash with the ones this package re-exports from
  `material_new_shapes`. If you import both packages, add
  `hide CornerRounding, RoundedPolygon` to the `material_ui` import.
* **Button group, segmented buttons:** the first enabled action or segment no
  longer takes focus on mount. Set `autofocus` on an action to focus it
  ([#35](https://github.com/paadevelopments/material_3_expressive/pull/35),
  thanks @StillMisty).
* **Search:** the view field takes focus as soon as the view starts opening,
  instead of after the expand animation settles
  ([#35](https://github.com/paadevelopments/material_3_expressive/pull/35),
  thanks @StillMisty).
* **Example:** the iOS runner targets iOS 15.0.

### Fixed

* **Buttons:** `M3EButton` with only an `icon` or only a `label` now renders
  that content
  ([#34](https://github.com/paadevelopments/material_3_expressive/pull/34),
  thanks @StillMisty).
* **Button group:** icon-only actions in the overflow menu are titled with
  their `semanticLabel` or `tooltip` instead of "Option N"
  ([#34](https://github.com/paadevelopments/material_3_expressive/pull/34),
  thanks @StillMisty). Connected gaps widen only for a visible keyboard focus
  ring, so tapping an action no longer shifts the layout
  ([#38](https://github.com/paadevelopments/material_3_expressive/pull/38)).
* **Date pickers:** `M3EInputDatePickerFormField` with `autofocus` now focuses
  its field, including when no `focusNode` is passed
  ([#37](https://github.com/paadevelopments/material_3_expressive/pull/37),
  thanks @StillMisty).
* **Theme:** on iOS, `M3EMaterialApp` and `M3EScrimSystemUi` set the correct
  `statusBarBrightness`, so status bar text contrasts with the theme and with
  scrims
  ([#40](https://github.com/paadevelopments/material_3_expressive/pull/40)).

## 1.1.7

* Raise `material_ui` to `^1.7.0`.
* **Bottom sheets, side sheets:** predictive back works with an `onDismissRequest` guard and no longer resets content state ([#31](https://github.com/paadevelopments/material_3_expressive/pull/31), [#32](https://github.com/paadevelopments/material_3_expressive/pull/32), thanks @StillMisty).
* **Chips:** long labels truncate with an ellipsis in bounded layouts ([#30](https://github.com/paadevelopments/material_3_expressive/pull/30), thanks @StillMisty).

## 1.1.6

* Raise `material_ui` to `^1.6.0`.
* **Carousel:** add `M3ECarouselScrim.gradient` with a default bottom fade.
* **Sliders:** larger tick marks that match the end stops.
* **Bottom sheets:** no bounce when content or system bars resize while closing ([#28](https://github.com/paadevelopments/material_3_expressive/pull/28), thanks @StillMisty).
