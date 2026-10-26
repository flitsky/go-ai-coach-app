package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun screenWidthDp(): Int = (LocalWindowInfo.current.containerSize.width / LocalDensity.current.density).toInt()

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun screenHeightDp(): Int = (LocalWindowInfo.current.containerSize.height / LocalDensity.current.density).toInt()

@OptIn(ExperimentalComposeUiApi::class)
actual fun fullScreenDialogProperties(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false, usePlatformInsets = false)
