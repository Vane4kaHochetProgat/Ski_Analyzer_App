/**
 * Single Activity that hosts the entire Compose app.
 *
 * Responsibilities:
 *   * Requests CAMERA + RECORD_AUDIO runtime permissions on launch and exposes
 *     the result through [cameraAllowedFlag] for the camera composable.
 *   * Owns the four screen-scoped ViewModels (`authViewModel`,
 *     `mistakesViewModel`, `profileViewModel`, `cameraViewModel`) via the
 *     `by viewModels()` delegate. No DI framework is used.
 *   * Switches between [AuthScreen] (when no user is signed in) and the main
 *     four-tab navigation Scaffold otherwise.
 *
 * Navigation uses Jetpack Navigation-Compose with the [Destination] enum as
 * the source of truth for routes, labels, and icons. The bottom bar's
 * selected index is preserved across config changes via `rememberSaveable`.
 */

package com.vane4ka.skianalyzer

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vane4ka.skianalyzer.ui.theme.AppBackground
import com.vane4ka.skianalyzer.ui.theme.CardSurface
import com.vane4ka.skianalyzer.ui.theme.MyApplicationTheme
import com.vane4ka.skianalyzer.ui.theme.PrimaryBlue
import com.vane4ka.skianalyzer.ui.theme.TextMuted

enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    SUBMIT("submit", R.string.nav_submit, Icons.Filled.Upload),
    VIDEOS("videos", R.string.nav_videos, Icons.Filled.Videocam),
    MISTAKES("mistakes", R.string.nav_mistakes, Icons.Filled.ErrorOutline),
    PROFILE("profile", R.string.nav_profile, Icons.Filled.Person)
}


class MainActivity : ComponentActivity() {

    val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            if (PERMISSIONS.all { p -> permissions[p] == true }) {
                cameraAllowedFlag.value = true
            }
        }
    val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val dest = java.io.File(filesDir, "${System.currentTimeMillis()}.mp4")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "failed to import picked video: ${e.message}", e)
            if (dest.exists()) dest.delete()
        }
    }

    val cameraAllowedFlag = mutableStateOf(false)
    val cameraViewModel: PreviewViewModel by viewModels()
    val authViewModel: AuthViewModel by viewModels()
    val mistakesViewModel: MistakesViewModel by viewModels()
    val profileViewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installBackendContext(applicationContext)
        kotlinx.coroutines.runBlocking { UserSession(applicationContext).bootstrap() }
        if (PERMISSIONS.all { p ->
                ContextCompat.checkSelfPermission(
                    this,
                    p
                ) == PackageManager.PERMISSION_GRANTED
            }) {
            cameraAllowedFlag.value = true
        } else {
            permissionLauncher.launch(PERMISSIONS.toTypedArray())
        }
        setContent {
            MyApplicationTheme {
                val currentUser by authViewModel.current.collectAsState()
                if (currentUser == null) {
                    val isSubmitting by authViewModel.isSubmitting.collectAsState()
                    val error by authViewModel.errorMessage.collectAsState()
                    AuthScreen(
                        onSubmit = { mode, username, email, password ->
                            authViewModel.submit(mode, username, email, password)
                        },
                        error = error,
                        isSubmitting = isSubmitting,
                        modifier = Modifier.fillMaxSize()
                    )
                    return@MyApplicationTheme
                }
                val navController = rememberNavController()
                val startDestination = Destination.SUBMIT
                var selectedDestination by rememberSaveable { mutableIntStateOf(startDestination.ordinal) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = AppBackground,
                    bottomBar = {
                        NavigationBar(
                            containerColor = CardSurface,
                            tonalElevation = 0.dp,
                            windowInsets = NavigationBarDefaults.windowInsets
                        ) {
                            Destination.entries.forEachIndexed { index, destination ->
                                val label = stringResource(destination.labelRes)
                                NavigationBarItem(
                                    selected = selectedDestination == index,
                                    onClick = {
                                        navController.navigate(route = destination.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                        selectedDestination = index
                                    },
                                    icon = {
                                        Icon(
                                            destination.icon,
                                            contentDescription = label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = PrimaryBlue,
                                        selectedTextColor = PrimaryBlue,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                ) { contentPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppBackground)
                            .padding(contentPadding)
                    ) {
                        NavHost(
                            navController,
                            startDestination.route,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(Destination.SUBMIT.route) {
                                SubmitScreen(
                                    onPickVideo = { this@MainActivity.picker.launch("video/*") },
                                    onOpenCamera = { navController.navigate("camera") }
                                )
                            }
                            composable("camera") {
                                if (cameraAllowedFlag.value) {
                                    MyCameraViewfinder(
                                        viewModel = cameraViewModel,
                                        onClose = { navController.popBackStack() },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text(text = stringResource(R.string.camera_permission_required))
                                }
                            }
                            composable(Destination.VIDEOS.route) {
                                VideoBrowser(
                                    modifier = Modifier.fillMaxSize(),
                                    onAnalysisSucceeded = { file, result ->
                                        mistakesViewModel.recordAnalysis(file, "skiing", result)
                                    }
                                )
                            }
                            composable(Destination.MISTAKES.route) {
                                MistakesScreen(mistakesViewModel, Modifier.fillMaxSize())
                            }
                            composable(Destination.PROFILE.route) {
                                ProfileScreen(
                                    profileViewModel,
                                    onOpenSettings = { navController.navigate("settings") },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            composable("settings") {
                                SettingsRoute(
                                    onBack = { navController.popBackStack() },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}