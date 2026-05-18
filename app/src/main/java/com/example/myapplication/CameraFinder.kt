package com.example.myapplication

import android.Manifest
import android.content.Context
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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

val PERMISSIONS = listOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
)

class PreviewViewModel : ViewModel() {
    private val _surfaceRequests = MutableStateFlow<SurfaceRequest?>(null)
    private val cameraPreviewUseCase = Preview.Builder().build()
    private val videoRecorder =
        Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build()
    private val videoCapture = VideoCapture.withOutput(videoRecorder)
    private var recording by mutableStateOf<Recording?>(null)

    public var recordingState = mutableStateOf<ImageVector>(Icons.Default.PlayArrow)

    val surfaceRequests: StateFlow<SurfaceRequest?>
        get() = _surfaceRequests.asStateFlow()

    fun focusOnPoint(surfaceBounds: Size, x: Float, y: Float) {
        // Create point for CameraX's CameraControl.startFocusAndMetering() and submit...
    }

    suspend fun startCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {

        cameraPreviewUseCase.setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequests.value = newSurfaceRequest
        }

        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)
        processCameraProvider.bindToLifecycle(
            lifecycleOwner, DEFAULT_BACK_CAMERA, cameraPreviewUseCase, videoCapture
        )

        // Cancellation signals we're done with the camera
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
fun MyCameraViewfinder(viewModel: PreviewViewModel, modifier: Modifier = Modifier) {
    val currentSurfaceRequest: SurfaceRequest? by viewModel.surfaceRequests.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        viewModel.startCamera(context.applicationContext, lifecycleOwner)
    }

    currentSurfaceRequest?.let { surfaceRequest ->

        // CoordinateTransformer for transforming from Offsets to Surface coordinates
        val coordinateTransformer = remember { MutableCoordinateTransformer() }

        CameraXViewfinder(
            surfaceRequest = surfaceRequest,
            implementationMode = ImplementationMode.EXTERNAL, // Can also use EMBEDDED
            modifier =
                modifier.pointerInput(Unit) {
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
        IconButton(
            onClick = {
                if (viewModel.recordingState.value == Icons.Default.PlayArrow) {
                    viewModel.startCapture(context)
                } else if (viewModel.recordingState.value == Icons.Default.Close) {
                    viewModel.endCapture()
                }
            },
            modifier = Modifier
                .width(200.dp)
                .height(200.dp),

            ) { Icon(viewModel.recordingState.value, "") }
    }
}