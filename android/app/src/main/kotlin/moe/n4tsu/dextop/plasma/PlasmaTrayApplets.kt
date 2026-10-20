package moe.n4tsu.dextop.plasma

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.concurrent.ExecutorService

/**
 * Runs blocking [PlasmaSystemControls] calls off the main thread and hands
 * results back to the main thread, but only while [owner] is still attached:
 * a popup may close before a scan or a shell command finishes.
 */
internal class TrayWorker(private val executor: ExecutorService, private val handler: Handler) {
    fun <T> load(owner: View, work: () -> T, done: (T) -> Unit) {
        runCatching {
            executor.execute {
                val result = runCatching(work).getOrNull()
                handler.post { if (result != null && owner.isAttachedToWindow) done(result) }
            }
        }
    }
}

private fun View.dp(value: Float) = (value * resources.displayMetrics.density).toInt()

/** Plasma 6 quick-settings tile: icon, title and state, accent-filled when on. */
@SuppressLint("ViewConstructor")
internal class QuickToggle(
    context: Context,
    private val palette: PlasmaPalette,
    glyph: Glyph,
    private val title: String,
    var subtitle: String,
    active: Boolean,
    private val onToggle: (QuickToggle) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = GlyphDrawable(glyph, palette.text)
    private val box = RectF()
    private var hover = 0f
    private var animator: ValueAnimator? = null
    var glyph: Glyph = glyph
        set(value) {
            field = value
            iconOverride = GlyphDrawable(value, palette.text)
            invalidate()
        }
    private var iconOverride: GlyphDrawable? = null
    var level: Float = 1f
        set(value) { field = value; invalidate() }
    var active = active
        set(value) { field = value; invalidate() }
    /** False while a change is being applied, or when the device refuses it. */
    var enabledToggle = true
        set(value) { field = value; invalidate() }

    init {
        setOnHoverListener { _, event ->
            val target = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) 0f else 1f
            animator?.cancel()
            animator = ValueAnimator.ofFloat(hover, target).apply {
                duration = PlasmaTheme.SHORT_MS
                addUpdateListener { hover = it.animatedValue as Float; invalidate() }
                start()
            }
            false
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (52 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val radius = density * 8
        box.set(density * 2, density * 2, width - density * 2, height - density * 2)
        fill.color = when {
            active -> PlasmaTheme.withAlpha(palette.accent, 0x48 + (0x20 * hover).toInt())
            else -> PlasmaTheme.withAlpha(palette.text, 0x12 + (0x12 * hover).toInt())
        }
        canvas.drawRoundRect(box, radius, radius, fill)
        stroke.color = if (active) palette.accent else PlasmaTheme.withAlpha(palette.text, 0x26)
        canvas.drawRoundRect(box, radius, radius, stroke)
        val alpha = if (enabledToggle) 255 else 110
        val drawable = iconOverride ?: icon
        drawable.color = if (active) palette.accent else palette.text
        drawable.level01 = level
        drawable.filled = active
        drawable.alpha = alpha
        val size = 22 * density
        val left = 12 * density
        drawable.setBounds(left.toInt(), (height / 2f - size / 2f).toInt(), (left + size).toInt(), (height / 2f + size / 2f).toInt())
        drawable.draw(canvas)
        val x = left + size + 10 * density
        val available = width - x - 8 * density
        text.typeface = PlasmaTheme.sansMedium
        text.textSize = 12.5f * density
        text.color = if (enabledToggle) palette.text else palette.disabledText
        canvas.drawText(TextUtilsCompat.ellipsize(title, text, available), x, height / 2f - 2 * density, text)
        text.typeface = PlasmaTheme.sans
        text.textSize = 11f * density
        text.color = palette.inactiveText
        canvas.drawText(TextUtilsCompat.ellipsize(subtitle, text, available), x, height / 2f + 13 * density, text)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledToggle) return true
        if (event.actionMasked == MotionEvent.ACTION_UP && event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) {
            onToggle(this)
        }
        return true
    }
}

/** Round, borderless icon button whose glyph can change (mute, play/pause…). */
@SuppressLint("ViewConstructor")
internal class PlasmaIconButton(
    context: Context,
    private val palette: PlasmaPalette,
    glyph: Glyph,
    private val sizeDp: Float = 32f,
    private val iconDp: Float = 18f,
    var onClick: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var icon = GlyphDrawable(glyph, palette.text)
    private var hover = 0f
    private var pressed = false
    var glyph: Glyph = glyph
        set(value) { if (field != value) { field = value; icon = GlyphDrawable(value, palette.text) }; invalidate() }
    var level: Float = 1f
        set(value) { field = value; invalidate() }
    var tint: Int? = null
        set(value) { field = value; invalidate() }

    init {
        setOnHoverListener { _, event ->
            hover = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) 0f else 1f
            invalidate()
            false
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (sizeDp * density).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val alpha = when { pressed -> 0x55; hover > 0f -> 0x2A; else -> 0 }
        if (alpha > 0) {
            paint.color = PlasmaTheme.withAlpha(palette.accent, alpha)
            canvas.drawCircle(width / 2f, height / 2f, width / 2f - density, paint)
        }
        val size = iconDp * density
        icon.color = tint ?: palette.text
        icon.level01 = level
        icon.setBounds((width / 2f - size / 2f).toInt(), (height / 2f - size / 2f).toInt(), (width / 2f + size / 2f).toInt(), (height / 2f + size / 2f).toInt())
        icon.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = true; invalidate() }
            MotionEvent.ACTION_UP -> {
                pressed = false
                invalidate()
                if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) onClick()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = false; invalidate() }
        }
        return true
    }
}

/** Single-line Breeze text field for passphrases. */
@SuppressLint("ViewConstructor")
internal class PlasmaPasswordField(context: Context, private val palette: PlasmaPalette, hint: String) : EditText(context) {
    private val density = resources.displayMetrics.density
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyph = GlyphDrawable(Glyph.KEY, palette.inactiveText)
    private val box = RectF()

    init {
        background = null
        this.hint = hint
        setHintTextColor(palette.inactiveText)
        setTextColor(palette.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        typeface = PlasmaTheme.sans
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        gravity = Gravity.CENTER_VERTICAL
        setPadding((34 * density).toInt(), 0, (10 * density).toInt(), 0)
        highlightColor = PlasmaTheme.withAlpha(palette.accent, 0x66)
    }

    override fun onDraw(canvas: Canvas) {
        box.set(.5f, .5f, width - .5f, height - .5f)
        box.offset(scrollX.toFloat(), 0f)
        frame.color = palette.viewBackground
        canvas.drawRoundRect(box, density * 4, density * 4, frame)
        outline.strokeWidth = if (isFocused) density * 1.5f else 1f
        outline.color = if (isFocused) palette.accent else palette.separator
        canvas.drawRoundRect(box, density * 4, density * 4, outline)
        val size = (16 * density).toInt()
        val left = scrollX + (10 * density).toInt()
        glyph.setBounds(left, height / 2 - size / 2, left + size, height / 2 + size / 2)
        glyph.draw(canvas)
        super.onDraw(canvas)
    }
}

/** Popup header: title on the left, optional icon actions on the right. */
private fun appletTitleRow(context: Context, palette: PlasmaPalette, title: String, vararg actions: View): LinearLayout =
    horizontalLayout(context).apply {
        val density = context.resources.displayMetrics.density
        setPadding((8 * density).toInt(), (4 * density).toInt(), (2 * density).toInt(), (8 * density).toInt())
        addView(plasmaLabel(context, title, palette.text, 15f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        actions.forEach { addView(it) }
    }

private fun sectionLabel(context: Context, palette: PlasmaPalette, text: String) =
    plasmaLabel(context, text, palette.inactiveText, 12f, bold = true).apply {
        val density = context.resources.displayMetrics.density
        setPadding((8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
    }

/** Two quick toggles per row. */
private fun toggleGrid(context: Context, toggles: List<QuickToggle>): LinearLayout = verticalLayout(context).apply {
    toggles.chunked(2).forEach { pair ->
        addView(horizontalLayout(context).apply {
            pair.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
            if (pair.size == 1) addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        }, LinearLayout.LayoutParams(-1, -2))
    }
}

private fun footerButton(context: Context, palette: PlasmaPalette, label: String, onClick: () -> Unit) =
    PlasmaButton(context, palette, Glyph.SETTINGS, label, flat = true) { onClick() }

private fun footerParams(view: View) =
    LinearLayout.LayoutParams(-2, view.dp(32f)).apply { topMargin = view.dp(8f); gravity = Gravity.END }

private fun onOff(on: Boolean) = t(if (on) "nativePlasmaOn" else "nativePlasmaOff")

/** "Networks" popup: radio toggles, the current connection and nearby Wi-Fi networks. */
@SuppressLint("ViewConstructor")
internal class NetworkApplet(
    context: Context,
    private val palette: PlasmaPalette,
    private val controls: PlasmaSystemControls,
    private val worker: TrayWorker,
    private val onChanged: () -> Unit,
    onOpenSettings: () -> Unit,
) : LinearLayout(context) {
    private var status = controls.networkStatus()
    private val wifiToggle = QuickToggle(context, palette, Glyph.WIFI, "Wi-Fi", "", status.wifiEnabled) { toggle ->
        apply(toggle, !toggle.active, { it.wifiEnabled }) { controls.setWifiEnabled(it) }
    }
    private val dataToggle = QuickToggle(context, palette, Glyph.CELLULAR, t("nativePlasmaMobileData"), "", status.mobileDataEnabled) { toggle ->
        apply(toggle, !toggle.active, { it.mobileDataEnabled }) { controls.setMobileData(it) }
    }
    private val airplaneToggle = QuickToggle(context, palette, Glyph.AIRPLANE, t("nativePlasmaAirplaneMode"), "", status.airplaneMode) { toggle ->
        apply(toggle, !toggle.active, { it.airplaneMode }) { controls.setAirplaneMode(it) }
    }
    private val bluetoothToggle = QuickToggle(context, palette, Glyph.BLUETOOTH, t("nativePlasmaBluetooth"), "", status.bluetoothEnabled) { toggle ->
        apply(toggle, !toggle.active, { it.bluetoothEnabled }) { controls.setBluetooth(it) }
    }
    private val connectionRow = PlasmaRow(context, palette, null, "", subtitle = "", heightDp = 40f, iconDp = 22f) {}
    private val cellularRow = PlasmaRow(context, palette, null, "", subtitle = "", heightDp = 40f, iconDp = 22f) {}
    private var cellularType: String? = null
    /** Details of the current Wi-Fi link, loaded once per connection change. */
    private var wifiInfo: PlasmaSystemControls.WifiConnection? = null
    private val list = verticalLayout(context)
    private val passwordArea = verticalLayout(context).apply { visibility = GONE }
    private val refresh = PlasmaIconButton(context, palette, Glyph.REFRESH) { scan() }
    private var scanning = false

    init {
        orientation = VERTICAL
        setPadding(dp(10f), dp(8f), dp(10f), dp(10f))
        addView(appletTitleRow(context, palette, t("nativePlasmaNetworks"), refresh))
        addView(toggleGrid(context, listOf(wifiToggle, dataToggle, airplaneToggle, bluetoothToggle)), LayoutParams(-1, -2))
        addView(sectionLabel(context, palette, t("nativePlasmaCurrentConnection")))
        addView(connectionRow, LayoutParams(-1, -2))
        addView(cellularRow, LayoutParams(-1, -2))
        addView(sectionLabel(context, palette, t("nativePlasmaAvailableNetworks")))
        addView(passwordArea, LayoutParams(-1, -2))
        addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            addView(list, LayoutParams(-1, -2))
        }, LayoutParams(-1, 0, 1f))
        addView(footerButton(context, palette, t("nativePlasmaConfigureNetwork"), onOpenSettings).also { it.layoutParams = footerParams(it) })
        render()
        loadConnection()
        loadCellular()
        scan()
    }

    private fun loadCellular() {
        if (!status.simPresent) return
        worker.load(this, { controls.cellularNetworkType().orEmpty() }) { type ->
            cellularType = type.takeIf { it.isNotEmpty() }
            render()
        }
    }

    private fun apply(
        toggle: QuickToggle,
        enabled: Boolean,
        state: (PlasmaSystemControls.NetworkStatus) -> Boolean,
        change: (Boolean) -> Boolean,
    ) {
        toggle.enabledToggle = false
        toggle.subtitle = t("nativePlasmaApplying")
        worker.load(this, {
            val ok = change(enabled)
            // Radios report their new state asynchronously: wait up to 5 s for it.
            var now = controls.networkStatus()
            var waited = 0L
            while (ok && state(now) != enabled && waited < 5_000L) {
                Thread.sleep(250L)
                waited += 250L
                now = controls.networkStatus()
            }
            ok to now
        }) { (ok, now) ->
            toggle.enabledToggle = true
            status = now
            render()
            if (!ok) toggle.subtitle = t("nativePlasmaUnavailable")
            onChanged()
            loadConnection()
            if (toggle === wifiToggle && enabled) scan()
        }
    }

    private fun render() {
        wifiToggle.active = status.wifiEnabled
        wifiToggle.subtitle = onOff(status.wifiEnabled)
        wifiToggle.glyph = if (status.wifiEnabled) Glyph.WIFI else Glyph.WIFI_OFF
        wifiToggle.level = status.wifiLevel ?: 1f
        dataToggle.active = status.mobileDataEnabled
        dataToggle.subtitle = listOfNotNull(status.carrierName, onOff(status.mobileDataEnabled)).joinToString(" · ")
        dataToggle.glyph = if (status.mobileDataEnabled) Glyph.CELLULAR else Glyph.CELLULAR_OFF
        dataToggle.level = status.cellularLevel ?: 1f
        airplaneToggle.active = status.airplaneMode
        airplaneToggle.subtitle = onOff(status.airplaneMode)
        bluetoothToggle.active = status.bluetoothEnabled
        bluetoothToggle.subtitle = onOff(status.bluetoothEnabled)
        bluetoothToggle.glyph = if (status.bluetoothEnabled) Glyph.BLUETOOTH else Glyph.BLUETOOTH_OFF
        val (glyph, title) = when (status.transport) {
            PlasmaSystemControls.Transport.WIFI -> Glyph.WIFI to "Wi-Fi"
            PlasmaSystemControls.Transport.CELLULAR -> Glyph.CELLULAR to (status.carrierName ?: t("nativePlasmaMobileData"))
            PlasmaSystemControls.Transport.ETHERNET -> Glyph.ETHERNET to "Ethernet"
            PlasmaSystemControls.Transport.OTHER -> Glyph.NETWORK to t("nativePlasmaConnected")
            PlasmaSystemControls.Transport.NONE ->
                (if (status.airplaneMode) Glyph.AIRPLANE else Glyph.WIFI_OFF) to t("nativePlasmaDisconnected")
        }
        connectionRow.icon = GlyphDrawable(glyph, palette.text).also { it.level01 = status.wifiLevel ?: status.cellularLevel ?: 1f }
        val wifiDetails = wifiInfo?.takeIf { status.transport == PlasmaSystemControls.Transport.WIFI }
        connectionRow.text = wifiDetails?.ssid ?: title
        connectionRow.subtitle = when {
            status.transport == PlasmaSystemControls.Transport.NONE -> t("nativePlasmaNoActiveNetwork")
            else -> listOfNotNull(
                if (status.internet) t("nativePlasmaConnected") else t("nativePlasmaNoInternet"),
                "VPN".takeIf { status.vpn },
                wifiDetails?.ipAddress,
                wifiDetails?.linkSpeedMbps?.let { "$it Mbps" },
                wifiDetails?.frequencyMhz?.let { if (it >= 5925) "6 GHz" else if (it >= 4900) "5 GHz" else "2.4 GHz" },
            ).joinToString(" · ")
        }
        connectionRow.invalidate()
        // The mobile network gets its own row unless it already is the current connection.
        val showCellular = status.simPresent && !status.airplaneMode && status.transport != PlasmaSystemControls.Transport.CELLULAR
        cellularRow.visibility = if (showCellular) VISIBLE else GONE
        if (showCellular) {
            cellularRow.icon = GlyphDrawable(if (status.cellularLevel == null) Glyph.CELLULAR_OFF else Glyph.CELLULAR, palette.text)
                .also { it.level01 = status.cellularLevel ?: 0f }
            cellularRow.text = status.carrierName ?: t("nativePlasmaMobileData")
            cellularRow.subtitle = listOfNotNull(
                cellularType,
                t("nativePlasmaMobileData") + " " + onOff(status.mobileDataEnabled),
            ).joinToString(" · ")
            cellularRow.invalidate()
        }
        if (status.transport == PlasmaSystemControls.Transport.CELLULAR && cellularType != null) {
            connectionRow.subtitle = cellularType + " · " + connectionRow.subtitle
        }
        refresh.visibility = if (status.wifiEnabled) VISIBLE else INVISIBLE
    }

    private fun loadConnection() {
        if (status.transport != PlasmaSystemControls.Transport.WIFI) return
        worker.load(this, { controls.wifiConnection() ?: PlasmaSystemControls.WifiConnection(null, null, null, null) }) { info ->
            wifiInfo = info
            render()
        }
    }

    private fun scan() {
        if (scanning) return
        list.removeAllViews()
        if (!status.wifiEnabled) {
            list.addView(message(t("nativePlasmaWifiIsOff")))
            return
        }
        scanning = true
        list.addView(message(t("nativePlasmaScanning")))
        worker.load(this, { controls.scanWifi() }) { networks ->
            scanning = false
            list.removeAllViews()
            if (networks.isEmpty()) list.addView(message(t("nativePlasmaNoNetworksFound")))
            networks.forEach { network -> list.addView(networkRow(network), LayoutParams(-1, -2)) }
        }
    }

    private fun message(text: String) = plasmaLabel(context, text, palette.inactiveText, 12.5f).apply {
        setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
    }

    private fun networkRow(network: PlasmaSystemControls.WifiNetwork): View {
        val state = listOfNotNull(
            when {
                network.connected -> t("nativePlasmaConnected")
                network.saved -> t("nativePlasmaSavedNetwork")
                else -> null
            },
            if (network.secured) t("nativePlasmaSecuredNetwork") else t("nativePlasmaOpenNetwork"),
        ).joinToString(" · ")
        val icon = GlyphDrawable(Glyph.WIFI, palette.text).also { it.level01 = network.level }
        return PlasmaRow(context, palette, icon, network.ssid, subtitle = state, heightDp = 34f, iconDp = 20f) {
            when {
                network.connected -> Unit
                network.secured && !network.saved -> askPassphrase(network)
                else -> connect(network, null)
            }
        }
    }

    private fun askPassphrase(network: PlasmaSystemControls.WifiNetwork) {
        passwordArea.removeAllViews()
        passwordArea.visibility = VISIBLE
        passwordArea.addView(plasmaLabel(context, network.ssid, palette.text, 13f, bold = true).apply {
            setPadding(dp(8f), 0, dp(8f), dp(6f))
        })
        val field = PlasmaPasswordField(context, palette, t("nativePlasmaPassword"))
        val submit = { connect(network, field.text?.toString()) }
        field.setOnEditorActionListener { _, action, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (action == EditorInfo.IME_ACTION_GO || enter) { submit(); true } else false
        }
        passwordArea.addView(field, LayoutParams(-1, dp(34f)))
        passwordArea.addView(horizontalLayout(context).apply {
            gravity = Gravity.END
            addView(PlasmaButton(context, palette, null, t("nativePlasmaCancel")) {
                passwordArea.visibility = GONE
            }, LayoutParams(-2, dp(32f)))
            addView(PlasmaButton(context, palette, Glyph.CHECK, t("nativePlasmaConnect"), flat = false) { submit() },
                LayoutParams(-2, dp(32f)).apply { leftMargin = dp(6f) })
        }, LayoutParams(-1, -2).apply { topMargin = dp(6f); bottomMargin = dp(6f) })
        field.requestFocus()
    }

    private fun connect(network: PlasmaSystemControls.WifiNetwork, passphrase: String?) {
        passwordArea.visibility = GONE
        list.removeAllViews()
        list.addView(message(t("nativePlasmaConnectingTo").format(network.ssid)))
        worker.load(this, {
            val ok = controls.connectWifi(network, passphrase)
            Thread.sleep(3_000L)
            ok
        }) { ok ->
            if (!ok) {
                list.removeAllViews()
                list.addView(message(t("nativePlasmaConnectionFailed")))
            }
            status = controls.networkStatus()
            render()
            onChanged()
            loadConnection()
            if (ok) scan()
        }
    }
}

/** "Audio Volume" popup: now playing, the output device and one slider per stream. */
@SuppressLint("ViewConstructor")
internal class AudioApplet(
    context: Context,
    private val palette: PlasmaPalette,
    private val controls: PlasmaSystemControls,
    private val worker: TrayWorker,
    private val onChanged: () -> Unit,
    onOpenSettings: () -> Unit,
) : LinearLayout(context) {
    private val mediaTitle = plasmaLabel(context, t("nativePlasmaNothingPlaying"), palette.text, 13.5f, bold = true)
    private val mediaArtist = plasmaLabel(context, "", palette.inactiveText, 12f)
    private val playPause = PlasmaIconButton(context, palette, Glyph.PLAY, 38f, 24f) {
        controls.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        refreshMediaSoon()
    }

    init {
        orientation = VERTICAL
        setPadding(dp(10f), dp(8f), dp(10f), dp(10f))
        addView(appletTitleRow(context, palette, t("nativePlasmaAudioVolume")))
        addView(mediaCard(), LayoutParams(-1, -2))
        addView(sectionLabel(context, palette, t("nativePlasmaVolume")))
        listOf(
            Triple(AudioManager.STREAM_MUSIC, Glyph.MUSIC, "nativePlasmaMediaVolume"),
            Triple(AudioManager.STREAM_RING, Glyph.RINGTONE, "nativePlasmaRingVolume"),
            Triple(AudioManager.STREAM_NOTIFICATION, Glyph.NOTIFICATIONS, "nativePlasmaNotificationVolume"),
            Triple(AudioManager.STREAM_ALARM, Glyph.ALARM, "nativePlasmaAlarmVolume"),
        ).forEach { (stream, glyph, label) -> addView(streamRow(stream, glyph, t(label)), LayoutParams(-1, -2)) }
        addView(outputRow(), LayoutParams(-1, -2))
        addView(footerButton(context, palette, t("nativePlasmaConfigureAudio"), onOpenSettings).also { it.layoutParams = footerParams(it) })
        refreshMedia()
    }

    private fun mediaCard(): View = horizontalLayout(context).apply {
        setPadding(dp(8f), dp(6f), dp(4f), dp(6f))
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(8f).toFloat()
            setColor(PlasmaTheme.withAlpha(palette.text, 0x12))
        }
        addView(View(context).apply { background = GlyphDrawable(Glyph.MUSIC, palette.accent) }, LayoutParams(dp(28f), dp(28f)))
        addView(verticalLayout(context).apply {
            setPadding(dp(10f), 0, dp(6f), 0)
            addView(mediaTitle)
            addView(mediaArtist)
        }, LayoutParams(0, -2, 1f))
        addView(PlasmaIconButton(context, palette, Glyph.SKIP_PREVIOUS) {
            controls.mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS); refreshMediaSoon()
        })
        addView(playPause)
        addView(PlasmaIconButton(context, palette, Glyph.SKIP_NEXT) {
            controls.mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT); refreshMediaSoon()
        })
    }

    private fun refreshMediaSoon() = postDelayed({ refreshMedia() }, 600L)

    private fun refreshMedia() {
        worker.load(this, { controls.nowPlaying() ?: PlasmaSystemControls.MediaInfo("", "", null, false) }) { media ->
            if (media.title.isEmpty()) {
                mediaTitle.text = t("nativePlasmaNothingPlaying")
                mediaArtist.text = ""
                playPause.glyph = Glyph.PLAY
            } else {
                mediaTitle.text = media.title
                mediaArtist.text = media.artist.orEmpty()
                playPause.glyph = if (media.playing) Glyph.PAUSE else Glyph.PLAY
            }
        }
    }

    private fun streamRow(stream: Int, glyph: Glyph, label: String): View {
        val volume = controls.streamVolume(stream)
        val percent = plasmaLabel(context, percentText(volume.level), palette.inactiveText, 12f).apply { gravity = Gravity.END }
        lateinit var slider: PlasmaSlider
        val mute = PlasmaIconButton(context, palette, if (volume.muted || volume.level <= 0f) Glyph.VOLUME_MUTED else glyph) {}
        fun refresh() {
            val now = controls.streamVolume(stream)
            slider.value = if (now.muted) 0f else now.level
            slider.invalidate()
            percent.text = percentText(slider.value)
            mute.glyph = if (now.muted || now.level <= 0f) Glyph.VOLUME_MUTED else glyph
            onChanged()
        }
        slider = PlasmaSlider(context, palette, if (volume.muted) 0f else volume.level) { value, finished ->
            percent.text = percentText(value)
            controls.setStreamVolume(stream, value)
            if (finished) refresh()
        }
        mute.onClick = { controls.toggleMute(stream); refresh() }
        return horizontalLayout(context).apply {
            setPadding(0, dp(2f), 0, dp(2f))
            addView(mute)
            addView(verticalLayout(context).apply {
                addView(plasmaLabel(context, label, palette.text, 12.5f).apply { setPadding(dp(10f), 0, 0, 0) })
                addView(slider, LayoutParams(-1, -2))
            }, LayoutParams(0, -2, 1f))
            addView(percent, LayoutParams(dp(44f), -2))
        }
    }

    private fun outputRow(): View {
        val device = controls.mediaOutput()
        val (glyph, name) = when (device?.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET -> Glyph.HEADPHONES to (device.productName?.toString()?.takeIf { it.isNotBlank() } ?: t("nativePlasmaHeadphones"))
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_SPEAKER ->
                Glyph.BLUETOOTH_CONNECTED to (device.productName?.toString()?.takeIf { it.isNotBlank() } ?: t("nativePlasmaBluetooth"))
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_REMOTE_SUBMIX ->
                Glyph.SPEAKER to (device.productName?.toString()?.takeIf { it.isNotBlank() } ?: t("nativePlasmaExternalOutput"))
            else -> Glyph.SPEAKER to t("nativePlasmaDeviceSpeaker")
        }
        return PlasmaRow(context, palette, GlyphDrawable(glyph, palette.text), name, subtitle = t("nativePlasmaOutputDevice"),
            heightDp = 34f, iconDp = 20f) {}.apply { setPadding(0, dp(6f), 0, 0) }
    }
}

/** "Power and Battery" popup: battery details, battery saver and screen brightness. */
@SuppressLint("ViewConstructor")
internal class PowerApplet(
    context: Context,
    private val palette: PlasmaPalette,
    private val controls: PlasmaSystemControls,
    private val worker: TrayWorker,
    private val onChanged: () -> Unit,
    onOpenSettings: () -> Unit,
) : LinearLayout(context) {
    private var status = controls.powerStatus()
    private val batteryIcon = View(context)
    private val percent = plasmaLabel(context, "", palette.text, 22f, bold = true)
    private val state = plasmaLabel(context, "", palette.inactiveText, 12f)
    private val details = plasmaLabel(context, "", palette.inactiveText, 12f)
    private val saver = QuickToggle(context, palette, Glyph.BATTERY_SAVER, t("nativePlasmaBatterySaver"), "", status.batterySaver) { toggle ->
        val target = !toggle.active
        toggle.enabledToggle = false
        worker.load(this, { controls.setBatterySaver(target).also { Thread.sleep(400L) } }) { ok ->
            toggle.enabledToggle = true
            status = controls.powerStatus()
            render()
            if (!ok) toggle.subtitle = t("nativePlasmaUnavailable")
            onChanged()
        }
    }
    private val autoBrightness = QuickToggle(context, palette, Glyph.BRIGHTNESS_AUTO, t("nativePlasmaAutoBrightness"), "", status.autoBrightness) { toggle ->
        val target = !toggle.active
        toggle.enabledToggle = false
        worker.load(this, { controls.setAutoBrightness(target).also { Thread.sleep(300L) } }) { ok ->
            toggle.enabledToggle = true
            status = controls.powerStatus()
            render()
            if (!ok) toggle.subtitle = t("nativePlasmaUnavailable")
        }
    }
    private var brightnessPending = false
    private val brightness = PlasmaSlider(context, palette, status.brightness) { value, finished ->
        if (finished || !brightnessPending) {
            brightnessPending = true
            worker.load(this, { controls.setBrightness(value) }) { brightnessPending = false }
        }
    }

    init {
        orientation = VERTICAL
        setPadding(dp(10f), dp(8f), dp(10f), dp(10f))
        addView(appletTitleRow(context, palette, t("nativePlasmaPowerAndBattery")))
        addView(horizontalLayout(context).apply {
            setPadding(dp(4f), 0, dp(4f), 0)
            addView(batteryIcon, LayoutParams(dp(44f), dp(44f)))
            addView(verticalLayout(context).apply {
                setPadding(dp(10f), 0, 0, 0)
                addView(percent)
                addView(state)
                addView(details)
            }, LayoutParams(0, -2, 1f))
        }, LayoutParams(-1, -2))
        addView(toggleGrid(context, listOf(saver, autoBrightness)), LayoutParams(-1, -2).apply { topMargin = dp(10f) })
        addView(sectionLabel(context, palette, t("nativePlasmaScreenBrightness")))
        addView(horizontalLayout(context).apply {
            addView(View(context).apply { background = GlyphDrawable(Glyph.BRIGHTNESS_LOW, palette.inactiveText) }, LayoutParams(dp(18f), dp(18f)))
            addView(brightness, LayoutParams(0, -2, 1f))
            addView(View(context).apply { background = GlyphDrawable(Glyph.BRIGHTNESS_HIGH, palette.inactiveText) }, LayoutParams(dp(18f), dp(18f)))
        }, LayoutParams(-1, -2).apply { leftMargin = dp(8f); rightMargin = dp(8f) })
        addView(footerButton(context, palette, t("nativePlasmaConfigurePower"), onOpenSettings).also { it.layoutParams = footerParams(it) })
        render()
    }

    private fun render() {
        batteryIcon.background = GlyphDrawable(if (status.charging) Glyph.BATTERY_CHARGING else Glyph.BATTERY,
            when {
                status.level < .15f && !status.charging -> palette.negative
                status.batterySaver -> palette.neutral
                status.charging -> palette.positive
                else -> palette.text
            }).also { it.level01 = status.level; it.filled = true }
        percent.text = percentText(status.level)
        val source = when (status.source) {
            PlasmaSystemControls.PowerSource.AC -> t("nativePlasmaPowerSourceAc")
            PlasmaSystemControls.PowerSource.USB -> "USB"
            PlasmaSystemControls.PowerSource.WIRELESS -> t("nativePlasmaPowerSourceWireless")
            PlasmaSystemControls.PowerSource.DOCK -> t("nativePlasmaPowerSourceDock")
            PlasmaSystemControls.PowerSource.NONE -> null
        }
        state.text = listOfNotNull(
            when {
                status.full -> t("nativePlasmaFullyCharged")
                status.charging -> t("nativePlasmaCharging")
                else -> t("nativePlasmaDischarging")
            },
            source,
            status.chargeTimeRemainingMs?.takeIf { status.charging }?.let { t("nativePlasmaTimeUntilFull").format(duration(it)) },
        ).joinToString(" · ")
        details.text = listOfNotNull(
            status.temperatureCelsius?.let { "%.1f °C".format(it) },
        ).joinToString(" · ")
        saver.active = status.batterySaver
        saver.subtitle = onOff(status.batterySaver)
        autoBrightness.active = status.autoBrightness
        autoBrightness.subtitle = onOff(status.autoBrightness)
        brightness.value = status.brightness
        brightness.invalidate()
    }

    private fun duration(ms: Long): String {
        val minutes = (ms / 60_000L).coerceAtLeast(1L)
        return if (minutes >= 60) t("nativePlasmaHoursMinutes").format(minutes / 60, minutes % 60)
        else t("nativePlasmaMinutes").format(minutes)
    }
}

/** "Notifications" popup: Android and Linux notifications, newest first. */
@SuppressLint("ViewConstructor")
internal class NotificationApplet(
    context: Context,
    private val palette: PlasmaPalette,
    private val onOpen: (DextopNotification) -> Unit,
    private val onDismiss: (DextopNotification) -> Unit,
) : LinearLayout(context) {
    private val list = verticalLayout(context)
    private val refresh: () -> Unit = { rebuild() }

    init {
        orientation = VERTICAL
        setPadding(dp(10f), dp(8f), dp(10f), dp(10f))
        val clearAll = PlasmaButton(context, palette, Glyph.CLOSE, t("nativePlasmaClearAll"), flat = true) {
            NotificationCenter.all().filter { it.clearable }.forEach(onDismiss)
        }
        addView(appletTitleRow(context, palette, t("nativePlasmaNotifications"), clearAll))
        addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            addView(list, LayoutParams(-1, -2))
        }, LayoutParams(-1, dp(420f)))
        rebuild()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotificationCenter.addListener(refresh)
    }

    override fun onDetachedFromWindow() {
        NotificationCenter.removeListener(refresh)
        super.onDetachedFromWindow()
    }

    private fun rebuild() {
        list.removeAllViews()
        val items = NotificationCenter.all()
        if (items.isEmpty()) {
            list.addView(plasmaLabel(context, t("nativePlasmaNoNotifications"), palette.inactiveText, 13f).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(48f), 0, dp(48f))
            }, LayoutParams(-1, -2))
            return
        }
        for ((linux, group) in items.groupBy { it.linux }.toSortedMap()) {
            list.addView(sectionLabel(context, palette, if (linux) "Linux" else "Android"))
            group.forEach { list.addView(card(it), LayoutParams(-1, -2).apply { bottomMargin = dp(6f) }) }
        }
    }

    private fun card(item: DextopNotification): View = horizontalLayout(context).apply {
        gravity = Gravity.TOP
        setPadding(dp(8f), dp(8f), dp(4f), dp(8f))
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(8f).toFloat()
            setColor(PlasmaTheme.withAlpha(palette.text, 0x12))
        }
        val icon = item.icon ?: GlyphDrawable(if (item.linux) Glyph.COMPUTER else Glyph.NOTIFICATIONS, palette.text)
        addView(View(context).apply { background = icon }, LayoutParams(dp(28f), dp(28f)))
        addView(verticalLayout(context).apply {
            setPadding(dp(10f), 0, dp(6f), 0)
            val time = android.text.format.DateUtils.getRelativeTimeSpanString(item.time).toString()
            addView(plasmaLabel(context, "${item.app} · $time", palette.inactiveText, 11f))
            if (item.title.isNotBlank()) addView(plasmaLabel(context, item.title, palette.text, 13.5f, bold = true))
            if (item.text.isNotBlank()) addView(plasmaLabel(context, item.text, palette.text, 12.5f).apply {
                isSingleLine = false
                maxLines = 4
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }, LayoutParams(0, -2, 1f))
        if (item.clearable) addView(PlasmaIconButton(context, palette, Glyph.CLOSE, 28f, 16f) { onDismiss(item) })
        isClickable = true
        setOnClickListener { onOpen(item) }
    }
}
