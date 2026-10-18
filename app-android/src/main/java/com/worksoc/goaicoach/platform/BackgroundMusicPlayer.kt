package com.worksoc.goaicoach.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.worksoc.goaicoach.R

/**
 * 「앱 배경음악」으로 싣는 곡(백로그 #244) — 이 차례로 이어 틀고, 마지막 곡이 끝나면 처음으로 돌아간다.
 *
 * 곡은 사용자가 골랐다(2026-10-18): 원곡 폴더 `assets/audio/bgm/ogg/`의 여덟 곡 중 **크기가 작은 것부터 1·3·5·7번째**.
 * 나머지 넷은 나중 업데이트에서 한 곡씩 더한다 — 더할 때는 `res/raw/bgm_*.ogg`와 이 목록과
 * `config/aboutlibraries/libraries/bgm-*.json`(크레딧, CC BY 4.0)을 함께 더한다(`BackgroundMusicContractTest`가 셋을 대조한다).
 * 차례(작은 곡부터)는 스레드가 정했다.
 */
internal val BackgroundMusicPlaylist: List<Int> = listOf(
    R.raw.bgm_zen_oriental_senbazuru,
    R.raw.bgm_piano_gymnopedie_no1,
    R.raw.bgm_zen_oriental_shenyang,
    R.raw.bgm_zen_oriental_eastern_thought,
)

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

/**
 * 앱 배경음악 플레이어(백로그 #244) — 프로세스에 하나, `BackgroundMusicCoordinator`가 만들고 [update]로 입력을 넘긴다.
 * 앱 전체에서 한 플레이어가 틀므로 화면을 옮겨도(대국 화면 포함) 끊기지 않는다. 앱이 뒤로 가면 멈추고 돌아오면 이어 튼다.
 *
 * ## 소리의 몫
 * - 볼륨은 최대의 [Volume] — 원곡 폴더 안내서의 권고(20~30%)에서 스레드가 골랐다. 앱이 내는 소리는 이것뿐이다(착수는 진동만 — [PlayHaptics]).
 * - **오디오 포커스**를 받는다(`USAGE_GAME`): 전화·알림처럼 잠깐 가져가면 멈췄다 이어 틀고(작게 낮추는 것은 시스템이 한다),
 *   다른 앱이 음악을 틀어 아예 가져가면 멈추고 **스스로 다시 틀지 않는다** — 다음에 앱으로 돌아왔을 때 조용하면 그때 튼다.
 *
 * ⚠️ 모든 호출은 메인 스레드에서 온다(코디네이터가 `Dispatchers.Main`에서 모은다) — `MediaPlayer`의 콜백도 같은 스레드로 온다.
 */
internal class BackgroundMusicPlayer(
    context: Context,
    private val playlist: List<Int> = BackgroundMusicPlaylist,
) {
    private val appContext = context.applicationContext
    private val audioManager: AudioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(::onFocusChange, Handler(Looper.getMainLooper()))
        .build()

    private var lastInputs: BackgroundMusicInputs? = null
    private var player: MediaPlayer? = null
    private var isPrepared = false
    private var trackIndex = 0

    /** 지금 소리를 내야 하는가 — 전화로 포커스를 잠깐 잃은 동안에도 참이라 돌아오면 이어 튼다. */
    private var wantsToPlay = false
    private var hasFocus = false

    /** 전면 광고가 뜰 때 틀고 있었는가 — 닫힌 뒤 다시 틀지를 이것으로 정한다. */
    private var wasPlayingBeforeAd = false

    fun update(inputs: BackgroundMusicInputs) {
        val command = backgroundMusicCommandFor(lastInputs, inputs)
        lastInputs = inputs
        when (command) {
            BackgroundMusicCommand.Start -> start(respectOtherAudio = true)
            BackgroundMusicCommand.StartOnRequest -> start(respectOtherAudio = false)
            BackgroundMusicCommand.Pause -> pause()
            BackgroundMusicCommand.PauseForAd -> {
                wasPlayingBeforeAd = wantsToPlay
                pause()
            }
            BackgroundMusicCommand.ResumeAfterAd -> start(respectOtherAudio = !wasPlayingBeforeAd).also { wasPlayingBeforeAd = false }
            BackgroundMusicCommand.Stop -> stop()
        }
    }

    private fun start(respectOtherAudio: Boolean) {
        if (wantsToPlay && player?.isPlaying == true) return
        // 우리 플레이어는 여기서 멈춰 있으므로, 나오고 있는 음악은 다른 앱의 것이다.
        if (respectOtherAudio && audioManager.isMusicActive) return
        if (!hasFocus) {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
            hasFocus = true
        }
        wantsToPlay = true
        val current = player
        when {
            current == null -> prepare()
            isPrepared -> current.start()
            // 준비 중이면 준비가 끝나는 대로 튼다(onPrepared).
        }
    }

    private fun pause() {
        wantsToPlay = false
        if (isPrepared) player?.takeIf { it.isPlaying }?.pause()
        abandonFocus()
    }

    private fun stop() {
        wantsToPlay = false
        releasePlayer()
        abandonFocus()
    }

    private fun prepare() {
        val next = MediaPlayer()
        player = next
        isPrepared = false
        next.setAudioAttributes(attributes)
        next.setOnPreparedListener { prepared ->
            if (player !== prepared) return@setOnPreparedListener
            isPrepared = true
            if (wantsToPlay && hasFocus) prepared.start()
        }
        next.setOnCompletionListener { finished ->
            if (player !== finished) return@setOnCompletionListener
            releasePlayer()
            trackIndex = nextBackgroundMusicTrack(trackIndex, playlist.size)
            if (wantsToPlay) prepare()
        }
        // 곡이 깨졌으면 조용히 멈춘다 — 다음에 앱으로 돌아올 때 다시 시도한다.
        next.setOnErrorListener { failed, what, extra ->
            if (player === failed) {
                Log.w(Tag, "track $trackIndex failed: what=$what extra=$extra")
                stop()
            }
            true
        }
        runCatching {
            val descriptor = appContext.resources.openRawResourceFd(playlist[trackIndex])
            try {
                next.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            } finally {
                descriptor.close()
            }
            next.setVolume(Volume, Volume)
            next.prepareAsync()
        }.onFailure { error ->
            Log.w(Tag, "track $trackIndex could not be opened", error)
            stop()
        }
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> if (wantsToPlay && isPrepared) player?.start()
            // 전화·알림 — 끝나면 AUDIOFOCUS_GAIN으로 이어 튼다.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (isPrepared) player?.takeIf { it.isPlaying }?.pause()
            // 다른 앱이 음악을 틀었다 — 그 음악을 존중해 멈추고 스스로 다시 틀지 않는다.
            AudioManager.AUDIOFOCUS_LOSS -> pause()
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        isPrepared = false
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private companion object {
        const val Tag = "BackgroundMusic"
        const val Volume = 0.25f
    }
}
