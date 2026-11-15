package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray

/** What [LaptopThemeSettings] needs from the session beyond the shared interfaces. */
internal interface LaptopThemeSettingsHost : DesktopSession, OverlayMenu {
    val laptopDeckContent: LinearLayout?
    var laptopLanguagePopup: View?
    val laptopPreviewThemeId: String?
    var laptopSettingsVisible: Boolean
    val laptopTypeface: Typeface
    fun rebuildLaptopDeck(showSettings: Boolean = false)
    val root: TouchRoutingFrame?
    val swipeTyping: SwipeTyping
}

/** Keyboard themes: the active palette, the theme and language pickers, and colour helpers. */
internal class LaptopThemeSettings(
    private val context: Context,
    private val desk: LaptopThemeSettingsHost,
) {
    private val laptopThemeRepository by lazy { LaptopKeyboardThemeRepository(context) }

    internal fun laptopKeyboardTheme(): String =
        desk.laptopPreviewThemeId ?: context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getString("flutter.laptop_keyboard_theme", null)
        ?: context.getSharedPreferences(MirrorService.PREFS, Context.MODE_PRIVATE)
            .getString("laptop_keyboard_theme", "standard") ?: "standard"

    internal fun opacityColor(color: Int, opacity: Float): Int = Color.argb(
        (Color.alpha(color) * opacity.coerceIn(.1f, 1f)).toInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    /** Keep transparent theme imports from exposing the mirrored display. */
    internal fun opaqueColor(color: Int): Int = Color.rgb(
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private fun contrastingColor(color: Int): Int =
        LaptopKeyboardThemeRepository.contrastingColor(color)

    internal fun laptopPalette(): LaptopPalette = laptopPaletteFor(laptopKeyboardTheme())

    /** Resolve a palette for previews as well as the active keyboard. */
    private fun laptopPaletteFor(id: String): LaptopPalette = laptopThemeRepository.palette(id)

    internal fun showLaptopKeyboardSettings() {
        val deck = desk.laptopDeckContent ?: return
        desk.laptopSettingsVisible = true
        TransitionManager.beginDelayedTransition(deck, AutoTransition().apply { duration = 240 })
        deck.removeAllViews()
        val currentTheme = laptopKeyboardTheme()
        val crimson = currentTheme == "crimson"
        val amoled = currentTheme == "amoled"
        val themeAccent = when {
            crimson -> Color.rgb(236, 145, 101)
            amoled -> Color.rgb(210, 210, 210)
            else -> Color.rgb(208, 188, 237)
        }
        deck.background = if (crimson) GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(25, 2, 4), Color.rgb(57, 7, 8), Color.rgb(34, 3, 5))
        ) else GradientDrawable().apply {
            setColor(if (amoled) Color.BLACK else Color.rgb(18, 18, 22))
        }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(desk.dp(12), desk.dp(10), desk.dp(20), desk.dp(8))
        }
        header.addView(ImageButton(context).apply {
            contentDescription = NativeStrings.text("nativeReturn")
            setImageDrawable(KeyboardGlyphDrawable(KeyboardGlyphDrawable.BACK, Color.WHITE))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(desk.dp(14), desk.dp(14), desk.dp(14), desk.dp(14))
            setOnClickListener { desk.rebuildLaptopDeck() }
        }, LinearLayout.LayoutParams(desk.dp(52), desk.dp(52)).apply { rightMargin = desk.dp(8) })
        header.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = NativeStrings.text("nativeKeyboardSettings")
                typeface = desk.laptopTypeface
                textSize = 19f
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = NativeStrings.text("nativeKeyboardSettingsDescription")
                typeface = desk.laptopTypeface
                textSize = 11f
                setTextColor(if (crimson) Color.rgb(207, 132, 101) else Color.rgb(170, 165, 177))
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        deck.addView(header, LinearLayout.LayoutParams(-1, -2))
        deck.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(desk.dp(28), desk.dp(2), desk.dp(28), desk.dp(2))
            addView(TextView(context).apply {
                text = NativeStrings.text("nativeSwipeLanguage")
                typeface = desk.laptopTypeface
                textSize = 14f
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply {
                text = NativeStrings.text("nativeSwipeLanguageDescription")
                typeface = desk.laptopTypeface
                textSize = 10f
                setTextColor(Color.rgb(170, 165, 177))
            })
        }, LinearLayout.LayoutParams(-1, desk.dp(38)))
        val languageChoices = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(desk.dp(24), 0, desk.dp(24), desk.dp(4))
        }
        val activeLanguage = desk.swipeTyping.laptopSwipeLanguage()
        desk.swipeTyping.laptopSwipeLanguageChoices().forEach { (language, label) ->
            val selected = language == activeLanguage
            languageChoices.addView(TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                typeface = desk.laptopTypeface
                textSize = 12f
                setTextColor(if (selected) contrastingColor(themeAccent) else Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(if (selected) themeAccent else Color.rgb(48, 45, 54))
                    setStroke(desk.dp(1), if (selected) themeAccent else Color.rgb(87, 82, 94))
                    cornerRadius = desk.dp(16).toFloat()
                }
                setOnClickListener {
                    context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
                        .putString("flutter.laptop_swipe_language", language.id).apply()
                    showLaptopKeyboardSettings()
                }
            }, LinearLayout.LayoutParams(desk.dp(92), desk.dp(34)).apply {
                leftMargin = desk.dp(3); rightMargin = desk.dp(3)
            })
        }
        deck.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(languageChoices, LinearLayout.LayoutParams(-2, -1))
        }, LinearLayout.LayoutParams(-1, desk.dp(42)))
        deck.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(desk.dp(28), desk.dp(4), desk.dp(28), desk.dp(4))
            addView(ImageView(context).apply {
                setImageDrawable(
                    KeyboardGlyphDrawable(
                        KeyboardGlyphDrawable.PALETTE,
                        themeAccent
                    )
                )
            }, LinearLayout.LayoutParams(desk.dp(28), desk.dp(28)).apply { rightMargin = desk.dp(12) })
            addView(TextView(context).apply {
                text = NativeStrings.text("nativeTheme")
                typeface = desk.laptopTypeface
                textSize = 15f
                setTextColor(Color.WHITE)
            })
        }, LinearLayout.LayoutParams(-1, desk.dp(42)))
        val choices = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(desk.dp(28), desk.dp(8), desk.dp(28), desk.dp(24))
        }
        laptopThemeChoices().forEachIndexed { index, (id, label) ->
            choices.addView(laptopThemeChoice(id, label), LinearLayout.LayoutParams(desk.dp(220), -1).apply {
                if (index > 0) leftMargin = desk.dp(6)
                if (index < laptopThemeChoices().lastIndex) rightMargin = desk.dp(6)
            })
        }
        deck.addView(HorizontalScrollView(context).apply {
            isFillViewport = false
            isHorizontalScrollBarEnabled = true
            addView(choices, LinearLayout.LayoutParams(-2, -1))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    internal fun showLaptopLanguagePopup(anchor: View) {
        if (!desk.swipeTyping.isLaptopSwipeEnabled()) return
        val host = desk.root ?: return
        hideLaptopLanguagePopup()
        desk.swipeTyping.hideLaptopSwipeCandidates()
        val overlay = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { hideLaptopLanguagePopup() }
        }
        val languageChoices = desk.swipeTyping.laptopSwipeLanguageChoices()
        val popupWidth = min(host.width - desk.dp(32), desk.dp(280))
        val desiredPopupHeight = desk.dp(64) + languageChoices.size * desk.dp(48)
        val popupHeight = minOf(host.height - desk.dp(32), desk.dp(320), desiredPopupHeight)
        val rootLocation = IntArray(2).also(host::getLocationOnScreen)
        val anchorLocation = IntArray(2).also(anchor::getLocationOnScreen)
        val anchorCenter = anchorLocation[0] - rootLocation[0] + anchor.width / 2
        val popupLeft = (anchorCenter - popupWidth / 2)
            .coerceIn(desk.dp(12), max(desk.dp(12), host.width - popupWidth - desk.dp(12)))
        val preferredTop = anchorLocation[1] - rootLocation[1] - popupHeight - desk.dp(10)
        val popupTop = preferredTop.coerceIn(desk.dp(12), max(desk.dp(12), host.height - popupHeight - desk.dp(12)))
        val palette = laptopPalette()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            elevation = desk.dp(12).toFloat()
            setPadding(desk.dp(14), desk.dp(12), desk.dp(14), desk.dp(12))
            background = GradientDrawable().apply {
                setColor(opaqueColor(palette.background))
                setStroke(desk.dp(1), palette.border)
                cornerRadius = desk.dp(20).toFloat()
            }
            isClickable = true
            setOnClickListener { /* Keep taps inside the bubble. */ }
            addView(TextView(context).apply {
                text = NativeStrings.text("nativeSwipeLanguage")
                typeface = desk.laptopTypeface
                textSize = 16f
                setTextColor(palette.text)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(desk.dp(6), 0, desk.dp(6), desk.dp(8))
            }, LinearLayout.LayoutParams(-1, desk.dp(40)))
            val choices = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val active = desk.swipeTyping.laptopSwipeLanguage()
            languageChoices.forEach { (language, label) ->
                val selected = language == active
                choices.addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(desk.dp(14), 0, desk.dp(12), 0)
                    background = GradientDrawable().apply {
                        setColor(if (selected) palette.selected else opacityColor(palette.key, palette.keyOpacity))
                        setStroke(desk.dp(1), if (selected) palette.selected else palette.border)
                        cornerRadius = desk.dp(14).toFloat()
                    }
                    addView(TextView(context).apply {
                        text = label
                        typeface = desk.laptopTypeface
                        textSize = 13f
                        setTextColor(if (selected) contrastingColor(palette.selected) else palette.text)
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    if (selected) addView(ImageView(context).apply {
                        setImageDrawable(KeyboardGlyphDrawable(
                            KeyboardGlyphDrawable.CHECK,
                            contrastingColor(palette.selected),
                        ))
                    }, LinearLayout.LayoutParams(desk.dp(22), desk.dp(22)))
                    setOnClickListener {
                        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
                            .putString("flutter.laptop_swipe_language", language.id).apply()
                        hideLaptopLanguagePopup()
                        desk.rebuildLaptopDeck()
                    }
                }, LinearLayout.LayoutParams(-1, desk.dp(42)).apply {
                    setMargins(desk.dp(3), desk.dp(3), desk.dp(3), desk.dp(3))
                })
            }
            addView(ScrollView(context).apply {
                isVerticalScrollBarEnabled = true
                addView(choices, FrameLayout.LayoutParams(-1, -2))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        overlay.addView(panel, FrameLayout.LayoutParams(popupWidth, popupHeight).apply {
            leftMargin = popupLeft
            topMargin = popupTop
        })
        host.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        overlay.bringToFront()
        desk.laptopLanguagePopup = overlay
        panel.pivotX = (anchorCenter - popupLeft).coerceIn(0, popupWidth).toFloat()
        panel.pivotY = popupHeight.toFloat()
        panel.alpha = 0f
        panel.scaleX = .94f
        panel.scaleY = .94f
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(PathInterpolator(.22f, 1f, .36f, 1f))
            .setDuration(180L).start()
    }

    internal fun hideLaptopLanguagePopup() {
        val popup = desk.laptopLanguagePopup ?: return
        desk.laptopLanguagePopup = null
        (popup.parent as? FrameLayout)?.removeView(popup)
    }

    private fun laptopThemeChoices(): List<Pair<String, String>> {
        val choices = mutableListOf(
            "standard" to NativeStrings.text("nativeKeyboardThemeStandard"),
            "crimson" to NativeStrings.text("nativeKeyboardThemeCrimson"),
            "cloud" to NativeStrings.text("nativeKeyboardThemeCloud"),
            "amoled" to NativeStrings.text("nativeKeyboardThemeAmoled")
        )
        val raw = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getString("flutter.dextop_laptop_keyboard_themes", null)
        runCatching {
            val list = JSONArray(raw ?: "[]")
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) choices += id to name
            }
        }
        return choices
    }

    private fun laptopThemeChoice(id: String, label: String): View {
        val selected = laptopKeyboardTheme() == id
        val palette = laptopPaletteFor(id)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(desk.dp(18), desk.dp(14), desk.dp(18), desk.dp(14))
            background = GradientDrawable().apply {
                setColor(palette.background)
                setStroke(
                    desk.dp(if (selected) 2 else 1), if (selected)
                        palette.selected else palette.border
                )
                cornerRadius = desk.dp(18).toFloat()
            }
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = label
                    typeface = desk.laptopTypeface
                    textSize = 16f
                    setTextColor(Color.WHITE)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (selected) addView(ImageView(context).apply {
                    // Keep the selected state legible on both the dark
                    // Crimson palette and the light Cloud Pop palette. The
                    // old check used the same accent as the card background.
                    val checkColor = contrastingColor(palette.selected)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(palette.selected)
                        setStroke(desk.dp(1), checkColor)
                    }
                    setPadding(desk.dp(3), desk.dp(3), desk.dp(3), desk.dp(3))
                    setImageDrawable(
                        KeyboardGlyphDrawable(
                            KeyboardGlyphDrawable.CHECK,
                            checkColor
                        )
                    )
                }, LinearLayout.LayoutParams(desk.dp(30), desk.dp(30)))
            }, LinearLayout.LayoutParams(-1, -2))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                repeat(7) { index ->
                    addView(View(context).apply {
                        background = GradientDrawable().apply {
                            val fill = if (index == 0 || index == 6) {
                                palette.keyVariant
                            } else {
                                palette.key
                            }
                            setColor(opacityColor(fill, palette.keyOpacity))
                            cornerRadius = desk.dp(3).toFloat()
                        }
                    }, LinearLayout.LayoutParams(0, desk.dp(35), 1f).apply {
                        setMargins(desk.dp(2), 0, desk.dp(2), 0)
                    })
                }
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = desk.dp(14)
                bottomMargin = desk.dp(8)
            })
            addView(TextView(context).apply {
                text = when (id) {
                    "crimson" -> NativeStrings.text("nativeKeyboardThemeCrimsonDescription")
                    "cloud" -> NativeStrings.text("nativeKeyboardThemeCloudDescription")
                    "amoled" -> NativeStrings.text("nativeKeyboardThemeAmoledDescription")
                    "standard" -> NativeStrings.text("nativeKeyboardThemeStandardDescription")
                    else -> NativeStrings.text("nativeKeyboardThemeCustomDescription")
                }
                typeface = desk.laptopTypeface
                textSize = 11f
                setTextColor(palette.trackpadText)
            })
            setOnClickListener {
                context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit()
                    .putString("flutter.laptop_keyboard_theme", id).apply()
                context.getSharedPreferences(MirrorService.PREFS, Context.MODE_PRIVATE).edit()
                    .putString("laptop_keyboard_theme", id).apply()
                // Keep the deck attached while changing its contents. Removing
                // it exposes the Android display behind the accessibility layer.
                showLaptopKeyboardSettings()
            }
        }
    }
}
