/**
 * Mistakes tab — renders the user's recorded mistakes from [MistakesViewModel].
 *
 * Public composables:
 *   * [MistakesScreen]         — wires the ViewModel: triggers `refresh()`
 *                                on first composition.
 *   * [MistakesScreenContent]  — stateless renderer that switches on
 *                                [MistakesUiState] (Loading → spinner,
 *                                NotSignedIn / Error → red message, empty
 *                                Loaded → placeholder, populated Loaded →
 *                                [MistakesList]).
 *
 * Helpers (file-private):
 *   * [severityStyle]   — maps "high"/"medium"/"low" to a [SeverityStyle]
 *                         (label + bg/fg color from the palette).
 *   * [sportLabel]      — server sport code → localized label.
 *   * [iconFor]         — server icon code (e.g. "warning", "rotate_right")
 *                         → Material [ImageVector]; unknown codes fall back
 *                         to [Icons.Filled.ErrorOutline].
 *   * [parseTintHex]    — "#RRGGBB" string → [Color] (defaults to neutral
 *                         gray on missing/invalid input).
 *   * [relativeDetectedAt] — strips the time portion off an ISO timestamp.
 *
 * Each [MistakeCard] shows the icon (tinted by the server-supplied hex),
 * title, description, a colored severity badge, a sport tag, and the
 * detection date.
 */

package com.vane4ka.skianalyzer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vane4ka.skianalyzer.ui.theme.CardSurface
import com.vane4ka.skianalyzer.ui.theme.IssueRed
import com.vane4ka.skianalyzer.ui.theme.PrimaryBlue
import com.vane4ka.skianalyzer.ui.theme.SeverityHighBg
import com.vane4ka.skianalyzer.ui.theme.SeverityHighFg
import com.vane4ka.skianalyzer.ui.theme.SeverityLowBg
import com.vane4ka.skianalyzer.ui.theme.SeverityLowFg
import com.vane4ka.skianalyzer.ui.theme.SeverityMedBg
import com.vane4ka.skianalyzer.ui.theme.SeverityMedFg
import com.vane4ka.skianalyzer.ui.theme.TagBg
import com.vane4ka.skianalyzer.ui.theme.TagFg
import com.vane4ka.skianalyzer.ui.theme.TextMuted
import com.vane4ka.skianalyzer.ui.theme.TextPrimary
import com.vane4ka.skianalyzer.ui.theme.TextSecondary
import com.vane4ka.skianalyzer.ui.theme.UploadTint

private data class SeverityStyle(val labelRes: Int, val bg: Color, val fg: Color)

private fun severityStyle(code: String): SeverityStyle = when (code.lowercase()) {
    "high"   -> SeverityStyle(R.string.severity_high,   SeverityHighBg, SeverityHighFg)
    "medium" -> SeverityStyle(R.string.severity_medium, SeverityMedBg,  SeverityMedFg)
    else     -> SeverityStyle(R.string.severity_low,    SeverityLowBg,  SeverityLowFg)
}

@Composable
private fun sportLabel(code: String): String = when (code.lowercase()) {
    "skiing"       -> stringResource(R.string.sport_skiing)
    "snowboarding" -> stringResource(R.string.sport_snowboarding)
    "both"         -> stringResource(R.string.sport_both)
    else           -> code.replaceFirstChar { it.uppercase() }
}

private fun iconFor(code: String?): ImageVector = when (code) {
    "warning"        -> Icons.Filled.Warning
    "swap_horiz"     -> Icons.Filled.SwapHoriz
    "eye"            -> Icons.Filled.RemoveRedEye
    "directions_run" -> Icons.AutoMirrored.Filled.DirectionsRun
    "rotate_right"   -> Icons.AutoMirrored.Filled.RotateRight
    else             -> Icons.Filled.ErrorOutline
}

private fun parseTintHex(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color(0xFF6B7280)
    val clean = hex.removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return Color(0xFF6B7280)
    return Color(0xFF000000 or value)
}

@Composable
fun MistakesScreen(
    viewModel: MistakesViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    MistakesScreenContent(state, modifier)
}

@Composable
fun MistakesScreenContent(state: MistakesUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.mistakes_title),
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = stringResource(R.string.mistakes_subtitle),
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.size(14.dp))

        when (state) {
            MistakesUiState.Loading -> LoadingView()
            MistakesUiState.NotSignedIn -> ErrorView(stringResource(R.string.mistakes_not_signed_in))
            is MistakesUiState.Error -> ErrorView(state.message)
            is MistakesUiState.Loaded ->
                if (state.mistakes.isEmpty()) EmptyView()
                else MistakesList(state.mistakes)
        }
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.mistakes_error_loading, message),
            color = IssueRed,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EmptyView() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.mistakes_empty),
            color = TextMuted,
            fontSize = 13.sp
        )
    }
}

private data class AggregatedMistake(
    val code: String,
    val count: Int,
    val sample: UserMistakeDetailDto,
    val highestSeverity: String,
    val lastDetected: String,
)

private fun aggregate(items: List<UserMistakeDetailDto>): List<AggregatedMistake> {
    val severityRank = mapOf("low" to 0, "medium" to 1, "high" to 2)
    return items.groupBy { it.mistake_code }
        .map { (code, group) ->
            val worst = group.maxByOrNull { severityRank[it.severity.lowercase()] ?: 0 }!!
            val latest = group.maxByOrNull { it.detected_at }!!.detected_at
            AggregatedMistake(
                code = code,
                count = group.size,
                sample = group.first(),
                highestSeverity = worst.severity,
                lastDetected = latest.substringBefore('T'),
            )
        }
        .sortedByDescending { it.count }
}

@Composable
private fun MistakesList(items: List<UserMistakeDetailDto>) {
    val aggregated = remember(items) { aggregate(items) }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(aggregated, key = { it.code }) { m -> AggregatedMistakeCard(m) }
    }
}

@Composable
private fun AggregatedMistakeCard(m: AggregatedMistake) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(UploadTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                iconFor(m.sample.icon_code),
                contentDescription = null,
                tint = parseTintHex(m.sample.icon_tint_hex),
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = m.sample.title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                FrequencyBadge(m.count)
                Spacer(Modifier.size(6.dp))
                SeverityBadge(severityStyle(m.highestSeverity))
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = m.sample.description,
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SportTag(sportLabel(m.sample.sport))
                Spacer(Modifier.weight(1f))
                Text(
                    text = m.lastDetected,
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun FrequencyBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PrimaryBlue)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = "×$count",
            color = CardSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SeverityBadge(style: SeverityStyle) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(style.bg)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = stringResource(style.labelRes),
            color = style.fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SportTag(sport: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TagBg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = sport,
            color = TagFg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

