package com.example.myapplication

import android.app.Application
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.ui.theme.CardSurface
import com.example.myapplication.ui.theme.DividerLight
import com.example.myapplication.ui.theme.PrimaryBlue
import com.example.myapplication.ui.theme.TextMuted
import com.example.myapplication.ui.theme.TextPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val session = UserSession(app)

    private val _offlineMode = MutableStateFlow(OfflineMode.RUN_LOCALLY)
    val offlineMode: StateFlow<OfflineMode> = _offlineMode.asStateFlow()

    init {
        viewModelScope.launch {
            session.offlineMode.collect { _offlineMode.value = it }
        }
    }

    fun setOfflineMode(mode: OfflineMode) {
        viewModelScope.launch { session.setOfflineMode(mode) }
    }

    fun signOut() {
        viewModelScope.launch { session.clear() }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode by viewModel.offlineMode.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextPrimary)
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = "Настройки",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(20.dp))

        Text(
            text = "При отсутствии интернета",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Что делать, когда вы отправляете видео на анализ без сети",
            color = TextMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        OfflineModeOption(
            title = "Обработать локально",
            subtitle = "Запустить YOLOv8 на устройстве. Быстрее, но точность ниже серверной.",
            selected = mode == OfflineMode.RUN_LOCALLY,
            onClick = { viewModel.setOfflineMode(OfflineMode.RUN_LOCALLY) },
        )
        Spacer(Modifier.height(10.dp))
        OfflineModeOption(
            title = "Подождать интернет",
            subtitle = "Видео встанет в очередь и отправится на сервер, как только появится сеть.",
            selected = mode == OfflineMode.WAIT_FOR_INTERNET,
            onClick = { viewModel.setOfflineMode(OfflineMode.WAIT_FOR_INTERNET) },
        )

        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardSurface)
                .clickable {
                    viewModel.signOut()
                    onBack()
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Выйти из аккаунта",
                color = androidx.compose.ui.graphics.Color(0xFFE53935),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun OfflineModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) PrimaryBlue else DividerLight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioDot(selected = selected, ring = borderColor)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean, ring: Color) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(ring),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(CardSurface),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue),
                )
            }
        }
    }
}

@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    SettingsScreen(vm, onBack, modifier)
}
