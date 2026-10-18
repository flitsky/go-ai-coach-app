package com.worksoc.goaicoach.platform

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.platform.BackgroundMusicCommand.Pause
import com.worksoc.goaicoach.platform.BackgroundMusicCommand.PauseForAd
import com.worksoc.goaicoach.platform.BackgroundMusicCommand.ResumeAfterAd
import com.worksoc.goaicoach.platform.BackgroundMusicCommand.Start
import com.worksoc.goaicoach.platform.BackgroundMusicCommand.StartOnRequest
import com.worksoc.goaicoach.platform.BackgroundMusicCommand.Stop
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.backgroundMusicLabelFor
import com.worksoc.goaicoach.ui.l10n.settingsSoundSectionFor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「앱 배경음악」(백로그 #244)의 판정과, 곡 하나가 걸쳐 있는 세 자리가 같은지.
 *
 * 곡을 더할 때(사용자 2026-10-18 — 나중 업데이트에서 한 곡씩) 세 곳을 함께 고친다: `res/raw/bgm_*.ogg`(실을 파일) ·
 * [BackgroundMusicPlaylist](틀 차례) · `config/aboutlibraries/libraries/bgm-*.json`(CC BY 4.0 저작자 표시). 하나라도 빠지면
 * 실렸는데 안 틀거나, 틀면서 크레딧이 없거나, 없는 파일을 가리켜 빌드가 깨진다 — 앞의 둘은 조용하다.
 */
class BackgroundMusicContractTest {

    private fun inputs(isForeground: Boolean, isEnabled: Boolean, isAdShowing: Boolean = false) =
        BackgroundMusicInputs(isForeground, isEnabled, isAdShowing)

    @Test
    fun theCommandFollowsForegroundAndTheSetting() {
        // 앱이 처음 뜰 때 — 아직 화면 앞이 아니면 멈춘 채로, 앞에 오면 튼다(다른 앱 음악은 존중).
        assertEquals(Pause, backgroundMusicCommandFor(null, inputs(isForeground = false, isEnabled = true)))
        assertEquals(Start, backgroundMusicCommandFor(inputs(false, true), inputs(true, true)))
        // 뒤로 갔다 돌아오면 다시 「존중하며 튼다」 — 그사이 다른 앱이 음악을 틀었으면 틀지 않는다.
        assertEquals(Pause, backgroundMusicCommandFor(inputs(true, true), inputs(false, true)))
        assertEquals(Start, backgroundMusicCommandFor(inputs(false, true), inputs(true, true)))
        // 설정에서 끄면 놓고, 방금 켜면 사용자가 고른 것이라 다른 앱 음악이 있어도 튼다.
        assertEquals(Stop, backgroundMusicCommandFor(inputs(true, true), inputs(true, false)))
        assertEquals(StartOnRequest, backgroundMusicCommandFor(inputs(true, false), inputs(true, true)))
        // 꺼져 있으면 앞에 와도 틀지 않는다.
        assertEquals(Stop, backgroundMusicCommandFor(inputs(false, false), inputs(true, false)))
        // 꺼진 채 뒤에 있다가 켜진 채 앞으로 — 켠 것은 이 앱 안에서였을 수 없으니 존중하며 튼다.
        assertEquals(Start, backgroundMusicCommandFor(inputs(false, false), inputs(true, true)))
        // 전면 광고 — 떠 있는 동안 멈추고, 닫히면 「광고 뒤 다시」(광고 전에 틀고 있었는지는 플레이어가 기억한다).
        assertEquals(PauseForAd, backgroundMusicCommandFor(inputs(true, true), inputs(true, true, isAdShowing = true)))
        assertEquals(ResumeAfterAd, backgroundMusicCommandFor(inputs(true, true, isAdShowing = true), inputs(true, true)))
        // 광고 중에 홈으로 가면 뒤로 간 것이 먼저다. 꺼져 있으면 광고와 상관없이 놓는다.
        assertEquals(Pause, backgroundMusicCommandFor(inputs(true, true, isAdShowing = true), inputs(false, true, isAdShowing = true)))
        assertEquals(Stop, backgroundMusicCommandFor(inputs(true, false), inputs(true, false, isAdShowing = true)))
    }

    @Test
    fun thePlaylistWrapsToTheFirstTrack() {
        assertEquals(1, nextBackgroundMusicTrack(0, 4))
        assertEquals(0, nextBackgroundMusicTrack(3, 4))
        assertEquals(0, nextBackgroundMusicTrack(0, 1))
    }

    @Test
    fun everyShippedTrackIsPlayedAndCredited() {
        val shipped = RepoPaths.appAndroidMain("res/raw").listFiles().orEmpty()
            .map { it.name }.filter { it.startsWith("bgm_") }
            .map { it.removePrefix("bgm_").removeSuffix(".ogg") }.sorted()
        val played = Regex("""R\.raw\.bgm_(\w+)""").findAll(RepoPaths.platformFile("BackgroundMusicPlayer.kt").readContractSource())
            .map { it.groupValues[1] }.toList()
        val credited = RepoPaths.root.resolve("app-android/config/aboutlibraries/libraries").listFiles().orEmpty()
            .filter { it.name.startsWith("bgm-") }
            .map { JSONObject(it.readText()).getString("uniqueId").substringAfter("bundled.music:") }.sorted()

        assertEquals("사용자가 고른 곡은 넷이다(2026-10-18)", 4, shipped.size)
        assertEquals("res/raw에 실은 곡과 플레이어의 목록이 다르다", shipped, played.sorted())
        assertEquals("플레이어 목록에 같은 곡이 두 번 있다", played.distinct(), played)
        assertEquals("res/raw에 실은 곡과 크레딧(bgm-*.json)이 다르다 — CC BY 4.0은 저작자 표시가 조건이다", shipped, credited)
    }

    @Test
    fun theSoundSectionIsFilledInEveryLanguage() {
        UiLanguage.entries.forEach { language ->
            assertTrue("$language 「소리」 절 제목이 비었다", settingsSoundSectionFor(language).isNotBlank())
            assertTrue("$language 「앱 배경음악」 문구가 비었다", backgroundMusicLabelFor(language).isNotBlank())
        }
        assertEquals("소리", settingsSoundSectionFor(UiLanguage.Korean))
        assertEquals("앱 배경음악", backgroundMusicLabelFor(UiLanguage.Korean))
    }

    /**
     * 코디네이터를 만들지 않으면 설정은 켜져 있는데 아무 소리도 안 난다 — 컴파일도 테스트도 조용하다.
     * 「앞에 있는가」는 수명 주기의 지금 상태(`currentStateFlow`)에서 읽는다 — 다시 주지 않는 이벤트로 바꾸면 늦은 구독이
     * 첫 `onStart`를 놓쳐 켜도 틀지 않을 수 있다(2026-10-18 에뮬레이터, 코디네이터 KDoc).
     */
    @Test
    fun theApplicationStartsTheCoordinatorThatReadsTheLifecycleState() {
        val application = RepoPaths.compositionFile("GoAiCoachApplication.kt").readContractSource()
        assertTrue("Application이 배경음악 코디네이터를 시작하지 않는다", application.contains("backgroundMusic.start()"))
        val coordinator = RepoPaths.compositionFile("BackgroundMusicCoordinator.kt").readContractSource()
        assertTrue("코디네이터가 수명 주기의 지금 상태를 읽지 않는다", coordinator.contains("ProcessLifecycleOwner.get().lifecycle.currentStateFlow"))
    }

    /** 보상형 광고를 띄우는 단일 깔때기가 음악을 멈춘다 — 빼면 광고를 본 뒤 음악이 앱을 나갔다 올 때까지 안 나온다(2026-10-18 에뮬레이터). */
    @Test
    fun theRewardedAdFunnelPausesTheMusic() {
        val glue = RepoPaths.compositionFile("PremiumPurchaseGlue.kt").readContractSource()
        val clientCalls = Regex("""AndroidRewardedInterstitialAdClient\(""").findAll(glue).count()
        val wrapped = Regex("""whileFullScreenAdShows \{\s*AndroidRewardedInterstitialAdClient\(""").containsMatchIn(glue)
        assertTrue("showRewardedAdOnce가 광고를 whileFullScreenAdShows 안에서 띄우지 않는다", wrapped)
        assertEquals("광고 클라이언트를 만드는 곳이 하나가 아니다 — 새 곳도 음악을 멈춰야 한다", 1, clientCalls)
    }
}
