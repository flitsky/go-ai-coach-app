package com.worksoc.goaicoach.ios

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.kv.iosAppStores
import com.worksoc.goaicoach.platform.AppNotice
import com.worksoc.goaicoach.platform.NoticeHandle
import com.worksoc.goaicoach.platform.PlayHapticFeedback
import com.worksoc.goaicoach.ui.foundation.AppPlatform
import com.worksoc.goaicoach.ui.foundation.AppStores
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UIPasteboard

/** iOS의 [AppPlatform](백로그 #294 — S1). 루트(`MainViewController`)가 하나 만들어 넣는다. 기능은 웨이브마다 는다. */
internal class IosAppPlatform : AppPlatform {
    override val notice = IosNotice()
    override val clipboard: ClipboardPort = IosClipboard()
    override val stores: AppStores = iosAppStores
    override val haptics: PlayHapticFeedback = IosPlayHaptics
}

/** 착수 진동(S10) — 돌을 놓으면 묵직한 한 번, 놓을 수 없으면 경고 알림(짧게 두 번 떨린다). */
private object IosPlayHaptics : PlayHapticFeedback {
    override fun play() {
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).impactOccurred()
    }

    override fun playInvalid() {
        UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
    }
}

/**
 * 토스트 대신 화면 아래에 잠깐 뜨는 한 줄(S3) — 한 번에 하나, 새것이 지금 것을 바꾼다. 짧게 2초, 길게 3.5초(안드로이드 토스트와 같은 길이).
 * 그리는 것은 루트의 [IosNoticeOverlay]다.
 */
internal class IosNotice : AppNotice {
    internal class Entry(val message: String, val long: Boolean)

    private val _current = MutableStateFlow<Entry?>(null)
    val current: StateFlow<Entry?> = _current.asStateFlow()

    override fun show(message: String, long: Boolean): NoticeHandle {
        val entry = Entry(message, long)
        _current.value = entry
        return NoticeHandle { _current.compareAndSet(entry, null) }
    }

    fun expire(entry: Entry) {
        _current.compareAndSet(entry, null)
    }
}

@Composable
internal fun IosNoticeOverlay(notice: IosNotice) {
    val entry by notice.current.collectAsState()
    LaunchedEffect(entry) {
        val shown = entry ?: return@LaunchedEffect
        delay(if (shown.long) 3_500L else 2_000L)
        notice.expire(shown)
    }
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
        // 사라지는 동안에도 글자가 남아 있게 마지막 것을 기억한다.
        val last = remember { mutableLastEntry() }
        entry?.let { last.value = it }
        AnimatedVisibility(visible = entry != null, enter = fadeIn(), exit = fadeOut()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Text(
                    text = last.value?.message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private class LastEntry(var value: IosNotice.Entry? = null)

private fun mutableLastEntry() = LastEntry()

private class IosClipboard : ClipboardPort {
    override fun setText(label: String, text: String): Boolean {
        UIPasteboard.generalPasteboard.string = text
        return true
    }
}
