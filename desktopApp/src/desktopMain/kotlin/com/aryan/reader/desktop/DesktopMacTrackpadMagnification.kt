package com.aryan.reader.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Function
import java.awt.Component
import java.awt.EventQueue
import java.awt.MouseInfo
import java.awt.Window
import java.io.File
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.exp

internal data class DesktopTrackpadMagnification(
    val factor: Float,
    val rootPosition: Offset?
)

internal const val DesktopTrackpadZoomTraceTag = "EpistemeTrackpadZoom"

private val DesktopTrackpadZoomTraceLock = Any()
private val DesktopTrackpadZoomTraceFile: File by lazy {
    File(desktopUserDataRoot(), "diagnostics/trackpad-zoom.log")
}

internal fun logDesktopTrackpadZoom(message: () -> String) {
    val line = "$DesktopTrackpadZoomTraceTag time=${Instant.now()} thread=\"${Thread.currentThread().name}\" ${message()}"
    System.err.println(line)
    runCatching {
        synchronized(DesktopTrackpadZoomTraceLock) {
            DesktopTrackpadZoomTraceFile.parentFile?.mkdirs()
            DesktopTrackpadZoomTraceFile.appendText("$line\n")
        }
    }
}

internal fun desktopTrackpadZoomTraceFile(): File = DesktopTrackpadZoomTraceFile

internal fun desktopTrackpadMagnificationFactor(magnification: Double): Float {
    if (!magnification.isFinite()) return 1f
    // NSEvent.magnification is already a signed delta for this event. A moderate
    // gain gives macOS-style control while still allowing a long pinch to cover
    // a useful zoom range.
    return exp(magnification.coerceIn(-0.25, 0.25) * 0.55).toFloat()
}

internal object DesktopTrackpadZoomTraceActivity {
    private val lastEventAtMillis = AtomicLong(0L)

    fun mark(nowMillis: Long = System.currentTimeMillis()) {
        lastEventAtMillis.set(nowMillis)
    }

    fun isRecent(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis - lastEventAtMillis.get() in 0L..4_000L
    }
}

internal object DesktopTrackpadMagnificationEvents {
    private val mutableEvents = MutableSharedFlow<DesktopTrackpadMagnification>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = mutableEvents.asSharedFlow()

    fun emit(event: DesktopTrackpadMagnification): Boolean = mutableEvents.tryEmit(event)
}

@Composable
internal fun DesktopMacTrackpadMagnificationEffect(window: Component?) {
    DisposableEffect(window) {
        logDesktopTrackpadZoom {
            "event=effect_start os=${currentDesktopPlatform().os} window=${window?.javaClass?.name ?: "null"} " +
                "displayable=${window?.isDisplayable} showing=${window?.isShowing}"
        }
        val rootPane = window
            ?.let(SwingUtilities::getRootPane)
            ?.takeIf { currentDesktopPlatform().os == DesktopOperatingSystem.MACOS }
        logDesktopTrackpadZoom {
            "event=root_resolved root=${rootPane?.javaClass?.name ?: "null"} " +
                "displayable=${rootPane?.isDisplayable} showing=${rootPane?.isShowing}"
        }
        val registration = if (rootPane != null && window is Window) {
            registerNativeMacTrackpadMagnificationListener(window, rootPane)
                ?: registerMacTrackpadMagnificationListener(rootPane)
        } else {
            null
        }
        onDispose {
            logDesktopTrackpadZoom { "event=effect_dispose registered=${registration != null}" }
            registration?.close()
        }
    }
}

private fun registerNativeMacTrackpadMagnificationListener(
    window: Window,
    component: JComponent
): AutoCloseable? {
    logDesktopTrackpadZoom { "event=native_registration_start" }
    return runCatching {
        val view = macOsAwtView(window)
        check(view != Pointer.NULL) { "AWT NSView pointer is null." }
        val targetClass = macMagnificationTargetClass()
        val target = MacObjectiveC.objc_msgSend(targetClass, MacObjectiveC.selector("new"))
        check(target != Pointer.NULL) { "Could not create magnification target." }
        val targetViews = macOsLeafViews(view).ifEmpty { listOf(view) }
        val recognizers = targetViews.map { targetView ->
            val recognizerClass = MacObjectiveC.objc_getClass("NSMagnificationGestureRecognizer")
            val allocatedRecognizer = MacObjectiveC.objc_msgSend(
                recognizerClass,
                MacObjectiveC.selector("alloc")
            )
            val recognizer = MacObjectiveC.objc_msgSend(
                allocatedRecognizer,
                MacObjectiveC.selector("initWithTarget:action:"),
                target,
                MacObjectiveC.selector(MacMagnificationActionSelectorName)
            )
            check(recognizer != Pointer.NULL) { "Could not create NSMagnificationGestureRecognizer." }
            targetView to recognizer
        }
        runOnMacSwtThread {
            recognizers.forEach { (targetView, recognizer) ->
                MacObjectiveC.objc_msgSendVoid(
                    targetView,
                    MacObjectiveC.selector("addGestureRecognizer:"),
                    recognizer
                )
            }
        }
        val awtViewInterception = installAwtViewMagnificationInterception(view, component)
        MacNativeMagnificationTargets[target.toString()] = component
        logDesktopTrackpadZoom {
            "event=native_registration_success view=$view class=${MacObjectiveC.className(view)} " +
                "target=$target leafCount=${targetViews.size} leaves=" +
                targetViews.joinToString(prefix = "[", postfix = "]") {
                    "${MacObjectiveC.className(it)}@$it"
                }
        }
        AutoCloseable {
            MacNativeMagnificationTargets.remove(target.toString())
            awtViewInterception.close()
            runCatching {
                runOnMacSwtThread {
                    recognizers.forEach { (targetView, recognizer) ->
                        MacObjectiveC.objc_msgSendVoid(
                            targetView,
                            MacObjectiveC.selector("removeGestureRecognizer:"),
                            recognizer
                        )
                        MacObjectiveC.objc_msgSendVoid(recognizer, MacObjectiveC.selector("release"))
                    }
                    MacObjectiveC.objc_msgSendVoid(target, MacObjectiveC.selector("release"))
                }
            }
            logDesktopTrackpadZoom { "event=native_registration_removed" }
        }
    }.onFailure { error ->
        logDesktopTrackpadZoom {
            "event=native_registration_failed error=${error.javaClass.name} message=\"${error.message.orEmpty()}\""
        }
    }.getOrNull()
}

private interface MacAwtViewMagnificationCallback : Callback {
    fun invoke(view: Pointer, command: Pointer, event: Pointer)
}

private val MacAwtViewMagnificationComponents =
    java.util.concurrent.ConcurrentHashMap<String, JComponent>()
private val MacAwtViewFallbackComponent = AtomicReference<JComponent?>()
private val MacAwtViewInterceptionLock = Any()
private var MacAwtViewOriginalMagnificationImplementation: Pointer? = null
private var MacAwtViewMagnificationMethod: Pointer? = null
private var MacAwtViewInterceptionUsers: Int = 0

private val MacAwtViewMagnificationCallbackInstance = object : MacAwtViewMagnificationCallback {
    override fun invoke(view: Pointer, command: Pointer, event: Pointer) {
        val eventMagnification = MacObjectiveC.objc_msgSendDouble(
            event,
            MacObjectiveC.selector("magnification")
        )
        val phase = MacObjectiveC.objc_msgSendLong(event, MacObjectiveC.selector("phase"))
        val nowMillis = System.currentTimeMillis()
        val progressKey = view.toString()
        val endsGesture = phase and (8L or 16L) != 0L
        val appliedMagnification = if (endsGesture) {
            0.0
        } else {
            eventMagnification
        }
        val component = MacAwtViewMagnificationComponents[progressKey]
            ?: MacAwtViewFallbackComponent.get()
        DesktopTrackpadZoomTraceActivity.mark(nowMillis)
        EventQueue.invokeLater {
            val factor = desktopTrackpadMagnificationFactor(appliedMagnification)
            val rootPosition = component?.pointerPositionInRoot()
            val emitted = factor != 1f && DesktopTrackpadMagnificationEvents.emit(
                DesktopTrackpadMagnification(factor, rootPosition)
            )
            logDesktopTrackpadZoom {
                "event=awt_view_magnify raw=$eventMagnification " +
                    "applied=$appliedMagnification phase=$phase ends=$endsGesture " +
                    "factor=$factor emitted=$emitted view=$view " +
                    "component=${component?.javaClass?.name ?: "null"} " +
                    "root=${rootPosition?.let { "${it.x},${it.y}" } ?: "null"}"
            }
        }
        MacAwtViewOriginalMagnificationImplementation?.let { original ->
            Function.getFunction(original).invokeVoid(arrayOf(view, command, event))
        }
    }
}

private fun installAwtViewMagnificationInterception(
    view: Pointer,
    component: JComponent
): AutoCloseable {
    synchronized(MacAwtViewInterceptionLock) {
        MacAwtViewMagnificationComponents[view.toString()] = component
        MacAwtViewFallbackComponent.set(component)
        if (MacAwtViewInterceptionUsers == 0) {
            val viewClass = MacObjectiveC.object_getClass(view)
            val selector = MacObjectiveC.selector("magnifyWithEvent:")
            val method = MacObjectiveC.class_getInstanceMethod(viewClass, selector)
            check(method != Pointer.NULL) {
                "${MacObjectiveC.className(view)} does not implement magnifyWithEvent:."
            }
            val original = MacObjectiveC.method_getImplementation(method)
            check(original != Pointer.NULL) { "magnifyWithEvent: implementation is null." }
            val replacement = CallbackReference.getFunctionPointer(
                MacAwtViewMagnificationCallbackInstance
            )
            MacObjectiveC.method_setImplementation(method, replacement)
            MacAwtViewOriginalMagnificationImplementation = original
            MacAwtViewMagnificationMethod = method
            logDesktopTrackpadZoom {
                "event=awt_view_interception_installed class=${MacObjectiveC.className(view)} " +
                    "method=$method original=$original replacement=$replacement"
            }
        }
        MacAwtViewInterceptionUsers += 1
    }
    return AutoCloseable {
        synchronized(MacAwtViewInterceptionLock) {
            MacAwtViewMagnificationComponents.remove(view.toString())
            MacAwtViewInterceptionUsers = (MacAwtViewInterceptionUsers - 1).coerceAtLeast(0)
            if (MacAwtViewInterceptionUsers == 0) {
                val method = MacAwtViewMagnificationMethod
                val original = MacAwtViewOriginalMagnificationImplementation
                if (method != null && original != null) {
                    MacObjectiveC.method_setImplementation(method, original)
                }
                MacAwtViewMagnificationMethod = null
                MacAwtViewOriginalMagnificationImplementation = null
                MacAwtViewFallbackComponent.set(null)
                logDesktopTrackpadZoom { "event=awt_view_interception_removed" }
            }
        }
    }
}

private fun macOsLeafViews(root: Pointer): List<Pointer> {
    fun leaves(view: Pointer, depth: Int): List<Pointer> {
        if (depth >= 12) return listOf(view)
        val subviews = MacObjectiveC.objc_msgSend(view, MacObjectiveC.selector("subviews"))
        if (subviews == Pointer.NULL) return listOf(view)
        val count = MacObjectiveC.objc_msgSendLong(subviews, MacObjectiveC.selector("count"))
            .coerceIn(0L, 128L)
        if (count == 0L) return listOf(view)
        return (0L until count).flatMap { index ->
            val child = MacObjectiveC.objc_msgSend(
                subviews,
                MacObjectiveC.selector("objectAtIndex:"),
                index
            )
            if (child == Pointer.NULL) emptyList() else leaves(child, depth + 1)
        }
    }
    return runOnMacSwtThreadWithResult { leaves(root, 0) }
}

private fun registerMacTrackpadMagnificationListener(component: JComponent): AutoCloseable? {
    logDesktopTrackpadZoom {
        "event=registration_start component=${component.javaClass.name} displayable=${component.isDisplayable}"
    }
    return runCatching {
        val listenerClass = Class.forName("com.apple.eawt.event.MagnificationListener")
        val utilitiesClass = Class.forName("com.apple.eawt.event.GestureUtilities")
        val magnificationMethod = Class.forName("com.apple.eawt.event.MagnificationEvent")
            .getMethod("getMagnification")
        val listener = Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass)
        ) { proxy, method, args ->
            when (method.name) {
                "magnify" -> {
                    val magnification = (magnificationMethod.invoke(args?.firstOrNull()) as? Number)
                        ?.toDouble()
                        ?: 0.0
                    val factor = desktopTrackpadMagnificationFactor(magnification)
                    val rootPosition = component.pointerPositionInRoot()
                    val emitted = if (factor != 1f) {
                        DesktopTrackpadMagnificationEvents.emit(
                            DesktopTrackpadMagnification(
                                factor = factor,
                                rootPosition = rootPosition
                            )
                        )
                    } else {
                        false
                    }
                    logDesktopTrackpadZoom {
                        "event=native_magnify raw=$magnification factor=$factor emitted=$emitted " +
                            "root=${rootPosition?.let { "${it.x},${it.y}" } ?: "null"}"
                    }
                    null
                }
                "toString" -> "EpistemeMacTrackpadMagnificationListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        val addMethod = utilitiesClass.getMethod(
            "addGestureListenerTo",
            JComponent::class.java,
            Class.forName("com.apple.eawt.event.GestureListener")
        )
        val removeMethod = utilitiesClass.getMethod(
            "removeGestureListenerFrom",
            JComponent::class.java,
            Class.forName("com.apple.eawt.event.GestureListener")
        )
        onAwtEventThread { addMethod.invoke(null, component, listener) }
        logDesktopTrackpadZoom {
            "event=registration_success listener=${listener.javaClass.name} component=${component.javaClass.name}"
        }
        AutoCloseable {
            runCatching {
                onAwtEventThread { removeMethod.invoke(null, component, listener) }
            }
            logDesktopTrackpadZoom { "event=registration_removed" }
        }
    }.onFailure { error ->
        logDesktopTrackpadZoom {
            "event=registration_failed error=${error.javaClass.name} message=\"${error.message.orEmpty()}\""
        }
    }.getOrNull()
}

private const val MacMagnificationTargetClassName = "EpistemeTrackpadMagnificationTarget"
private const val MacMagnificationActionSelectorName = "handleMagnification:"
private val MacNativeMagnificationTargets = java.util.concurrent.ConcurrentHashMap<String, JComponent>()

private interface MacMagnificationCallback : Callback {
    fun invoke(self: Pointer, command: Pointer, recognizer: Pointer)
}

private val MacMagnificationCallbackInstance = object : MacMagnificationCallback {
    override fun invoke(self: Pointer, command: Pointer, recognizer: Pointer) {
        val magnification = MacObjectiveC.objc_msgSendDouble(
            recognizer,
            MacObjectiveC.selector("magnification")
        )
        MacObjectiveC.objc_msgSendVoid(
            recognizer,
            MacObjectiveC.selector("setMagnification:"),
            0.0
        )
        val component = MacNativeMagnificationTargets[self.toString()]
        val factor = desktopTrackpadMagnificationFactor(magnification)
        val rootPosition = component?.pointerPositionInRoot()
        val emitted = factor != 1f && DesktopTrackpadMagnificationEvents.emit(
            DesktopTrackpadMagnification(factor, rootPosition)
        )
        logDesktopTrackpadZoom {
            "event=native_view_magnify raw=$magnification factor=$factor emitted=$emitted " +
                "root=${rootPosition?.let { "${it.x},${it.y}" } ?: "null"} recognizer=$recognizer"
        }
    }
}

@Synchronized
private fun macMagnificationTargetClass(): Pointer {
    MacObjectiveC.objc_getClass(MacMagnificationTargetClassName)
        .takeIf { it != Pointer.NULL }
        ?.let { return it }
    val parent = MacObjectiveC.objc_getClass("NSObject")
    val allocated = MacObjectiveC.objc_allocateClassPair(
        parent,
        MacMagnificationTargetClassName,
        0
    )
    check(allocated != Pointer.NULL) { "Could not allocate Objective-C target class." }
    val added = MacObjectiveC.class_addMethod(
        allocated,
        MacObjectiveC.selector(MacMagnificationActionSelectorName),
        CallbackReference.getFunctionPointer(MacMagnificationCallbackInstance),
        "v@:@"
    )
    check(added) { "Could not add Objective-C magnification callback." }
    MacObjectiveC.objc_registerClassPair(allocated)
    return allocated
}

private fun macOsAwtView(window: Window): Pointer {
    val accessorClass = Class.forName("sun.awt.AWTAccessor")
    val componentAccessorClass = Class.forName("sun.awt.AWTAccessor\$ComponentAccessor")
    val accessor = accessorClass.getMethod("getComponentAccessor").invoke(null)
    val peer = componentAccessorClass
        .getMethod("getPeer", Component::class.java)
        .invoke(accessor, window)
        ?: error("AWT window peer is unavailable.")
    val platformWindow = peer.javaClass.getMethod("getPlatformWindow").invoke(peer)
        ?: error("AWT platform window is unavailable.")
    val contentView = platformWindow.javaClass.getMethod("getContentView").invoke(platformWindow)
        ?: error("AWT content view is unavailable.")
    val address = (contentView.javaClass.getMethod("getAWTView").invoke(contentView) as Number).toLong()
    return Pointer(address)
}

private fun runOnMacSwtThread(block: () -> Unit) {
    val failure = AtomicReference<Throwable>()
    DesktopSwtBrowserEventLoop.syncExec(
        onError = failure::set,
        block = { block() }
    )
    failure.get()?.let { throw it }
}

private fun <T> runOnMacSwtThreadWithResult(block: () -> T): T {
    val value = AtomicReference<T>()
    runOnMacSwtThread { value.set(block()) }
    return value.get()
}

private interface MacObjectiveCLibrary : Library {
    fun objc_getClass(name: String): Pointer
    fun sel_registerName(name: String): Pointer
    fun objc_allocateClassPair(parent: Pointer, name: String, extraBytes: Long): Pointer
    fun objc_registerClassPair(targetClass: Pointer)
    fun class_addMethod(targetClass: Pointer, selector: Pointer, implementation: Pointer, types: String): Boolean
    fun object_getClass(instance: Pointer): Pointer
    fun class_getName(targetClass: Pointer): Pointer
    fun class_getInstanceMethod(targetClass: Pointer, selector: Pointer): Pointer
    fun method_getImplementation(method: Pointer): Pointer
    fun method_setImplementation(method: Pointer, implementation: Pointer): Pointer
}

private object MacObjectiveC : MacObjectiveCLibrary by Native.load("objc", MacObjectiveCLibrary::class.java) {
    private val messageSend = NativeLibrary.getInstance("objc").getFunction("objc_msgSend")

    fun selector(name: String): Pointer = sel_registerName(name)

    fun objc_msgSend(receiver: Pointer, selector: Pointer, vararg arguments: Any?): Pointer {
        return messageSend.invokePointer(arrayOf(receiver, selector, *arguments))
    }

    fun objc_msgSendVoid(receiver: Pointer, selector: Pointer, vararg arguments: Any?) {
        messageSend.invokeVoid(arrayOf(receiver, selector, *arguments))
    }

    fun objc_msgSendDouble(receiver: Pointer, selector: Pointer, vararg arguments: Any?): Double {
        return messageSend.invokeDouble(arrayOf(receiver, selector, *arguments))
    }

    fun objc_msgSendLong(receiver: Pointer, selector: Pointer, vararg arguments: Any?): Long {
        return messageSend.invokeLong(arrayOf(receiver, selector, *arguments))
    }

    fun className(instance: Pointer): String {
        val targetClass = object_getClass(instance)
        val name = class_getName(targetClass)
        return name.getString(0)
    }
}

private fun Component.pointerPositionInRoot(): Offset? {
    return runCatching {
        val pointer = MouseInfo.getPointerInfo()?.location ?: return null
        val rootLocation = locationOnScreen
        val transform = graphicsConfiguration?.defaultTransform
        Offset(
            x = ((pointer.x - rootLocation.x) * (transform?.scaleX ?: 1.0)).toFloat(),
            y = ((pointer.y - rootLocation.y) * (transform?.scaleY ?: 1.0)).toFloat()
        )
    }.getOrNull()
}

private fun <T> onAwtEventThread(block: () -> T): T {
    if (EventQueue.isDispatchThread()) return block()
    val value = AtomicReference<T>()
    val failure = AtomicReference<Throwable>()
    EventQueue.invokeAndWait {
        runCatching(block)
            .onSuccess(value::set)
            .onFailure(failure::set)
    }
    failure.get()?.let { throw it }
    return value.get()
}
