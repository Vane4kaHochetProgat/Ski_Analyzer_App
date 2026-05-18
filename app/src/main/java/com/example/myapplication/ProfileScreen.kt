package com.example.myapplication

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.AccentCyan
import com.example.myapplication.ui.theme.AchievementBlue
import com.example.myapplication.ui.theme.AchievementGold
import com.example.myapplication.ui.theme.AchievementMuted
import com.example.myapplication.ui.theme.AchievementPurple
import com.example.myapplication.ui.theme.CardSurface
import com.example.myapplication.ui.theme.DividerLight
import com.example.myapplication.ui.theme.PrimaryBlue
import com.example.myapplication.ui.theme.ProgressGreen
import com.example.myapplication.ui.theme.ProgressPink
import com.example.myapplication.ui.theme.ScoreGreen
import com.example.myapplication.ui.theme.TextMuted
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp)
    ) {
        Text(
            text = "Profile",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
        ProfileHeader()
        Spacer(Modifier.height(14.dp))
        AchievementsCard()
        Spacer(Modifier.height(14.dp))
        ProgressCard()
    }
}

@Composable
private fun ProfileHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(listOf(PrimaryBlue, AccentCyan))
            )
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.DownhillSkiing,
                    contentDescription = null,
                    tint = CardSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(
                    text = "Alex Johnson",
                    color = CardSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Advanced Skier",
                    color = Color(0xCCFFFFFF),
                    fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x33FFFFFF))
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatColumn(value = "24", label = "Videos")
            StatColumn(value = "87", label = "Avg Score")
            StatColumn(value = "12", label = "Days Active")
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = CardSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color(0xCCFFFFFF),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun AchievementsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = AchievementGold,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Achievements",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AchievementTile(
                icon = Icons.Filled.EmojiEvents,
                bg = AchievementGold,
                label = "First Video",
                locked = false
            )
            AchievementTile(
                icon = Icons.Filled.Star,
                bg = AchievementBlue,
                label = "10 Videos",
                locked = false
            )
            AchievementTile(
                icon = Icons.Filled.Diamond,
                bg = AchievementPurple,
                label = "Perfect Run",
                locked = false
            )
            AchievementTile(
                icon = Icons.Filled.Lock,
                bg = AchievementMuted,
                label = "Locked",
                locked = true
            )
        }
    }
}

@Composable
private fun AchievementTile(
    icon: ImageVector,
    bg: Color,
    label: String,
    locked: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (locked) DividerLight else bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (locked) TextMuted else CardSurface,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = if (locked) TextMuted else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProgressCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = ScoreGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Progress",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(14.dp))
        ProgressRow("Technique Score", 0.87f, "87%", PrimaryBlue)
        Spacer(Modifier.height(12.dp))
        ProgressRow("Consistency", 0.72f, "72%", ProgressGreen)
        Spacer(Modifier.height(12.dp))
        ProgressRow("Form Accuracy", 0.94f, "94%", ProgressPink)
    }
}

@Composable
private fun ProgressRow(
    label: String,
    progress: Float,
    valueText: String,
    barColor: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = valueText,
                color = barColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = barColor,
            trackColor = DividerLight,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp)),
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
    }
}