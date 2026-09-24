package com.worksoc.goaicoach.smoke

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.worksoc.goaicoach.ui.GuideTargetSpots

/**
 * 이 스모크 테스트들이 기대는 **"갓 설치한 앱"** 상태를 실제로 만든다.
 *
 * 계기 테스트 APK는 앱과 **프로세스·저장소를 공유**하므로, 앞선 실행이 남긴 저장값(이어하기·
 * 온보딩 봤음·프리미엄·플레이어 설정)이 그대로 있으면 어느 화면이 먼저 뜨는지가 실행마다 달라진다.
 *
 * ## ⚠️ `shared_prefs`의 **파일만 지우는 것으로는 안 된다** (2026-09-23 실측)
 *
 * 세 테스트가 각자 `prefsDir.listFiles()?.forEach { it.delete() }` 한 줄로 이 일을 했는데,
 * **그것은 디스크만 지운다.** 안드로이드는 `SharedPreferences` 인스턴스를 **프로세스 단위로
 * 캐시**하므로(`ContextImpl`의 이름별 캐시), 같은 프로세스에서 이미 한 번 열린 설정은 **메모리에
 * 그대로 살아 있고** 다음 쓰기에서 파일까지 되살아난다.
 *
 * 그래서 테스트 **하나만 돌리면 초록인데 `make test-device`로 셋을 함께 돌리면 빨간** 상태가
 * 됐다 — `AppLaunchSmokeTest`가 진짜 앱을 띄우며 남긴 설정(판 크기·좌석·봇 캐릭터)을
 * `NewGameBoardTapSmokeTest`가 물려받아, *"흑은 사람이 기본"* 이라는 전제가 조용히 깨졌다.
 * 증상은 **착수가 안 되는 것처럼** 보이지만 원인은 화면에 닿기도 전에 있다.
 *
 * ⚠️ **파일 삭제로 되돌리지 말 것.** 지우기 **전에** 같은 이름으로 열어 `clear().commit()`을
 * 해야 메모리 사본까지 비워진다. 순서가 뒤집히면 아무것도 안 한 것과 같다.
 *
 * ## ⚠️ 프로세스 전역 `object`도 함께 되감는다
 *
 * [GuideTargetSpots]는 첫돌이 코치마크가 **동그라미를 칠 좌표**를 프로세스 전역에 들고 있고
 * 화면을 떠나도 지우지 않는다(그 판단의 사유는 그쪽 KDoc). 앞 테스트가 남긴 좌표가 있으면
 * 코치마크가 **자기 버튼이 자리를 잡기 전에** 떠서, 전면 흡수 층이 **반상 터치를 먹는다**
 * (`GuideCoachMark`의 *"어디를 눌러도 다음으로"*). 그쪽이 이미 `resetForTest()`를 갖고 있다.
 *
 * ⚠️ [com.worksoc.goaicoach.ui.SplashVisibility]는 **일부러 안 건드린다** — 되감으면 테스트마다
 * 스플래시가 다시 재생되고, 그 1초짜리 전면 오버레이는 **터치를 먹는다**(`SplashPlayer`).
 * 남아 있는 "이미 재생됨"은 오버레이를 **없애는** 쪽이라 이 테스트들에 해롭지 않다.
 */
internal fun resetToFreshInstallState() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val prefsDir = context.filesDir.resolveSibling("shared_prefs")
    val files = prefsDir.listFiles().orEmpty()
    files.forEach { file ->
        val name = file.name.removeSuffix(".xml")
        if (name != file.name) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    prefsDir.listFiles()?.forEach { it.delete() }

    GuideTargetSpots.resetForTest()
}
