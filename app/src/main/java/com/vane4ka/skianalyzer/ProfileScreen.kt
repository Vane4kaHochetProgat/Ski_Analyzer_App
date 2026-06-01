/**
 * Profile tab — renders [ProfileUiState] from [ProfileViewModel].
 *
 * Split into two Composables:
 *   * [ProfileScreen]        — wires the ViewModel: triggers `refresh()` on
 *                              first composition and `collectAsState()` for
 *                              re-renders.
 *   * [ProfileScreenContent] — stateless renderer; takes a [ProfileUiState]
 *                              directly so it can be previewed and unit-tested
 *                              without a ViewModel.
 *
 * Visual: a gradient (PrimaryBlue → AccentCyan) card with an avatar
 * placeholder, username, email, and a single "videos analyzed" stat. The
 * stat shows "—" while [ProfileUiState.videosCount] is null (loading or
 * failed fetch — see [ProfileViewModel.refresh]).
 */

package com.vane4ka.skianalyzer

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vane4ka.skianalyzer.ui.theme.AccentCyan
import com.vane4ka.skianalyzer.ui.theme.CardSurface
import com.vane4ka.skianalyzer.ui.theme.PrimaryBlue
import com.vane4ka.skianalyzer.ui.theme.TextPrimary

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    ProfileScreenContent(state, onOpenSettings, modifier)
}

@Composable
fun ProfileScreenContent(
    state: ProfileUiState,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.profile_title),
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Настройки",
                    tint = TextPrimary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        ProfileHeader(state)
        Spacer(Modifier.height(20.dp))
        ProgressSection(state)
    }
}

@Composable
private fun ProgressSection(state: ProfileUiState) {
    Text(
        text = "Прогресс",
        color = TextPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            title = "Видео за неделю",
            value = state.videosThisWeek?.toString() ?: "—",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            title = "Ошибок всего",
            value = state.mistakesCount?.toString() ?: "—",
            modifier = Modifier.weight(1f),
        )
    }
    if (!state.topMistakeTitle.isNullOrBlank()) {
        Spacer(Modifier.height(12.dp))
        TopMistakeCard(state.topMistakeTitle)
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .padding(14.dp),
    ) {
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = title,
            color = com.vane4ka.skianalyzer.ui.theme.TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TopMistakeCard(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .padding(14.dp),
    ) {
        Text(
            text = "Самая частая ошибка",
            color = com.vane4ka.skianalyzer.ui.theme.TextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProfileHeader(state: ProfileUiState) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.username.ifBlank { "—" },
                    color = CardSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (state.email.isNotBlank()) {
                    Text(
                        text = state.email,
                        color = Color(0xCCFFFFFF),
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
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
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StatColumn(
                value = state.videosCount?.toString() ?: "—",
                label = stringResource(R.string.profile_stat_videos)
            )
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