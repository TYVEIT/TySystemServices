package com.android.ty.systemservices.compose

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.ty.systemservices.compose.theme.InstallerTheme
import com.android.ty.systemservices.compose.theme.PaletteStyle
import com.android.ty.systemservices.compose.theme.PresetColors
import com.android.ty.systemservices.compose.theme.ThemeColorSpec
import com.android.ty.systemservices.compose.theme.ThemeMode
import com.android.ty.systemservices.compose.ui.HomePage
import com.android.ty.systemservices.compose.ui.HomeState
import com.android.ty.systemservices.compose.ui.InstallerDemoPage
import com.android.ty.systemservices.compose.ui.PreferencePage
import com.android.ty.systemservices.compose.ui.ThemeSettingsPage
import com.android.ty.systemservices.compose.ui.ThemeSettingsState

@OptIn(ExperimentalMaterial3Api::class)
class CoActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            var themeState by remember {
                mutableStateOf(
                    ThemeSettingsState(
                        themeMode = ThemeMode.SYSTEM,
                        paletteStyle = PaletteStyle.Expressive,
                        colorSpec = ThemeColorSpec.SPEC_2025,
                        useDynamicColor = false,
                        seedColor = PresetColors.list.first().first,
                    )
                )
            }
            var homeState by remember { mutableStateOf(HomeState()) }

            // 简单的多页导航
            var route by remember { mutableStateOf(Route.HOME) }

            InstallerTheme(
                themeMode = themeState.themeMode,
                paletteStyle = themeState.paletteStyle,
                colorSpec = themeState.colorSpec,
                useDynamicColor = themeState.useDynamicColor,
                seedColor = themeState.seedColor,
                backgroundColor = themeState.backgroundColor,
                useDynamicBackgroundColor = themeState.useDynamicBackgroundColor,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(InstallerTheme.backgroundColor),
                ) {
                when (route) {
                    Route.HOME -> HomePage(
                        state = homeState,
                        onStateChange = { homeState = it },
                        onOpenTheme = { route = Route.THEME },
                        onOpenInstaller = { route = Route.INSTALLER },
                        onOpenPreference = { route = Route.PREFERENCE },
                    )
                    Route.THEME -> ThemeSettingsPage(
                        state = themeState,
                        onStateChange = { themeState = it },
                        onBack = { route = Route.HOME },
                    )
                    Route.INSTALLER -> InstallerDemoPage(
                        onBack = { route = Route.HOME },
                    )
                    Route.PREFERENCE -> Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Preference 设置（Compose 嵌入）") },
                                navigationIcon = {
                                    IconButton(onClick = { route = Route.HOME }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                    }
                                },
                            )
                        },
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
                            PreferencePage(
                                fragmentManager = supportFragmentManager,
                                onBack = { route = Route.HOME },
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

private enum class Route { HOME, THEME, INSTALLER, PREFERENCE }

