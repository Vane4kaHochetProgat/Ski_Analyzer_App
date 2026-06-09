package com.vane4ka.skianalyzer

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.abs
import kotlin.math.atan2

data class PoseFrame(val pose: Pose, val imageWidth: Int, val imageHeight: Int)

class PoseAnalyzer(
    private val onPose: (PoseFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        val (w, h) = if (rotation == 90 || rotation == 270)
            imageProxy.height to imageProxy.width
        else
            imageProxy.width to imageProxy.height

        detector.process(input)
            .addOnSuccessListener { pose -> onPose(PoseFrame(pose, w, h)) }
            .addOnCompleteListener { imageProxy.close() }
    }
}

private val SKELETON: List<Pair<Int, Int>> = listOf(
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
    PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
    PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
    PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
)

data class JointRule(
    val proximal: Int,
    val vertex: Int,
    val distal: Int,
    val targetDeg: Float,
    val toleranceDeg: Float,
    val name: String,
)

val DEFAULT_RULES: List<JointRule> = listOf(
    JointRule(
        proximal = PoseLandmark.LEFT_SHOULDER,
        vertex = PoseLandmark.LEFT_ELBOW,
        distal = PoseLandmark.LEFT_WRIST,
        targetDeg = 90f, toleranceDeg = 15f, name = "left elbow",
    ),
    JointRule(
        proximal = PoseLandmark.RIGHT_SHOULDER,
        vertex = PoseLandmark.RIGHT_ELBOW,
        distal = PoseLandmark.RIGHT_WRIST,
        targetDeg = 90f, toleranceDeg = 15f, name = "right elbow",
    ),
)

data class JointError(val rule: JointRule, val measuredDeg: Float, val deltaDeg: Float)

private fun angleAt(a: PoseLandmark, vertex: PoseLandmark, c: PoseLandmark): Float {
    val ax = a.position.x - vertex.position.x
    val ay = a.position.y - vertex.position.y
    val cx = c.position.x - vertex.position.x
    val cy = c.position.y - vertex.position.y
    val raw = Math.toDegrees(
        (atan2(cy, cx) - atan2(ay, ax)).toDouble()
    ).toFloat()
    var deg = abs(raw)
    if (deg > 180f) deg = 360f - deg
    return deg
}

fun evaluatePose(pose: Pose, rules: List<JointRule> = DEFAULT_RULES): List<JointError> {
    val out = mutableListOf<JointError>()
    for (rule in rules) {
        val a = pose.getPoseLandmark(rule.proximal) ?: continue
        val v = pose.getPoseLandmark(rule.vertex) ?: continue
        val c = pose.getPoseLandmark(rule.distal) ?: continue
        if (a.inFrameLikelihood < 0.5f || v.inFrameLikelihood < 0.5f || c.inFrameLikelihood < 0.5f) continue
        val measured = angleAt(a, v, c)
        val delta = measured - rule.targetDeg
        if (abs(delta) > rule.toleranceDeg) {
            out.add(JointError(rule, measured, delta))
        }
    }
    return out
}

@Composable
fun PoseOverlay(
    frame: PoseFrame?,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
) {
    if (frame == null || frame.imageWidth == 0 || frame.imageHeight == 0) return
    val pose = frame.pose
    val errors = evaluatePose(pose)

    Canvas(modifier = modifier) {
        val scale = minOf(size.width / frame.imageWidth, size.height / frame.imageHeight)
        val dx = (size.width - frame.imageWidth * scale) / 2f
        val dy = (size.height - frame.imageHeight * scale) / 2f

        fun mapLm(lm: PoseLandmark): Offset {
            val x = if (mirrored) frame.imageWidth - lm.position.x else lm.position.x
            return Offset(x * scale + dx, lm.position.y * scale + dy)
        }

        for ((typeA, typeB) in SKELETON) {
            val a = pose.getPoseLandmark(typeA) ?: continue
            val b = pose.getPoseLandmark(typeB) ?: continue
            if (a.inFrameLikelihood < 0.5f || b.inFrameLikelihood < 0.5f) continue
            drawLine(
                color = Color.Green,
                start = mapLm(a),
                end = mapLm(b),
                strokeWidth = 6f,
            )
        }
        for (lm in pose.allPoseLandmarks) {
            if (lm.inFrameLikelihood < 0.5f) continue
            drawCircle(color = Color.Yellow, radius = 8f, center = mapLm(lm))
        }
        for (err in errors) {
            val v = pose.getPoseLandmark(err.rule.vertex) ?: continue
            drawCircle(
                color = Color.Red,
                radius = 24f,
                center = mapLm(v),
                style = Stroke(width = 5f),
            )
        }
    }
}
