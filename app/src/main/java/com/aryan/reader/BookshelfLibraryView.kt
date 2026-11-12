package com.aryan.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aryan.reader.data.RecentFileItem
import kotlin.math.abs

private val ShelfSidePadding = 14.dp
private val SlotGap = 1.dp
private val PlankTopDepth = 4.dp
private val PlankFrontHeight = 7.dp
private val PlankShadowHeight = 14.dp
private val BracketHeight = 9.dp
private val MaxSideDepth = 4.dp

private data class ShelfPalette(
    val wall: Color,
    val ink: Color,
    val plankFar: Color,
    val plankNear: Color,
    val plankFrontTop: Color,
    val plankFrontBottom: Color,
)

private fun shelfPalette(wall: BookshelfWall, finish: BookshelfFinish, systemDark: Boolean): ShelfPalette {
    val wallColor = bookshelfWallColor(wall, systemDark)
    val ink = bookshelfInkFor(wallColor)
    fun make(far: Long, near: Long, frontTop: Long, frontBottom: Long) =
        ShelfPalette(wallColor, ink, Color(far), Color(near), Color(frontTop), Color(frontBottom))
    return when (finish) {
        BookshelfFinish.WALNUT -> make(0xFF5A4030, 0xFF46311F, 0xFF3D2A1C, 0xFF2A1B10)
        BookshelfFinish.OAK -> make(0xFFD7B48C, 0xFFBE9768, 0xFFA97F52, 0xFF8C6740)
        BookshelfFinish.EBONY -> make(0xFF4A4440, 0xFF332E2B, 0xFF2A2522, 0xFF171412)
        BookshelfFinish.WHITE -> make(0xFFFAF7F2, 0xFFE9E3D9, 0xFFE0DACF, 0xFFC9C2B5)
    }
}

private val SpineTints = listOf(
    Color(0xFF7B2D3A), // burgundy
    Color(0xFF2F5D50), // forest
    Color(0xFF28405F), // navy
    Color(0xFFB6842C), // ochre
    Color(0xFFE6DCC4), // cream
    Color(0xFF34373D), // charcoal
    Color(0xFF2E7B83), // teal
    Color(0xFFB4573A), // terracotta
    Color(0xFF5B3A6B), // plum
    Color(0xFF6B7433), // olive
    Color(0xFF58687A), // slate blue
    Color(0xFFCDB68C), // sand
)

private fun spineColor(tintKey: String, bookId: String): Color {
    val base = SpineTints[(tintKey.hashCode() and 0x7fffffff) % SpineTints.size]
    // Small per-book brightness jitter keeps a series coherent without looking cloned.
    val jitter = ((bookId.hashCode() and 0x7fffffff) % 7 - 3) * 0.012f
    return Color(
        red = (base.red + jitter).coerceIn(0f, 1f),
        green = (base.green + jitter).coerceIn(0f, 1f),
        blue = (base.blue + jitter).coerceIn(0f, 1f),
    )
}

private data class PackedGroup(val group: BookshelfGroup, val rows: List<List<ShelfSlot>>)

/**
 * 3D-looking bookshelf over a list of books: floating planks on a wall, packed spines and covers for
 * the books being read.
 *
 * It is a pure presentation of [books] (already filtered and sorted by the host), so books are never
 * duplicated: every action is delegated to the host's existing handlers. Tap opens the book,
 * long-press keeps the library's selection model (add to shelf, tags, delete, ...). The "organize"
 * mode lets people move books freely; only the id order is persisted.
 *
 * The shelves are a lazily composed column of rows, so the page height simply follows the number of
 * books and only the visible rows draw covers, which keeps RAM and battery use low.
 *
 * @param title optional heading shown above the shelves.
 * @param controls optional host controls (filters, sort...) shown right below the shelves.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun BookshelfLibraryView(
    books: List<RecentFileItem>,
    selectedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
    onBookClick: (RecentFileItem) -> Unit,
    onBookLongClick: (RecentFileItem) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    controls: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val store = remember(context) { BookshelfPreferences(context) }
    var settings by remember { mutableStateOf(BookshelfAppearance.settings ?: store.loadSettings()) }
    var order by remember { mutableStateOf(store.loadOrder()) }
    // Organize/customize are opened from the screen's top-bar menu, so their state is shared.
    var organizing by BookshelfUiState.organizing
    var showCustomize by BookshelfUiState.customizeRequested
    var pickedId by rememberSaveable { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            BookshelfUiState.organizing.value = false
            BookshelfUiState.customizeRequested.value = false
        }
    }

    val systemDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = remember(settings.wall, settings.finish, systemDark) {
        shelfPalette(settings.wall, settings.finish, systemDark)
    }
    val groups = remember(books, settings.grouping, order) {
        buildBookshelfGroups(books, settings.grouping, order.takeIf { it.isNotEmpty() })
    }
    val visibleIds = remember(groups) { groups.flatMap { g -> g.books.map { it.bookId } } }

    fun updateSettings(transform: (BookshelfSettings) -> BookshelfSettings) {
        val updated = transform(settings)
        settings = updated
        store.saveSettings(updated)
        BookshelfAppearance.settings = updated
    }

    fun placePicked(beforeId: String?) {
        val moving = pickedId ?: return
        order = moveInOrder(order, visibleIds, moving, beforeId).also(store::saveOrder)
        pickedId = null
    }

    MaterialTheme(
        colorScheme = bookshelfWallColorScheme(palette.wall),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        Surface(
            modifier = modifier.fillMaxSize().testTag("BookshelfLibraryView"),
            color = palette.wall,
            contentColor = palette.ink,
        ) {
            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val rowWidth = maxWidth - ShelfSidePadding * 2
                    val metrics = remember(maxWidth) { shelfMetricsFor(maxWidth.value) }
                    val packed = remember(groups, rowWidth, metrics, settings.display) {
                        groups.map { group ->
                            PackedGroup(
                                group,
                                packShelfRows(
                                    buildShelfSlots(group, settings.display, metrics),
                                    rowWidth.value,
                                    SlotGap.value,
                                ),
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 112.dp),
                    ) {
                        if (title != null) {
                            item(key = "title") {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = ShelfSidePadding, bottom = 4.dp),
                                )
                            }
                        }
                        if (packed.isEmpty()) {
                            item(key = "empty") {
                                Box(
                                    Modifier.fillMaxWidth().padding(top = 96.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.unified_library_no_books),
                                        color = palette.ink.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                        packed.forEach { packedGroup ->
                            val group = packedGroup.group
                            if (settings.grouping != BookshelfGrouping.NONE) {
                                item(key = "h:${group.id}") {
                                    ShelfGroupHeader(
                                        group = group,
                                        ink = palette.ink,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                            itemsIndexed(
                                items = packedGroup.rows,
                                key = { index, _ -> "r:${group.id}:$index" },
                            ) { _, row ->
                                ShelfRow(
                                    row = row,
                                    rowWidth = rowWidth,
                                    palette = palette,
                                    organizing = organizing,
                                    pickedId = pickedId,
                                    selectedBookIds = selectedBookIds,
                                    downloadingBookIds = downloadingBookIds,
                                    usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
                                    onSlotClick = { item ->
                                        when {
                                            !organizing -> onBookClick(item)
                                            pickedId == null -> pickedId = item.bookId
                                            pickedId == item.bookId -> pickedId = null
                                            else -> placePicked(item.bookId)
                                        }
                                    },
                                    onSlotLongClick = onBookLongClick,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                        if (controls != null) {
                            item(key = "controls") {
                                Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) { controls() }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = organizing,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    if (pickedId == null) R.string.bookshelf_organize_pick else R.string.bookshelf_organize_place,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (pickedId != null) {
                                TextButton(onClick = { placePicked(null) }) {
                                    Text(stringResource(R.string.bookshelf_move_to_end))
                                }
                            }
                            TextButton(onClick = {
                                organizing = false
                                pickedId = null
                            }) {
                                Text(stringResource(R.string.bookshelf_done_organizing))
                            }
                        }
                    }
                }
            }
        }

        if (showCustomize) {
            ModalBottomSheet(onDismissRequest = { showCustomize = false }) {
                BookshelfCustomizeSheet(
                    settings = settings,
                    hasManualOrder = order.isNotEmpty(),
                    onSettingsChange = { updateSettings { _ -> it } },
                    onResetOrder = {
                        order = emptyList()
                        store.saveOrder(emptyList())
                    },
                )
            }
        }
    }
}

/**
 * Card for the "Shelves" screen: a small wall panel with one floating plank previewing the first
 * books of a shelf. Tapping anywhere (including a book) opens the shelf.
 */
@Composable
internal fun BookshelfShelfCard(
    name: String,
    countLabel: String,
    books: List<RecentFileItem>,
    usePdfFileNameAsDisplayName: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { BookshelfPreferences(context) }
    val settings = BookshelfAppearance.settings ?: remember(store) { store.loadSettings() }
    val systemDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = remember(settings.wall, settings.finish, systemDark) {
        shelfPalette(settings.wall, settings.finish, systemDark)
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.wall)
            .clickable(onClick = onClick)
            .testTag("BookshelfShelfCard"),
    ) {
        val rowWidth = maxWidth - ShelfSidePadding * 2
        val previewRow = remember(books, rowWidth) {
            val metrics = ShelfMetrics(coverWidth = 56f, coverHeight = 84f)
            val group = BookshelfGroup("preview", BookshelfGroupKind.ALL, null, books.take(24))
            packShelfRows(
                buildShelfSlots(group, BookshelfDisplay.MIXED, metrics),
                rowWidth.value,
                SlotGap.value,
            ).firstOrNull().orEmpty()
        }
        Column(Modifier.fillMaxWidth()) {
            ShelfPill(title = name, count = countLabel, ink = palette.ink, dotKey = name)
            if (previewRow.isEmpty()) {
                Spacer(Modifier.height(84.dp))
                ShelfPlank(palette = palette, modifier = Modifier.offset(y = -(PlankTopDepth / 2)))
            } else {
                ShelfRow(
                    row = previewRow,
                    rowWidth = rowWidth,
                    palette = palette,
                    organizing = false,
                    pickedId = null,
                    selectedBookIds = emptySet(),
                    downloadingBookIds = emptySet(),
                    usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
                    onSlotClick = { onClick() },
                    onSlotLongClick = {},
                )
            }
        }
    }
}

/** Small rounded label above a shelf: coloured dot, name and book count (like the reference). */
@Composable
private fun ShelfPill(title: String, count: String, ink: Color, dotKey: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ShelfSidePadding, end = ShelfSidePadding, top = 14.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ink.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(spineColor(dotKey.lowercase(), dotKey), CircleShape),
            )
            Text(
                text = title,
                color = ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = count,
                color = ink.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ShelfGroupHeader(group: BookshelfGroup, ink: Color, modifier: Modifier = Modifier) {
    val title = when (group.kind) {
        BookshelfGroupKind.ALL -> stringResource(R.string.bookshelf_group_all)
        BookshelfGroupKind.SERIES -> group.label.orEmpty()
        BookshelfGroupKind.STANDALONE -> stringResource(R.string.bookshelf_group_standalone)
        BookshelfGroupKind.AUTHOR -> group.label.orEmpty()
        BookshelfGroupKind.UNKNOWN_AUTHOR -> stringResource(R.string.bookshelf_group_unknown_author)
        BookshelfGroupKind.READING -> stringResource(R.string.bookshelf_group_reading)
        BookshelfGroupKind.UNREAD -> stringResource(R.string.bookshelf_group_unread)
        BookshelfGroupKind.FINISHED -> stringResource(R.string.bookshelf_group_finished)
    }
    ShelfPill(
        title = title,
        count = pluralStringResource(R.plurals.book_count, group.books.size, group.books.size),
        ink = ink,
        dotKey = group.label ?: group.id,
        modifier = modifier,
    )
}

@Composable
private fun ShelfRow(
    row: List<ShelfSlot>,
    rowWidth: Dp,
    palette: ShelfPalette,
    organizing: Boolean,
    pickedId: String?,
    selectedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    usePdfFileNameAsDisplayName: Boolean,
    onSlotClick: (RecentFileItem) -> Unit,
    onSlotLongClick: (RecentFileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowHeight = remember(row) { row.maxOf { it.height }.dp }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .zIndex(1f)
                .padding(horizontal = ShelfSidePadding)
                .height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(SlotGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            var cursor = 0f
            row.forEach { slot ->
                // Where this book sits relative to the vanishing point (shelf centre): books left of
                // centre show their right edge, books right of centre show their left edge.
                val centre = (cursor + slot.width / 2f) / rowWidth.value.coerceAtLeast(1f)
                val sideFactor = ((0.5f - centre) * 2f).coerceIn(-1f, 1f)
                cursor += slot.width + SlotGap.value
                ShelfBook(
                    slot = slot,
                    sideFactor = sideFactor,
                    selected = slot.item.bookId in selectedBookIds,
                    picked = organizing && slot.item.bookId == pickedId,
                    organizing = organizing,
                    downloading = slot.item.bookId in downloadingBookIds,
                    usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
                    onClick = { onSlotClick(slot.item) },
                    onLongClick = { onSlotLongClick(slot.item) },
                )
            }
        }
        ShelfPlank(palette = palette, modifier = Modifier.offset(y = -(PlankTopDepth / 2)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBook(
    slot: ShelfSlot,
    sideFactor: Float,
    selected: Boolean,
    picked: Boolean,
    organizing: Boolean,
    downloading: Boolean,
    usePdfFileNameAsDisplayName: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val item = slot.item
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current
    val lifted = pressed || picked
    // Pulling a book off the shelf: a soft spring lift + scale, no layout change (cheap).
    val lift by animateFloatAsState(
        targetValue = if (lifted) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bookLift",
    )
    val title = item.cardTitle(usePdfFileNameAsDisplayName)
    val sideDepth = if (slot.asCover) MaxSideDepth * abs(sideFactor) else 0.dp
    val shape = if (slot.asCover) RoundedCornerShape(topStart = 2.dp, topEnd = 3.dp, bottomEnd = 1.dp, bottomStart = 1.dp)
    else RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
    val needsLayer = lift > 0.001f || !item.isAvailable

    Box(
        modifier = Modifier
            .width(slot.width.dp)
            .height(slot.height.dp)
            // A render layer per book is wasteful: only books being pressed/moved (or unavailable) get one.
            .then(
                if (needsLayer) {
                    Modifier.graphicsLayer {
                        val dp = density.density
                        cameraDistance = 14f * dp
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = -10f * dp * lift
                        val scale = 1f + 0.07f * lift
                        scaleX = scale
                        scaleY = scale
                        // A little yaw while lifted sells the "pulled out" depth without any 3D engine.
                        rotationY = -sideFactor * 10f * lift
                        alpha = if (item.isAvailable) 1f else 0.55f
                    }
                } else Modifier,
            )
            .drawBehind {
                // Contact shadow on the plank + visible side face (depth) of forward-facing books.
                val depthPx = sideDepth.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                        startY = size.height - 3.dp.toPx(),
                        endY = size.height + 2.dp.toPx(),
                    ),
                    topLeft = Offset(0f, size.height - 3.dp.toPx()),
                    size = Size(size.width + depthPx, 5.dp.toPx()),
                )
                if (depthPx > 0.5f) {
                    val onRight = sideFactor > 0f
                    val x0 = if (onRight) size.width else 0f
                    val dir = if (onRight) 1f else -1f
                    val face = Path().apply {
                        moveTo(x0, 0f)
                        lineTo(x0 + dir * depthPx, 1.5.dp.toPx())
                        lineTo(x0 + dir * depthPx, size.height)
                        lineTo(x0, size.height)
                        close()
                    }
                    drawPath(
                        face,
                        Brush.horizontalGradient(
                            if (onRight) listOf(Color(0xFFEDE6D6), Color(0xFFCFC6B2))
                            else listOf(Color(0xFFCFC6B2), Color(0xFFEDE6D6)),
                            startX = minOf(x0, x0 + dir * depthPx),
                            endX = maxOf(x0, x0 + dir * depthPx),
                        ),
                    )
                }
            }
            .clip(shape)
            .then(
                if (selected || picked) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else Modifier,
            )
            .semantics { contentDescription = title }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .testTag("BookshelfBook"),
    ) {
        if (slot.asCover) {
            CoverFace(item = item, slot = slot)
        } else {
            SpineFace(item = item, slot = slot, title = title)
        }

        if (downloading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            }
        }
        if (organizing && !picked) {
            // Subtle scrim hint that books are movable in organize mode.
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)))
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun CoverFace(item: RecentFileItem, slot: ShelfSlot) {
    Box(Modifier.fillMaxSize()) {
        // Real cover (or the app's generated fallback) through the existing, cached cover pipeline.
        ThemedBookCover(
            item = item,
            modifier = Modifier.fillMaxSize(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
        // Hinge shading + soft gloss: static gradients, no offscreen layers.
        Box(
            Modifier
                .fillMaxHeight()
                .width((slot.width * 0.07f).dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.38f), Color.White.copy(alpha = 0.10f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.10f)),
                    ),
                ),
        )
    }
}

@Composable
private fun SpineFace(item: RecentFileItem, slot: ShelfSlot, title: String) {
    val base = remember(slot.tintKey, item.bookId) { spineColor(slot.tintKey, item.bookId) }
    val textColor = if (base.luminance() > 0.55f) Color(0xFF2A241C) else Color(0xFFF6F0E2)
    val edgeLeft = remember(base) { Color.Black.copy(alpha = 0.35f).compositeOver(base) }
    val highlight = remember(base) { Color.White.copy(alpha = 0.16f).compositeOver(base) }
    val edgeRight = remember(base) { Color.Black.copy(alpha = 0.42f).compositeOver(base) }
    val band = remember(textColor) { textColor.copy(alpha = 0.55f) }
    // Body, rounded-spine shading and gilded bands are painted in a single draw pass (no extra nodes).
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.horizontalGradient(
                        0f to edgeLeft,
                        0.18f to base,
                        0.42f to highlight,
                        0.78f to base,
                        1f to edgeRight,
                    ),
                )
                val bandWidth = size.width * 0.72f
                val bandX = (size.width - bandWidth) / 2f
                drawRect(band, Offset(bandX, 8.dp.toPx()), Size(bandWidth, 1.5.dp.toPx()))
                drawRect(band, Offset(bandX, 12.dp.toPx()), Size(bandWidth, 1.dp.toPx()))
                drawRect(band, Offset(bandX, size.height - 10.dp.toPx()), Size(bandWidth, 1.5.dp.toPx()))
            }
            .padding(top = 20.dp, bottom = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        SpineText(
            text = title,
            color = textColor,
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        )
    }
}

/** Single-line text rotated to read top-to-bottom, measured against the spine's *height*. */
@Composable
private fun SpineText(text: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = 0,
                        maxWidth = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity,
                        minHeight = 0,
                        maxHeight = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity,
                    ),
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(
                        x = -(placeable.width - placeable.height) / 2,
                        y = (placeable.width - placeable.height) / 2,
                    )
                }
            }
            .rotate(90f),
    )
}

@Composable
private fun ShelfPlank(palette: ShelfPalette, modifier: Modifier = Modifier) {
    val totalHeight = PlankTopDepth + PlankFrontHeight + BracketHeight + PlankShadowHeight
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight),
    ) {
        val depth = PlankTopDepth.toPx()
        val front = PlankFrontHeight.toPx()
        val bracket = BracketHeight.toPx()
        val inset = 3.dp.toPx()
        // Thin top face as a trapezoid: the far edge is narrower, giving perspective depth.
        val top = Path().apply {
            moveTo(inset, 0f)
            lineTo(size.width - inset, 0f)
            lineTo(size.width, depth)
            lineTo(0f, depth)
            close()
        }
        drawPath(top, Brush.verticalGradient(listOf(palette.plankFar, palette.plankNear), startY = 0f, endY = depth))
        drawRect(
            brush = Brush.verticalGradient(
                listOf(palette.plankFrontTop, palette.plankFrontBottom),
                startY = depth,
                endY = depth + front,
            ),
            topLeft = Offset(0f, depth),
            size = Size(size.width, front),
        )
        // Highlight on the leading edge of the plank.
        drawRect(
            color = Color.White.copy(alpha = 0.14f),
            topLeft = Offset(0f, depth),
            size = Size(size.width, 1.dp.toPx()),
        )
        // Two small triangular brackets holding the floating shelf, as in the reference.
        val bracketWidth = 16.dp.toPx()
        listOf(size.width * 0.07f, size.width * 0.93f - bracketWidth).forEach { x ->
            val tri = Path().apply {
                moveTo(x, depth + front)
                lineTo(x + bracketWidth, depth + front)
                lineTo(x + bracketWidth / 2f, depth + front + bracket)
                close()
            }
            drawPath(tri, palette.plankFrontBottom)
        }
        // Soft shadow cast on the wall below the plank.
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent),
                startY = depth + front,
                endY = depth + front + PlankShadowHeight.toPx(),
            ),
            topLeft = Offset(0f, depth + front),
            size = Size(size.width, PlankShadowHeight.toPx()),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookshelfCustomizeSheet(
    settings: BookshelfSettings,
    hasManualOrder: Boolean,
    onSettingsChange: (BookshelfSettings) -> Unit,
    onResetOrder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.bookshelf_customize),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        SheetSection(stringResource(R.string.bookshelf_group_by)) {
            BookshelfGrouping.entries.forEach { option ->
                FilterChip(
                    selected = settings.grouping == option,
                    onClick = { onSettingsChange(settings.copy(grouping = option)) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    BookshelfGrouping.NONE -> R.string.bookshelf_grouping_none
                                    BookshelfGrouping.SERIES -> R.string.bookshelf_grouping_series
                                    BookshelfGrouping.AUTHOR -> R.string.bookshelf_grouping_author
                                    BookshelfGrouping.STATUS -> R.string.bookshelf_grouping_status
                                },
                            ),
                        )
                    },
                )
            }
        }
        SheetSection(stringResource(R.string.bookshelf_display)) {
            BookshelfDisplay.entries.forEach { option ->
                FilterChip(
                    selected = settings.display == option,
                    onClick = { onSettingsChange(settings.copy(display = option)) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    BookshelfDisplay.MIXED -> R.string.bookshelf_display_mixed
                                    BookshelfDisplay.COVERS -> R.string.bookshelf_display_covers
                                    BookshelfDisplay.SPINES -> R.string.bookshelf_display_spines
                                },
                            ),
                        )
                    },
                )
            }
        }
        SheetSection(stringResource(R.string.bookshelf_finish)) {
            BookshelfFinish.entries.forEach { option ->
                FilterChip(
                    selected = settings.finish == option,
                    onClick = { onSettingsChange(settings.copy(finish = option)) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    BookshelfFinish.WALNUT -> R.string.bookshelf_finish_walnut
                                    BookshelfFinish.OAK -> R.string.bookshelf_finish_oak
                                    BookshelfFinish.EBONY -> R.string.bookshelf_finish_ebony
                                    BookshelfFinish.WHITE -> R.string.bookshelf_finish_white
                                },
                            ),
                        )
                    },
                )
            }
        }
        SheetSection(stringResource(R.string.bookshelf_wall)) {
            BookshelfWall.entries.forEach { option ->
                FilterChip(
                    selected = settings.wall == option,
                    onClick = { onSettingsChange(settings.copy(wall = option)) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    BookshelfWall.AUTO -> R.string.bookshelf_wall_auto
                                    BookshelfWall.CREAM -> R.string.bookshelf_wall_cream
                                    BookshelfWall.SAND -> R.string.bookshelf_wall_sand
                                    BookshelfWall.SLATE -> R.string.bookshelf_wall_slate
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (hasManualOrder) {
            TextButton(onClick = onResetOrder) {
                Text(stringResource(R.string.bookshelf_reset_order))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    }
}
