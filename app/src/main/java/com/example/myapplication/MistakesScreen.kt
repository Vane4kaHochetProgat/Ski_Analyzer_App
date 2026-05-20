package com.example.myapplication

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.CardSurface
import com.example.myapplication.ui.theme.IssueRed
import com.example.myapplication.ui.theme.PrimaryBlue
import com.example.myapplication.ui.theme.SeverityHighBg
import com.example.myapplication.ui.theme.SeverityHighFg
import com.example.myapplication.ui.theme.SeverityLowBg
import com.example.myapplication.ui.theme.SeverityLowFg
import com.example.myapplication.ui.theme.SeverityMedBg
import com.example.myapplication.ui.theme.SeverityMedFg
import com.example.myapplication.ui.theme.TagBg
import com.example.myapplication.ui.theme.TagFg
import com.example.myapplication.ui.theme.TextMuted
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.ui.theme.UploadTint

private data class SeverityStyle(val label: String, val bg: Color, val fg: Color)

private fun severityStyle(code: String): SeverityStyle = when (code.lowercase()) {
    "high"   -> SeverityStyle("high",   SeverityHighBg, SeverityHighFg)
    "medium" -> SeverityStyle("medium", SeverityMedBg,  SeverityMedFg)
    else     -> SeverityStyle("low",    SeverityLowBg,  SeverityLowFg)
}

private fun sportLabel(code: String): String = when (code.lowercase()) {
    "skiing"       -> "Skiing"
    "snowboarding" -> "Snowboarding"
    "both"         -> "Both"
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
    ) {
        Text(
            text = "Your Mistakes",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = "Technique errors detected in your videos",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.size(14.dp))

        when (val s = state) {
            MistakesUiState.Loading -> LoadingView()
            is MistakesUiState.Error -> ErrorView(s.message)
            is MistakesUiState.Loaded ->
                if (s.mistakes.isEmpty()) EmptyView()
                else MistakesList(s.mistakes)
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
        Text(text = "Couldn't load mistakes: $message", color = IssueRed, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyView() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "No mistakes recorded yet — analyze a video to see your results here.",
            color = TextMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun MistakesList(items: List<UserMistakeDetailDto>) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(items, key = { it.user_mistake_id }) { m -> MistakeCard(m) }
    }
}

@Composable
private fun MistakeCard(m: UserMistakeDetailDto) {
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
                iconFor(m.icon_code),
                contentDescription = null,
                tint = parseTintHex(m.icon_tint_hex),
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = m.title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                SeverityBadge(severityStyle(m.severity))
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = m.description,
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SportTag(sportLabel(m.sport))
                Spacer(Modifier.weight(1f))
                Text(
                    text = relativeDetectedAt(m.detected_at),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
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
            text = style.label,
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

private fun relativeDetectedAt(iso: String): String {
    val date = iso.substringBefore('T')
    return if (date.isNotEmpty() && date != iso) date else iso
}