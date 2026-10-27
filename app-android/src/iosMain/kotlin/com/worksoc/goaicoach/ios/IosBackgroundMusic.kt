package com.worksoc.goaicoach.ios

import com.worksoc.goaicoach.application.preferences.BackgroundMusicSettingsPort
import com.worksoc.goaicoach.platform.BackgroundMusicCommand
import com.worksoc.goaicoach.platform.BackgroundMusicInputs
import com.worksoc.goaicoach.platform.backgroundMusicCommandFor
import com.worksoc.goaicoach.platform.debugLog
import com.worksoc.goaicoach.platform.nextBackgroundMusicTrack
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategorySoloAmbient
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.otherAudioPlaying
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.darwin.NSObject

/**
 * 「앱 배경음악」으로 싣는 곡 — 안드로이드 `BackgroundMusicPlaylist`(`BackgroundMusicPlayer.kt`)와 **같은 곡 · 같은 차례**다
 * (`BackgroundMusicContractTest`가 둘을 대조한다). 파일은 `scripts/ios-bundle-resources.sh`가 그 ogg를 m4a로 바꿔 `appassets/audio/`에 싣는다.
 */
internal val IosBackgroundMusicPlaylist: List<String> = listOf(
    "bgm_zen_oriental_senbazuru",
    "bgm_piano_gymnopedie_no1",
    "bgm_zen_oriental_shenyang",
    "bgm_zen_oriental_eastern_thought",
)

/**
 * iOS의 앱 배경음악(백로그 #294 · #295 — 안드로이드 `BackgroundMusicCoordinator` + `BackgroundMusicPlayer`의 짝). 언제 틀고 멈출지는
 * 같은 공용 판정(`backgroundMusicCommandFor`)이 정하고, 여기는 그 명령을 `AVAudioPlayer`로 옮길 뿐이다.
 *
 * 안드로이드와 같게 맞춘 것: 볼륨 25% · 곡을 이어 틀고 끝나면 처음으로 · 앱이 뒤로 가면 멈추고 돌아오면 이어 틂 · **다른 앱 음악이 나오고
 * 있으면 틀지 않음**(사용자가 설정에서 방금 켰을 때만 튼다 — 그때는 다른 앱 음악이 멈춘다: `SoloAmbient`) · 전화처럼 잠깐 끊기면 끝난 뒤 이어 틂.
 * 전면 광고 동안은 멈췄다가 닫히면 이어 튼다([whileFullScreenAdShows] — 보상형 광고의 단일 깔때기 `IosAppMonetization.showRewardedAdOnce`가 부른다, #296).
 * iOS에만 있는 것: `SoloAmbient`라 무음 스위치를 켜면 조용하다(게임 앱의 보통 동작 — 스레드가 정한 것).
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosBackgroundMusic(
    private val store: BackgroundMusicSettingsPort,
    private val playlist: List<String> = IosBackgroundMusicPlaylist,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val isForeground = MutableStateFlow(UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateBackground)
    private val isFullScreenAdShowing = MutableStateFlow(false)

    private var lastInputs: BackgroundMusicInputs? = null
    private var player: AVAudioPlayer? = null
    private var trackIndex = 0
    private var wantsToPlay = false
    private var wasPlayingBeforeAd = false
    private var sessionActive = false

    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            // ⚠️ `===`로 견주지 않는다 — 콜백으로 건너온 같은 플레이어가 Kotlin에서는 다른 참조로 보여 늘 거짓이었다(첫 곡 뒤 멈춤).
            if (this@IosBackgroundMusic.player?.isEqual(player) != true) return
            this@IosBackgroundMusic.player = null
            trackIndex = nextBackgroundMusicTrack(trackIndex, playlist.size)
            if (wantsToPlay) playCurrent()
        }
    }

    /** 전면 광고가 떠 있는 동안 — 안드로이드 `BackgroundMusicCoordinator.whileFullScreenAdShows`와 같다. */
    suspend fun <T> whileFullScreenAdShows(block: suspend () -> T): T {
        isFullScreenAdShowing.value = true
        try {
            return block()
        } finally {
            isFullScreenAdShowing.value = false
        }
    }

    fun start() {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ -> isForeground.value = false }
        center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) { _ -> isForeground.value = true }
        // 전화 · 알람이 끝나면 이어 튼다(안드로이드의 AUDIOFOCUS_LOSS_TRANSIENT → GAIN).
        center.addObserverForName(AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue) { note ->
            val type = (note?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedIntegerValue
            if (type == AVAudioSessionInterruptionTypeEnded && wantsToPlay) player?.play()
        }
        scope.launch {
            combine(isForeground, store.enabledChanges(), isFullScreenAdShowing, ::BackgroundMusicInputs)
                .distinctUntilChanged()
                .collect(::update)
        }
    }

    private fun update(inputs: BackgroundMusicInputs) {
        val command = backgroundMusicCommandFor(lastInputs, inputs)
        lastInputs = inputs
        when (command) {
            BackgroundMusicCommand.Start -> play(respectOtherAudio = true)
            BackgroundMusicCommand.StartOnRequest -> play(respectOtherAudio = false)
            BackgroundMusicCommand.Pause -> pause()
            BackgroundMusicCommand.PauseForAd -> {
                wasPlayingBeforeAd = wantsToPlay
                pause()
            }
            BackgroundMusicCommand.ResumeAfterAd -> play(respectOtherAudio = !wasPlayingBeforeAd).also { wasPlayingBeforeAd = false }
            BackgroundMusicCommand.Stop -> stop()
        }
    }

    private fun play(respectOtherAudio: Boolean) {
        if (wantsToPlay && player?.playing == true) return
        val session = AVAudioSession.sharedInstance()
        // 우리 플레이어는 여기서 멈춰 있으므로, 나오고 있는 소리는 다른 앱의 것이다.
        if (respectOtherAudio && session.otherAudioPlaying) return
        if (!sessionActive) {
            session.setCategory(AVAudioSessionCategorySoloAmbient, error = null)
            sessionActive = session.setActive(true, error = null)
            if (!sessionActive) return
        }
        wantsToPlay = true
        val current = player
        if (current != null) current.play() else playCurrent()
    }

    private fun playCurrent() {
        val path = NSBundle.mainBundle.resourcePath?.let { "$it/appassets/audio/${playlist[trackIndex]}.m4a" }
        val next = path?.let { AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(it), error = null) }
        // 곡이 없거나 깨졌으면 조용히 멈춘다 — 다음에 앱으로 돌아올 때 다시 시도한다(안드로이드와 같다).
        if (next == null) {
            debugLog(Tag, "track $trackIndex could not be opened: $path")
            stop()
            return
        }
        next.delegate = delegate
        next.volume = Volume
        player = next
        if (!next.play()) debugLog(Tag, "track $trackIndex did not start")
    }

    private fun pause() {
        wantsToPlay = false
        player?.pause()
        deactivate()
    }

    private fun stop() {
        wantsToPlay = false
        player?.stop()
        player = null
        deactivate()
    }

    private fun deactivate() {
        if (!sessionActive) return
        AVAudioSession.sharedInstance().setActive(false, error = null)
        sessionActive = false
    }

    private companion object {
        const val Tag = "BackgroundMusic"
        const val Volume = 0.25f
    }
}
