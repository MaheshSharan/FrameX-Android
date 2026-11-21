package com.framex.app.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

/** Shows each message emitted by a ViewModel as a short toast. */
@Composable
fun ToastEffect(messages: Flow<Int>) {
    val context = LocalContext.current
    // Collects for as long as the screen is composed; the flow is a one-shot channel.
    LaunchedEffect(messages) {
        messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}
