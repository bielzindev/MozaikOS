package com.wpreborn.apps.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.KeyEvent
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

private enum class FlashMode(val capture: Int) { Auto(ImageCapture.FLASH_MODE_AUTO), On(ImageCapture.FLASH_MODE_ON), Off(ImageCapture.FLASH_MODE_OFF) }

/**
 * Camera in the style of Lumia Camera on Windows Phone 8.1: full-screen viewfinder, a slim
 * control column (flash, front/back, timer, grid), the round shutter, photo/video switch
 * and the last shot in the corner. "Pro" opens manual exposure and zoom dials. Pictures and
 * videos go to Pictures/ZUNEX and Movies/ZUNEX. Volume keys and the camera key shoot.
 */
class CameraActivity : WpActivity() {

    override val showStatusBar: Boolean get() = false

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
    var video by remember { mutableStateOf(activity.intent?.action == MediaStore.INTENT_ACTION_VIDEO_CAMERA) }
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(FlashMode.Auto) }
    var timer by remember { mutableIntStateOf(0) }
    var grid by remember { mutableStateOf(false) }
    var pro by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasFront by remember { mutableStateOf(true) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordStart by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var ev by remember { mutableFloatStateOf(0.5f) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    var error by remember { mutableStateOf<String?>(null) }
    val blink = remember { Animatable(0f) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
            .build()
    }
    val videoCapture = remember {
        VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build())
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
                imageCapture.targetRotation = rotation
                videoCapture.targetRotation = rotation
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    // (Re)binds the camera when the lens or the photo/video mode changes.
    LaunchedEffect(front, video) {
        val provider = try {
            kotlinx.coroutines.suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
                val f = ProcessCameraProvider.getInstance(context)
                f.addListener({ runCatching { cont.resumeWith(Result.success(f.get())) } }, ContextCompat.getMainExecutor(context))
            }
        } catch (e: Exception) {
            error = e.message
            return@LaunchedEffect
        }
        hasFront = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
        val selector = if (front && hasFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(if (video) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycle, selector, preview, if (video) videoCapture else imageCapture)
            error = null
            zoom = 0f
            ev = 0.5f
        } catch (e: Exception) {
            // No back camera (tablets, some emulators): fall back to whatever exists.
            try {
                camera = provider.bindToLifecycle(lifecycle, CameraSelector.Builder().build(), preview, if (video) videoCapture else imageCapture)
            } catch (e2: Exception) {
                error = context.getString(R.string.camera_unavailable)
            }
        }
    }
    LaunchedEffect(flash) { imageCapture.flashMode = flash.capture }
    LaunchedEffect(camera, zoom) { camera?.cameraControl?.setLinearZoom(zoom) }
    LaunchedEffect(camera, ev) {
        val c = camera ?: return@LaunchedEffect
        val range = c.cameraInfo.exposureState.exposureCompensationRange
        if (c.cameraInfo.exposureState.isExposureCompensationSupported) {
            val index = (range.lower + (range.upper - range.lower) * ev).roundToInt()
            c.cameraControl.setExposureCompensationIndex(index)
        }
    }
    LaunchedEffect(recording) {
        while (recording != null) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    LaunchedEffect(Unit) { lastUri = latestCapture(context) }

    fun takePhoto() {
        val name = "ZUNEX_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ZUNEX")
        }
        val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        scope.launch {
            blink.snapTo(1f)
            blink.animateTo(0f, tween(250))
        }
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                lastUri = output.savedUri
            }

            override fun onError(exception: ImageCaptureException) {
                error = context.getString(R.string.camera_save_failed)
            }
        })
    }

    fun toggleRecording() {
        recording?.let {
            it.stop()
            recording = null
            return
        }
        val name = "ZUNEX_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/ZUNEX")
        }
        val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        val pending = videoCapture.output.prepareRecording(context, output)
        if (context.hasPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))) pending.withAudioEnabled()
        if (flash == FlashMode.On) camera?.cameraControl?.enableTorch(true)
        recordStart = System.currentTimeMillis()
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
        if (countdown > 0) return
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
                .aspectRatio(if (video) 9f / 16f else 3f / 4f)
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
            if (grid) {
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
            TopButton(stringResource(R.string.camera_grid), iconAngle, label = if (grid) null else "×", icon = Glyphs.Grid) { grid = !grid }
            if (hasFront) {
                TopButton(stringResource(R.string.camera_switch), iconAngle, label = null, icon = WpIcons.Sync) { if (recording == null) front = !front }
            }
            TopButton(stringResource(R.string.camera_pro), iconAngle, label = null, icon = WpIcons.Settings, selected = pro) { pro = !pro }
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
                // Lumia Camera's "pro" dials, as two sliders over the viewfinder.
                Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 20.dp, vertical = 6.dp)) {
                    val evText = camera?.cameraInfo?.exposureState?.let { st ->
                        val r = st.exposureCompensationRange
                        val idx = (r.lower + (r.upper - r.lower) * ev).roundToInt()
                        val v = idx * st.exposureCompensationStep.toFloat()
                        (if (v > 0) "+" else "") + "%.1f".format(v)
                    } ?: "0"
                    WpText(stringResource(R.string.camera_exposure) + "  " + evText, style = WpType.Small, color = Color.White)
                    WpSlider(ev, { ev = it })
                    WpText(stringResource(R.string.camera_zoom) + "  " + "%.1fx".format(1f + zoom * ((camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f) - 1f)), style = WpType.Small, color = Color.White)
                    WpSlider(zoom, { zoom = it })
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
                        .background(if (recording != null) Color(0xFFE51400) else if (video) Color(0xFFE51400).copy(alpha = 0.85f) else Color.White)
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

/** The newest picture or video in Pictures/ZUNEX or Movies/ZUNEX, for the thumbnail. */
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
