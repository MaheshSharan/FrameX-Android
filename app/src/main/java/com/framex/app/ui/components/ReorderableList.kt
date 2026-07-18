package com.framex.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Content-space distance from the viewport edge, in px, that triggers autoscroll while held. */
private const val AUTO_SCROLL_EDGE_ZONE_PX = 150f

/** Autoscroll speed in px per tick while a held row sits inside the edge zone. */
private const val AUTO_SCROLL_SPEED_PX = 14f

/** How often the autoscroll loop advances while active, in milliseconds. */
private const val AUTO_SCROLL_TICK_MS = 16L

/** Extra invisible padding added around the drag handle to make it easier to grab reliably. */
private val DRAG_HANDLE_TOUCH_PADDING = 8.dp

/**
 * Drives a handle-triggered drag-to-reorder gesture for a [LazyColumn] of uniform-height rows.
 *
 * Deliberately avoids reading [LazyListState.layoutInfo]'s per-item info mid-gesture: that data
 * reflects the *previous* layout pass and lags behind rapid pointer events, which is what
 * previously caused the drag to compute swap targets against stale positions — producing
 * overlapping rows, growing gaps, and the drag appearing to "let go" mid-motion. Instead, because
 * every row has the same [itemHeightPx], a row's position is pure arithmetic
 * (`index * itemHeightPx`), so the entire gesture is tracked as an accumulated content-space
 * offset — correct regardless of what LazyColumn's virtualization or recomposition timing is
 * doing, and correct even when the target row isn't currently rendered (e.g. dragging the top
 * row down past several screens' worth of content).
 */
private class UniformReorderState(
    private val lazyListState: LazyListState,
    private val coroutineScope: CoroutineScope,
    private val itemCount: () -> Int,
    private val itemHeightPx: Float,
    private val topContentPaddingPx: Float,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    /** Index of the row currently being dragged, or null when no drag is in progress. */
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    /**
     * Visual pixel offset to apply, via `graphicsLayer { translationY = ... }`, to whichever row
     * currently sits at [draggingIndex] — the gap between where the gesture wants that row's
     * center to be and where it's naturally laid out at its current slot.
     */
    var dragOffsetPx by mutableFloatStateOf(0f)
        private set

    /** Authoritative content-space Y (unaffected by scroll) of the dragged row's center. */
    private var draggedCenterContentSpace = 0f

    private var autoScrollDirection = 0
    private var autoScrollJob: Job? = null

    fun onDragStart(index: Int) {
        draggingIndex = index
        draggedCenterContentSpace = (index + 0.5f) * itemHeightPx
        dragOffsetPx = 0f
    }

    fun onDrag(deltaY: Float) {
        if (draggingIndex == null) return
        draggedCenterContentSpace += deltaY
        recomputeSlotAndOffset()
        recomputeAutoScrollDirection()
        ensureAutoScrollLoopRunning()
    }

    /** Ends the drag. The only place [draggingIndex] is ever cleared — no other code path resets it. */
    fun onDragEnd() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        draggingIndex = null
        dragOffsetPx = 0f
        draggedCenterContentSpace = 0f
        autoScrollDirection = 0
    }

    private fun recomputeSlotAndOffset() {
        val current = draggingIndex ?: return
        val targetIndex = (draggedCenterContentSpace / itemHeightPx)
            .toInt()
            .coerceIn(0, (itemCount() - 1).coerceAtLeast(0))

        if (targetIndex != current) {
            onMove(current, targetIndex)
            draggingIndex = targetIndex
        }

        val settledIndex = draggingIndex ?: return
        dragOffsetPx = draggedCenterContentSpace - (settledIndex + 0.5f) * itemHeightPx
    }

    private fun currentScrollOffsetPx(): Float =
        lazyListState.firstVisibleItemIndex * itemHeightPx + lazyListState.firstVisibleItemScrollOffset

    private fun recomputeAutoScrollDirection() {
        val viewportY = draggedCenterContentSpace - currentScrollOffsetPx() + topContentPaddingPx
        val viewportHeight = lazyListState.layoutInfo.viewportSize.height.toFloat()

        autoScrollDirection = when {
            viewportY < AUTO_SCROLL_EDGE_ZONE_PX && lazyListState.canScrollBackward -> -1
            viewportY > viewportHeight - AUTO_SCROLL_EDGE_ZONE_PX && lazyListState.canScrollForward -> 1
            else -> 0
        }
    }

    /**
     * Autoscroll must keep running while the finger is held stationary near an edge — pointer
     * events only fire on movement, so this uses its own repeating loop rather than piggybacking
     * on [onDrag]. Started lazily on first need and left running (cheaply idling) for the rest of
     * the drag; [onDragEnd] is the only thing that cancels it.
     */
    private fun ensureAutoScrollLoopRunning() {
        if (autoScrollJob?.isActive == true) return
        autoScrollJob = coroutineScope.launch {
            while (isActive && draggingIndex != null) {
                if (autoScrollDirection != 0) {
                    val delta = AUTO_SCROLL_SPEED_PX * autoScrollDirection
                    lazyListState.scrollBy(delta)
                    draggedCenterContentSpace += delta
                    recomputeSlotAndOffset()
                    recomputeAutoScrollDirection()
                }
                delay(AUTO_SCROLL_TICK_MS)
            }
        }
    }
}

/**
 * A [LazyColumn] of fixed-height rows that can be reordered by dragging a designated handle.
 * [itemContent] receives the drag-handle [Modifier] plus whether that specific row is the one
 * currently being dragged — apply "held" styling (border/elevation/etc.) based on that flag.
 * This component owns all positioning itself (tracking the dragged row under the finger and
 * elevating it above its neighbors), so [itemContent] never has to think about offsets.
 *
 * @param items the current, externally-owned list. Never mutated directly here — reordering is
 *   reported through [onMove], and the caller must apply it before the next recomposition.
 * @param key stable identity per item, so Compose preserves row state (including this
 *   component's own gesture-detection coroutine) across reorders instead of disposing it.
 * @param itemHeight the exact height every row renders at. This is a hard requirement, not a
 *   hint — content taller than this is clipped, because the drag math assumes every row occupies
 *   precisely this many pixels; see [UniformReorderState].
 * @param itemSpacing vertical gap below each row.
 * @param onMove called with (fromIndex, toIndex) whenever the drag crosses into a new slot.
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    key: (T) -> Any,
    itemHeight: Dp,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 0.dp,
    itemContent: @Composable (item: T, dragHandleModifier: Modifier, isDragging: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val topContentPaddingPx = with(density) { contentPadding.calculateTopPadding().toPx() }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val reorderState = remember(lazyListState, coroutineScope, itemHeightPx, topContentPaddingPx) {
        UniformReorderState(
            lazyListState = lazyListState,
            coroutineScope = coroutineScope,
            itemCount = { items.size },
            itemHeightPx = itemHeightPx,
            topContentPaddingPx = topContentPaddingPx,
            onMove = onMove
        )
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            // pointerInput is keyed by the item's stable identity (not its index) so the
            // gesture-detection coroutine survives reorders instead of restarting — which means
            // a plain captured `index` would go stale the moment this row moves. rememberUpdatedState
            // keeps onDragStart reading whatever index this row currently holds when a new drag begins.
            val latestIndex = rememberUpdatedState(index)
            val isDragging = reorderState.draggingIndex == index
            val offsetPx = if (isDragging) reorderState.dragOffsetPx else 0f

            val handleModifier = Modifier
                .padding(DRAG_HANDLE_TOUCH_PADDING)
                .pointerInput(key(item)) {
                    detectDragGestures(
                        onDragStart = { reorderState.onDragStart(latestIndex.value) },
                        onDragEnd = { reorderState.onDragEnd() },
                        onDragCancel = { reorderState.onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            reorderState.onDrag(dragAmount.y)
                        }
                    )
                }

            Box(modifier = Modifier.padding(bottom = itemSpacing)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer { translationY = offsetPx }
                        .zIndex(if (isDragging) 1f else 0f)
                ) {
                    itemContent(item, handleModifier, isDragging)
                }
            }
        }
    }
}
