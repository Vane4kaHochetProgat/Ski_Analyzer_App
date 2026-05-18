package com.example.myapplication

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.theme.AppBackground
import com.example.myapplication.ui.theme.CardSurface
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.PrimaryBlue
import com.example.myapplication.ui.theme.TextMuted
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

const val BASE_URL = "https://jsonplaceholder.typicode.com/"

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    SUBMIT("submit", "Submit", Icons.Filled.Upload),
    VIDEOS("videos", "Videos", Icons.Filled.Videocam),
    MISTAKES("mistakes", "Mistakes", Icons.Filled.ErrorOutline),
    PROFILE("profile", "Profile", Icons.Filled.Person)
}

val typicode = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .addConverterFactory(ScalarsConverterFactory.create())
    .build()
    .create(TypicodeAPI::class.java)


class MainActivity : ComponentActivity() {

    val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            if (PERMISSIONS.all { p -> permissions[p] == true }) {
                cameraAllowedFlag.value = true
            }
        }
    val picker = registerForActivityResult(ActivityResultContracts.GetContent())
    { uri ->
        uri ?: return@registerForActivityResult
        viewModel.uriState.value = uri
    }

    val cameraAllowedFlag = mutableStateOf(false)
    val viewModel: MyAppViewModel by viewModels<MyAppViewModel>()
    val cameraViewModel: PreviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                            contentDescription = destination.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = destination.label,
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
                                    MyCameraViewfinder(cameraViewModel, Modifier.fillMaxSize())
                                } else {
                                    Text(text = "Camera needs your permission to open")
                                }
                            }
                            composable(Destination.VIDEOS.route) {
                                VideoBrowser(Modifier.fillMaxSize())
                            }
                            composable(Destination.MISTAKES.route) {
                                MistakesScreen(Modifier.fillMaxSize())
                            }
                            composable(Destination.PROFILE.route) {
                                ProfileScreen(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}