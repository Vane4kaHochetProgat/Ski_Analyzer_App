package com.example.myapplication.analysis

import android.content.Context
import com.google.gson.Gson

private data class TemplateJson(
    val n_points: Int,
    val angles: Map<String, AngleCurves>,
)

private data class AngleCurves(val mean: DoubleArray, val std: DoubleArray)

object TemplateLoader {

    private const val ASSET_PATH = "template_angles.json"

    @Volatile private var cached: AngleTemplate? = null

    fun load(context: Context): AngleTemplate {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            val parsed = Gson().fromJson(text, TemplateJson::class.java)
            val template = AngleTemplate(
                nPoints = parsed.n_points,
                means = parsed.angles.mapValues { it.value.mean },
                stds = parsed.angles.mapValues { it.value.std },
            )
            cached = template
            return template
        }
    }
}
