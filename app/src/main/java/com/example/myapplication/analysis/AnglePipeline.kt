package com.example.myapplication.analysis

import com.example.myapplication.AnalysisResult
import com.example.myapplication.Analysis
import com.example.myapplication.AngleAnalysis
import com.example.myapplication.Files
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object Coco17 {
    const val LEFT_SHOULDER = 5
    const val RIGHT_SHOULDER = 6
    const val LEFT_HIP = 11
    const val RIGHT_HIP = 12
    const val LEFT_KNEE = 13
    const val RIGHT_KNEE = 14
    const val LEFT_ANKLE = 15
    const val RIGHT_ANKLE = 16
    const val COUNT = 17
}

data class Keypoint(val x: Float, val y: Float, val conf: Float)

data class PoseFrame17(val points: List<Keypoint>) {
    init { require(points.size == Coco17.COUNT) }
    operator fun get(index: Int): Keypoint = points[index]
}

private const val N_RESAMPLE = 100
private const val K_STD = 1.5
private const val BAD_PERCENT_THRESHOLD = 30.0

private val ABS_THRESHOLDS = mapOf(
    "left_knee_angle" to 10.0,
    "right_knee_angle" to 10.0,
    "left_body_angle" to 8.0,
    "right_body_angle" to 8.0,
)

val ANGLE_KEYS = listOf(
    "left_knee_angle",
    "right_knee_angle",
    "left_body_angle",
    "right_body_angle",
)

// Savitzky-Golay коэффициенты для window=11, poly=3 (Vandermonde solution)
private val SG_11_3 = doubleArrayOf(
    -36.0, 9.0, 44.0, 69.0, 84.0, 89.0, 84.0, 69.0, 44.0, 9.0, -36.0
).also { c -> val sum = 429.0; for (i in c.indices) c[i] /= sum }

private fun angleAt(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Double {
    val bax = (ax - bx).toDouble(); val bay = (ay - by).toDouble()
    val bcx = (cx - bx).toDouble(); val bcy = (cy - by).toDouble()
    val denom = sqrt(bax * bax + bay * bay) * sqrt(bcx * bcx + bcy * bcy)
    if (denom == 0.0) return 0.0
    val cos = ((bax * bcx + bay * bcy) / denom).coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(cos))
}

fun calculateAngles(frames: List<PoseFrame17?>): Map<String, DoubleArray> {
    val lk = mutableListOf<Double>()
    val rk = mutableListOf<Double>()
    val lb = mutableListOf<Double>()
    val rb = mutableListOf<Double>()
    for (f in frames) {
        if (f == null) continue
        val lh = f[Coco17.LEFT_HIP]; val lkn = f[Coco17.LEFT_KNEE]; val la = f[Coco17.LEFT_ANKLE]
        val rh = f[Coco17.RIGHT_HIP]; val rkn = f[Coco17.RIGHT_KNEE]; val ra = f[Coco17.RIGHT_ANKLE]
        val ls = f[Coco17.LEFT_SHOULDER]; val rs = f[Coco17.RIGHT_SHOULDER]
        lk += angleAt(lh.x, lh.y, lkn.x, lkn.y, la.x, la.y)
        rk += angleAt(rh.x, rh.y, rkn.x, rkn.y, ra.x, ra.y)
        lb += angleAt(ls.x, ls.y, lh.x, lh.y, lkn.x, lkn.y)
        rb += angleAt(rs.x, rs.y, rh.x, rh.y, rkn.x, rkn.y)
    }
    return mapOf(
        "left_knee_angle" to lk.toDoubleArray(),
        "right_knee_angle" to rk.toDoubleArray(),
        "left_body_angle" to lb.toDoubleArray(),
        "right_body_angle" to rb.toDoubleArray(),
    )
}

fun smoothSavGol11_3(y: DoubleArray): DoubleArray {
    val n = y.size
    if (n < SG_11_3.size) return y.copyOf()
    val out = DoubleArray(n)
    val half = SG_11_3.size / 2
    for (i in 0 until half) out[i] = y[i]
    for (i in n - half until n) out[i] = y[i]
    for (i in half until n - half) {
        var s = 0.0
        for (k in SG_11_3.indices) s += SG_11_3[k] * y[i - half + k]
        out[i] = s
    }
    return out
}

fun resampleLinear(y: DoubleArray, targetLen: Int = N_RESAMPLE): DoubleArray {
    if (y.isEmpty()) return DoubleArray(targetLen)
    if (y.size == targetLen) return y.copyOf()
    val out = DoubleArray(targetLen)
    val srcLastIdx = (y.size - 1).toDouble()
    for (i in 0 until targetLen) {
        val t = if (targetLen == 1) 0.0 else i.toDouble() / (targetLen - 1)
        val pos = t * srcLastIdx
        val lo = pos.toInt()
        val hi = min(lo + 1, y.size - 1)
        val frac = pos - lo
        out[i] = y[lo] * (1 - frac) + y[hi] * frac
    }
    return out
}

data class AngleTemplate(val nPoints: Int, val means: Map<String, DoubleArray>, val stds: Map<String, DoubleArray>) {
    init {
        for (k in ANGLE_KEYS) {
            requireNotNull(means[k]) { "template missing mean for $k" }
            requireNotNull(stds[k]) { "template missing std for $k" }
        }
    }
}

fun compareToTemplate(
    userAngles: Map<String, DoubleArray>,
    template: AngleTemplate,
): AnalysisResult {
    val perAngle = mutableListOf<AngleAnalysis>()
    for (angle in ANGLE_KEYS) {
        val raw = userAngles[angle] ?: continue
        val smoothed = smoothSavGol11_3(raw)
        val user = resampleLinear(smoothed, template.nPoints)
        val mean = template.means[angle]!!
        val std = template.stds[angle]!!
        val n = minOf(user.size, mean.size, std.size)
        var bad = 0; var sumDiff = 0.0; var maxAbs = 0.0
        val thr = ABS_THRESHOLDS[angle] ?: 10.0
        for (i in 0 until n) {
            val d = user[i] - mean[i]
            val a = abs(d)
            sumDiff += d
            if (a > maxAbs) maxAbs = a
            if (a > thr || a > K_STD * std[i]) bad++
        }
        val percentBad = if (n == 0) 0.0 else 100.0 * bad / n
        perAngle += AngleAnalysis(
            angle = angle,
            percent_bad = percentBad,
            mean_diff_deg = if (n == 0) 0.0 else sumDiff / n,
            max_diff_deg = maxAbs,
            is_critical = percentBad > BAD_PERCENT_THRESHOLD,
        )
    }
    val avgBad = if (perAngle.isEmpty()) 0.0 else perAngle.sumOf { it.percent_bad } / perAngle.size
    val score = max(0.0, 100.0 - avgBad)
    return AnalysisResult(
        status = "completed",
        analysis = Analysis(
            overall_score = (score * 10.0).roundToInt() / 10.0,
            angle_analysis = perAngle,
            recommendations = generateRecommendations(perAngle),
        ),
        files = Files(annotated_video = null, charts = null),
    )
}

private fun generateRecommendations(perAngle: List<AngleAnalysis>): List<String> {
    val recs = mutableListOf<String>()
    for (fb in perAngle) {
        if (fb.percent_bad < BAD_PERCENT_THRESHOLD) continue
        val isLeft = fb.angle.startsWith("left")
        if ("knee" in fb.angle) {
            val side = if (isLeft) "левое" else "правое"
            recs += if (fb.mean_diff_deg > 0)
                "Колено ($side): чаще более разогнуто, чем в эталоне. Увеличьте сгибание колена в фазе поворота."
            else
                "Колено ($side): сгибается больше, чем в эталоне. Возможна излишняя посадка."
        } else if ("body" in fb.angle) {
            val side = if (isLeft) "левая" else "правая"
            recs += if (fb.mean_diff_deg > 0)
                "Корпус ($side сторона): отклонён назад относительно эталона. Сместите центр тяжести к носкам лыж."
            else
                "Корпус ($side сторона): подан вперёд сильнее, чем в эталоне. Следите за балансом."
        }
    }
    if (recs.isEmpty()) recs += "Техника близка к эталонной. Продолжайте в том же духе!"
    return recs
}
