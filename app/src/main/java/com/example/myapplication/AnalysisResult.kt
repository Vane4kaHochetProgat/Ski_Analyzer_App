/**
 * Data classes describing the JSON returned by the video-analysis service
 * (`POST /analyze-video` via [ApiService]).
 *
 * Shape (decoded by Gson):
 *   AnalysisResult
 *     ├── status           — "completed" / "pending" / "failed"
 *     ├── analysis
 *     │     ├── overall_score   — 0..100 numeric score
 *     │     ├── angle_analysis  — per-joint metrics (see [AngleAnalysis])
 *     │     └── recommendations — free-form text tips
 *     └── files
 *           ├── annotated_video — server-side path to the overlay-rendered MP4
 *           └── charts          — server-side path to a chart image (PNG)
 *
 * Field names are `snake_case` to match the server payload — they're consumed
 * by [MistakesViewModel.recordAnalysis] and rendered by [VideoBrowser].
 */

package com.example.myapplication

data class AnalysisResult(
    val status: String,
    val analysis: Analysis,
    val files: Files
)

data class Analysis(
    val overall_score: Double,
    val angle_analysis: List<AngleAnalysis>,
    val recommendations: List<String>
)

data class AngleAnalysis(
    val angle: String,
    val percent_bad: Double,
    val mean_diff_deg: Double,
    val max_diff_deg: Double,
    val is_critical: Boolean
)

data class Files(
    val annotated_video: String?,
    val charts: String?
)