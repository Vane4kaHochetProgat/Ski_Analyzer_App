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
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.CardSurface
import com.example.myapplication.ui.theme.SeverityHighBg
import com.example.myapplication.ui.theme.SeverityHighFg
import com.example.myapplication.ui.theme.SeverityLowBg
import com.example.myapplication.ui.theme.SeverityLowFg
import com.example.myapplication.ui.theme.SeverityMedBg
import com.example.myapplication.ui.theme.SeverityMedFg
import com.example.myapplication.ui.theme.TagBg
import com.example.myapplication.ui.theme.TagFg
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary
import com.example.myapplication.ui.theme.UploadTint

private enum class Severity(val label: String, val bg: Color, val fg: Color) {
    HIGH("high", SeverityHighBg, SeverityHighFg),
    MEDIUM("medium", SeverityMedBg, SeverityMedFg),
    LOW("low", SeverityLowBg, SeverityLowFg)
}

private data class Mistake(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val description: String,
    val severity: Severity,
    val sport: String,
    val users: String
)

private val mistakes = listOf(
    Mistake(
        Icons.Filled.Warning,
        Color(0xFFF59E0B),
        "Leaning Back",
        "Weight distribution too far back, reducing control",
        Severity.HIGH,
        "Skiing",
        "78% of users"
    ),
    Mistake(
        Icons.Filled.SwapHoriz,
        Color(0xFF6B7280),
        "Arms Too Wide",
        "Poor balance and reduced turning efficiency",
        Severity.MEDIUM,
        "Snowboarding",
        "62% of users"
    ),
    Mistake(
        Icons.Filled.RemoveRedEye,
        Color(0xFF334155),
        "Looking Down",
        "Eyes focused on skis instead of ahead",
        Severity.HIGH,
        "Both",
        "85% of users"
    ),
    Mistake(
        Icons.AutoMirrored.Filled.DirectionsRun,
        Color(0xFFF59E0B),
        "Stiff Knees",
        "Not absorbing terrain properly",
        Severity.MEDIUM,
        "Skiing",
        "54% of users"
    ),
    Mistake(
        Icons.AutoMirrored.Filled.RotateRight,
        Color(0xFF6B7280),
        "Hip Rotation Issues",
        "Hips not aligning properly during turns",
        Severity.LOW,
        "Both",
        "41% of users"
    )
)

@Composable
fun MistakesScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
    ) {
        Text(
            text = "Common Mistakes",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = "Learn from common technique errors",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.size(14.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(mistakes) { m -> MistakeCard(m) }
        }
    }
}

@Composable
private fun MistakeCard(m: Mistake) {
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
            Icon(m.icon, contentDescription = null, tint = m.iconTint, modifier = Modifier.size(28.dp))
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
                SeverityBadge(m.severity)
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = m.description,
                color = TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SportTag(m.sport)
                Spacer(Modifier.weight(1f))
                Text(
                    text = m.users,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SeverityBadge(severity: Severity) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(severity.bg)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = severity.label,
            color = severity.fg,
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