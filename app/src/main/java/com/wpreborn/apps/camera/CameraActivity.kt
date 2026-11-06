package com.wpreborn.apps.camera

import com.wpreborn.core.motion.turnstile
import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.KeyEvent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.activity.compose.BackHandler
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.MirrorMode
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.video.FallbackStrategy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.wpreborn.core.ui.WpToggle
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.apps.photos.PhotosActivity
import com.wpreborn.core.icons.Glyphs
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.ImageLoader
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpSlider
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.permissions.PermissionGate
import com.wpreborn.permissions.hasPermissions
import com.wpreborn.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val aiScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

private enum class FlashMode(val capture: Int) { Auto(ImageCapture.FLASH_MODE_AUTO), On(ImageCapture.FLASH_MODE_ON), Off(ImageCapture.FLASH_MODE_OFF) }

/**
 * Camera in the style of Lumia Camera on Windows Phone 8.1: full-screen viewfinder, a slim
 * control column (flash, front/back, timer, grid), the round shutter, photo/video switch
 * and the last shot in the corner. Photo modes use the phone maker's own processing (HDR,
 * night, portrait, auto) where offered; "pro" gives manual EV, ISO, shutter, white balance,
 * focus and zoom. Pictures and videos go to Pictures/Mozaik and Movies/Mozaik. Volume keys and
 * the camera key shoot.
 */
class CameraActivity : WpActivity() {

    override val showStatusBar: Boolean get() = false
    override val showPlayingNow: Boolean get() = false

    /** Set by the composable; called by the hardware keys. */
    var shutter: (() -> Unit)? = null

    @Composable
    override fun Content() {
        val perm = rememberPermissionState(arrayOf(Manifest.permission.CAMERA))
        if (!perm.granted) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(start = 20.dp, top = 14.dp)) {
                WpText(stringResource(R.string.app_camera).uppercase(), style = WpType.AppTitle)
                PermissionGate(perm, stringResource(R.string.perm_camera)) {}
            }
            return
        }
        CameraScreen(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val k = event.keyCode
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_CAMERA) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) shutter?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** Saved camera settings. */
private data class CamSettings(
    val wide: Boolean = false,
    /** Off (default): about 12 MP, the sensor's binned mode (best quality); on: every pixel it has. */
    val fullRes: Boolean = false,
    val fast: Boolean = false,
    /** 0 = best available, 1 = 4K, 2 = 1080p, 3 = 720p. */
    val videoRes: Int = 0,
    val stabilize: Boolean = true,
    /** Off (default): selfies are saved as others see you, not mirrored like the viewfinder. */
    val mirror: Boolean = false,
    val sound: Boolean = true,
    val grid: Boolean = false,
    /** ZUNEX AI: scene recognition and per-scene processing of photos. */
    val ai: Boolean = true,
    val look: Look = Look.Auto,
) {
    fun save(context: Context) {
        context.getSharedPreferences("camera", Context.MODE_PRIVATE).edit()
            .putBoolean("wide", wide).putBoolean("fullRes", fullRes).putBoolean("fast", fast).putInt("videoRes", videoRes)
            .putBoolean("stabilize", stabilize).putBoolean("mirror", mirror).putBoolean("sound", sound)
            .putBoolean("grid", grid).putBoolean("ai", ai).putString("look", look.name)
            .apply()
    }

    companion object {
        fun load(context: Context): CamSettings {
            val p = context.getSharedPreferences("camera", Context.MODE_PRIVATE)
            return CamSettings(
                wide = p.getBoolean("wide", false),
                fullRes = p.getBoolean("fullRes", false),
                fast = p.getBoolean("fast", false),
                videoRes = p.getInt("videoRes", 0),
                stabilize = p.getBoolean("stabilize", true),
                mirror = p.getBoolean("mirror", false),
                sound = p.getBoolean("sound", true),
                grid = p.getBoolean("grid", false),
                ai = p.getBoolean("ai", true),
                look = runCatching { Look.valueOf(p.getString("look", null) ?: "") }.getOrDefault(Look.Auto),
            )
        }
    }
}

/**
 * Photo modes. Except "normal", each one is the phone maker's own image processing
 * (CameraX Extensions: multi-frame HDR, night, portrait blur, face retouch, automatic),
 * offered only when the phone has it for the lens in use.
 */
private enum class Look(val ext: Int, val label: Int) {
    Normal(ExtensionMode.NONE, R.string.camera_mode_normal),
    Auto(ExtensionMode.AUTO, R.string.camera_mode_auto),
    Hdr(ExtensionMode.HDR, R.string.camera_mode_hdr),
    Night(ExtensionMode.NIGHT, R.string.camera_mode_night),
    Portrait(ExtensionMode.BOKEH, R.string.camera_mode_portrait),
    /** ZUNEX AI portrait (person segmentation + background blur), where the phone has none. */
    AiPortrait(ExtensionMode.NONE, R.string.camera_mode_portrait),
    Retouch(ExtensionMode.FACE_RETOUCH, R.string.camera_mode_retouch),
}

/** Manual controls of the "pro" panel, as on Lumia Camera. */
private enum class ProDial(val label: Int) { Ev(R.string.camera_pro_ev), Iso(R.string.camera_pro_iso), Shutter(R.string.camera_pro_shutter), Wb(R.string.camera_pro_wb), Focus(R.string.camera_pro_focus), Zoom(R.string.camera_pro_zoom) }

/** What the lens in use allows manually (read from Camera2). */
private class LensCaps(
    val iso: Range<Int>?,
    val exposure: Range<Long>?,
    /** In diopters; 0 = fixed focus. */
    val minFocus: Float,
    val wb: List<Int>,
) {
    val manualExposure get() = iso != null && exposure != null
}

private val WB_ORDER = listOf(
    CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT to R.string.camera_wb_incandescent,
    CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT to R.string.camera_wb_fluorescent,
    CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT to R.string.camera_wb_daylight,
    CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to R.string.camera_wb_cloudy,
    CameraMetadata.CONTROL_AWB_MODE_SHADE to R.string.camera_wb_shade,
)

private val ISO_STOPS = listOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000, 12800)

/** Shutter speeds in seconds, 1/8000 to 1". */
private val SHUTTER_STOPS = listOf(8000, 4000, 2000, 1000, 500, 250, 125, 60, 30, 15, 8, 4, 2).map { 1.0 / it } + 1.0

/** Slider positions below this are "auto". */
private const val AUTO_ZONE = 0.06f

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun readCaps(camera: Camera): LensCaps? = runCatching {
    val info = Camera2CameraInfo.from(camera.cameraInfo)
    val capsList = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
    val manual = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capsList
    val awb = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: IntArray(0)
    LensCaps(
        iso = if (manual) info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) else null,
        exposure = if (manual) info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) else null,
        minFocus = if (manual) info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f else 0f,
        wb = WB_ORDER.map { it.first }.filter { it in awb },
    )
}.getOrNull()

/** Position (past the auto zone) → a value on a log scale between [lo] and [hi]. */
private fun logScale(p: Float, lo: Double, hi: Double): Double {
    val t = ((p - AUTO_ZONE) / (1f - AUTO_ZONE)).coerceIn(0f, 1f)
    return lo * Math.pow(hi / lo, t.toDouble())
}

private fun isoValue(caps: LensCaps, p: Float): Int {
    val r = caps.iso ?: return 100
    val raw = logScale(p, r.lower.toDouble(), r.upper.toDouble())
    val stops = ISO_STOPS.filter { it in r.lower..r.upper }.ifEmpty { listOf(raw.roundToInt()) }
    return stops.minBy { kotlin.math.abs(Math.log(it / raw)) }
}

private fun shutterNs(caps: LensCaps, p: Float): Long {
    val r = caps.exposure ?: return 16_666_666L
    val hi = minOf(r.upper, 1_000_000_000L)
    val raw = logScale(p, r.lower.toDouble(), hi.toDouble()) / 1e9
    val stops = SHUTTER_STOPS.filter { (it * 1e9).toLong() in r.lower..hi }.ifEmpty { listOf(raw) }
    return (stops.minBy { kotlin.math.abs(Math.log(it / raw)) } * 1e9).toLong()
}

private fun shutterText(ns: Long): String {
    val s = ns / 1e9
    return if (s >= 0.5) "%.1f\"".format(s).replace(".0", "") else "1/" + (1 / s).roundToInt()
}

/** Focus position → diopters (0 = infinity, minFocus = closest). */
private fun focusDiopters(caps: LensCaps, p: Float): Float = caps.minFocus * ((p - AUTO_ZONE) / (1f - AUTO_ZONE)).coerceIn(0f, 1f)

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.awaitOn(context: Context): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addListener({ cont.resumeWith(runCatching { get() }) }, ContextCompat.getMainExecutor(context))
    }

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
@SuppressLint("MissingPermission")
@Composable
private fun CameraScreen(activity: CameraActivity) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    var settings by remember { mutableStateOf(CamSettings.load(context)) }
    fun update(s: CamSettings) {
        settings = s
        s.save(context)
    }
    var video by remember { mutableStateOf(activity.intent?.action == MediaStore.INTENT_ACTION_VIDEO_CAMERA) }
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(FlashMode.Auto) }
    var timer by remember { mutableIntStateOf(0) }
    var pro by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var dial by remember { mutableStateOf(ProDial.Ev) }
    var countdown by remember { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var extensions by remember { mutableStateOf<ExtensionsManager?>(null) }
    var looks by remember { mutableStateOf(listOf(Look.Normal)) }
    var activeLook by remember { mutableStateOf(Look.Normal) }
    var hasFront by remember { mutableStateOf(true) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordStart by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var busy by remember { mutableStateOf(false) }
    var ev by remember { mutableFloatStateOf(0.5f) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var iso by remember { mutableFloatStateOf(0f) }
    var shutter by remember { mutableFloatStateOf(0f) }
    var wb by remember { mutableFloatStateOf(0f) }
    var focus by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    var error by remember { mutableStateOf<String?>(null) }
    var scene by remember { mutableStateOf(Scene.General) }
    var processing by remember { mutableIntStateOf(0) }
    val analysisExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    val sceneDetector = remember { SceneDetector { s -> scene = s } }
    val fusion = remember { FusionAnalyzer { s -> scene = s } }
    var fusionOn by remember { mutableStateOf(false) }
    var analysisUse by remember { mutableStateOf<ImageAnalysis?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            sceneDetector.close()
            fusion.close()
            analysisExecutor.shutdown()
        }
    }
    val blink = remember { Animatable(0f) }

    val shutterSound = remember {
        android.media.MediaActionSound().apply {
            load(android.media.MediaActionSound.SHUTTER_CLICK)
            load(android.media.MediaActionSound.START_VIDEO_RECORDING)
            load(android.media.MediaActionSound.STOP_VIDEO_RECORDING)
        }
    }
    DisposableEffect(Unit) { onDispose { shutterSound.release() } }
    // Some countries require the sound; then it can't be turned off.
    val soundForced = remember { Build.VERSION.SDK_INT >= 33 && android.media.MediaActionSound.mustPlayShutterSound() }
    fun sound(kind: Int) {
        if (settings.sound || soundForced) shutterSound.play(kind)
    }

    // Keep photos upright when the phone is turned, while the UI stays in portrait.
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                rotation = when (o) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture?.targetRotation = rotation
                analysisUse?.targetRotation = rotation
                videoCapture?.targetRotation = rotation
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    // Manual settings belong to a lens: back to auto when switching.
    LaunchedEffect(front) {
        iso = 0f
        shutter = 0f
        wb = 0f
        focus = 0f
    }

    // (Re)binds the camera when the lens, the mode or a capture setting changes.
    LaunchedEffect(front, video, settings.look, settings.wide, settings.fullRes, settings.ai, settings.fast, settings.videoRes, settings.stabilize, settings.mirror) {
        val provider = try {
            ProcessCameraProvider.getInstance(context).awaitOn(context)
        } catch (e: Exception) {
            error = e.message
            return@LaunchedEffect
        }
        val ext = extensions ?: runCatching { ExtensionsManager.getInstanceAsync(context, provider).awaitOn(context) }.getOrNull()?.also { extensions = it }
        hasFront = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
        val base = if (front && hasFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val vendor = Look.entries.filter { it.ext != ExtensionMode.NONE && runCatching { ext?.isExtensionAvailable(base, it.ext) == true }.getOrDefault(false) }
        looks = Look.entries.filter { it == Look.Normal || it in vendor || (it == Look.AiPortrait && Look.Portrait !in vendor) }
        val look = if (!video && settings.look in looks) settings.look else Look.Normal

        val ratio = if (video || settings.wide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        // Photos: the sensor's full resolution, the slower high-quality pipeline (noise reduction,
        // sharpening, multi-frame where the phone does it) and a high JPEG quality.
        val icb = ImageCapture.Builder()
            .setCaptureMode(if (settings.fast) ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY else ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(if (settings.fast) 90 else 97)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(ratio)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .apply {
                        if (settings.fullRes) {
                            setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                        } else {
                            // 50-200 MP sensors combine 4 or 9 pixels into one (~12 MP): far less
                            // noise and the full processing, like the phone's own camera. Their
                            // unbinned size is noisier and slower, so it's an option.
                            setResolutionFilter { sizes, _ ->
                                sizes.filter { it.width.toLong() * it.height <= 16_800_000L }.ifEmpty { sizes }
                            }
                        }
                    }
                    .build(),
            )
            .setFlashMode(flash.capture)
            .setTargetRotation(rotation)
        if (!settings.fast && look.ext == ExtensionMode.NONE) {
            // The sensor's best still processing: high-quality noise reduction, sharpening and
            // lens aberration correction (only the modes this camera lists).
            runCatching {
                val ch = Camera2CameraInfo.from(provider.getCameraInfo(base))
                val ext2 = Camera2Interop.Extender(icb)
                val nr = ch.getCameraCharacteristic(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) ?: IntArray(0)
                if (CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY in nr) ext2.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                val edge = ch.getCameraCharacteristic(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES) ?: IntArray(0)
                if (CameraMetadata.EDGE_MODE_HIGH_QUALITY in edge) ext2.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY)
                val ab = ch.getCameraCharacteristic(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES) ?: IntArray(0)
                if (CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY in ab) ext2.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
            }
        }
        val ic = icb.build()
        val q = when (settings.videoRes) { 1 -> Quality.UHD; 2 -> Quality.FHD; 3 -> Quality.HD; else -> Quality.HIGHEST }
        val qs = if (q == Quality.HIGHEST) QualitySelector.from(q) else QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q))
        val recorder = Recorder.Builder().setQualitySelector(qs).build()
        val vb = VideoCapture.Builder(recorder)
            .setMirrorMode(if (settings.mirror) MirrorMode.MIRROR_MODE_ON_FRONT_ONLY else MirrorMode.MIRROR_MODE_OFF)
            .setTargetRotation(rotation)
        val stabilizable = runCatching { Recorder.getVideoCapabilities(provider.getCameraInfo(base)).isStabilizationSupported }.getOrDefault(false)
        if (settings.stabilize && stabilizable) vb.setVideoStabilizationEnabled(true)
        val vc = vb.build()
        imageCapture = ic
        videoCapture = vc
        val useCase = if (video) vc else ic

        fun bind(selector: CameraSelector, analysis: Boolean = false): Camera {
            provider.unbindAll()
            if (analysis) {
                // A small frame about once a second for the AI's scene recognition.
                val ia = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                ia.setAnalyzer(analysisExecutor, sceneDetector)
                return provider.bindToLifecycle(lifecycle, selector, preview, useCase, ia)
            }
            return provider.bindToLifecycle(lifecycle, selector, preview, useCase)
        }
        val extSelector = if (look.ext != ExtensionMode.NONE && ext != null) runCatching { ext.getExtensionEnabledCameraSelector(base, look.ext) }.getOrNull() else null
        var bound: Camera? = null
        var usedLook = Look.Normal
        if (extSelector != null) {
            bound = runCatching { bind(extSelector) }.getOrNull()
            if (bound != null) usedLook = look
        }
        // ZUNEX Fusion: preview + full-resolution frames for the multi-frame merge.
        var fused = false
        analysisUse = null
        if (bound == null && !video && settings.ai && !settings.fullRes && (look == Look.Normal || look == Look.AiPortrait)) {
            bound = runCatching {
                provider.unbindAll()
                val ia = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(ratio)
                            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                            .setResolutionFilter { sizes, _ -> sizes.filter { it.width.toLong() * it.height <= 16_800_000L }.ifEmpty { sizes } }
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setTargetRotation(rotation)
                    .build()
                ia.setAnalyzer(analysisExecutor, fusion)
                val cam = provider.bindToLifecycle(lifecycle, base, preview, ia)
                // Only worth it at (near) photo resolution; else the single-shot pipeline.
                val res = ia.resolutionInfo?.resolution
                if (res == null || res.width.toLong() * res.height < 6_000_000L) {
                    provider.unbindAll()
                    throw IllegalStateException("fusion stream too small")
                }
                analysisUse = ia
                cam
            }.getOrNull()
            fused = bound != null
        }
        fusionOn = fused
        if (bound == null && !video && settings.ai) bound = runCatching { bind(base, analysis = true) }.getOrNull()
        if (bound == null) bound = runCatching { bind(base) }.getOrNull()
        if (bound != null && usedLook == Look.Normal && look == Look.AiPortrait) usedLook = Look.AiPortrait
        if (video || !settings.ai) scene = Scene.General
        // No back camera (tablets, some emulators): whatever exists.
        if (bound == null) bound = runCatching { bind(CameraSelector.Builder().build()) }.getOrNull()
        camera = bound
        activeLook = usedLook
        error = if (bound == null) context.getString(R.string.camera_unavailable) else null
        zoom = 0f
        ev = 0.5f
    }
    val caps = remember(camera) { camera?.let { readCaps(it) } }
    val isFront = camera?.cameraInfo?.lensFacing == CameraSelector.LENS_FACING_FRONT
    val manualAllowed = activeLook == Look.Normal

    LaunchedEffect(flash, imageCapture) { imageCapture?.flashMode = flash.capture }
    LaunchedEffect(camera, zoom) { camera?.cameraControl?.setLinearZoom(zoom) }
    LaunchedEffect(camera, ev) {
        val c = camera ?: return@LaunchedEffect
        val range = c.cameraInfo.exposureState.exposureCompensationRange
        if (c.cameraInfo.exposureState.isExposureCompensationSupported) {
            val index = (range.lower + (range.upper - range.lower) * ev).roundToInt()
            c.cameraControl.setExposureCompensationIndex(index)
        }
    }
    // Pro: manual ISO, shutter speed, white balance and focus, sent to the sensor with Camera2.
    LaunchedEffect(camera, caps, manualAllowed, iso, shutter, wb, focus) {
        val c = camera ?: return@LaunchedEffect
        val k = caps ?: return@LaunchedEffect
        if (!manualAllowed) return@LaunchedEffect
        val b = CaptureRequestOptions.Builder()
        if (k.manualExposure && (iso >= AUTO_ZONE || shutter >= AUTO_ZONE)) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, if (iso >= AUTO_ZONE) isoValue(k, iso) else 400.coerceIn(k.iso!!.lower, k.iso.upper))
            val ns = if (shutter >= AUTO_ZONE) shutterNs(k, shutter) else 16_666_666L.coerceIn(k.exposure!!.lower, k.exposure.upper)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, ns)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(ns, 33_333_333L))
        }
        val wbIndex = (wb * k.wb.size).roundToInt()
        if (wbIndex > 0 && k.wb.isNotEmpty()) b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, k.wb[(wbIndex - 1).coerceIn(0, k.wb.size - 1)])
        if (k.minFocus > 0f && focus >= AUTO_ZONE) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDiopters(k, focus))
        }
        runCatching { Camera2CameraControl.from(c.cameraControl).setCaptureRequestOptions(b.build()) }
    }
    LaunchedEffect(recording) {
        while (recording != null) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    LaunchedEffect(Unit) { lastUri = latestCapture(context) }

    /** ZUNEX Fusion: a burst of frames, merged and tone-mapped, then the scene AI. */
    fun fusionShot(name: String, portraitAi: Boolean) {
        val shotScene = if (portraitAi && scene == Scene.General) Scene.People else scene
        val mirror = isFront && settings.mirror
        val night = fusion.brightness < 0.22f
        val count = if (night) 8 else 6
        // Highlights burning out: a darker burst (-1 EV) for the tone map to lift (HDR).
        val under = !night && fusion.clipped > 0.015f
        val torch = flash == FlashMode.On
        val c = camera
        scope.launch {
            blink.snapTo(1f)
            blink.animateTo(0f, tween(250))
        }
        sound(android.media.MediaActionSound.SHUTTER_CLICK)
        processing++
        scope.launch {
            var restore: Int? = null
            val st = c?.cameraInfo?.exposureState
            if (under && st != null && st.isExposureCompensationSupported && manualAllowed && iso < AUTO_ZONE && shutter < AUTO_ZONE) {
                val step = st.exposureCompensationStep.toFloat()
                val target = (st.exposureCompensationIndex + (-1f / step).roundToInt()).coerceIn(st.exposureCompensationRange.lower, st.exposureCompensationRange.upper)
                restore = st.exposureCompensationIndex
                runCatching { kotlinx.coroutines.withTimeoutOrNull(800) { c.cameraControl.setExposureCompensationIndex(target).awaitOn(context) } }
            }
            if (torch) {
                c?.cameraControl?.enableTorch(true)
                delay(400)
            }
            var done = false
            fusion.burst(count) { frames ->
                aiScope.launch {
                    if (done) return@launch
                    done = true
                    restore?.let { c?.cameraControl?.setExposureCompensationIndex(it) }
                    if (torch) c?.cameraControl?.enableTorch(false)
                    val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        runCatching {
                            val merged = fuse(frames)
                            val bmp = renderFused(merged, mirror, under)
                            saveFusedPhoto(context, bmp, shotScene, portraitAi, name).also { bmp.recycle() }
                        }.getOrNull()
                    }
                    processing--
                    if (uri != null) lastUri = uri else error = context.getString(R.string.camera_save_failed)
                }
            }
            // The stream stopped (camera closed, error): give up after a while.
            delay(4000)
            if (!done) {
                done = true
                fusion.burst(0) {}
                restore?.let { c?.cameraControl?.setExposureCompensationIndex(it) }
                if (torch) c?.cameraControl?.enableTorch(false)
                processing--
                error = context.getString(R.string.camera_save_failed)
            }
        }
    }

    fun takePhoto() {
        if (fusionOn) {
            fusionShot("Mozaik_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()), activeLook == Look.AiPortrait)
            return
        }
        val ic = imageCapture ?: return
        val name = "Mozaik_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val portraitAi = activeLook == Look.AiPortrait
        if ((settings.ai && activeLook == Look.Normal || portraitAi) && !settings.fullRes) {
            // ZUNEX AI: the picture comes to memory, is processed for its scene and saved.
            val shotScene = if (portraitAi && scene == Scene.General) Scene.People else scene
            val mirror = isFront && settings.mirror
            scope.launch {
                blink.snapTo(1f)
                blink.animateTo(0f, tween(250))
            }
            sound(android.media.MediaActionSound.SHUTTER_CLICK)
            processing++
            ic.takePicture(analysisExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val bytes = try {
                        if (image.format == android.graphics.ImageFormat.JPEG) {
                            val buf = image.planes[0].buffer
                            buf.rewind()
                            ByteArray(buf.remaining()).also { buf.get(it) }
                        } else {
                            val bmp = image.toBitmap()
                            java.io.ByteArrayOutputStream().use { os ->
                                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 97, os)
                                os.toByteArray()
                            }
                        }
                    } catch (_: Throwable) {
                        null
                    } finally {
                        image.close()
                    }
                    // Not tied to the screen: the photo is finished even if the camera is closed.
                    aiScope.launch {
                        val uri = bytes?.let { saveAiPhoto(context, it, rotation, mirror, shotScene, portraitAi, name) }
                        processing--
                        if (uri != null) lastUri = uri else error = context.getString(R.string.camera_save_failed)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    aiScope.launch {
                        processing--
                        error = context.getString(R.string.camera_save_failed)
                    }
                }
            })
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Mozaik")
        }
        // Selfies: saved the way others see you unless "mirror" is on (CameraX would mirror them).
        val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = isFront && settings.mirror }
        val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            .setMetadata(metadata)
            .build()
        scope.launch {
            blink.snapTo(1f)
            blink.animateTo(0f, tween(250))
        }
        sound(android.media.MediaActionSound.SHUTTER_CLICK)
        busy = true
        ic.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                busy = false
                lastUri = output.savedUri
            }

            override fun onError(exception: ImageCaptureException) {
                busy = false
                error = context.getString(R.string.camera_save_failed)
            }
        })
    }

    fun toggleRecording() {
        recording?.let {
            it.stop()
            sound(android.media.MediaActionSound.STOP_VIDEO_RECORDING)
            recording = null
            return
        }
        val vc = videoCapture ?: return
        val name = "Mozaik_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Mozaik")
        }
        val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        val pending = vc.output.prepareRecording(context, output)
        if (context.hasPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))) pending.withAudioEnabled()
        if (flash == FlashMode.On) camera?.cameraControl?.enableTorch(true)
        recordStart = System.currentTimeMillis()
        sound(android.media.MediaActionSound.START_VIDEO_RECORDING)
        recording = pending.start(ContextCompat.getMainExecutor(context)) { e ->
            if (e is VideoRecordEvent.Finalize) {
                camera?.cameraControl?.enableTorch(false)
                if (!e.hasError()) lastUri = e.outputResults.outputUri
                recording = null
            }
        }
    }

    val audioPerm = rememberPermissionState(arrayOf(Manifest.permission.RECORD_AUDIO))
    fun shoot() {
        if (countdown > 0 || showSettings) return
        if (video) {
            if (recording == null && !audioPerm.granted && audioPerm.asked == 0) audioPerm.request()
            toggleRecording()
            return
        }
        if (timer == 0) {
            takePhoto()
        } else {
            scope.launch {
                for (s in timer downTo 1) {
                    countdown = s
                    delay(1000)
                }
                countdown = 0
                takePhoto()
            }
        }
    }
    DisposableEffect(Unit) {
        activity.shutter = { shoot() }
        onDispose { activity.shutter = null }
    }
    DisposableEffect(Unit) { onDispose { recording?.stop() } }

    // Icons turn with the phone, like on Lumia Camera.
    val iconAngle = when (rotation) {
        Surface.ROTATION_90 -> 90f
        Surface.ROTATION_270 -> -90f
        Surface.ROTATION_180 -> 180f
        else -> 0f
    }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Viewfinder
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (video || settings.wide) 9f / 16f else 3f / 4f)
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val point = previewView.meteringPointFactory.createPoint(p.x, p.y)
                        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        focusPoint = p
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, z, _ ->
                        if (z != 1f) zoom = (zoom + (z - 1f) * 0.6f).coerceIn(0f, 1f)
                    }
                },
        ) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            if (settings.grid) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Color.White.copy(alpha = 0.45f)
                    for (k in 1..2) {
                        drawLine(c, Offset(size.width * k / 3, 0f), Offset(size.width * k / 3, size.height), 1.5f)
                        drawLine(c, Offset(0f, size.height * k / 3), Offset(size.width, size.height * k / 3), 1.5f)
                    }
                }
            }
            focusPoint?.let { p ->
                LaunchedEffect(p) {
                    delay(1200)
                    focusPoint = null
                }
                val half = with(density) { 32.dp.toPx() }
                Box(
                    Modifier
                        .offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) }
                        .size(64.dp)
                        .border(2.dp, Color.White),
                )
            }
            if (countdown > 0) {
                WpText(countdown.toString(), style = WpType.PanoramaTitle, color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = blink.value }.background(Color.Black))
        }
        error?.let {
            WpText(it, style = WpType.Large, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }

        // Top controls
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!video) {
                TopButton(stringResource(R.string.camera_flash_hint), iconAngle, label = when (flash) { FlashMode.Auto -> "A"; FlashMode.On -> null; FlashMode.Off -> "×" }, icon = Glyphs.Sparkle) {
                    flash = FlashMode.entries[(flash.ordinal + 1) % FlashMode.entries.size]
                }
                TopButton(stringResource(R.string.camera_timer), iconAngle, label = if (timer == 0) null else "${timer}s", icon = WpIcons.Clock) {
                    timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 }
                }
            } else {
                TopButton(stringResource(R.string.camera_light), iconAngle, label = if (flash == FlashMode.On) null else "×", icon = WpIcons.Flashlight) {
                    flash = if (flash == FlashMode.On) FlashMode.Off else FlashMode.On
                    camera?.cameraControl?.enableTorch(flash == FlashMode.On && recording != null)
                }
            }
            if (hasFront) {
                TopButton(stringResource(R.string.camera_switch), iconAngle, label = null, icon = WpIcons.Sync) { if (recording == null) front = !front }
            }
            if (!video) {
                AiButton(settings.ai, iconAngle) { update(settings.copy(ai = !settings.ai)) }
            }
            TopButton(stringResource(R.string.camera_pro), iconAngle, label = null, icon = Glyphs.Grid, selected = pro) {
                pro = !pro
                if (pro) {
                    // Manual controls work on the plain camera, not on the maker's processing modes.
                    if (!video && settings.look != Look.Normal) update(settings.copy(look = Look.Normal))
                } else {
                    iso = 0f
                    shutter = 0f
                    wb = 0f
                    focus = 0f
                    ev = 0.5f
                }
            }
            TopButton(stringResource(R.string.camera_settings), iconAngle, label = null, icon = WpIcons.Settings) { if (recording == null) showSettings = true }
        }

        if (!video && settings.ai && (activeLook == Look.Normal || activeLook == Look.AiPortrait) || processing > 0) {
            // What the AI sees, and when it's working on a picture.
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 62.dp).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WpText(
                    if (processing > 0) stringResource(R.string.camera_ai_processing) else "IA · " + stringResource(scene.label),
                    style = WpType.Small,
                    color = Color.White,
                    modifier = Modifier.rotate(iconAngle),
                )
            }
        }

        if (recording != null) {
            val secs = ((now - recordStart) / 1000).coerceAtLeast(0)
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp).background(Color(0xCCE51400)).padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                WpText("%d:%02d".format(secs / 60, secs % 60), style = WpType.Normal, color = Color.White)
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
            if (pro) {
                val dials = ProDial.entries.filter {
                    when (it) {
                        ProDial.Iso, ProDial.Shutter -> caps?.manualExposure == true && manualAllowed
                        ProDial.Focus -> (caps?.minFocus ?: 0f) > 0f && manualAllowed
                        ProDial.Wb -> caps?.wb?.isNotEmpty() == true && manualAllowed
                        else -> true
                    }
                }
                val current = if (dial in dials) dial else ProDial.Ev
                val auto = stringResource(R.string.camera_auto)
                val wbLabels = caps?.wb?.map { m -> stringResource(WB_ORDER.first { it.first == m }.second) } ?: emptyList()
                fun valueOf(d: ProDial): String = when (d) {
                    ProDial.Ev -> camera?.cameraInfo?.exposureState?.let { st ->
                        val r = st.exposureCompensationRange
                        val idx = (r.lower + (r.upper - r.lower) * ev).roundToInt()
                        val v = idx * st.exposureCompensationStep.toFloat()
                        (if (v > 0) "+" else "") + "%.1f".format(v)
                    } ?: "0"
                    ProDial.Iso -> if (iso < AUTO_ZONE || caps == null) auto else isoValue(caps, iso).toString()
                    ProDial.Shutter -> if (shutter < AUTO_ZONE || caps == null) auto else shutterText(shutterNs(caps, shutter))
                    ProDial.Wb -> {
                        val i = (wb * wbLabels.size).roundToInt()
                        if (i <= 0 || wbLabels.isEmpty()) auto else wbLabels[(i - 1).coerceIn(0, wbLabels.size - 1)]
                    }
                    ProDial.Focus -> if (focus < AUTO_ZONE || caps == null) auto else {
                        val d2 = focusDiopters(caps, focus)
                        if (d2 < 0.05f) "∞" else if (d2 >= 10f) "%.0f cm".format(100f / d2) else "%.2f m".format(1f / d2)
                    }
                    ProDial.Zoom -> "%.1fx".format(1f + zoom * ((camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f) - 1f))
                }
                // Lumia Camera's "pro" controls: pick a setting, then turn it with the slider
                // (all the way left is auto).
                Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    val value = when (current) { ProDial.Ev -> ev; ProDial.Iso -> iso; ProDial.Shutter -> shutter; ProDial.Wb -> wb; ProDial.Focus -> focus; ProDial.Zoom -> zoom }
                    WpSlider(value, { v ->
                        when (current) {
                            ProDial.Ev -> ev = v
                            ProDial.Iso -> iso = v
                            ProDial.Shutter -> shutter = v
                            ProDial.Wb -> wb = v
                            ProDial.Focus -> focus = v
                            ProDial.Zoom -> zoom = v
                        }
                    }, Modifier.padding(horizontal = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        dials.forEach { d ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .background(if (d == current) Wp.accent else Color.Transparent)
                                    .wpClickable { dial = d }
                                    .padding(vertical = 4.dp)
                                    .rotate(iconAngle),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                WpText(stringResource(d.label), style = WpType.Small, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                                WpText(valueOf(d), style = WpType.Small, color = Color.White, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (!video && !pro && looks.size > 1) {
                // Photo modes (the phone's own processing).
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    looks.forEach { l ->
                        val selected = l == activeLook
                        WpText(
                            stringResource(l.label),
                            style = WpType.Normal,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.wpClickable { update(settings.copy(look = l)) },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Last shot → camera roll
                Box(
                    Modifier
                        .size(56.dp)
                        .rotate(iconAngle)
                        .background(Color(0xFF222222))
                        .wpClickable {
                            runCatching {
                                context.startActivity(Intent(context, PhotosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                ) {
                    lastUri?.let { uri ->
                        val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, uri) {
                            value = runCatching {
                                context.contentResolver.loadThumbnail(uri, android.util.Size(168, 168), null)
                            }.getOrNull() ?: ImageLoader.thumbnail(context, uri, 168)
                        }
                        bmp?.let {
                            androidx.compose.foundation.Image(
                                it.asImageBitmap(),
                                null,
                                Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        }
                    }
                }
                // Shutter
                Box(
                    Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color.White, CircleShape)
                        .padding(7.dp)
                        .clip(CircleShape)
                        .background(if (recording != null) Color(0xFFE51400) else if (video) Color(0xFFE51400).copy(alpha = 0.85f) else if (busy) Color.White.copy(alpha = 0.6f) else Color.White)
                        .wpClickable { shoot() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (recording != null) Box(Modifier.size(22.dp).background(Color.White))
                    else WpIcon(if (video) Glyphs.Video else WpIcons.Camera, tint = if (video) Color.White else Color.Black, size = 30.dp, modifier = Modifier.rotate(iconAngle))
                }
                // Photo / video
                Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ModeIcon(WpIcons.Camera, !video, iconAngle) { if (recording == null) video = false }
                    Spacer(Modifier.height(10.dp))
                    ModeIcon(Glyphs.Video, video, iconAngle) { if (recording == null) video = true }
                }
            }
        }

        // The settings page comes in with the turnstile and leaves with the backward turnstile.
        val settingsTurn = remember { com.wpreborn.core.motion.TurnstileState(com.wpreborn.core.motion.TurnstileMode.None) }
        var settingsShown by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(showSettings) {
            if (showSettings) {
                settingsTurn.mode = com.wpreborn.core.motion.TurnstileMode.ForwardIn
                settingsTurn.progress.snapTo(0f)
                settingsShown = true
                settingsTurn.run(com.wpreborn.core.motion.TurnstileMode.ForwardIn)
            } else if (settingsShown) {
                settingsTurn.run(com.wpreborn.core.motion.TurnstileMode.BackwardOut)
                settingsShown = false
            }
        }
        if (settingsShown) {
            Box(Modifier.fillMaxSize().turnstile(settingsTurn)) {
                CameraSettingsPage(settings, soundForced, ::update) { showSettings = false }
            }
        }
    }
}

/** Camera settings, as a Windows Phone settings page over the viewfinder. */
@Composable
private fun CameraSettingsPage(s: CamSettings, soundForced: Boolean, onChange: (CamSettings) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)
    Column(
        Modifier
            .fillMaxSize()
            .background(Wp.palette.background)
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        WpText(stringResource(R.string.app_camera).uppercase(), style = WpType.AppTitle)
        WpText(stringResource(R.string.camera_settings), style = WpType.Header, modifier = Modifier.padding(bottom = 12.dp))

        Options(stringResource(R.string.camera_set_ratio), listOf("4:3", "16:9"), if (s.wide) 1 else 0) { onChange(s.copy(wide = it == 1)) }
        Options(stringResource(R.string.camera_set_resolution), listOf(stringResource(R.string.camera_res_binned), stringResource(R.string.camera_res_full)), if (s.fullRes) 1 else 0) { onChange(s.copy(fullRes = it == 1)) }
        WpText(stringResource(R.string.camera_set_resolution_hint), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(bottom = 4.dp))
        Options(stringResource(R.string.camera_set_quality), listOf(stringResource(R.string.camera_quality_max), stringResource(R.string.camera_quality_fast)), if (s.fast) 1 else 0) { onChange(s.copy(fast = it == 1)) }
        Options(stringResource(R.string.camera_set_video), listOf(stringResource(R.string.camera_video_best), "4K", "1080p", "720p"), s.videoRes) { onChange(s.copy(videoRes = it)) }
        WpToggle(stringResource(R.string.camera_set_stabilize), s.stabilize, onText = on, offText = off) { onChange(s.copy(stabilize = it)) }
        WpToggle(stringResource(R.string.camera_set_mirror), s.mirror, onText = on, offText = off) { onChange(s.copy(mirror = it)) }
        WpText(stringResource(R.string.camera_set_mirror_hint), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(bottom = 8.dp))
        WpToggle(stringResource(R.string.camera_set_sound), s.sound || soundForced, onText = on, offText = off, enabled = !soundForced) { onChange(s.copy(sound = it)) }
        if (soundForced) WpText(stringResource(R.string.camera_set_sound_forced), style = WpType.Subtle, color = Wp.palette.subtle)
        WpToggle(stringResource(R.string.camera_ai), s.ai, onText = on, offText = off) { onChange(s.copy(ai = it)) }
        WpText(stringResource(R.string.camera_ai_hint), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(bottom = 8.dp))
        WpToggle(stringResource(R.string.camera_grid), s.grid, onText = on, offText = off) { onChange(s.copy(grid = it)) }
        WpText(stringResource(R.string.camera_processing_info), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun Options(header: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        WpText(header, style = WpType.Subtle, color = Wp.palette.subtle)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, label ->
                val sel = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .border(2.dp, if (sel) Wp.accent else Wp.palette.foreground)
                        .background(if (sel) Wp.accent else Color.Transparent)
                        .wpClickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    WpText(label, style = WpType.Small, color = if (sel) Color.White else Wp.palette.foreground, maxLines = 1)
                }
            }
        }
    }
}

/** The "AI" switch: accent when on, like the camera apps of today's phones but in Metro style. */
@Composable
private fun AiButton(on: Boolean, angle: Float, onClick: () -> Unit) {
    val desc = stringResource(R.string.camera_ai)
    Box(
        Modifier
            .size(48.dp)
            .semantics { contentDescription = desc }
            .wpClickable(onClick = onClick)
            .rotate(angle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .border(2.dp, Color.White)
                .background(if (on) Wp.accent else Color.Transparent)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            WpText(stringResource(R.string.camera_ai_short), style = WpType.Small, color = Color.White)
        }
    }
}

@Composable
private fun TopButton(description: String, angle: Float, label: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .background(if (selected) Wp.accent else Color.Transparent, CircleShape)
            .semantics { contentDescription = description }
            .wpClickable(onClick = onClick)
            .rotate(angle),
        contentAlignment = Alignment.Center,
    ) {
        WpIcon(icon, tint = Color.White, size = 24.dp)
        if (label != null) {
            WpText(label, style = WpType.Small, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp))
        }
    }
}

@Composable
private fun ModeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, angle: Float, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).background(if (selected) Wp.accent else Color.Transparent, CircleShape).wpClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WpIcon(icon, tint = Color.White, size = 22.dp, modifier = Modifier.rotate(angle))
    }
}

/** The newest picture or video in Pictures/Mozaik or Movies/Mozaik, for the thumbnail. */
private suspend fun latestCapture(context: Context): Uri? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < 29) return@withContext null
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?",
            arrayOf(context.packageName),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { c ->
            if (c.moveToFirst()) android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) else null
        }
    }.getOrNull()
}
