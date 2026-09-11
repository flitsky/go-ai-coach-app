package com.worksoc.goaicoach

import android.content.Context
import android.content.Intent
import com.worksoc.goaicoach.application.lifecycle.DeveloperModeResetPolicy
import com.worksoc.goaicoach.persistence.DeveloperModeStore
import com.worksoc.goaicoach.persistence.DeviceIdentityStore
import java.io.File
import kotlin.system.exitProcess

/**
 * 개발자 모드가 켜져 있는 동안 **[DeveloperModeResetPolicy.ResetIntervalHours]시간마다 앱을 최초
 * 설치 상태로 되돌린다**(백로그 #99).
 *
 * ## 왜 있는가
 * 개발자 테스트 1차 섹션은 **release 빌드에도 실린다**(함정 11번). 지금까지 그것을 막는 것은
 * *"우연히 10번 두드릴 일은 없다"* 는 진입 장벽뿐이었다. 이 코디네이터가 그 자리를 메운다 —
 * **알아내도 3시간마다 전부 잃으므로 이득이 남지 않는다.**
 *
 * ## ⚠️ 무엇을 지우고 무엇을 남기는가 (2026-09-05 사용자 확정)
 * 기준은 *"사용자가 만들거나 얻은 것은 전부 지우고, 앱이 자기 APK에서 되만들 수 있는 것은 남긴다"* 다.
 * · **지운다** — `go_ai_coach_`로 시작하는 SharedPreferences **전부**(권한 넷 · 대국 기록 ·
 *   진행 중 대국 · 설정 · 언어 · 온보딩 완료 · 릴리즈 초기화 마커 · **개발자 모드 플래그 자신**).
 * · **남긴다** — [DeviceIdentityStore]. ⚠️ 지우면 **한 기기가 하루 8개의 새 기기로 보여** 기기
 *   기준 지표가 오염된다(#63도 같은 이유로 남긴다).
 * · **남긴다** — `filesDir`의 KataGo 모델(약 100MB)과 진단 로그. 모델은 **AAB에 들어 있어 지워도
 *   다시 풀리므로**(`release` 소스셋이 `src/friend/assets`를 쓴다) 재설치와 결과가 같고, 3시간마다
 *   100MB를 다시 쓰는 것은 시간·저장장치 낭비일 뿐이다.
 *
 * ## ⚠️ 이름 목록을 여기 손으로 적지 않는다
 * `shared_prefs` 디렉터리를 훑어 **접두사로** 고른다. 저장소가 새로 늘어도 **자동으로 포함되고**,
 * 목록이 두 벌로 갈라지지 않는다(함정 6번). 남길 것 하나만 이름으로 가리킨다.
 * · ⚠️ **`go_ai_coach_` 접두사 밖은 건드리지 않는다** — `admob.xml`·`WebViewChromiumPrefs.xml`
 *   같은 SDK 상태까지 지우면 실행 중인 SDK가 흔들린다. 신규 설치라면 없을 파일이지만, 여기서는
 *   **앱이 살아 있는 상태에서** 지우는 것이라 사정이 다르다.
 *
 * ## ⚠️ debug 빌드에서는 돌지 않는다
 * 2026-09-05 사용자 결정 — 개발자 본인의 실기 테스트가 3시간마다 날아가면 안 된다.
 * 호출부([GoAiCoachApplication])가 `BuildConfig.DEBUG`로 막는다.
 *
 * ## ⚠️ 다른 무엇보다 먼저 돌아야 한다
 * [ReleaseResetCoordinator]와 같은 이유다 — 출석 체크인이 먼저 돌면 그날 기록이 붙었다가 곧바로
 * 지워진다. 그래서 `onCreate`의 가장 앞에서 부른다.
 */
internal class DeveloperModeResetCoordinator(
    private val isDeveloperModeEnabled: () -> Boolean,
    private val lastResetUtcMillis: () -> Long?,
    private val wipe: () -> Unit,
) {

    /**
     * 필요하면 초기화하고, **실제로 지웠는지** 돌려준다.
     *
     * ⚠️ 개발자 모드가 꺼져 있으면 아무것도 하지 않는다 — 이 기능은 **켠 사람에게만** 적용된다.
     */
    fun applyIfNeeded(nowUtcMillis: Long): Boolean {
        if (!isDeveloperModeEnabled()) return false
        if (!DeveloperModeResetPolicy.shouldReset(lastResetUtcMillis(), nowUtcMillis)) return false
        wipe()
        return true
    }

    /**
     * ⚠️ **의존을 람다로 받는 이유는 테스트 때문이다 — 그럴 만한 사정이 있다.**
     * 이 코디네이터는 **`BuildConfig.DEBUG`가 false인 빌드에서만 돈다**(사용자 결정). 그런데 그런
     * 빌드는 `isDebuggable = false`라 **`run-as`로 저장소를 들여다볼 수 없고**, Play 이미지
     * 에뮬레이터는 `adb root`도 안 된다. 즉 **실기로는 이 판정을 관찰할 방법이 없다**
     * (2026-09-05에 `playInternal`을 실제로 설치해 확인했다).
     * 그래서 "관찰할 수 없으니 테스트로 덮는다" — 이 시임이 그 값이다.
     */
    companion object {
        operator fun invoke(context: Context): DeveloperModeResetCoordinator {
            val store = DeveloperModeStore(context)
            return DeveloperModeResetCoordinator(
                isDeveloperModeEnabled = store::isEnabled,
                lastResetUtcMillis = store::lastResetUtcMillis,
                wipe = { wipeToFreshInstall(context) },
            )
        }
    }
}

/**
 * 앱 저장소를 **최초 설치 상태**로 되돌린다 — 기기 식별자만 남긴다.
 *
 * ⚠️ **개발자 모드 끄기 버튼(#99 ⓑ)과 주기 초기화(ⓒ)가 이 함수 하나로 수렴한다.** 둘은 같은 일을
 * 하므로 나눠 쓰면 한쪽만 고쳐진다 — 사용자 확정 사항이다(*"끄면 최초 설치 상태로 전환"*).
 */
internal fun wipeToFreshInstall(context: Context) {
    val app = context.applicationContext
    val prefsDir = File(app.dataDir, "shared_prefs")
    prefsDir.listFiles()
        ?.mapNotNull { file -> file.name.removeSuffix(".xml").takeIf { it != file.name } }
        ?.filter { name -> name.startsWith(AppPrefsPrefix) && name != DeviceIdentityStore.PrefsName }
        ?.forEach { name ->
            // ⚠️ 파일을 지우는 것이 아니라 이 API를 쓴다 — 프로세스가 들고 있는 인메모리 사본까지
            // 함께 버려야 한다. 파일만 지우면 살아 있는 인스턴스가 옛 값을 계속 돌려준다.
            app.deleteSharedPreferences(name)
        }
}

/**
 * [wipeToFreshInstall] **+ 프로세스 재시작** — 개발자 테스트 2차의 *'앱 최초 실행 상태로 되돌리기'*
 * 버튼 전용이다(2026-09-09 사용자 요청).
 *
 * ## ⚠️ 지우는 것만으로는 첫 실행이 되지 않는다 — 이 함수가 있는 이유가 그것이다
 * 저장소를 비워도 **화면은 그대로다.** `GoCoachApp`이 `hasSeenOnboarding`을 컴포지션 시작에
 * 한 번 읽고, 목적지(`currentDestination`)는 `remember`라 저장되지 않은 채 살아 있다. 그래서
 * 지우기만 하면 *"지웠다는데 가이드가 안 뜬다"* 가 된다 — 기존 **개발자 모드 끄기(ⓑ)가 실제로
 * 그 상태**이고, 그쪽은 홈으로 돌아가는 것으로 끝난다(그 버튼의 목적은 흔적을 지우는 것이지
 * 첫 실행을 재현하는 것이 아니라 고치지 않았다).
 *
 * ## ⚠️ 액티비티 재생성이 아니라 **프로세스**를 다시 띄운다
 * 액티비티만 새로 만들면 [GoAiCoachApplication]의 `onCreate`가 다시 돌지 않는다 — 릴리즈 초기화와
 * 출석 체크인이 거기 있어서, 빠지면 "최초 실행"이 반쪽이 된다(1일차 보상이 안 붙은 채로 가이드만
 * 뜬다). 진짜 첫 실행과 같은 순서를 얻으려면 **콜드 스타트**여야 한다.
 *
 * ⚠️ **debug 전용 경로에서만 부른다.** `exitProcess`는 사용자 앱이 할 일이 아니다 — 호출부는
 * `BuildConfig.DEBUG`로 감싼 2차 섹션이고, release·playInternal에서는 R8이 통째로 지운다.
 */
internal fun restartToFreshInstall(context: Context) {
    val app = context.applicationContext
    wipeToFreshInstall(app)
    // ⚠️ 런처 인텐트를 **패키지 매니저에서 얻는다** — `MainActivity`를 직접 가리키면 런처
    // 진입점이 바뀌는 날 조용히 어긋난다. `CLEAR_TASK`로 기존 태스크를 걷어내야 재시작 뒤
    // 옛 액티비티가 되살아나지 않는다.
    app.packageManager.getLaunchIntentForPackage(app.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ?.let(app::startActivity)
    // `startActivity`는 AMS가 받아 둔 뒤 돌아오므로, 여기서 프로세스를 끝내도 그 실행은 살아 있다
    // — 시스템이 앱을 새 프로세스로 다시 띄운다. 그것이 곧 콜드 스타트다.
    exitProcess(0)
}

/** 이 앱이 만드는 SharedPreferences의 공통 접두사. 밖의 것(SDK 상태)은 건드리지 않는다. */
private const val AppPrefsPrefix = "go_ai_coach_"
