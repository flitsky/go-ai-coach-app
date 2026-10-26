package com.worksoc.goaicoach.ui.l10n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.worksoc.goaicoach.application.preferences.UiLanguageStorePort
import com.worksoc.goaicoach.kv.iosAppStores

@Composable
internal actual fun rememberUiLanguageStore(): UiLanguageStorePort = remember { iosAppStores.uiLanguage }
