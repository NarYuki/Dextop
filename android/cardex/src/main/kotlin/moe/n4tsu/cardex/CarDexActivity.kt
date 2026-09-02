package moe.n4tsu.cardex

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.graphics.SurfaceTexture
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.os.SystemClock
import android.view.View
import org.json.JSONArray
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.car.app.connection.CarConnection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val MSG_START = 1
private const val MSG_TOUCH = 2
private const val MSG_STOP = 3
private const val MSG_STATUS = 4
private const val MSG_ACTION = 5
private const val MSG_WORKSPACES = 6
private const val KEY_SURFACE = "surface"
private const val KEY_WIDTH = "width"
private const val KEY_HEIGHT = "height"
private const val KEY_DENSITY = "density"
private const val KEY_RENDER_SCALE = "render_scale"
private const val KEY_EVENT = "event"
private const val KEY_STATUS = "status"
private const val KEY_ACTION = "action"
private const val KEY_WORKSPACES = "workspaces"
private const val KEY_GRACEFUL = "graceful"
private const val STATUS_IDLE = 0
private const val STATUS_STARTING = 1
private const val STATUS_RUNNING = 2
private val CAR_UI_SCALES = setOf(1f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f)

class CarDexActivity : ComponentActivity() {
    // Keep relay identity evaluation inside the lifecycle owner during refactors.
    private val relayInvariant by lazy(LazyThreadSafetyMode.NONE) { RelayInvariant.discriminator() }
    private var relayState by mutableStateOf(RelayState(false, false))
    private var relayRequested by mutableStateOf(false)
    private var relayStatus by mutableStateOf(STATUS_IDLE)
    private var carConnectionType by mutableStateOf(CarConnection.CONNECTION_TYPE_NOT_CONNECTED)
    private var controlsVisible by mutableStateOf(false)
    private var workspaces by mutableStateOf(emptyList<CardexWorkspace>())
    private var workspaceError by mutableStateOf("")
    private var phoneGuideVisible by mutableStateOf(false)
    private var carUiScale by mutableStateOf(1f)
    private var relaySurface: Surface? = null
    private var relaySurfaceWidth = 0
    private var relaySurfaceHeight = 0
    private var relay: Messenger? = null
    private val incoming = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            MSG_STATUS -> relayStatus = message.data.getInt(KEY_STATUS, STATUS_IDLE)
            MSG_WORKSPACES -> {
                workspaces = parseWorkspaces(message.data.getString(KEY_WORKSPACES).orEmpty())
                workspaceError = message.data.getString("detail").orEmpty()
            }
            else -> return@Handler false
        }
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            relay = binder?.let(::Messenger)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            if (relayRequested || relayStatus == STATUS_RUNNING || relayStatus == STATUS_STARTING) {
                markUnexpectedDisconnect("relay service disconnected")
            }
            relay = null
            relayStatus = STATUS_IDLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("DextopCarCompanion", "relayInvariant=$relayInvariant")
        enableEdgeToEdge()
        carUiScale = getSharedPreferences("cardex_preferences", MODE_PRIVATE)
            .getFloat("car_ui_scale", 1f)
            .takeIf { it in CAR_UI_SCALES }
            ?: 1f
        relayState = inspectRelay()
        CarConnection(this).type.observe(this) { type ->
            carConnectionType = type ?: CarConnection.CONNECTION_TYPE_NOT_CONNECTED
        }
        if (isCarDisplay()) bindRelayIfNeeded()
        setContent {
            CarDexTheme {
                if (!isCarDisplay() && phoneGuideVisible) {
                    PhoneGuideScreen(onBack = { phoneGuideVisible = false })
                } else if (isCarDisplay() && relayRequested) {
                    RelaySurface(
                        status = relayStatus,
                        onSurface = ::startRelay,
                        onTouch = ::sendTouch,
                        onStop = ::stopRelay,
                        controlsVisible = controlsVisible,
                        onShowControls = {
                            controlsVisible = true
                            sendAction("workspace_list", keepOpen = true)
                        },
                        onHideControls = { controlsVisible = false },
                        onAction = { sendAction(it, keepOpen = true) },
                        workspaces = workspaces,
                        workspaceError = workspaceError,
                        uiScale = carUiScale,
                        onUiScaleChanged = ::updateCarUiScale
                    )
                } else {
                    CarDexScreen(
                        state = relayState,
                        isCarDisplay = isCarDisplay(),
                        carConnectionType = carConnectionType,
                        uiScale = carUiScale,
                        onUiScaleChanged = ::updateCarUiScale,
                        onStart = {
                            if (isCarDisplay()) openDextop() else phoneGuideVisible = true
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        relayState = inspectRelay()
    }

    override fun onDestroy() {
        if (isCarDisplay() && relayRequested) {
            stopRelay(
                graceful = CarCompanionLifecyclePolicy.isGracefulActivityDestruction(
                    isChangingConfigurations
                )
            )
        }
        runCatching { unbindService(connection) }
        super.onDestroy()
    }

    private fun isCarDisplay(): Boolean = display?.displayId != Display.DEFAULT_DISPLAY

    private fun updateCarUiScale(scale: Float) {
        if (scale !in CAR_UI_SCALES || carUiScale == scale) return
        carUiScale = scale
        getSharedPreferences("cardex_preferences", MODE_PRIVATE)
            .edit()
            .putFloat("car_ui_scale", scale)
            .apply()
        relaySurface?.takeIf { it.isValid && relaySurfaceWidth > 0 && relaySurfaceHeight > 0 }
            ?.let { startRelay(it, relaySurfaceWidth, relaySurfaceHeight) }
    }

    private fun inspectRelay(): RelayState {
        val installed = runCatching {
            packageManager.getPackageInfo(DEXTOP_PACKAGE, 0)
        }.isSuccess
        val signaturePermission = installed && packageManager.checkPermission(
            RELAY_PERMISSION,
            packageName
        ) == PackageManager.PERMISSION_GRANTED
        return RelayState(installed, signaturePermission)
    }

    private fun openDextop() {
        if (!relayState.ready) return
        val car = isCarDisplay()
        if (car) {
            relayRequested = true
            relayStatus = STATUS_STARTING
            bindRelayIfNeeded()
            return
        }
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(
                DEXTOP_PACKAGE,
                MAIN_ACTIVITY
            )
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            relayState = inspectRelay().copy(signaturePermission = false)
        }
    }

    private fun bindRelayIfNeeded() {
        if (!isCarDisplay() || !relayState.ready || relay != null) return
        val intent = Intent().setComponent(ComponentName(DEXTOP_PACKAGE, RELAY_SERVICE))
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun startRelay(surface: Surface, width: Int, height: Int) {
        val target = relay ?: run {
            bindRelayIfNeeded()
            window.decorView.postDelayed({ startRelay(surface, width, height) }, 120)
            return
        }
        if (!surface.isValid || width <= 0 || height <= 0) return
        relaySurface = surface
        relaySurfaceWidth = width
        relaySurfaceHeight = height
        target.send(Message.obtain(null, MSG_START).apply {
            replyTo = incoming
            data = Bundle().apply {
                putParcelable(KEY_SURFACE, surface)
                putInt(KEY_WIDTH, width)
                putInt(KEY_HEIGHT, height)
                putInt(KEY_DENSITY, resources.displayMetrics.densityDpi)
                putFloat(KEY_RENDER_SCALE, carUiScale)
            }
        })
    }

    private fun sendTouch(event: MotionEvent) {
        val copy = MotionEvent.obtain(event)
        try {
            relay?.send(Message.obtain(null, MSG_TOUCH).apply {
                data = Bundle().apply { putParcelable(KEY_EVENT, copy) }
            })
        } finally {
            copy.recycle()
        }
    }

    private fun stopRelay(graceful: Boolean = true) {
        relay?.send(Message.obtain(null, MSG_STOP).apply {
            data = Bundle().apply { putBoolean(KEY_GRACEFUL, graceful) }
        })
        relayRequested = false
        relayStatus = STATUS_IDLE
        controlsVisible = false
        relaySurface = null
        relaySurfaceWidth = 0
        relaySurfaceHeight = 0
    }

    private fun markUnexpectedDisconnect(reason: String) {
        sendBroadcast(
            Intent(ACTION_CARDEX_INTERRUPTED)
                .setComponent(ComponentName(DEXTOP_PACKAGE, CARDEX_RECOVERY_RECEIVER))
                .putExtra("reason", reason)
        )
    }

    private fun sendAction(action: String, keepOpen: Boolean = false) {
        relay?.send(Message.obtain(null, MSG_ACTION).apply {
            data = Bundle().apply { putString(KEY_ACTION, action) }
        })
        if (!keepOpen && action != "reconnect") controlsVisible = false
    }

    companion object {
        private const val DEXTOP_PACKAGE = "moe.n4tsu.dextop"
        private const val MAIN_ACTIVITY = "moe.n4tsu.dextop.MainActivity"
        private const val RELAY_SERVICE = "moe.n4tsu.dextop.CardexRelayService"
        private const val RELAY_PERMISSION = "moe.n4tsu.dextop.permission.CARDEX_RELAY"
        private const val CARDEX_RECOVERY_RECEIVER = "moe.n4tsu.dextop.CardexRecoveryReceiver"
        private const val ACTION_CARDEX_INTERRUPTED = "moe.n4tsu.dextop.action.CARDEX_INTERRUPTED"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneGuideScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    Text(
                        stringResource(R.string.back),
                        modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 20.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.guide_description),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context -> LoopingGifView(context, R.raw.dextop_companion_guide) }
                )
            }
        }
    }
}

private class LoopingGifView(context: Context, resourceId: Int) : View(context) {
    private val movie = context.resources.openRawResource(resourceId).use(Movie::decodeStream)
    private var startedAt = 0L

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val animation = movie ?: return
        if (startedAt == 0L) startedAt = SystemClock.uptimeMillis()
        val duration = animation.duration().takeIf { it > 0 } ?: 1_000
        animation.setTime(((SystemClock.uptimeMillis() - startedAt) % duration).toInt())
        val scale = minOf(
            width.toFloat() / animation.width().coerceAtLeast(1),
            height.toFloat() / animation.height().coerceAtLeast(1)
        )
        val drawWidth = animation.width() * scale
        val drawHeight = animation.height() * scale
        canvas.save()
        canvas.translate((width - drawWidth) / 2f, (height - drawHeight) / 2f)
        canvas.scale(scale, scale)
        animation.draw(canvas, 0f, 0f)
        canvas.restore()
        postInvalidateOnAnimation()
    }
}

@Composable
private fun RelaySurface(
    status: Int,
    onSurface: (Surface, Int, Int) -> Unit,
    onTouch: (MotionEvent) -> Unit,
    onStop: () -> Unit,
    controlsVisible: Boolean,
    onShowControls: () -> Unit,
    onHideControls: () -> Unit,
    onAction: (String) -> Unit,
    workspaces: List<CardexWorkspace>,
    workspaceError: String,
    uiScale: Float,
    onUiScaleChanged: (Float) -> Unit
) {
    // Android Auto hosts may reserve a persistent navigation rail on either
    // side of the projection surface. Edge-to-edge remains enabled for modern
    // hosts, but the desktop destination must use only the host's safe content
    // rectangle. Because the TextureView itself is inset, its measured size,
    // relay resolution and touch coordinates all share the same origin.
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.union(WindowInsets.tappableElement)
            )
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TextureView(context).apply {
                    isOpaque = true
                    var edgeGesture = false
                    var edgeStartX = 0f
                    val edgeWidth = 48f * resources.displayMetrics.density
                    val revealDistance = 72f * resources.displayMetrics.density
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var relaySurface: Surface? = null

                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            relaySurface = Surface(texture).also { onSurface(it, width, height) }
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                            relaySurface?.takeIf { it.isValid }?.let { onSurface(it, width, height) }
                        }

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            relaySurface?.release()
                            relaySurface = null
                            onStop()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                edgeStartX = event.x
                                edgeGesture = event.x <= edgeWidth
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (edgeGesture && event.x - edgeStartX >= revealDistance) {
                                    edgeGesture = false
                                    onShowControls()
                                    return@setOnTouchListener true
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (edgeGesture) {
                                    edgeGesture = false
                                    return@setOnTouchListener true
                                }
                            }
                        }
                        if (!edgeGesture) onTouch(event)
                        true
                    }
                }
            }
        )
        if (status != STATUS_RUNNING) {
            Card(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    stringResource(if (status == STATUS_STARTING) R.string.starting else R.string.not_ready),
                    modifier = Modifier.padding(28.dp),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            AutoControlPanel(
                modifier = Modifier,
                onDismiss = onHideControls,
                onAction = onAction,
                onStop = onStop,
                workspaces = workspaces,
                workspaceError = workspaceError,
                uiScale = uiScale,
                onUiScaleChanged = onUiScaleChanged
            )
        }
    }
}

@Composable
private fun AutoControlPanel(
    modifier: Modifier,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
    onStop: () -> Unit,
    workspaces: List<CardexWorkspace>,
    workspaceError: String,
    uiScale: Float,
    onUiScaleChanged: (Float) -> Unit
) {
    var workspaceExpanded by remember { mutableStateOf(false) }
    val wideLayout = LocalConfiguration.current.screenWidthDp >= 700
    val panelWidth by animateDpAsState(
        targetValue = if (wideLayout && workspaceExpanded) 652.dp else 326.dp,
        animationSpec = tween(durationMillis = 220),
        label = "autoPanelWidth"
    )
    Card(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp)
            .width(panelWidth),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF211F26)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            if (wideLayout || !workspaceExpanded) {
                Column(
                    Modifier.width(326.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Dextop",
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFE6E1E5),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.close),
                            modifier = Modifier
                                .background(Color(0xFF322F37), RoundedCornerShape(14.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            color = Color(0xFFE6E1E5),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    AutoPanelButton(
                        label = stringResource(R.string.workspace),
                        trailing = if (workspaceExpanded) "‹" else "›"
                    ) { workspaceExpanded = !workspaceExpanded }
                    AutoPanelButton(stringResource(R.string.reconnect), icon = Icons.Default.Refresh) {
                        onAction("reconnect")
                    }
                    AutoPanelButton(
                        stringResource(R.string.stop),
                        destructive = true,
                        icon = Icons.Default.Close
                    ) { onStop() }
                    Text(
                        stringResource(R.string.display_scale),
                        color = Color(0xFFE6E1E5),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    DisplayScaleSelector(
                        scale = uiScale,
                        onScaleChanged = onUiScaleChanged,
                        dark = true
                    )
                }
            }
            AnimatedVisibility(
                visible = workspaceExpanded,
                enter = expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = tween(220)
                ) + fadeIn(tween(160)),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    animationSpec = tween(200)
                ) + fadeOut(tween(120))
            ) {
                Column(
                    Modifier.width(326.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.workspace),
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFE6E1E5),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!wideLayout) {
                            Text(
                                "‹",
                                modifier = Modifier
                                    .background(Color(0xFF322F37), RoundedCornerShape(14.dp))
                                    .clickable { workspaceExpanded = false }
                                    .padding(horizontal = 18.dp, vertical = 8.dp),
                                color = Color(0xFFE6E1E5),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    AutoPanelButton(stringResource(R.string.save_workspace)) { onAction("workspace_save") }
                    if (workspaces.isEmpty()) {
                        Text(stringResource(R.string.no_workspaces), color = Color(0xFFCAC4D0))
                    } else {
                        workspaces.forEach { workspace ->
                            WorkspacePanelButton(workspace) { onAction("workspace_open:${workspace.id}") }
                        }
                    }
                    if (workspaceError.isNotBlank()) Text(workspaceError, color = Color(0xFFFFB4AB))
                }
            }
        }
    }
}

@Composable
private fun AutoPanelButton(
    label: String,
    destructive: Boolean = false,
    trailing: String? = null,
    icon: ImageVector? = null,
    action: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                if (destructive) Color(0xFF5F2325) else Color(0xFF322F37),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = action)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (destructive) Color(0xFFFFB4AB) else Color(0xFFE6E1E5)
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = if (destructive) Color(0xFFFFB4AB) else Color(0xFFE6E1E5),
                fontWeight = FontWeight.Medium
            )
            trailing?.let { Text(it, color = Color(0xFFE6E1E5), style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun WorkspacePanelButton(workspace: CardexWorkspace, action: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color(0xFF322F37), RoundedCornerShape(16.dp))
            .clickable(onClick = action)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            workspace.name,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            color = Color(0xFFE6E1E5),
            maxLines = 1
        )
        workspace.apps.take(4).forEach { packageName ->
            val bitmap = remember(packageName) {
                runCatching {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    val size = 64
                    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { output ->
                        drawable.setBounds(0, 0, size, size)
                        drawable.draw(Canvas(output))
                    }
                }.getOrNull()
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = packageName,
                    modifier = Modifier.width(28.dp).height(28.dp).padding(start = 4.dp)
                )
            }
        }
    }
}

private data class CardexWorkspace(val id: String, val name: String, val apps: List<String>)

private fun parseWorkspaces(raw: String): List<CardexWorkspace> = runCatching {
    val array = JSONArray(raw.ifBlank { "[]" })
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val apps = item.optJSONArray("apps")
            add(CardexWorkspace(
                item.optString("id"),
                item.optString("name", "Workspace ${index + 1}"),
                buildList {
                    if (apps != null) for (appIndex in 0 until apps.length()) add(apps.optString(appIndex))
                }.filter(String::isNotBlank)
            ))
        }
    }
}.getOrDefault(emptyList())

private data class RelayState(
    val installed: Boolean,
    val signaturePermission: Boolean
) {
    val ready: Boolean get() = installed && signaturePermission
}

@Composable
private fun DisplayScaleSelector(
    scale: Float,
    onScaleChanged: (Float) -> Unit,
    dark: Boolean = false
) {
    val selectedBackground = if (dark) Color(0xFFD7BFFF) else MaterialTheme.colorScheme.primary
    val selectedForeground = if (dark) Color(0xFF2A163D) else MaterialTheme.colorScheme.onPrimary
    val normalBackground = if (dark) Color(0xFF322F37) else MaterialTheme.colorScheme.surfaceVariant
    val normalForeground = if (dark) Color(0xFFE6E1E5) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CAR_UI_SCALES.sortedDescending().forEach { option ->
            val selected = scale == option
            Text(
                text = "${(option * 100).toInt()}%",
                modifier = Modifier
                    .width(82.dp)
                    .background(
                        if (selected) selectedBackground else normalBackground,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onScaleChanged(option) }
                    .padding(vertical = 12.dp),
                color = if (selected) selectedForeground else normalForeground,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarDexScreen(
    state: RelayState,
    isCarDisplay: Boolean,
    carConnectionType: Int,
    uiScale: Float,
    onUiScaleChanged: (Float) -> Unit,
    onStart: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dextop Car Companion", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(if (state.ready) R.string.ready else R.string.not_ready),
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onStart,
                            enabled = state.ready,
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(
                                stringResource(if (isCarDisplay) R.string.start else R.string.open_phone),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Text(
                    stringResource(R.string.connection),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Card(shape = RoundedCornerShape(24.dp)) {
                    StatusRow(
                        title = stringResource(if (state.installed) R.string.dextop_installed else R.string.dextop_missing),
                        successful = state.installed
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    StatusRow(
                        title = stringResource(
                            if (state.signaturePermission) R.string.signature_verified
                            else R.string.signature_mismatch
                        ),
                        successful = state.signaturePermission
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    StatusRow(
                        title = stringResource(
                            when (carConnectionType) {
                                CarConnection.CONNECTION_TYPE_PROJECTION -> R.string.android_auto_connected
                                CarConnection.CONNECTION_TYPE_NATIVE -> R.string.automotive_connected
                                else -> R.string.android_auto_disconnected
                            }
                        ),
                        successful = carConnectionType != CarConnection.CONNECTION_TYPE_NOT_CONNECTED
                    )
                }
                if (isCarDisplay) {
                    Text(
                        stringResource(R.string.display_scale),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Card(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text(
                                stringResource(R.string.display_scale_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(14.dp))
                            DisplayScaleSelector(
                                scale = uiScale,
                                onScaleChanged = onUiScaleChanged
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.parked_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(title: String, successful: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (successful) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
        )
        Text(
            title,
            modifier = Modifier.padding(start = 14.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun CarDexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFCDB7FF),
            onPrimary = Color(0xFF2D1657),
            primaryContainer = Color(0xFF302541),
            onPrimaryContainer = Color(0xFFF0E7FF),
            surface = Color(0xFF100E14),
            surfaceVariant = Color(0xFF242128),
            onSurface = Color(0xFFEAE4ED),
            onSurfaceVariant = Color(0xFFCBC3CF),
            error = Color(0xFFFFB4AB)
        ),
        content = content
    )
}
