/**
 * Videos tab — lists locally-stored `.mp4` files and uploads them to the
 * analysis service.
 *
 * File source: `context.filesDir` (everything written by [PreviewViewModel]
 * lands here), sorted by `lastModified` descending. The list refreshes after
 * a successful delete.
 *
 * Selecting a card opens [SelectedVideoPanel] with an ExoPlayer preview and
 * a "send to server" button. That button calls [uploadVideoForAnalysis] on
 * `Dispatchers.IO`; on success it (a) calls back into
 * [MistakesViewModel.recordAnalysis] via `onAnalysisSucceeded`, and (b)
 * shows the parsed [AnalysisResult] inline through [AnalysisResultView].
 *
 * UI state is local to the composable:
 *   * [UploadState] sealed class      — Idle / Uploading / Success / Error.
 *   * `selectedFile` / `fileToDelete` — view-model-free, kept in `remember`.
 *
 * Delete uses a confirmation [AlertDialog]; the player is released in a
 * `DisposableEffect.onDispose`. Relative timestamps for cards are produced
 * by [relativeAge] using `plurals` resources for localization.
 */

package com.example.myapplication

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.material3.Player
import com.example.myapplication.ui.theme.AccentCyan
import com.example.myapplication.ui.theme.CardSurface
import com.example.myapplication.ui.theme.IssueRed
import com.example.myapplication.ui.theme.PrimaryBlue
import com.example.myapplication.ui.theme.ScoreGreen
import com.example.myapplication.ui.theme.UploadTint
import com.example.myapplication.ui.theme.TextMuted
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

sealed class UploadState {
    object Idle : UploadState()
    object Uploading : UploadState()
    data class Success(val result: AnalysisResult, val source: AnalysisSource = AnalysisSource.SERVER) : UploadState()
    object QueuedOffline : UploadState()
    data class Error(val message: String) : UploadState()
}

enum class AnalysisSource { SERVER, LOCAL }

private fun listLocalVideos(context: android.content.Context): List<File> =
    context.filesDir
        .listFiles { f -> f.isFile && f.name.endsWith(".mp4") }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

@Composable
fun VideoBrowser(
    modifier: Modifier = Modifier,
    onAnalysisSucceeded: (File, AnalysisResult) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(listLocalVideos(context)) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var uploadState by remember { mutableStateOf<UploadState>(UploadState.Idle) }
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(selectedFile) {
        uploadState = UploadState.Idle
        val file = selectedFile
        if (file != null) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
            player.play()

            val store = com.example.myapplication.analysis.PendingAnalysisStore(context)
            store.result(file.absolutePath)?.let { stored ->
                uploadState = UploadState.Success(stored, AnalysisSource.SERVER)
                onAnalysisSucceeded(file, stored)
            }
            val workName = com.example.myapplication.analysis.UploadWorker.workName(file)
            androidx.work.WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(workName)
                .collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    when (info.state) {
                        androidx.work.WorkInfo.State.ENQUEUED,
                        androidx.work.WorkInfo.State.BLOCKED,
                        androidx.work.WorkInfo.State.RUNNING -> {
                            if (uploadState !is UploadState.Success) {
                                uploadState = UploadState.QueuedOffline
                            }
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            val json = info.outputData.getString(
                                com.example.myapplication.analysis.UploadWorker.KEY_RESULT_JSON
                            )
                            val parsed = json?.let {
                                runCatching { com.google.gson.Gson().fromJson(it, AnalysisResult::class.java) }.getOrNull()
                            } ?: store.result(file.absolutePath)
                            if (parsed != null) {
                                uploadState = UploadState.Success(parsed, AnalysisSource.SERVER)
                                onAnalysisSucceeded(file, parsed)
                            }
                        }
                        androidx.work.WorkInfo.State.FAILED,
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            if (uploadState !is UploadState.Success) {
                                uploadState = UploadState.Error("Не удалось отправить из очереди")
                            }
                        }
                    }
                }
        } else {
            player.stop()
            player.clearMediaItems()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.videos_title),
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = pluralStringResource(R.plurals.videos_count_analyzed, files.size, files.size),
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))

        val current = selectedFile
        if (current != null) {
            SelectedVideoPanel(
                player = player,
                fileName = current.nameWithoutExtension,
                uploadState = uploadState,
                onClose = { selectedFile = null },
                onUpload = {
                    uploadState = UploadState.Uploading
                    scope.launch {
                        uploadState = try {
                            val orchestrator = com.example.myapplication.analysis.AnalysisOrchestrator(context)
                            when (val outcome = orchestrator.analyze(current)) {
                                is com.example.myapplication.analysis.AnalysisOutcome.Done -> {
                                    onAnalysisSucceeded(current, outcome.result)
                                    val src = when (outcome.source) {
                                        com.example.myapplication.analysis.AnalysisOutcome.Source.SERVER -> AnalysisSource.SERVER
                                        com.example.myapplication.analysis.AnalysisOutcome.Source.LOCAL -> AnalysisSource.LOCAL
                                    }
                                    UploadState.Success(outcome.result, src)
                                }
                                is com.example.myapplication.analysis.AnalysisOutcome.QueuedOffline -> UploadState.QueuedOffline
                                is com.example.myapplication.analysis.AnalysisOutcome.Failed -> UploadState.Error(outcome.message)
                            }
                        } catch (t: Throwable) {
                            UploadState.Error(t.message ?: t.javaClass.simpleName)
                        }
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
        }

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.videos_empty),
                    color = TextMuted,
                    modifier = Modifier.padding(top = 40.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                items(files) { file ->
                    VideoCard(
                        file = file,
                        onPlay = { selectedFile = file },
                        onDelete = { fileToDelete = file }
                    )
                }
            }
        }
    }

    val target = fileToDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(text = stringResource(R.string.videos_delete_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.videos_delete_message,
                        formatVideoTitle(target.nameWithoutExtension)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val deleted = target.delete()
                    if (deleted && selectedFile == target) selectedFile = null
                    files = listLocalVideos(context)
                    fileToDelete = null
                }) {
                    Text(
                        text = stringResource(R.string.videos_delete_confirm),
                        color = IssueRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text(text = stringResource(R.string.videos_cancel), color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SelectedVideoPanel(
    player: ExoPlayer,
    fileName: String,
    uploadState: UploadState,
    onClose: () -> Unit,
    onUpload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fileName,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.videos_close),
                    tint = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Player(
            player = player,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onUpload,
            enabled = uploadState !is UploadState.Uploading,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryBlue,
                contentColor = CardSurface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.videos_send_to_server),
                fontWeight = FontWeight.SemiBold
            )
        }
        UploadStatus(uploadState)
    }
}

@Composable
private fun VideoCard(file: File, onPlay: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(listOf(PrimaryBlue, AccentCyan))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.DownhillSkiing,
                contentDescription = null,
                tint = CardSurface,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatVideoTitle(file.nameWithoutExtension),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AccessTime,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = relativeAge(file.lastModified()),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = ScoreGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.videos_pending),
                    color = ScoreGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.size(10.dp))
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = IssueRed,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "—",
                    color = IssueRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.videos_delete_cd),
                tint = IssueRed,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.size(4.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(PrimaryBlue)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.videos_play_cd),
                tint = CardSurface,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun formatVideoTitle(raw: String): String =
    raw.toLongOrNull()?.let { stringResource(R.string.videos_recording_prefix, raw.takeLast(5)) } ?: raw

@Composable
private fun relativeAge(epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    if (diff < 0) return stringResource(R.string.videos_just_now)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        days >= 7 -> pluralStringResource(R.plurals.week_ago, (days / 7).toInt(), (days / 7).toInt())
        days >= 1 -> pluralStringResource(R.plurals.days_ago, days.toInt(), days.toInt())
        hours >= 1 -> pluralStringResource(R.plurals.hours_ago, hours.toInt(), hours.toInt())
        minutes >= 1 -> pluralStringResource(R.plurals.minutes_ago, minutes.toInt(), minutes.toInt())
        else -> stringResource(R.string.videos_just_now)
    }
}

@Composable
private fun UploadStatus(state: UploadState) {
    when (state) {
        UploadState.Idle -> {}
        UploadState.Uploading -> Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = PrimaryBlue)
            Spacer(Modifier.size(8.dp))
            Text(text = stringResource(R.string.videos_uploading), color = TextSecondary, fontSize = 13.sp)
        }
        is UploadState.Error -> Text(
            text = stringResource(R.string.videos_upload_failed, state.message),
            color = IssueRed,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
        UploadState.QueuedOffline -> Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = PrimaryBlue)
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Ждём интернет — отправим автоматически",
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
        is UploadState.Success -> Column {
            if (state.source == AnalysisSource.LOCAL) {
                Text(
                    text = "Анализ выполнен локально (без интернета)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            AnalysisResultView(state.result)
        }
    }
}

@Composable
private fun AnalysisResultView(result: AnalysisResult) {
    val recs = result.analysis.recommendations.orEmpty().filter { it.isNotBlank() }
    val criticalCount = result.analysis.angle_analysis.orEmpty().count { it.is_critical }

    Column(modifier = Modifier.padding(top = 12.dp)) {
        val (verdictText, verdictColor) = when {
            recs.isEmpty() && criticalCount == 0 ->
                "Техника близка к эталонной" to ScoreGreen
            criticalCount > 0 ->
                "Есть критичные моменты" to IssueRed
            else ->
                "Есть над чем поработать" to TextPrimary
        }
        Text(
            text = verdictText,
            color = verdictColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))

        if (recs.isEmpty()) {
            Text(
                text = "Продолжайте в том же духе.",
                color = TextSecondary,
                fontSize = 13.sp,
            )
        } else {
            Text(
                text = "Рекомендации",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            recs.forEach { rec ->
                RecommendationCard(rec)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun RecommendationCard(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(UploadTint)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = 10.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(PrimaryBlue),
        )
        Text(text = text, color = TextPrimary, fontSize = 13.sp)
    }
}