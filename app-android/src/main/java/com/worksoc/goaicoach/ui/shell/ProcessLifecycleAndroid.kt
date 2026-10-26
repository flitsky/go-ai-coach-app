package com.worksoc.goaicoach.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

@Composable
internal actual fun rememberProcessLifecycle(): Lifecycle = remember { ProcessLifecycleOwner.get().lifecycle }
