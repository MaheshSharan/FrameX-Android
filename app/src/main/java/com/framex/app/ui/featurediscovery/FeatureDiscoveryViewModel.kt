package com.framex.app.ui.featurediscovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeatureDiscoveryViewModel @Inject constructor(
    private val repository: FeatureDiscoveryRepository
) : ViewModel() {

    private val _guideState = MutableStateFlow<DiscoveryGuideState>(DiscoveryGuideState.Hidden)
    val guideState: StateFlow<DiscoveryGuideState> = _guideState.asStateFlow()

    private val _completionEvent = MutableStateFlow<DiscoveryCompletionEvent?>(null)
    val completionEvent: StateFlow<DiscoveryCompletionEvent?> = _completionEvent.asStateFlow()

    private val _effect = MutableSharedFlow<DiscoveryUiEffect>()
    val effect: SharedFlow<DiscoveryUiEffect> = _effect.asSharedFlow()

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

    fun launchAction(action: DiscoveryAction) {
        viewModelScope.launch {
            _effect.emit(DiscoveryUiEffect.LaunchAction(action))
        }
    }

    fun consumeCompletionEvent() {
        _completionEvent.value = null
    }

    private companion object {
        const val TAG = "FeatureDiscovery"
    }
}
