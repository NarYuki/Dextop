package moe.n4tsu.dextop

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.Parcel
import android.util.Base64
import org.json.JSONObject
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.math.hypot

/** Privileged bridge for Android's hidden multi-display topology API. */
class DisplayTopologyController(private val context: Context) {
    private val privilegedAccess = PrivilegedAccess("DextopTopology")
    private val session = context.getSharedPreferences("dextop_topology_session", Context.MODE_PRIVATE)
    private val layout = context.getSharedPreferences("dextop_topology_layout", Context.MODE_PRIVATE)

    fun activateDextopTopology(overlayDisplayId: Int) {
        check(privilegedAccess.isAvailable()) { NativeStrings.text("nativeShizukuUnavailable") }
        rememberOriginalTopology()
        val manager = context.getSystemService(DisplayManager::class.java)
        val externalIds = ExternalDisplayDetector(context).snapshot().displayIds.toSet()
        val eligible = manager.displays.filter { display ->
            display.displayId == overlayDisplayId || display.displayId in externalIds
        }
        val overlay = eligible.firstOrNull { it.displayId == overlayDisplayId }
            ?: error("The Dextop overlay display is not available")
        fun node(display: android.view.Display): Node {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            return Node(
                display.displayId,
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi.coerceAtLeast(1),
                POSITION_RIGHT,
                0f,
                mutableListOf()
            )
        }
        val root = node(overlay)
        var parent = root
        eligible.filter { it.displayId != overlayDisplayId }
            .sortedBy { it.displayId }
            .forEach { display ->
                val child = node(display)
                parent.children += child
                parent = child
            }
        writeTopology(Topology(root, overlayDisplayId))
        restoreSavedArrangement(overlayDisplayId, eligible)
        OperationLog.i(
            context,
            "DisplayTopology",
            "activated overlay=$overlayDisplayId external=${eligible.map { it.displayId }.filter { it != overlayDisplayId }}"
        )
    }

    fun restoreDextopTopology() {
        if (!session.getBoolean("snapshot_saved", false)) return
        val wasNull = session.getBoolean("snapshot_was_null", true)
        val topology = if (wasNull) null else {
            val encoded = session.getString("snapshot", null) ?: return
            val parcel = Parcel.obtain()
            try {
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                Topology.read(parcel)
            } finally {
                parcel.recycle()
            }
        }
        writeTopology(topology)
        session.edit().clear().apply()
        OperationLog.i(context, "DisplayTopology", "restored pre-Dextop topology")
    }

    private fun rememberOriginalTopology() {
        if (session.getBoolean("snapshot_saved", false)) return
        val original = readTopology()
        val editor = session.edit()
            .putBoolean("snapshot_saved", true)
            .putBoolean("snapshot_was_null", original == null)
        if (original != null) {
            val parcel = Parcel.obtain()
            try {
                original.write(parcel)
                editor.putString(
                    "snapshot",
                    Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
                )
            } finally {
                parcel.recycle()
            }
        }
        check(editor.commit()) { "Unable to save the original display topology" }
    }

    fun read(): Map<String, Any> = runCatching {
        val topology = readTopology()
            ?: return mapOf("supported" to true, "displays" to emptyList<Map<String, Any>>())
        val manager = context.getSystemService(DisplayManager::class.java)
        val bounds = linkedMapOf<Int, RectF>()
        topology.root?.collectBounds(0f, 0f, bounds)
        val displays = bounds
            .filterKeys { it != android.view.Display.DEFAULT_DISPLAY }
            .map { (id, rect) ->
            val display = manager.getDisplay(id)
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display?.getRealMetrics(metrics)
            linkedMapOf<String, Any>(
                "id" to id,
                "name" to (display?.name ?: "Display $id"),
                "x" to rect.left.toDouble(),
                "y" to rect.top.toDouble(),
                "widthDp" to rect.width().toDouble(),
                "heightDp" to rect.height().toDouble(),
                "widthPx" to metrics.widthPixels,
                "heightPx" to metrics.heightPixels,
                "densityDpi" to metrics.densityDpi,
                "primary" to (id == topology.primaryDisplayId),
                "dextopOverlay" to (id == MirrorService.topologyOverlayDisplayId())
            )
        }
        linkedMapOf(
            "supported" to true,
            "primaryDisplayId" to topology.primaryDisplayId,
            "displays" to displays
        )
    }.getOrElse {
        android.util.Log.e("DextopTopology", "topology read failed", it)
        linkedMapOf(
            "supported" to false,
            "reason" to (it.cause?.message ?: it.message ?: "Display topology is unavailable"),
            "displays" to emptyList<Map<String, Any>>()
        )
    }

    fun rearrange(rawPositions: Map<*, *>): Map<String, Any> {
        check(privilegedAccess.isAvailable()) { NativeStrings.text("nativeShizukuUnavailable") }
        val current = readTopology() ?: error("No display topology is active")
        val positions = linkedMapOf<Int, PointF>()
        val currentBounds = linkedMapOf<Int, RectF>()
        current.root?.collectBounds(0f, 0f, currentBounds)
        currentBounds.forEach { (id, rect) -> positions[id] = PointF(rect.left, rect.top) }
        rawPositions.forEach { (rawId, rawPosition) ->
            val id = rawId.toString().toIntOrNull() ?: return@forEach
            val position = rawPosition as? Map<*, *> ?: return@forEach
            val x = (position["x"] as? Number)?.toFloat() ?: return@forEach
            val y = (position["y"] as? Number)?.toFloat() ?: return@forEach
            positions[id] = PointF(x, y)
        }
        val nodes = current.root?.flatten()?.associateBy { it.displayId }
            ?: error("No displays are present")
        check(nodes.keys == positions.keys) { "Positions must include every active display" }
        val rootId = current.primaryDisplayId.takeIf(nodes::containsKey) ?: nodes.keys.first()
        val root = nodes.getValue(rootId).copy(children = mutableListOf())
        val placed = linkedMapOf(rootId to root)
        val pending = nodes.keys.filter { it != rootId }.toMutableSet()
        while (pending.isNotEmpty()) {
            val best = pending.flatMap { childId ->
                placed.keys.map { parentId ->
                    Triple(childId, parentId, edgeDistance(nodes, positions, childId, parentId))
                }
            }.minBy { it.third }
            val child = nodes.getValue(best.first).copy(children = mutableListOf())
            val parent = placed.getValue(best.second)
            val childPoint = positions.getValue(child.displayId)
            val parentPoint = positions.getValue(parent.displayId)
            val childCenterX = childPoint.x + child.widthDp / 2f
            val childCenterY = childPoint.y + child.heightDp / 2f
            val parentCenterX = parentPoint.x + parent.widthDp / 2f
            val parentCenterY = parentPoint.y + parent.heightDp / 2f
            if (kotlin.math.abs(childCenterX - parentCenterX) >=
                kotlin.math.abs(childCenterY - parentCenterY)) {
                child.position = if (childCenterX < parentCenterX) POSITION_LEFT else POSITION_RIGHT
                child.offset = childPoint.y - parentPoint.y
            } else {
                child.position = if (childCenterY < parentCenterY) POSITION_TOP else POSITION_BOTTOM
                child.offset = childPoint.x - parentPoint.x
            }
            parent.children += child
            placed[child.displayId] = child
            pending.remove(child.displayId)
        }
        writeTopology(Topology(root, rootId))
        saveArrangement(positions, nodes, rootId)
        OperationLog.i(context, "DisplayTopology", "rearranged ${positions.keys.sorted()}")
        return read()
    }

    private fun saveArrangement(
        positions: Map<Int, PointF>,
        nodes: Map<Int, Node>,
        primaryDisplayId: Int
    ) {
        val manager = context.getSystemService(DisplayManager::class.java)
        val overlayId = MirrorService.topologyOverlayDisplayId()
            .takeIf(positions::containsKey) ?: primaryDisplayId
        val origin = positions.getValue(overlayId)
        val entries = JSONObject()
        positions.forEach { (id, point) ->
            val display = manager.getDisplay(id) ?: return@forEach
            entries.put(stableDisplayKey(display, id == overlayId), JSONObject().apply {
                put("x", (point.x - origin.x).toDouble())
                put("y", (point.y - origin.y).toDouble())
            })
        }
        layout.edit().putString(KEY_SAVED_ARRANGEMENT, entries.toString()).apply()
        OperationLog.i(context, "DisplayTopology", "saved arrangement displays=${entries.length()}")
    }

    private fun restoreSavedArrangement(
        overlayDisplayId: Int,
        displays: List<android.view.Display>
    ) {
        val encoded = layout.getString(KEY_SAVED_ARRANGEMENT, null) ?: return
        val saved = runCatching { JSONObject(encoded) }.getOrNull() ?: return
        val current = readTopology() ?: return
        val bounds = linkedMapOf<Int, RectF>()
        current.root?.collectBounds(0f, 0f, bounds)
        val restored = linkedMapOf<Int, Map<String, Double>>()
        displays.forEach { display ->
            val fallback = bounds[display.displayId] ?: return@forEach
            val value = saved.optJSONObject(
                stableDisplayKey(display, display.displayId == overlayDisplayId)
            )
            restored[display.displayId] = mapOf(
                "x" to (value?.optDouble("x") ?: fallback.left.toDouble()),
                "y" to (value?.optDouble("y") ?: fallback.top.toDouble())
            )
        }
        if (restored.keys == bounds.keys) {
            rearrange(restored)
            OperationLog.i(context, "DisplayTopology", "restored saved arrangement")
        }
    }

    private fun stableDisplayKey(display: android.view.Display, overlay: Boolean): String {
        if (overlay) return "dextop_overlay"
        val uniqueId = runCatching {
            android.view.Display::class.java.getMethod("getUniqueId").invoke(display) as String
        }.getOrNull()
        if (!uniqueId.isNullOrBlank()) return "external:$uniqueId"
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return "external:${display.name}:${metrics.widthPixels}x${metrics.heightPixels}"
    }

    private fun edgeDistance(
        nodes: Map<Int, Node>,
        positions: Map<Int, PointF>,
        childId: Int,
        parentId: Int
    ): Double {
        val child = nodes.getValue(childId)
        val parent = nodes.getValue(parentId)
        val c = positions.getValue(childId)
        val p = positions.getValue(parentId)
        val dx = maxOf(p.x - (c.x + child.widthDp), c.x - (p.x + parent.widthDp), 0f)
        val dy = maxOf(p.y - (c.y + child.heightDp), c.y - (p.y + parent.heightDp), 0f)
        return hypot(dx.toDouble(), dy.toDouble())
    }

    private fun readTopology(): Topology? = transact(TRANSACTION_GET) { data, reply ->
        reply.readException()
        if (reply.readInt() == 0) null else Topology.read(reply)
    }

    private fun writeTopology(topology: Topology?) {
        transact(TRANSACTION_SET, write = { data ->
            if (topology == null) data.writeInt(0) else {
                data.writeInt(1)
                topology.write(data)
            }
        }) { _, reply ->
            reply.readException()
        }
    }

    private fun <T> transact(
        code: Int,
        write: (Parcel) -> Unit = {},
        read: (Parcel, Parcel) -> T
    ): T {
        check(privilegedAccess.isAvailable()) { NativeStrings.text("nativeShizukuUnavailable") }
        val binder: IBinder = ShizukuBinderWrapper(
            SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE)
        )
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DISPLAY_INTERFACE)
            write(data)
            check(binder.transact(code, data, reply, 0)) { "Display service rejected topology transaction" }
            return read(data, reply)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private data class Topology(val root: Node?, val primaryDisplayId: Int) {
        fun write(parcel: Parcel) {
            if (root == null) parcel.writeInt(0) else {
                parcel.writeInt(1)
                root.write(parcel)
            }
            parcel.writeInt(primaryDisplayId)
        }

        companion object {
            fun read(parcel: Parcel): Topology {
                val root = if (parcel.readInt() == 0) null else Node.read(parcel)
                return Topology(root, parcel.readInt())
            }
        }
    }

    private data class Node(
        val displayId: Int,
        val logicalWidth: Int,
        val logicalHeight: Int,
        val logicalDensity: Int,
        var position: Int,
        var offset: Float,
        val children: MutableList<Node>
    ) {
        val widthDp: Float get() = logicalWidth * 160f / logicalDensity.coerceAtLeast(1)
        val heightDp: Float get() = logicalHeight * 160f / logicalDensity.coerceAtLeast(1)

        fun collectBounds(x: Float, y: Float, result: MutableMap<Int, RectF>) {
            result[displayId] = RectF(x, y, x + widthDp, y + heightDp)
            children.forEach { child ->
                val childX = when (child.position) {
                    POSITION_LEFT -> x - child.widthDp
                    POSITION_RIGHT -> x + widthDp
                    else -> x + child.offset
                }
                val childY = when (child.position) {
                    POSITION_TOP -> y - child.heightDp
                    POSITION_BOTTOM -> y + heightDp
                    else -> y + child.offset
                }
                child.collectBounds(childX, childY, result)
            }
        }

        fun flatten(): List<Node> = listOf(this) + children.flatMap(Node::flatten)

        fun write(parcel: Parcel) {
            parcel.writeInt(displayId)
            parcel.writeInt(logicalWidth)
            parcel.writeInt(logicalHeight)
            parcel.writeInt(logicalDensity)
            parcel.writeInt(position)
            parcel.writeFloat(offset)
            parcel.writeInt(children.size)
            children.forEach {
                parcel.writeInt(1)
                it.write(parcel)
            }
        }

        companion object {
            fun read(parcel: Parcel): Node {
                val node = Node(
                    parcel.readInt(), parcel.readInt(), parcel.readInt(), parcel.readInt(),
                    parcel.readInt(), parcel.readFloat(), mutableListOf()
                )
                repeat(parcel.readInt()) {
                    if (parcel.readInt() != 0) node.children += read(parcel)
                }
                return node
            }
        }
    }

    companion object {
        private const val KEY_SAVED_ARRANGEMENT = "saved_arrangement_v1"
        private const val DISPLAY_INTERFACE = "android.hardware.display.IDisplayManager"
        private const val TRANSACTION_GET = 106
        private const val TRANSACTION_SET = 107
        private const val POSITION_LEFT = 0
        private const val POSITION_TOP = 1
        private const val POSITION_RIGHT = 2
        private const val POSITION_BOTTOM = 3
    }
}
