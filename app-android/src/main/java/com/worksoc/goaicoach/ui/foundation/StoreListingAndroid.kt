package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.platform.openStoreListing

@Composable
actual fun rememberStoreListingLauncher(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { openStoreListing(context) } }
}
