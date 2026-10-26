package com.worksoc.goaicoach.ui.l10n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.application.preferences.UiLanguageStorePort
import com.worksoc.goaicoach.persistence.UiLanguageStore

@Composable
internal actual fun rememberUiLanguageStore(): UiLanguageStorePort {
    val context = LocalContext.current
    return remember(context) { UiLanguageStore(context) }
}
