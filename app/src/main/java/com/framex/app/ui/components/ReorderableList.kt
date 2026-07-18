package com.framex.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import kotlin.math.roundToInt

/** Content-space distance from the viewport edge, in px, that triggers autoscroll while held. */
private const val AUTO_SCROLL_EDGE_ZONE_PX = 150f

/** How often the autoscroll loop advances while active, in milliseconds. */
private const val AUTO_SCROLL_TICK_MS = 16L

/** Extra invisible padding added around the drag handle to make it easier to grab reliably. */
private val DRAG_HANDLE_TOUCH_PADDING = 8.dp

/**
 * Tracks the drag gesture parameters and calculates slot indexes and offsets Authoritatively,
 * factoring in both item height and item spacing to prevent drift and visual jumping.
 */
private class UniformReorderState(
    private val lazyListState: LazyListState,
    private val coroutineScope: CoroutineScope,
    private val itemCount: () -> Int,
    private val itemHeightPx: Float,
    private val itemSpacingPx: Float,
    private val topContentPaddingPx: Float,
    private val minReorderIndex: Int,
    private val onMoveState: State<(from: Int, to: Int) -> Unit>
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

    private var autoScrollSpeed = 0f
    private var autoScrollJob: Job? = null

    private val slotHeightPx: Float
        get() = itemHeightPx + itemSpacingPx

    fun onDragStart(index: Int) {
        if (index < minReorderIndex) return
        draggingIndex = index
        draggedCenterContentSpace = topContentPaddingPx + index * slotHeightPx + itemHeightPx / 2
        dragOffsetPx = 0f
        autoScrollSpeed = 0f
    }

    fun onDrag(deltaY: Float) {
        if (draggingIndex == null) return
        draggedCenterContentSpace += deltaY
        
        // Clamp draggedCenterContentSpace within valid bounds of the list to prevent card from flying off-screen
        val count = itemCount()
        if (count > 0) {
            val minIndex = minReorderIndex.coerceIn(0, count - 1)
            val minCenter = topContentPaddingPx + minIndex * slotHeightPx + itemHeightPx / 2
            val maxCenter = topContentPaddingPx + (count - 1) * slotHeightPx + itemHeightPx / 2
            draggedCenterContentSpace = draggedCenterContentSpace.coerceIn(minCenter, maxCenter)
        }

        recomputeSlotAndOffset()
        recomputeAutoScrollSpeed()
        ensureAutoScrollLoopRunning()
    }

    /** Ends the drag. The only place [draggingIndex] is ever cleared — no other code path resets it. */
    fun onDragEnd() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        draggingIndex = null
        dragOffsetPx = 0f
        draggedCenterContentSpace = 0f
        autoScrollSpeed = 0f
    }

    private fun recomputeSlotAndOffset() {
        val current = draggingIndex ?: return
        val count = itemCount()
        if (count == 0) return

        val minIndex = minReorderIndex.coerceIn(0, count - 1)
        val targetIndex = ((draggedCenterContentSpace - topContentPaddingPx - itemHeightPx / 2) / slotHeightPx)
            .roundToInt()
            .coerceIn(minIndex, count - 1)

        if (targetIndex != current) {
            onMoveState.value(current, targetIndex)
            draggingIndex = targetIndex
        }

        val settledIndex = draggingIndex ?: return
        val naturalCenter = topContentPaddingPx + settledIndex * slotHeightPx + itemHeightPx / 2
        dragOffsetPx = draggedCenterContentSpace - naturalCenter
    }

    private fun currentScrollOffsetPx(): Float {
        val firstIndex = lazyListState.firstVisibleItemIndex
        val firstOffset = lazyListState.firstVisibleItemScrollOffset
        return if (firstIndex == 0) {
            firstOffset.toFloat()
        } else {
            topContentPaddingPx + firstIndex * slotHeightPx + firstOffset
        }
    }

    private fun recomputeAutoScrollSpeed() {
        val viewportY = draggedCenterContentSpace - currentScrollOffsetPx()
        val viewportHeight = lazyListState.layoutInfo.viewportSize.height.toFloat()

        autoScrollSpeed = when {
            viewportY < AUTO_SCROLL_EDGE_ZONE_PX && lazyListState.canScrollBackward -> {
                val ratio = (AUTO_SCROLL_EDGE_ZONE_PX - viewportY) / AUTO_SCROLL_EDGE_ZONE_PX
                val speed = 3f + (22f * ratio.coerceIn(0f, 1f))
                -speed
            }
            viewportY > viewportHeight - AUTO_SCROLL_EDGE_ZONE_PX && lazyListState.canScrollForward -> {
                val distFromEdge = viewportY - (viewportHeight - AUTO_SCROLL_EDGE_ZONE_PX)
                val ratio = distFromEdge / AUTO_SCROLL_EDGE_ZONE_PX
                val speed = 3f + (22f * ratio.coerceIn(0f, 1f))
                speed
            }
            else -> 0f
        }
    }

    /**
     * Autoscroll runs dynamically based on proportional speed. Swapping continues as list scrolls.
     */
    private fun ensureAutoScrollLoopRunning() {
        if (autoScrollJob?.isActive == true) return
        autoScrollJob = coroutineScope.launch {
            while (isActive && draggingIndex != null) {
                if (autoScrollSpeed != 0f) {
                    val consumed = lazyListState.scrollBy(autoScrollSpeed)
                    if (consumed != 0f) {
                        draggedCenterContentSpace += consumed
                        recomputeSlotAndOffset()
                        recomputeAutoScrollSpeed()
                    } else {
                        autoScrollSpeed = 0f
                    }
                }
                delay(AUTO_SCROLL_TICK_MS)
            }
        }
    }
}

/**
 * A [LazyColumn] of fixed-height rows that can be reordered by dragging a designated handle.
 * Includes placement animations for non-dragged rows and is fully spacing-aware.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ReorderableList(
    items: List<T>,
    key: (T) -> Any,
    itemHeight: Dp,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 0.dp,
    minReorderIndex: Int = 0,
    itemContent: @Composable LazyItemScope.(item: T, dragHandleModifier: Modifier?, isDragging: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val itemSpacingPx = with(density) { itemSpacing.toPx() }
    val topContentPaddingPx = with(density) { contentPadding.calculateTopPadding().toPx() }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val onMoveState = rememberUpdatedState(onMove)

    val reorderState = remember(lazyListState, coroutineScope, itemHeightPx, itemSpacingPx, topContentPaddingPx, minReorderIndex) {
        UniformReorderState(
            lazyListState = lazyListState,
            coroutineScope = coroutineScope,
            itemCount = { items.size },
            itemHeightPx = itemHeightPx,
            itemSpacingPx = itemSpacingPx,
            topContentPaddingPx = topContentPaddingPx,
            minReorderIndex = minReorderIndex,
            onMoveState = onMoveState
        )
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            val latestIndex = rememberUpdatedState(index)
            val isDragging = reorderState.draggingIndex == index
            val offsetPx = if (isDragging) reorderState.dragOffsetPx else 0f

            val handleModifier = if (index >= minReorderIndex) {
                Modifier
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
            } else {
                null
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = itemSpacing)
                    .then(if (isDragging) Modifier else Modifier.animateItemPlacement())
            ) {
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
