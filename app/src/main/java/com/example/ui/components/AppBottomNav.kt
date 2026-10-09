package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentGold
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.Screen

@Composable
fun AppBottomNav(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .background(DarkSurface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("bottom_nav_bar"),
        containerColor = DarkSurface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentScreen == Screen.HOME,
            onClick = { onNavigate(Screen.HOME) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == Screen.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Início"
                )
            },
            label = { Text("Início") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentGold,
                selectedTextColor = AccentGold,
                indicatorColor = DarkSurfaceBorder,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("nav_tab_home")
        )

        NavigationBarItem(
            selected = currentScreen == Screen.NEW_PROJECT,
            onClick = { onNavigate(Screen.NEW_PROJECT) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == Screen.NEW_PROJECT) Icons.Filled.AddCircle else Icons.Outlined.AddCircleOutline,
                    contentDescription = "Novo Projeto"
                )
            },
            label = { Text("Novo") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentGold,
                selectedTextColor = AccentGold,
                indicatorColor = DarkSurfaceBorder,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("nav_tab_new")
        )

        NavigationBarItem(
            selected = currentScreen == Screen.MY_PRAISES || currentScreen == Screen.RESULT_STUDIO,
            onClick = { onNavigate(Screen.MY_PRAISES) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == Screen.MY_PRAISES) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic,
                    contentDescription = "Meus Louvores"
                )
            },
            label = { Text("Louvores") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentGold,
                selectedTextColor = AccentGold,
                indicatorColor = DarkSurfaceBorder,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("nav_tab_praises")
        )

        NavigationBarItem(
            selected = currentScreen == Screen.SETTINGS,
            onClick = { onNavigate(Screen.SETTINGS) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == Screen.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                    contentDescription = "Configurações"
                )
            },
            label = { Text("Ajustes") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentGold,
                selectedTextColor = AccentGold,
                indicatorColor = DarkSurfaceBorder,
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("nav_tab_settings")
        )
    }
}
