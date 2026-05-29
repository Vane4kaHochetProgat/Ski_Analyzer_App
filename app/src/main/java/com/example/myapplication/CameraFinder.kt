/**
 * CameraX-backed viewfinder for in-app recording.
 *
 * Contents:
 *   * [PERMISSIONS]         — runtime permissions ([Manifest.permission.CAMERA]
 *                             + [Manifest.permission.RECORD_AUDIO]) checked
 *                             by [MainActivity] before this screen is shown.
 *   * [PreviewViewModel]    — owns the CameraX `Preview` use case, a
 *                             `VideoCapture<Recorder>` at `Quality.HIGHEST`,
 *                             and an icon-state mutableState that toggles
 *                             between `PlayArrow` (idle) and `Close` (recording).
 *                             `startCapture` writes the MP4 to
 *                             `filesDir/<epochMillis>.mp4`; those files are
 *                             later listed by [VideoBrowser].
 *   * [MyCameraViewfinder]  — Composable that binds the camera to the current
 *                             `LifecycleOwner`, renders [CameraXViewfinder]
 *                             and a centered toggle button for record/stop.
 *
 * NOTE: tap-to-focus is wired up to [PreviewViewModel.focusOnPoint] but the
 * implementation is currently empty — feature marked as partially implemented.
 */

package com.example.myapplication

import android.Manifest
import android.content.Context
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

val PERMISSIONS = listOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
)

class PreviewViewModel : ViewModel() {
    private val _surfaceRequests = MutableStateFlow<SurfaceRequest?>(null)
    private val _poseFrames = MutableStateFlow<PoseFrame?>(null)
    private val cameraPreviewUseCase = Preview.Builder().build()
    private val videoRecorder =
        Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build()
    private val videoCapture = VideoCapture.withOutput(videoRecorder)
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also {
            it.setAnalyzer(analyzerExecutor, PoseAnalyzer { frame ->
                _poseFrames.value = frame
            })
        }
    private var recording by mutableStateOf<Recording?>(null)

    public var recordingState = mutableStateOf<ImageVector>(Icons.Default.PlayArrow)

    val surfaceRequests: StateFlow<SurfaceRequest?>
        get() = _surfaceRequests.asStateFlow()

    val poseFrames: StateFlow<PoseFrame?>
        get() = _poseFrames.asStateFlow()

    fun focusOnPoint(surfaceBounds: Size, x: Float, y: Float) {}

    override fun onCleared() {
        analyzerExecutor.shutdown()
        super.onCleared()
    }

    suspend fun startCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {

        cameraPreviewUseCase.setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequests.value = newSurfaceRequest
        }

        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)
        processCameraProvider.bindToLifecycle(
            lifecycleOwner, DEFAULT_BACK_CAMERA,
            cameraPreviewUseCase, videoCapture, imageAnalysis,
        )

        try {
            awaitCancellation()
        } finally {
            processCameraProvider.unbindAll()
        }

    }


    fun startCapture(appContext: Context) {
        recording = null
        val fileOutputOptions = FileOutputOptions.Builder(
            File(
                appContext.filesDir,
                System.currentTimeMillis().toString() + ".mp4"
            )
        ).build()
        val pendingRecording = videoCapture.output.prepareRecording(appContext, fileOutputOptions)
        recording =
            pendingRecording.start(ContextCompat.getMainExecutor(appContext)) { videoRecordEvent ->
                when (videoRecordEvent) {
                    is VideoRecordEvent.Start -> {
                        recordingState.value = Icons.Default.Close
                    }

                    is VideoRecordEvent.Finalize -> {
                        recordingState.value = Icons.Default.PlayArrow
                    }
                }
            }
    }

    fun endCapture() {
        recording?.stop()
        recording = null
    }
}

@Composable
fun MyCameraViewfinder(
    viewModel: PreviewViewModel,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val currentSurfaceRequest: SurfaceRequest? by viewModel.surfaceRequests.collectAsState()
    val poseFrame: PoseFrame? by viewModel.poseFrames.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        viewModel.startCamera(context.applicationContext, lifecycleOwner)
    }

    currentSurfaceRequest?.let { surfaceRequest ->

        val coordinateTransformer = remember { MutableCoordinateTransformer() }
        val isRecording = viewModel.recordingState.value == Icons.Default.Close

        Box(modifier = modifier) {
            CameraXViewfinder(
                surfaceRequest = surfaceRequest,
                implementationMode = ImplementationMode.EXTERNAL,
                modifier = Modifier.matchParentSize().pointerInput(Unit) {
                    detectTapGestures {
                        with(coordinateTransformer) {
                            val surfaceCoords = it.transform()
                            viewModel.focusOnPoint(
                                surfaceRequest.resolution,
                                surfaceCoords.x,
                                surfaceCoords.y,
                            )
                        }
                    }
                },
                coordinateTransformer = coordinateTransformer,
            )
            PoseOverlay(
                frame = poseFrame,
                modifier = Modifier.matchParentSize(),
                mirrored = false,
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(bottom = 40.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                RecordButton(
                    isRecording = isRecording,
                    onClick = {
                        if (isRecording) {
                            viewModel.endCapture()
                            onClose()
                        } else {
                            viewModel.startCapture(context)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE53935)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935)),
                )
            }
        }
    }
}