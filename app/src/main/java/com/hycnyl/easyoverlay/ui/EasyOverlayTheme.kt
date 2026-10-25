package com.hycnyl.easyoverlay.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.hycnyl.easyoverlay.data.ThemeChoice

@Composable fun EasyOverlayTheme(choice:ThemeChoice,content:@Composable ()->Unit) {
    val dark=when(choice) { ThemeChoice.SYSTEM->isSystemInDarkTheme();ThemeChoice.LIGHT->false;ThemeChoice.DARK->true }
    val colors=if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    MaterialTheme(colorScheme=colors,typography=Typography(),content=content)
}
