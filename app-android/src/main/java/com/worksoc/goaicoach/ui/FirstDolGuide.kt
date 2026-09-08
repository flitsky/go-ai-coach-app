package com.worksoc.goaicoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.worksoc.goaicoach.application.guide.GuideSetupFacts
import com.worksoc.goaicoach.application.guide.GuideStep
import com.worksoc.goaicoach.application.guide.GuideSurface
import com.worksoc.goaicoach.application.guide.autoPlayStep
import com.worksoc.goaicoach.persistence.GuideProgressStore
import kotlinx.coroutines.delay

/**
 * 첫돌이 가이드의 **라이브 배선** — 훅과 저장 쓰기의 **유일한 소유자**(백로그 #128).
 *
 * ## ⚠️ 화면들은 이것을 한 줄로 부른다
 *
 * `GoCoachApp.kt`는 **한 줄도 건드리지 않는다.** 예산이 훅 42/42로 여유 0이고, 셸에 provider를
 * 꽂는 방식은 설계 심사에서 *"여유가 정확히 0인 자원을 쓰면서 얻는 것이 없다"* 로 기각됐다 —
 * 앵커가 `LocalContext`로 저장소를 직접 열면 같은 일이 셸 비용 0으로 된다.
 * ⚠️ **그 성질을 계약으로 지킨다**: `FirstDolGuideContractTest`가 셸에 이 파일의 이름들이
 * 나타나지 않는지 본다. 나중에 누가 provider로 되돌리면 조용한 회귀가 아니라 빨간 테스트가 된다.
 *
 * ## ⚠️ 판정을 `remember`로 기억하지 않는다 — 설계 심사가 잡은 결함
 *
 * 초안 하나가 `remember { mutableStateOf(autoPlayStep(surface, store.load(), blocked)) }`로 짰는데,
 * 초기화식은 **한 번만** 돈다. 첫 실행 홈의 첫 프레임에는 1일차 출석 팝업이 떠 있어 `blocked=true`
 * → `null`로 굳고, **팝업이 닫혀도 홈은 dispose되지 않으므로 다시 계산되지 않는다.** 그러면 ③이
 * 첫 실행에 **영구히 안 뜬다.** 그래서 판정은 **컴포지션 중에 그냥 부른다.**
 */

/**
 * 출석 보상 팝업이 **지금 화면에 있는가.**
 *
 * ⚠️ **이 값을 지급 경로에서 끄지 말 것.** 팝업은 받을 것이 없으면 아예 뜨지 않는 갈래도 있고
 * (`pending.isEmpty()`), 엔진 안내·초기화 안내가 먼저 뜨면 그 뒤로 밀린다 — 지급에 걸어 두면
 * 그 갈래에서 `true`로 굳어 **③이 영구히 침묵한다.** 그래서 팝업 자신의 **컴포지션 수명**
 * (`DisposableEffect`)에만 묶는다.
 *
 * ⚠️ 프로세스 전역 가변 상태다. `Context`를 들지 않으므로 누수는 없고, 훗날 Robolectric이 들어오면
 * [resetForTest]로 되감을 것.
 */
internal object AttendanceClaimVisibility {
    private var count by mutableIntStateOf(0)

    val isShowing: Boolean get() = count > 0

    @Composable
    fun TrackWhileShown() {
        DisposableEffect(Unit) {
            count += 1
            onDispose { count -= 1 }
        }
    }

    internal fun resetForTest() {
        count = 0
    }
}

/**
 * 이 표면에서 지금 보여줄 단계가 있으면 그린다. 없으면 아무것도 그리지 않는다.
 *
 * @param blocked 이 표면 위에 **다른 다이얼로그가 떠 있는가.** ⚠️ 카드 표현형(④⑤)에서는
 *   **기본값 없이** 넘겨 컴파일러가 게이트를 강제한다 — 가려진 채 그려지면 사용자는 못 봤는데
 *   `seen`으로 기록돼 가이드가 조용히 소진된다.
 *
 * ## 기록 시점: **충분히 보인 뒤에** — 실기에서 값을 치르고 고친 자리다
 *
 * ⚠️ 처음에는 `onDispose`(화면을 떠날 때)에 기록했는데, **실기에서 ③이 아예 뜨지 않았다.**
 * 첫 실행의 순서가 이렇기 때문이다: 홈의 첫 프레임에는 출석 팝업이 아직 계산 중이라 `blocked=false`
 * → 말풍선이 한 프레임 뜨고 → 팝업이 올라와 `blocked=true` → **앵커가 컴포지션을 떠나며
 * `onDispose`가 돌아 "봤음"으로 기록**됐다. 사용자는 아무것도 못 봤는데 사슬이 소진된 것이다.
 * 심사가 경고한 *"가려진 채 소진"* 이 **가려짐이 아니라 차단으로** 들어온 셈이다.
 *
 * 그래서 **[ShownLongEnoughMillis] 동안 실제로 떠 있었을 때만** 기록한다. 차단되면 그 코루틴이
 * 취소되므로 한 프레임 반짝인 것은 기록되지 않는다.
 *
 * ## ⚠️ 기록한 뒤에도 **이 화면에 있는 동안은 계속 보여준다**
 *
 * 기록하면 판정이 곧바로 `null`을 돌려주므로, 그것을 그대로 따르면 **안내가 1.2초 뒤 사용자 눈앞에서
 * 사라진다.** 그래서 한 번 띄운 단계를 [latched]로 붙잡아 둔다 — 화면을 떠나면 `remember`가 사라져
 * 다음 진입에는 뜨지 않는다.
 *
 * ⚠️ 프로세스가 죽으면 기록이 남지 않아 **한 번 더 보일 수 있다.** 반대쪽(안 보여주고 기록되는 것)이
 * 훨씬 나쁘므로 이 대가를 택했다.
 */
private const val ShownLongEnoughMillis = 1_200L

@Composable
internal fun GuideAnchor(
    surface: GuideSurface,
    blocked: Boolean,
    modifier: Modifier = Modifier,
    facts: GuideSetupFacts? = null,
    toolLabels: GuideToolLabels? = null,
) {
    val context = LocalContext.current
    val store = remember(context) { GuideProgressStore(context) }
    // 디스크는 한 번만 읽는다 — 앵커마다 컴포지션에서 `load()`를 부르면 그것이 디스크 읽기가 된다.
    var progress by remember(context) { mutableStateOf(store.load()) }
    var latched by remember(context) { mutableStateOf<GuideStep?>(null) }

    // ⚠️ `remember`로 감싸지 말 것(위 파일 머리말) — `blocked`와 `progress`가 둘 다 변한다.
    val candidate = autoPlayStep(surface, progress, blocked)
    if (candidate != null && latched == null) latched = candidate
    val step = if (blocked) null else latched
    if (step == null) return
    val strings = LocalUiStrings.current

    fun ack() {
        store.markSeen(step)
        latched = null
        progress = store.load()
    }

    fun stopWholeChain() {
        store.dismiss()
        latched = null
        progress = store.load()
    }

    // ⚠️ **기록 시점이 표현형마다 다르다 — 그 비대칭이 의도다.**
    // 카드(④⑤)는 별도 윈도우 팝업에 **덮일 수 있어** 시간으로 기록하면 덮인 채 소진된다.
    // 말풍선·한 줄은 창 안 비모달이라 덮일 수 없으므로 시간이 안전하고, **누를 것이 없어** 시간
    // 말고는 기록할 계기가 없다(경계 밖에 그려져 히트테스트를 못 받는다).
    if (!step.isCard()) {
        LaunchedEffect(step) {
            delay(ShownLongEnoughMillis)
            store.markSeen(step)
            progress = store.load()
        }
    }

    val body = guideBodyFor(strings.language, step, facts, toolLabels)
    when (step) {
        // ①은 판정에 참여하지 않는다(랜딩의 정적 장식) — 여기 올 수 없다.
        GuideStep.Landing -> Unit
        GuideStep.AttendanceClaim -> GuideLine(text = body, modifier = modifier)
        GuideStep.HomeStartMatch -> ZeroSizeOverlay(modifier) { GuideBubble(text = body) }
        GuideStep.MatchSetup -> GuideCard(text = body, onAck = ::ack, onStop = ::stopWholeChain)
        // ⑤ 넷은 **자기 버튼 옆에서** 말하고 그 버튼에 동그라미를 친다(2026-09-09 사용자 지시).
        // `ack()`가 기록하면 판정이 곧바로 **다음 버튼**을 고른다 — 누를 때마다 다음이 뜨는 것이
        // 요구였고, 그것이 `GuideStep` 선언 순서로 이미 표현돼 있다(정책 테스트가 못박는다).
        GuideStep.InGameMagnifier, GuideStep.InGameBoardSize,
        GuideStep.InGameEval, GuideStep.InGameTopMoves,
        -> step.target?.let { target ->
            GuideCoachMark(target = target, text = body, onNext = ::ack, onStop = ::stopWholeChain)
        }
    }
}

/**
 * 카드로 그려지는 단계인가. ⚠️ **`when`을 exhaustive로 유지할 것** — 단계를 더하면서 여기를
 * 빠뜨리면 새 단계가 시간 기준으로 기록돼 **덮인 채 소진**될 수 있다.
 */
private fun GuideStep.isCard(): Boolean = when (this) {
    // ⑤ 코치마크도 **누를 때만** 기록한다 — 사용자가 확인해야 다음으로 넘어가는 구조이므로
    // 시간으로 기록하면 넷이 순식간에 소진된다.
    GuideStep.MatchSetup,
    GuideStep.InGameMagnifier, GuideStep.InGameBoardSize,
    GuideStep.InGameEval, GuideStep.InGameTopMoves,
    -> true
    GuideStep.Landing, GuideStep.AttendanceClaim, GuideStep.HomeStartMatch -> false
}
