package com.worksoc.goaicoach

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.worksoc.goaicoach.persistence.BackgroundMusicStore
import com.worksoc.goaicoach.platform.BackgroundMusicInputs
import com.worksoc.goaicoach.platform.BackgroundMusicPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 「앱 배경음악」(백로그 #244)의 배선 — 앱이 앞에 있는가 · 설정([BackgroundMusicStore]) · 전면 광고가 떠 있는가를 묶어 프로세스에 하나뿐인
 * [BackgroundMusicPlayer]에 넘긴다. 언제 틀고 멈출지는 플레이어 쪽 판정(`backgroundMusicCommandFor`)이 정한다.
 *
 * ## 「앞에 있는가」는 이벤트가 아니라 수명 주기의 **지금 상태**에서 읽는다
 * [AppForegroundEvents]는 지나간 이벤트를 다시 주지 않는다 — 구독이 첫 `onStart`보다 늦으면 앱이 앞에 있는데도 「뒤」로 알고,
 * 그 상태에서 설정을 켜면 틀지 않는다. 에뮬레이터에서 한 번 그렇게 됐다(2026-10-18 — 켰는데 소리가 없다가 홈에 갔다 오니 틀었다.
 * 콜드 스타트 6번 반복으로는 다시 나오지 않아 원인을 확정하지는 못했다). `ProcessLifecycleOwner`의 `currentStateFlow`는
 * 언제 구독해도 지금 상태부터 주므로 그 틈이 없다. `onStop`은 수명 주기가 조금 늦춰 보내 회전에는 뒤로 가지 않는다.
 *
 * ⚠️ 화면(셸)을 거치지 않는다 — 앱 전체의 음악이라 어느 화면에 있든 같은 판정이다(사용자 2026-10-18: 대국 화면에서도 이어진다).
 * 나중에 대국 중 음악을 따로 두게 되면 그때 「지금 대국 중인가」를 입력으로 더한다.
 * ⚠️ 메인 스레드에서 모은다 — `MediaPlayer`와 오디오 포커스 콜백이 같은 스레드에서 돌게.
 */
internal class BackgroundMusicCoordinator(context: Context) {
    private val store = BackgroundMusicStore(context)
    private val player = BackgroundMusicPlayer(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val isFullScreenAdShowing = MutableStateFlow(false)

    /**
     * 전면 광고를 [block] 동안 띄운다 — 그동안 음악을 멈추고, 닫히면 광고 전에 틀고 있었을 때만 다시 튼다(`BackgroundMusicInputs` KDoc).
     * 광고를 띄우는 길은 `showRewardedAdOnce` 하나다(그 KDoc — 단일 깔때기).
     */
    suspend fun <T> whileFullScreenAdShows(block: suspend () -> T): T {
        isFullScreenAdShowing.value = true
        try {
            return block()
        } finally {
            isFullScreenAdShowing.value = false
        }
    }

    fun start() {
        val isForeground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
        scope.launch {
            combine(isForeground, store.enabledChanges(), isFullScreenAdShowing, ::BackgroundMusicInputs)
                .distinctUntilChanged()
                .collect(player::update)
        }
    }
}
