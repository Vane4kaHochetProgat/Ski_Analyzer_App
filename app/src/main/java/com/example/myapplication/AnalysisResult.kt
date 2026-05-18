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