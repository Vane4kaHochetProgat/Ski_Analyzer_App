package com.vane4ka.skianalyzer.analysis

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class YoloPoseDetector(context: Context) : Closeable {

    private val interpreter: Interpreter
    private val inputW: Int
    private val inputH: Int
    private val outputShape: IntArray

    init {
        val (asset, model) = loadAnyKnownModel(context)
        android.util.Log.i("YoloPoseDetector", "loaded model from assets/$asset")
        val opts = Interpreter.Options().apply { numThreads = 4 }
        interpreter = Interpreter(model, opts)
        val inShape = interpreter.getInputTensor(0).shape()
        inputH = inShape[1]
        inputW = inShape[2]
        outputShape = interpreter.getOutputTensor(0).shape()
    }

    fun detect(bitmap: Bitmap, confThreshold: Float = 0.25f): PoseFrame17? {
        val (input, letterbox) = preprocessLetterbox(bitmap, inputW, inputH)
        val outN = outputShape[2]
        val outBuf = Array(1) { Array(56) { FloatArray(outN) } }
        interpreter.run(input, outBuf)

        val best = pickBestAnchor(outBuf[0], confThreshold) ?: return null
        val kps = ArrayList<Keypoint>(17)
        for (k in 0 until 17) {
            val base = 5 + k * 3
            val xModel = outBuf[0][base][best]
            val yModel = outBuf[0][base + 1][best]
            val conf = outBuf[0][base + 2][best]
            val (xOrig, yOrig) = letterbox.mapBack(xModel, yModel)
            kps += Keypoint(xOrig, yOrig, conf)
        }
        return PoseFrame17(kps)
    }

    private fun pickBestAnchor(out: Array<FloatArray>, conf: Float): Int? {
        val n = out[0].size
        var bestIdx = -1; var bestScore = conf
        for (i in 0 until n) {
            val s = out[4][i]
            if (s > bestScore) { bestScore = s; bestIdx = i }
        }
        return if (bestIdx >= 0) bestIdx else null
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        private val KNOWN_ASSETS = listOf(
            "yolov8n-pose_float16.tflite",
            "yolov8n-pose_float32.tflite",
            "yolov8n-pose_int8.tflite",
            "yolov8n-pose.tflite",
        )

        private fun loadAnyKnownModel(ctx: Context): Pair<String, ByteBuffer> {
            val available = try { ctx.assets.list("")?.toSet().orEmpty() } catch (_: Exception) { emptySet() }
            val picked = KNOWN_ASSETS.firstOrNull { it in available }
                ?: throw java.io.FileNotFoundException(
                    "TFLite-модель не найдена в assets/. Ожидалось одно из: " +
                            KNOWN_ASSETS.joinToString() +
                            ". В assets лежит: " +
                            (available.filter { it.endsWith(".tflite") }.ifEmpty { listOf("(ничего)") }.joinToString()) +
                            ". См. SETUP_LOCAL_ML.md."
                )
            return picked to loadModelFile(ctx, picked)
        }

        private fun loadModelFile(ctx: Context, asset: String): ByteBuffer {
            val afd = ctx.assets.openFd(asset)
            val input = afd.createInputStream()
            val bytes = input.readBytes()
            input.close(); afd.close()
            return ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
                .put(bytes)
                .apply { rewind() }
        }
    }
}

class Letterbox(
    val scale: Float, val padX: Float, val padY: Float,
    val origW: Int, val origH: Int,
) {
    fun mapBack(xModel: Float, yModel: Float): Pair<Float, Float> {
        val x = (xModel - padX) / scale
        val y = (yModel - padY) / scale
        return x.coerceIn(0f, origW - 1f) to y.coerceIn(0f, origH - 1f)
    }
}

fun preprocessLetterbox(src: Bitmap, dstW: Int, dstH: Int): Pair<Array<Array<Array<FloatArray>>>, Letterbox> {
    val origW = src.width
    val origH = src.height
    val scale = min(dstW.toFloat() / origW, dstH.toFloat() / origH)
    val newW = (origW * scale).toInt()
    val newH = (origH * scale).toInt()
    val padX = (dstW - newW) / 2f
    val padY = (dstH - newH) / 2f

    val resized = Bitmap.createScaledBitmap(src, newW, newH, true)
    val canvas = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(canvas)
    c.drawRGB(114, 114, 114)
    c.drawBitmap(resized, padX, padY, null)
    if (resized !== src) resized.recycle()

    val pixels = IntArray(dstW * dstH)
    canvas.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
    canvas.recycle()
    val tensor = Array(1) { Array(dstH) { Array(dstW) { FloatArray(3) } } }
    var p = 0
    for (y in 0 until dstH) {
        for (x in 0 until dstW) {
            val px = pixels[p++]
            tensor[0][y][x][0] = ((px shr 16) and 0xFF) / 255f
            tensor[0][y][x][1] = ((px shr 8) and 0xFF) / 255f
            tensor[0][y][x][2] = (px and 0xFF) / 255f
        }
    }
    return tensor to Letterbox(scale, padX, padY, origW, origH)
}
