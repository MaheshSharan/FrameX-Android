package com.framex.app.ui.screens.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.framex.app.R
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.ui.screens.permissions.PermissionItem

@Immutable
data class OnboardingPage(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val imageRes: Int
)

val DefaultOnboardingPages = listOf(
    OnboardingPage(
        titleRes = R.string.onboarding_page1_title,
        descriptionRes = R.string.onboarding_page1_desc,
        imageRes = R.drawable.img_onboarding_fps
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_page2_title,
        descriptionRes = R.string.onboarding_page2_desc,
        imageRes = R.drawable.img_onboarding_metrics
    ),
    OnboardingPage(
        titleRes = R.string.onboarding_page3_title,
        descriptionRes = R.string.onboarding_page3_desc,
        imageRes = R.drawable.img_onboarding_shizuku
    )
)

@Immutable
data class OnboardingUiState(
    val selectedMode: PrivilegeMode = PrivilegeMode.SHIZUKU,
    val isPrivilegeReady: Boolean = false,
    val essentials: List<PermissionItem> = emptyList()
)

sealed interface OnboardingUiEvent {
    data object CompleteOnboarding : OnboardingUiEvent
    data class SelectMode(val mode: PrivilegeMode) : OnboardingUiEvent
    data object RefreshPermissions : OnboardingUiEvent
}
