package com.worksoc.goaicoach.ui.monetization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.AndroidAppMonetization
import com.worksoc.goaicoach.platform.AppMonetization

@Composable
internal actual fun rememberAppMonetization(): AppMonetization {
    val context = LocalContext.current
    return remember(context) { AndroidAppMonetization(context) }
}
