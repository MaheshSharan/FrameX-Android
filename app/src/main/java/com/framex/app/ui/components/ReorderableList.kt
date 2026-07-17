package com.framex.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Distance from a scrollable edge, in px, that triggers autoscroll while dragging. */
private const val AUTO_SCROLL_EDGE_THRESHOLD_PX = 120f

/** Autoscroll speed in px per triggered scroll step while the drag sits in the edge zone. */
private const val AUTO_SCROLL_SPEED_PX = 16f

/**
 * Tracks a handle-driven drag-to-reorder gesture against a [LazyListState]'s live layout.
 *
 * This holds only *where the drag currently is*; it never mutates the caller's list. Each
 * [onDrag] call may invoke [onMove] zero or more times as the dragged row's midpoint crosses
 * a neighboring row's midpoint, and the caller is expected to apply that move to its own
 * list state before the next recomposition.
 */
internal class ReorderableListState(
    private val lazyListState: LazyListState,
    private val coroutineScope: CoroutineScope,
    private val onMove: (from: Int, to: Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    var draggingItemOffset by mutableFloatStateOf(0f)
        private set

    private val draggingItemLayoutInfo
        get() = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    fun onDragStart(itemIndex: Int) {
        draggingItemIndex = itemIndex
        draggingItemOffset = 0f
    }

    fun onDrag(deltaY: Float) {
        if (draggingItemIndex == null) return
        draggingItemOffset += deltaY

        val currentLayout = draggingItemLayoutInfo ?: return
        val draggedTop = currentLayout.offset + draggingItemOffset
        val draggedMiddle = draggedTop + currentLayout.size / 2f

        val targetLayout = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            candidate.index != draggingItemIndex &&
                draggedMiddle >= candidate.offset &&
                draggedMiddle <= candidate.offset + candidate.size
        }

        if (targetLayout != null) {
            val fromIndex = requireNotNull(draggingItemIndex)
            // Keep the dragged row's absolute screen position continuous across the swap by
            // correcting for the (usually zero, but not guaranteed) size difference between rows.
            draggingItemOffset += (currentLayout.offset - targetLayout.offset).toFloat()
            onMove(fromIndex, targetLayout.index)
            draggingItemIndex = targetLayout.index
        }

        autoScrollIfNearEdge(draggedTop, draggedTop + currentLayout.size)
    }

    fun onDragEnd() {
        draggingItemIndex = null
        draggingItemOffset = 0f
    }

    private fun autoScrollIfNearEdge(draggedTop: Float, draggedBottom: Float) {
        val layoutInfo = lazyListState.layoutInfo
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()

        val scrollDelta = when {
            draggedTop <= viewportStart + AUTO_SCROLL_EDGE_THRESHOLD_PX && lazyListState.canScrollBackward ->
                -AUTO_SCROLL_SPEED_PX
            draggedBottom >= viewportEnd - AUTO_SCROLL_EDGE_THRESHOLD_PX && lazyListState.canScrollForward ->
                AUTO_SCROLL_SPEED_PX
            else -> 0f
        }

        if (scrollDelta != 0f) {
            coroutineScope.launch {
                lazyListState.scrollBy(scrollDelta)
                draggingItemOffset += scrollDelta
            }
        }
    }
}

/**
 * A [LazyColumn] whose rows can be reordered by dragging a designated handle. [itemContent]
 * receives the [Modifier] to attach to that handle (typically a drag-indicator icon) plus the
 * live drag state for that row — only touches starting on the handle initiate a drag, so the
 * rest of the row keeps its normal click/toggle behavior.
 *
 * @param items the current, externally-owned list. This composable never mutates it directly;
 *   reordering is reported through [onMove] and the caller must apply it to its own state.
 * @param key stable identity per item, required so Compose doesn't lose row state across reorders.
 * @param onMove called with (fromIndex, toIndex) whenever the drag crosses a neighboring row's
 *   midpoint.
 * @param itemContent given the item, the drag-handle [Modifier], and the row's current visual
 *   drag offset in pixels (nonzero only for the row actively being dragged — apply it via
 *   `Modifier.graphicsLayer { translationY = dragOffsetPx }`).
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemContent: @Composable (item: T, dragHandleModifier: Modifier, dragOffsetPx: Float) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val reorderState = remember(lazyListState, coroutineScope) {
        ReorderableListState(
            lazyListState = lazyListState,
            coroutineScope = coroutineScope,
            onMove = onMove
        )
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            // pointerInput below is keyed by the item's stable identity, not its index, so
            // the gesture-detection coroutine is never restarted by a reorder — it keeps
            // running across the item's lifetime. A plain captured `index` would therefore
            // go stale the moment this row moves. rememberUpdatedState keeps onDragStart
            // reading whatever index this row currently holds at the moment a new drag begins.
            val latestIndex = rememberUpdatedState(index)
            val dragOffsetPx = if (reorderState.draggingItemIndex == index) reorderState.draggingItemOffset else 0f
            val handleModifier = Modifier.pointerInput(key(item)) {
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

            itemContent(item, handleModifier, dragOffsetPx)
        }
    }
}
