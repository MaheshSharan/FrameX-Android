package com.framex.app.ui.featurediscovery

import android.content.Context
import androidx.lifecycle.ViewModel
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class FeatureDiscoveryViewModel @Inject constructor(
    private val repository: FeatureDiscoveryRepository
) : ViewModel() {

    private val _guideState = MutableStateFlow<DiscoveryGuideState>(DiscoveryGuideState.Hidden)
    val guideState: StateFlow<DiscoveryGuideState> = _guideState.asStateFlow()

    private val _completionEvent = MutableStateFlow<DiscoveryCompletionEvent?>(null)
    val completionEvent: StateFlow<DiscoveryCompletionEvent?> = _completionEvent.asStateFlow()

    fun onScreenEntered(screenId: DiscoveryScreenId) {
        val guide = FeatureDiscoveryCatalog.guideFor(screenId) ?: return
        if (!repository.shouldAutoShow(screenId)) {
            FrameXLog.d("Discovery auto-show skipped for ${screenId.name}", tag = TAG)
            return
        }
        if (_guideState.value is DiscoveryGuideState.Active) return

        _guideState.value = DiscoveryGuideState.Active(
            screenId = screenId,
            guide = guide
        )
        FrameXLog.i("Discovery started: ${screenId.name}", tag = TAG)
    }

    fun completeGuide() {
        val state = _guideState.value as? DiscoveryGuideState.Active ?: return
        repository.markScreenCompleted(state.screenId)
        _guideState.value = DiscoveryGuideState.Hidden
        _completionEvent.value = DiscoveryCompletionEvent(
            screenId = state.screenId,
            message = FeatureDiscoveryCatalog.completionMessage(state.screenId),
            actionLabel = "Got it"
        )
        FrameXLog.i("Discovery completed for ${state.screenId.name}", tag = TAG)
    }

    fun skipAll() {
        val state = _guideState.value as? DiscoveryGuideState.Active
        repository.markGloballyDismissed()
        _guideState.value = DiscoveryGuideState.Hidden
        _completionEvent.value = DiscoveryCompletionEvent(
            screenId = state?.screenId ?: DiscoveryScreenId.PERMISSIONS,
            message = "Setup guide dismissed"
        )
        FrameXLog.i("Discovery globally dismissed by user", tag = TAG)
    }

    fun dismissOverlay() {
        _guideState.value = DiscoveryGuideState.Hidden
    }

    fun launchAction(context: Context, action: DiscoveryAction) {
        runCatching {
            context.startActivity(
                action.intentBuilder(context).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { error ->
            FrameXLog.w("Failed to launch discovery action intent", error, tag = TAG)
        }
    }

    fun consumeCompletionEvent() {
        _completionEvent.value = null
    }

    private companion object {
        const val TAG = "FeatureDiscovery"
    }
}
