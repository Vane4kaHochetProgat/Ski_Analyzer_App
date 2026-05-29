/**
 * ViewModel for the Mistakes tab — lists a user's recorded mistakes and turns
 * a freshly-completed [AnalysisResult] into rows on the backend.
 *
 * UI state is a sealed [MistakesUiState] (Loading / NotSignedIn / Loaded /
 * Error) exposed as a `StateFlow`. The screen calls [refresh] on first
 * composition, and [recordAnalysis] is invoked by [VideoBrowser] right after
 * a successful upload to the analysis service.
 *
 * [recordAnalysis] persists three things in order:
 *   1. `POST /videos`          — registers the source file.
 *   2. `POST /analyses`        — saves the score, recommendations, and the
 *                                per-joint `angle_analyses` rows.
 *   3. `POST /user_mistakes`   — one row per mistake derived from the
 *                                analyzed angles (see [mistakesFrom]).
 * After the writes, [refresh] reloads the list. 409 conflicts from
 * `createUserMistake` are intentionally swallowed (already recorded).
 *
 * [mistakesFrom] is the heuristic that maps generic angle names to the
 * stable mistake codes seeded in `seed.sql`:
 *   * hip*                          → "hip_rotation_issues"
 *   * knee*                         → "stiff_knees"
 *   * back* / spine* / torso*       → "leaning_back"
 *   * shoulder* / arm* / elbow*     → "arms_too_wide"
 *   * head* / neck* / gaze*         → "looking_down"
 * Severity is derived from `is_critical` and `percent_bad` thresholds
 * (≥70 → high, ≥50 → medium, ≥40 → low; below 40 is skipped unless critical).
 * Each code appears at most once per analysis. [mistakesFromForTest] exposes
 * the otherwise-private function for unit tests.
 */

package com.example.myapplication

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class MistakesUiState {
    object Loading : MistakesUiState()
    object NotSignedIn : MistakesUiState()
    data class Loaded(val mistakes: List<UserMistakeDetailDto>) : MistakesUiState()
    data class Error(val message: String) : MistakesUiState()
}

class MistakesViewModel @JvmOverloads constructor(
    app: Application,
    private val api: BackendAPI = backendApi,
    private val session: UserSession = UserSession(app),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<MistakesUiState>(MistakesUiState.Loading)
    val state: StateFlow<MistakesUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = MistakesUiState.Loading
            val user = session.current.first()
            if (user == null) {
                _state.value = MistakesUiState.NotSignedIn
                return@launch
            }
            _state.value = try {
                val rows = withContext(ioDispatcher) {
                    api.listMyMistakes()
                }
                MistakesUiState.Loaded(rows)
            } catch (e: Exception) {
                MistakesUiState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun recordAnalysis(file: File, sportCode: String, result: AnalysisResult) {
        viewModelScope.launch {
            val user = session.current.first() ?: return@launch
            try {
                withContext(ioDispatcher) {
                    val video = api.createVideo(
                        VideoCreateRequest(
                            title = file.nameWithoutExtension,
                            sport_code = sportCode,
                            file_path = file.absolutePath,
                            annotated_path = result.files.annotated_video
                        )
                    )
                    val analysis = api.createAnalysis(
                        AnalysisCreateRequest(
                            video_id = video.video_id,
                            overall_score = result.analysis.overall_score,
                            charts_path = result.files.charts,
                            status = result.status.ifBlank { "completed" },
                            angles = result.analysis.angle_analysis.map {
                                AngleAnalysisIn(
                                    angle = it.angle,
                                    percent_bad = it.percent_bad,
                                    mean_diff_deg = it.mean_diff_deg,
                                    max_diff_deg = it.max_diff_deg,
                                    is_critical = it.is_critical
                                )
                            },
                            recommendations = result.analysis.recommendations
                        )
                    )
                    for ((code, severity) in mistakesFrom(result.analysis.angle_analysis)) {
                        try {
                            api.createUserMistake(
                                UserMistakeCreateRequest(
                                    analysis_id = analysis.analysis_id,
                                    mistake_code = code,
                                    severity_code = severity
                                )
                            )
                        } catch (e: Exception) {
                            Log.w("MistakesVM", "skipping mistake $code: ${e.message}")
                        }
                    }
                }
                refresh()
            } catch (e: Exception) {
                Log.e("MistakesVM", "failed to persist analysis: ${e.message}", e)
            }
        }
    }
}

internal fun mistakesFromForTest(angles: List<AngleAnalysis>) = mistakesFrom(angles)

private fun mistakesFrom(angles: List<AngleAnalysis>): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    val seen = mutableSetOf<String>()
    for (a in angles) {
        if (!a.is_critical && a.percent_bad < 40.0) continue
        val code = when {
            a.angle.contains("hip", ignoreCase = true) -> "hip_rotation_issues"
            a.angle.contains("knee", ignoreCase = true) -> "stiff_knees"
            a.angle.contains("back", ignoreCase = true) ||
                a.angle.contains("spine", ignoreCase = true) ||
                a.angle.contains("torso", ignoreCase = true) -> "leaning_back"
            a.angle.contains("shoulder", ignoreCase = true) ||
                a.angle.contains("arm", ignoreCase = true) ||
                a.angle.contains("elbow", ignoreCase = true) -> "arms_too_wide"
            a.angle.contains("head", ignoreCase = true) ||
                a.angle.contains("neck", ignoreCase = true) ||
                a.angle.contains("gaze", ignoreCase = true) -> "looking_down"
            else -> null
        } ?: continue
        if (!seen.add(code)) continue
        val severity = when {
            a.is_critical -> "high"
            a.percent_bad >= 70.0 -> "high"
            a.percent_bad >= 50.0 -> "medium"
            else -> "low"
        }
        out += code to severity
    }
    return out
}