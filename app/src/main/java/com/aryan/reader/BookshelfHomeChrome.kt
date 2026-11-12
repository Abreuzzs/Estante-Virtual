package com.aryan.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Shared, observable copy of the bookshelf look so the screen chrome (top bar, status bar, scaffold
 * background) repaints the moment the wall is changed in the customize sheet.
 */
internal object BookshelfAppearance {
    var settings by mutableStateOf<BookshelfSettings?>(null)
}

/** Actions opened from the top bar's menu but handled by the shelf itself. */
internal object BookshelfUiState {
    val organizing = mutableStateOf(false)
    val customizeRequested = mutableStateOf(false)
}

/** Keeps Material controls (chips, buttons, sheets) legible on the shelf wall whatever the app theme. */
@Composable
internal fun bookshelfWallColorScheme(wall: Color): androidx.compose.material3.ColorScheme {
    val base = MaterialTheme.colorScheme
    val ink = bookshelfInkFor(wall)
    return if (wall.luminance() > 0.5f) {
        base.copy(
            onSurface = ink,
            onSurfaceVariant = ink.copy(alpha = 0.75f),
            outline = ink.copy(alpha = 0.45f),
            outlineVariant = ink.copy(alpha = 0.30f),
            secondaryContainer = Color(0xFFD8CCB4),
            onSecondaryContainer = ink,
            surfaceVariant = Color(0xFFDFD4BD),
            surfaceContainerLow = wall,
            surface = wall,
            background = wall,
        )
    } else {
        base.copy(
            onSurface = ink,
            onSurfaceVariant = ink.copy(alpha = 0.75f),
            surface = wall,
            background = wall,
        )
    }
}

@Composable
internal fun rememberBookshelfSettings(): BookshelfSettings {
    val context = LocalContext.current
    val stored = remember(context) { BookshelfPreferences(context).loadSettings() }
    return BookshelfAppearance.settings ?: stored
}

internal fun bookshelfWallColor(wall: BookshelfWall, systemDark: Boolean): Color = when (wall) {
    BookshelfWall.CREAM -> Color(0xFFEDE5D5)
    BookshelfWall.SAND -> Color(0xFFDDCDAE)
    BookshelfWall.SLATE -> Color(0xFF262A31)
    BookshelfWall.AUTO -> if (systemDark) Color(0xFF262A31) else Color(0xFFEDE5D5)
}

internal fun bookshelfInkFor(wallColor: Color): Color =
    if (wallColor.luminance() > 0.5f) Color(0xFF3B3125) else Color(0xFFEFE6D3)

/** Dark status-bar icons while a light wall is shown; the previous style is restored on exit. */
@Composable
internal fun ShelfStatusBarEffect(lightBackground: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, lightBackground) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = lightBackground
        onDispose {
            if (controller != null && previous != null) {
                controller.isAppearanceLightStatusBars = previous
            }
        }
    }
}

@Composable
private fun RoundChromeButton(
    description: String,
    ink: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Minimal top bar for the bookshelf home: menu, title, search and profile only. Search opens as an
 * inline field (and closes back to the title) so the screen stays clean until it is needed.
 */
@Composable
internal fun ShelfHomeTopBar(
    title: String,
    ink: Color,
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onMenu: () -> Unit,
    onAccount: () -> Unit,
    avatar: @Composable () -> Unit,
    organizing: Boolean,
    onToggleOrganize: () -> Unit,
    onCustomize: () -> Unit,
    organizeLabel: String,
    customizeLabel: String,
    moreDescription: String,
    menuDescription: String,
    searchDescription: String,
    closeDescription: String,
    accountDescription: String,
    searchPlaceholder: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (searchOpen) {
            RoundChromeButton(closeDescription, ink, onCloseSearch) {
                Icon(Icons.Default.Close, contentDescription = null, tint = ink)
            }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = ink, fontSize = 16.sp),
                cursorBrush = SolidColor(ink),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ink.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp)
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                        if (query.isEmpty()) {
                            Text(searchPlaceholder, color = ink.copy(alpha = 0.5f), fontSize = 16.sp, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
        } else {
            RoundChromeButton(menuDescription, ink, onMenu) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = ink)
            }
            Text(
                text = title,
                color = ink,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RoundChromeButton(searchDescription, ink, onOpenSearch) {
                Icon(Icons.Default.Search, contentDescription = null, tint = ink)
            }
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                RoundChromeButton(moreDescription, ink, { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = ink)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(organizeLabel) },
                        onClick = {
                            menuOpen = false
                            onToggleOrganize()
                        },
                        leadingIcon = {
                            Icon(if (organizing) Icons.Default.Check else Icons.Default.SwapHoriz, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(customizeLabel) },
                        onClick = {
                            menuOpen = false
                            onCustomize()
                        },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    )
                }
            }
            RoundChromeButton(accountDescription, ink, onAccount) { avatar() }
        }
    }
}
