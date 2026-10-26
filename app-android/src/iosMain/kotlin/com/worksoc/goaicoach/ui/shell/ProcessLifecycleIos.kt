package com.worksoc.goaicoach.ui.shell

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal actual fun rememberProcessLifecycle(): Lifecycle = LocalLifecycleOwner.current.lifecycle
