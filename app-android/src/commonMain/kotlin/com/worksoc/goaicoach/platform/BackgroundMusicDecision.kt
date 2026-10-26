package com.worksoc.goaicoach.platform

// 배경음악의 판정(백로그 #244) — 입력 → 할 일, 다음 곡. 소리를 내는 플레이어는 플랫폼마다 따로다(안드로이드 `BackgroundMusicPlayer.kt`).

/**
 * 플레이어가 따르는 입력 — 앱이 화면 앞에 있는가, 설정 「앱 배경음악」이 켜져 있는가, 전면 광고가 떠 있는가.
 *
 * 전면 광고는 앱 안의 화면이라 앱이 뒤로 가지 않는데, 광고 영상이 오디오 포커스를 가져가면 플레이어는 그것을 「다른 앱이 음악을
 * 틀었다」로 받아 멈춘 채 다시 틀지 않았다(2026-10-18 에뮬레이터 — 광고를 닫아도 조용했다). 그래서 광고 동안은 스스로 멈추고
 * 포커스를 놓았다가, 닫히면 광고 전에 틀고 있었을 때만 다시 튼다.
 */
internal data class BackgroundMusicInputs(
    val isForeground: Boolean,
    val isEnabled: Boolean,
    val isFullScreenAdShowing: Boolean = false,
)

internal enum class BackgroundMusicCommand {
    /** 튼다 — 다만 **다른 앱의 음악이 나오고 있으면 틀지 않는다**(사용자 2026-10-18 「그 음악을 존중」). */
    Start,

    /** 사용자가 설정에서 **방금 켰다** — 직접 고른 것이라 다른 앱 음악이 있어도 튼다(스레드가 정한 규칙). */
    StartOnRequest,

    /** 앱이 뒤로 갔다 — 자리를 기억한 채 멈춘다. */
    Pause,

    /** 전면 광고가 떴다 — 멈추고 포커스를 광고에 내준다. 그때 틀고 있었는지를 기억한다. */
    PauseForAd,

    /** 전면 광고가 닫혔다 — 광고 전에 틀고 있었으면 다시 튼다(나오던 소리는 우리 광고였다). 아니었으면 [Start]와 같다. */
    ResumeAfterAd,

    /** 설정에서 껐다 — 플레이어를 놓는다. */
    Stop,
}

/** 입력이 바뀌었을 때 할 일. 같은 입력이 거듭 와도 괜찮다 — 이미 트는 중이면 [BackgroundMusicPlayer]가 아무것도 하지 않는다. */
internal fun backgroundMusicCommandFor(previous: BackgroundMusicInputs?, next: BackgroundMusicInputs): BackgroundMusicCommand = when {
    !next.isEnabled -> BackgroundMusicCommand.Stop
    !next.isForeground -> BackgroundMusicCommand.Pause
    next.isFullScreenAdShowing -> BackgroundMusicCommand.PauseForAd
    previous?.isFullScreenAdShowing == true -> BackgroundMusicCommand.ResumeAfterAd
    previous != null && previous.isForeground && !previous.isEnabled -> BackgroundMusicCommand.StartOnRequest
    else -> BackgroundMusicCommand.Start
}

internal fun nextBackgroundMusicTrack(current: Int, size: Int): Int = (current + 1) % size
