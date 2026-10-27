package com.worksoc.goaicoach.ios

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.worksoc.goaicoach.platform.AppNotice
import com.worksoc.goaicoach.platform.NoticeHandle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/**
 * [IosNotice]를 그린다 — **떠 있는 창들보다 위에**(안드로이드 토스트는 앱 창 위에 뜬다). 알림이 뜰 때마다 새 팝업 층을 만들어서, 이미 열려 있는
 * 대화상자(캐릭터 고르기 · 진단 로그 …)보다 나중 층이 된다. 포커스를 갖지 않는 층이라 대화상자의 터치를 빼앗지 않는다.
 * 루트에서 앱 테마 안에 두어 어두운 화면 모드의 색을 따른다(`MainViewController.kt`).
 */
@Composable
internal fun IosNoticeOverlay(notice: IosNotice) {
    val entry by notice.current.collectAsState()
    // 팝업이 보여 주는 것 — 사라지는 동안에도 글자가 남게 마지막 것을 든다. 다 사라지면 null로 돌려 팝업 층을 걷는다.
    var shown by remember { mutableStateOf<IosNotice.Entry?>(null) }
    val visibility = remember { MutableTransitionState(false) }
    LaunchedEffect(entry) {
        val current = entry
        if (current == null) {
            visibility.targetState = false
            return@LaunchedEffect
        }
        shown = current
        visibility.targetState = true
        delay(if (current.long) 3_500L else 2_000L)
        notice.expire(current)
    }
    LaunchedEffect(visibility) {
        snapshotFlow { visibility.isIdle && !visibility.currentState }.collect { gone ->
            if (gone && notice.current.value == null) shown = null
        }
    }
    val message = shown ?: return
    // ⚠️ 팝업 층은 **제 사각형 안의 터치를 모두** 가져간다(포커스가 없어도) — 그래서 여백(아래 48dp · 양옆 24dp)을 팝업 안에 두지 않는다.
    //   안에 두면 알림이 떠 있는 2~3.5초 동안 그 여백 밑의 하단 탭 · 버튼이 눌리지 않는다. 띄우기는 offset, 폭은 widthIn으로.
    val density = LocalDensity.current
    val maxPillWidth = with(density) { (LocalWindowInfo.current.containerSize.width.toDp() - 48.dp).coerceAtLeast(120.dp) }
    val lift = with(density) { 48.dp.roundToPx() }
    key(message) {
        Popup(alignment = Alignment.BottomCenter, offset = IntOffset(0, -lift), properties = PopupProperties(focusable = false)) {
            Box {
                AnimatedVisibility(visibleState = visibility, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.widthIn(max = maxPillWidth),
                    ) {
                        Text(
                            text = message.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
