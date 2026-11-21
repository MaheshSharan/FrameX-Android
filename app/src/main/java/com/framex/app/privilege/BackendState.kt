package com.framex.app.privilege

/** Why a backend cannot be used until the person does something. */
enum class ActionReason {
    /** Shizuku is not running, so no binder is available. */
    SHIZUKU_NOT_RUNNING,
    SHIZUKU_PERMISSION_DENIED,
    /** No `su` binary answered as uid 0, or the root manager denied the request. */
    NO_ROOT_GRANT
}

/** Connection state of one privilege backend. */
sealed interface BackendState {
    data object Idle : BackendState
    data object Connecting : BackendState
    data class NeedsUserAction(val reason: ActionReason) : BackendState
    data object Connected : BackendState
}
