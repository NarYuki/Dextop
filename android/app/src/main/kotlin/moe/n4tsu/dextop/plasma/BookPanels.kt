@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.n4tsu.dextop.plasma

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Date

/** A floating panel's surface: Material 3 container, extra-large corners and elevation. */
@Composable
internal fun BookPanelSurface(width: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.width(width).blockPointer(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) { content() }
}

/** Network state and the Wi-Fi list, shared by the quick settings and the Settings app. */
internal class WifiState(private val host: BookHost) {
    var enabled by mutableStateOf(true)
    var networks by mutableStateOf<List<PlasmaSystemControls.WifiNetwork>?>(null)
    var connecting by mutableStateOf<String?>(null)
    var asking by mutableStateOf<PlasmaSystemControls.WifiNetwork?>(null)
    var failed by mutableStateOf<String?>(null)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun scan() {
        host.background {
            val status = runCatching { host.controls.networkStatus() }.getOrNull()
            val list = if (status?.wifiEnabled == false) emptyList() else runCatching { host.controls.scanWifi() }.getOrDefault(emptyList())
            main.post {
                enabled = status?.wifiEnabled ?: enabled
                networks = list
            }
        }
    }

    fun switchWifi(value: Boolean) {
        enabled = value
        if (!value) networks = emptyList()
        host.background {
            host.controls.setWifiEnabled(value)
            main.post { host.refreshTray(); if (value) { networks = null; scan() } }
        }
    }

    fun connect(network: PlasmaSystemControls.WifiNetwork, passphrase: String?) {
        asking = null
        connecting = network.ssid
        failed = null
        host.background {
            val ok = host.controls.connectWifi(network, passphrase)
            android.os.SystemClock.sleep(1_500L)
            main.post {
                connecting = null
                if (!ok) failed = network.ssid
                host.refreshTray()
                scan()
            }
        }
    }

    fun choose(network: PlasmaSystemControls.WifiNetwork) {
        when {
            network.connected -> host.openSystemSettings(Intent(Settings.ACTION_WIFI_SETTINGS))
            network.secured && !network.saved -> asking = network
            else -> connect(network, null)
        }
    }
}

/** The Wi-Fi networks, strongest first; a secured one asks for its password in place. */
@Composable
internal fun WifiList(state: WifiState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val networks = state.networks
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (networks == null) item {
            Text(tr("ネットワークを検索しています…", "Searching for networks…"), color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        } else if (networks.isEmpty()) item {
            Text(if (state.enabled) tr("ネットワークが見つかりません", "No networks found") else tr("Wi-Fi はオフです", "Wi-Fi is off"),
                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
        items(networks.orEmpty(), key = { it.ssid }) { network ->
            val asking = state.asking?.ssid == network.ssid
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(if (network.connected) colors.primaryContainer else colors.surfaceContainerHigh)) {
                Row(
                    Modifier.fillMaxWidth().clickable { state.choose(network) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val tint = if (network.connected) colors.onPrimaryContainer else colors.onSurface
                    Sym(wifiSymbol(network.level), tint, 22.dp, filled = true)
                    Column(Modifier.weight(1f)) {
                        Text(network.ssid, style = MaterialTheme.typography.titleSmall, color = tint, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        val status = when {
                            state.connecting == network.ssid -> tr("接続しています…", "Connecting…")
                            state.failed == network.ssid -> tr("接続できませんでした", "Couldn't connect")
                            network.connected -> tr("接続済み", "Connected")
                            network.saved -> tr("保存済み", "Saved")
                            network.secured -> null
                            else -> tr("オープン", "Open")
                        }
                        if (status != null) Text(status, style = MaterialTheme.typography.bodySmall,
                            color = if (network.connected) colors.onPrimaryContainer else colors.onSurfaceVariant)
                    }
                    if (network.secured && !network.connected) Sym("lock", colors.onSurfaceVariant, 18.dp)
                    if (network.connected) Sym("settings", tint, 22.dp)
                }
                AnimatedVisibility(asking) {
                    var password by remember { mutableStateOf("") }
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = CircleShape,
                            placeholder = { Text(tr("パスワード", "Password")) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { state.connect(network, password) }),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        BookIconButton("arrow_forward", { state.connect(network, password) }, selected = true, size = 48.dp)
                    }
                }
            }
        }
    }
}

internal fun wifiSymbol(level: Float) = when {
    level <= 0f -> "signal_wifi_0_bar"
    level < .34f -> "wifi_1_bar"
    level < .67f -> "wifi_2_bar"
    else -> "wifi"
}

/**
 * Book's floating panels: the quick settings (with their Internet, Bluetooth and sound pages),
 * notifications, the calendar, the media player and the taskbar menu.
 */
internal class BookPanels(private val context: Context, private val host: BookHost) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    // region quick settings
    private var page by mutableStateOf("main")
    private var network by mutableStateOf<PlasmaSystemControls.NetworkStatus?>(null)
    private var power by mutableStateOf<PlasmaSystemControls.PowerStatus?>(null)
    private var ssid by mutableStateOf<String?>(null)
    private var dnd by mutableStateOf(false)
    private var nightLight by mutableStateOf(false)
    private var volume by mutableStateOf(.5f)
    private var micBlocked by mutableStateOf(false)
    private var cameraBlocked by mutableStateOf(false)
    private var powerMenu by mutableStateOf(false)
    private var streams by mutableStateOf<List<PlasmaSystemControls.StreamVolume>>(emptyList())
    private var output by mutableStateOf<String?>(null)
    private val wifi = WifiState(host)

    /** Reads the switches' state; the panel shows the last known one meanwhile. */
    fun loadQuickSettings(startPage: String = "main") {
        page = startPage
        powerMenu = false
        volume = host.controls.streamVolume(AudioManager.STREAM_MUSIC).level
        host.background {
            val n = runCatching { host.controls.networkStatus() }.getOrNull()
            val p = runCatching { host.controls.powerStatus() }.getOrNull()
            val name = if (n?.transport == PlasmaSystemControls.Transport.WIFI) runCatching { host.controls.wifiConnection()?.ssid }.getOrNull() else null
            val d = runCatching { host.controls.doNotDisturb() }.getOrDefault(false)
            val night = runCatching { host.controls.nightLight() }.getOrDefault(false)
            main.post {
                network = n; power = p; ssid = name?.trim('"'); dnd = d; nightLight = night
            }
        }
        if (startPage == "internet") { wifi.networks = null; wifi.scan() }
    }

    private fun toggle(work: () -> Unit) = host.background {
        work()
        main.post { host.refreshTray(); loadQuickSettings(page) }
    }

    @Composable
    fun QuickSettings() {
        BookPanelSurface(420.dp) {
            AnimatedContent(page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "qs") { current ->
                when (current) {
                    "internet" -> InternetPage()
                    "bluetooth" -> BluetoothPage()
                    "sound" -> SoundPage()
                    else -> MainPage()
                }
            }
        }
    }

    @Composable
    private fun MainPage() {
        val colors = MaterialTheme.colorScheme
        val settings = host.settings()
        val dark = BookTheme.dark(context, settings)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookIconButton("widgets", { host.closePanel(); host.openWidgetGallery() }, tint = colors.onSurface)
                BookIconButton("settings", { host.closePanel(); host.openBookSettings() }, tint = colors.onSurface)
                Spacer(Modifier.weight(1f))
                Row(Modifier.clip(CircleShape).clickable { powerMenu = !powerMenu }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Sym("power_settings_new", colors.onSurface, 22.dp)
                    Sym(if (powerMenu) "expand_less" else "expand_more", colors.onSurface, 20.dp)
                }
            }
            AnimatedVisibility(powerMenu) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh)) {
                    PowerRow("bedtime", tr("スリープ", "Sleep")) { host.leave(LeaveAction.SLEEP) }
                    PowerRow("restart_alt", tr("Dextop Book を再起動", "Restart Dextop Book")) { host.leave(LeaveAction.RESTART) }
                    PowerRow("desktop_windows", tr("Android のデスクトップに切り替え", "Switch to the Android desktop")) { host.leave(LeaveAction.LOGOUT) }
                    PowerRow("logout", tr("セッションを終了", "End session")) { host.leave(LeaveAction.SHUTDOWN) }
                }
            }
            val brightness = power?.brightness ?: .5f
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookSlider(brightness, "brightness_high", onChange = { level ->
                    power = power?.copy(brightness = level)
                }, onCommit = { level -> host.background { host.controls.setBrightness(level) } }, modifier = Modifier.weight(1f))
                BookIconButton("dark_mode", {
                    host.applySettings(settings.copy(colorScheme = if (dark) "light" else "dark"))
                }, size = 48.dp, selected = dark, container = if (dark) colors.primary else colors.surfaceContainerHighest)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookSlider(volume, if (volume <= 0f) "volume_off" else "volume_up", onChange = { level ->
                    volume = level
                    host.controls.setStreamVolume(AudioManager.STREAM_MUSIC, level)
                }, onCommit = { host.refreshTray() }, modifier = Modifier.weight(1f))
                BookIconButton("tune", { openSound() }, size = 48.dp, container = colors.surfaceContainerHighest, tint = colors.onSurface)
            }
            val n = network
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val wifiOn = n?.wifiEnabled ?: true
                BigTile(Modifier.weight(1f), if (wifiOn) "wifi" else "wifi_off", "Wi-Fi",
                    when {
                        !wifiOn -> tr("オフ", "Off")
                        ssid != null -> ssid!!
                        n?.transport == PlasmaSystemControls.Transport.CELLULAR -> n.carrierName ?: tr("モバイルデータ", "Mobile data")
                        else -> tr("未接続", "Not connected")
                    }, wifiOn,
                    onIcon = { toggle { host.controls.setWifiEnabled(!wifiOn) } },
                    onClick = { page = "internet"; wifi.networks = null; wifi.scan() })
                val btOn = n?.bluetoothEnabled ?: false
                BigTile(Modifier.weight(1f), if (btOn) "bluetooth" else "bluetooth_disabled", "Bluetooth",
                    if (btOn) tr("オン", "On") else tr("オフ", "Off"), btOn,
                    onIcon = { toggle { host.controls.setBluetooth(!btOn) } },
                    onClick = { page = "bluetooth" })
            }
            val tiles = buildList {
                add(Tile("do_not_disturb_on", tr("通知をミュート", "Do Not Disturb"), dnd) { dnd = !dnd; toggle { host.controls.setDoNotDisturb(!host.controls.doNotDisturb()) } })
                add(Tile("battery_saver", tr("バッテリー セーバー", "Battery Saver"), power?.batterySaver == true) {
                    val next = power?.batterySaver != true
                    power = power?.copy(batterySaver = next)
                    toggle { host.controls.setBatterySaver(next) }
                })
                add(Tile("airplanemode_active", tr("機内モード", "Airplane mode"), n?.airplaneMode == true) {
                    val next = n?.airplaneMode != true
                    toggle { host.controls.setAirplaneMode(next) }
                })
                if (n?.simPresent == true) add(Tile("signal_cellular_alt", tr("モバイルデータ", "Mobile data"), n.mobileDataEnabled) {
                    val next = !n.mobileDataEnabled
                    toggle { host.controls.setMobileData(next) }
                })
                add(Tile("nightlight", tr("夜間モード", "Night Light"), nightLight) {
                    val next = !nightLight
                    nightLight = next
                    toggle { host.controls.setNightLight(next) }
                })
                add(Tile(if (micBlocked) "mic_off" else "mic", tr("マイクへのアクセス", "Microphone access"), !micBlocked) {
                    micBlocked = !micBlocked
                    val blocked = micBlocked
                    host.background { host.controls.setSensorBlocked("microphone", blocked) }
                })
                add(Tile(if (cameraBlocked) "videocam_off" else "videocam", tr("カメラへのアクセス", "Camera access"), !cameraBlocked) {
                    cameraBlocked = !cameraBlocked
                    val blocked = cameraBlocked
                    host.background { host.controls.setSensorBlocked("camera", blocked) }
                })
                add(Tile("calculate", tr("電卓", "Calculator"), false) { openCalculator() })
                add(Tile("accessibility_new", tr("ユーザー補助", "Accessibility"), false) {
                    host.closePanel(); host.openSystemSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
                add(Tile("edit_notifications", tr("通知の設定", "Notification settings"), false) {
                    host.closePanel(); host.openSystemSettings(Intent(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS))
                })
                add(Tile("screen_rotation", tr("画面の設定", "Display settings"), false) {
                    host.closePanel(); host.openSystemSettings(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                })
            }
            tiles.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { tile -> SmallTile(tile, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            power?.let { p ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sym("smartphone", colors.onSurfaceVariant, 18.dp)
                    Text(android.os.Build.MODEL, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Sym(if (p.charging) "battery_charging_full" else "battery_full", colors.onSurfaceVariant, 18.dp)
                    Text(percentText(p.level), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
            }
        }
    }

    private class Tile(val symbol: String, val label: String, val on: Boolean, val action: () -> Unit)

    @Composable
    private fun PowerRow(symbol: String, label: String, action: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable { host.closePanel(); action() }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Sym(symbol, MaterialTheme.colorScheme.onSurface, 22.dp)
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    @Composable
    private fun BigTile(modifier: Modifier, symbol: String, title: String, subtitle: String, on: Boolean,
                        onIcon: () -> Unit, onClick: () -> Unit) {
        val colors = MaterialTheme.colorScheme
        Row(modifier.height(72.dp).clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerHigh).clickable(onClick = onClick)
            .padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(if (on) colors.primary else colors.surfaceContainerHighest)
                .clickable(onClick = onIcon), contentAlignment = Alignment.Center) {
                Sym(symbol, if (on) colors.onPrimary else colors.onSurfaceVariant, 24.dp, filled = on)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun SmallTile(tile: Tile, modifier: Modifier) {
        val colors = MaterialTheme.colorScheme
        var hovered by remember { mutableStateOf(false) }
        Box(
            modifier.height(64.dp).clip(RoundedCornerShape(if (tile.on) 20.dp else 32.dp))
                .background(if (tile.on) colors.primary else if (hovered) colors.surfaceContainerHighest else colors.surfaceContainerHigh)
                .pointerButtons(onHover = { hovered = it })
                .clickable(onClick = tile.action),
            contentAlignment = Alignment.Center,
        ) { Sym(tile.symbol, if (tile.on) colors.onPrimary else colors.onSurface, 24.dp, filled = tile.on) }
    }

    @Composable
    private fun PageHeader(title: String, subtitle: String? = null, settings: Intent? = null) {
        val colors = MaterialTheme.colorScheme
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BookIconButton("arrow_back", { page = "main"; loadQuickSettings() }, tint = colors.onSurface)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (settings != null) BookIconButton("settings", { host.closePanel(); host.openSystemSettings(settings) }, tint = colors.onSurface)
            else Spacer(Modifier.size(40.dp))
        }
    }

    @Composable
    private fun InternetPage() {
        val colors = MaterialTheme.colorScheme
        LaunchedEffect(Unit) { wifi.enabled = network?.wifiEnabled ?: true }
        Column(Modifier.padding(20.dp).heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeader(tr("インターネット", "Internet"), tr("ネットワークをタップして接続", "Tap a network to connect"),
                Intent(Settings.ACTION_WIFI_SETTINGS))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { wifi.switchWifi(!wifi.enabled) }
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Wi-Fi", style = MaterialTheme.typography.titleMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
                Switch(checked = wifi.enabled, onCheckedChange = { wifi.switchWifi(it) })
            }
            WifiList(wifi, Modifier.weight(1f, fill = false).heightIn(min = 120.dp, max = 380.dp))
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(colors.surfaceContainerHighest)
                .clickable { host.closePanel(); host.openSystemSettings(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }.padding(14.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Sym("settings", colors.onSurface, 20.dp)
                Text(tr("ネットワークとインターネットの設定", "Network & internet settings"), style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }

    @Composable
    private fun BluetoothPage() {
        val colors = MaterialTheme.colorScheme
        val on = network?.bluetoothEnabled ?: false
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeader("Bluetooth", null, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { toggle { host.controls.setBluetooth(!on) } }
                .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Bluetooth を使用", "Use Bluetooth"), style = MaterialTheme.typography.titleMedium, color = colors.onSurface,
                    modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = { value -> network = network?.copy(bluetoothEnabled = value); toggle { host.controls.setBluetooth(value) } })
            }
            BookListItem(tr("新しいデバイスとペア設定", "Pair new device"), null, "add", RoundedCornerShape(20.dp),
                onClick = { host.closePanel(); host.openSystemSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
            BookListItem(tr("接続済みのデバイス", "Connected devices"), tr("ペア設定済みのデバイスを管理", "Manage paired devices"),
                "devices", RoundedCornerShape(20.dp),
                onClick = { host.closePanel(); host.openSystemSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
        }
    }

    private fun openSound() {
        streams = host.controls.streams.map { host.controls.streamVolume(it) }
        output = host.controls.mediaOutput()?.productName?.toString()
        page = "sound"
    }

    @Composable
    private fun SoundPage() {
        val colors = MaterialTheme.colorScheme
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeader(tr("サウンド", "Sound"), output?.let { trf("出力: {0}", "Output: {0}", it) }, Intent(Settings.ACTION_SOUND_SETTINGS))
            streams.forEach { stream ->
                val (symbol, label) = when (stream.stream) {
                    AudioManager.STREAM_MUSIC -> "music_note" to tr("メディア", "Media")
                    AudioManager.STREAM_RING -> "ring_volume" to tr("着信音", "Ring")
                    AudioManager.STREAM_NOTIFICATION -> "notifications" to tr("通知", "Notifications")
                    else -> "alarm" to tr("アラーム", "Alarm")
                }
                Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                BookSlider(stream.level, symbol, onChange = { level ->
                    streams = streams.map { if (it.stream == stream.stream) it.copy(level = level) else it }
                    host.controls.setStreamVolume(stream.stream, level)
                    if (stream.stream == AudioManager.STREAM_MUSIC) volume = level
                }, onCommit = { host.refreshTray() }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    private fun openCalculator() {
        host.closePanel()
        val apps = host.apps()
        val calculator = apps.firstOrNull { it.packageName.contains("calculator", true) || it.packageName.contains("calc", true) }
        if (calculator != null) host.launch(calculator.packageName)
        else host.openSystemSettings(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR))
    }
    // endregion

    // region notifications
    private var expanded by mutableStateOf(setOf<String>())

    @Composable
    fun Notifications(items: List<DextopNotification>) {
        val colors = MaterialTheme.colorScheme
        BookPanelSurface(420.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (dnd) tr("サイレント", "Silent") else tr("通知", "Notifications"), style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface, modifier = Modifier.weight(1f))
                    BookIconButton("close", { host.closePanel() }, tint = colors.onSurface)
                }
                if (items.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Sym("notifications_off", colors.onSurfaceVariant, 40.dp)
                        Text(tr("通知はありません", "No notifications"), style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(items, key = { it.key }) { item -> NotificationCard(item) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookIconButton("history", {
                        host.closePanel(); host.openSystemSettings(Intent("android.settings.NOTIFICATION_HISTORY"))
                    }, size = 48.dp, container = colors.surfaceContainerHighest, tint = colors.onSurface)
                    Box(Modifier.weight(1f).height(48.dp).clip(CircleShape).background(colors.surfaceContainerHighest)
                        .clickable(enabled = items.any { it.clearable }) { items.filter { it.clearable }.forEach(host::dismissNotification) },
                        contentAlignment = Alignment.Center) {
                        Text(tr("すべて消去", "Clear all"), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    }
                    BookIconButton(if (dnd) "notifications_off" else "edit_notifications", {
                        dnd = !dnd
                        val next = dnd
                        host.background { host.controls.setDoNotDisturb(next) }
                    }, size = 48.dp, selected = dnd, container = if (dnd) colors.primary else colors.surfaceContainerHighest,
                        tint = colors.onSurface)
                }
            }
        }
    }

    @Composable
    private fun NotificationCard(item: DextopNotification) {
        val colors = MaterialTheme.colorScheme
        val open = item.key in expanded
        var hovered by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerHigh)
            .pointerButtons(onHover = { hovered = it })
            .clickable { host.openNotification(item) }.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                if (item.icon != null) DrawableIcon(item.icon, 26.dp) else Sym("notifications", colors.onPrimaryContainer, 22.dp)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${item.app} • ${DateUtils.getRelativeTimeSpanString(item.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE)}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (hovered && item.clearable) BookIconButton("close", { host.dismissNotification(item) }, size = 28.dp)
                    BookIconButton(if (open) "expand_less" else "expand_more",
                        { expanded = if (open) expanded - item.key else expanded + item.key }, size = 28.dp,
                        container = colors.surfaceContainerHighest)
                }
                if (item.title.isNotBlank()) Text(item.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface,
                    maxLines = if (open) 4 else 1, overflow = TextOverflow.Ellipsis)
                if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    maxLines = if (open) 12 else 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    // endregion

    // region calendar
    private var shownMonth by mutableStateOf(Calendar.getInstance())
    private var pickingMonth by mutableStateOf(false)

    fun resetCalendar() {
        shownMonth = Calendar.getInstance()
        pickingMonth = false
    }

    @Composable
    fun CalendarPanel() {
        val colors = MaterialTheme.colorScheme
        BookPanelSurface(380.dp) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clip(CircleShape).clickable { pickingMonth = !pickingMonth }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(monthTitle(shownMonth), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                        Sym("arrow_drop_down", colors.onSurface, 24.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    BookIconButton("chevron_left", { shift(if (pickingMonth) -12 else -1) }, tint = colors.onSurface)
                    BookIconButton("chevron_right", { shift(if (pickingMonth) 12 else 1) }, tint = colors.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                if (pickingMonth) {
                    val symbols = java.text.DateFormatSymbols.getInstance().shortMonths
                    (0 until 12).chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { month ->
                                val selected = month == shownMonth.get(Calendar.MONTH)
                                Box(Modifier.weight(1f).padding(4.dp).height(48.dp).clip(CircleShape)
                                    .background(if (selected) colors.primary else Color.Transparent)
                                    .clickable {
                                        shownMonth = (shownMonth.clone() as Calendar).apply { set(Calendar.MONTH, month) }
                                        pickingMonth = false
                                    }, contentAlignment = Alignment.Center) {
                                    Text(symbols[month], color = if (selected) colors.onPrimary else colors.onSurface)
                                }
                            }
                        }
                    }
                } else {
                    MonthGrid(shownMonth, Modifier.fillMaxWidth())
                }
            }
        }
    }

    private fun shift(months: Int) {
        shownMonth = (shownMonth.clone() as Calendar).apply { add(Calendar.MONTH, months) }
    }
    // endregion

    // region media (the live activity's panel)
    @Composable
    fun MediaPanel() {
        val colors = MaterialTheme.colorScheme
        val media = BookLive.media
        BookPanelSurface(380.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                        val art = BookLive.mediaArt
                        val icon = BookLive.mediaIcon
                        when {
                            art != null -> androidx.compose.foundation.Image(remember(art) { art.asImageBitmap() }, null, Modifier.size(64.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            icon != null -> DrawableIcon(icon, 44.dp)
                            else -> Sym("music_note", colors.onPrimaryContainer, 32.dp)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(media?.title ?: tr("再生中のメディアはありません", "Nothing playing"), style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        media?.artist?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    BookIconButton("skip_previous", { host.controls.mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, size = 52.dp, tint = colors.onSurface)
                    BookIconButton(if (media?.playing == true) "pause" else "play_arrow",
                        { host.controls.mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, size = 64.dp, selected = true)
                    BookIconButton("skip_next", { host.controls.mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) }, size = 52.dp, tint = colors.onSurface)
                }
                if (media != null && media.packageName.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().clip(CircleShape).background(colors.surfaceContainerHighest)
                        .clickable { host.closePanel(); host.launch(media.packageName) }.padding(12.dp),
                        horizontalArrangement = Arrangement.Center) {
                        Text(tr("アプリを開く", "Open app"), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    }
                }
            }
        }
    }
    // endregion

    // region taskbar menu
    @Composable
    fun TaskbarMenu(alwaysShow: Boolean, onToggle: (Boolean) -> Unit) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.blockPointer(), shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh) {
            Row(Modifier.clickable { onToggle(!alwaysShow) }.padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Sym(if (alwaysShow) "visibility" else "visibility_off", colors.onSurface, 22.dp)
                Text(tr("タスクバーを常に表示", "Always show Taskbar"), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                Switch(checked = alwaysShow, onCheckedChange = onToggle)
            }
        }
    }
    // endregion

    /** "Today, 10:20" style text for the status bar's notification count tooltip. */
    fun clockText(now: Date): String = DateFormat.getTimeFormat(context).format(now)
}
