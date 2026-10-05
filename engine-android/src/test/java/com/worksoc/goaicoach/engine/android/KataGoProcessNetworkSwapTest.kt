package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.engine.android.FakeEngineProcessRuntime.Kind
import com.worksoc.goaicoach.engine.android.FakeEngineProcessRuntime.Reply
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.enginecontract.AnalysisLimit
import com.worksoc.goaicoach.shared.enginecontract.EngineNetwork
import com.worksoc.goaicoach.shared.enginecontract.EngineProfile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 신경망은 한 번에 하나만 올린다(백로그 #215) — [KataGoProcessEngineAdapter.useNetwork]가 프로세스를 내리고 다른 모델로
 * 다시 띄우는 것, 그리고 사람 모델이 올라가 있는 동안 프로필을 어떻게 거는지를 가짜 프로세스로 고정한다.
 *
 * 신경망 하나가 폰에서 메모리 약 500MB다(실험실 E5). 둘이 같이 올라가 있는 순간이 생기면 이 구조의 뜻이 사라진다 —
 * 그래서 「물러나는 프로세스가 내려갔는가」와 「주 모델을 쥔 analysis 프로세스가 남아 있지 않은가」를 따로 본다.
 */
class KataGoProcessNetworkSwapTest {
    private val runtime = FakeEngineProcessRuntime().apply {
        humanNetworkAvailable = true
        responder = { _, line -> if (line == "kata-raw-nn 0") Reply.Now(RawNnReply) else null }
    }
    private val adapter = KataGoProcessEngineAdapter(runtime)

    @AfterTest
    fun tearDown() {
        runtime.releaseAll()
    }

    /** 갈아 올리면 떠 있던 프로세스를 곧바로 내리고(SIGKILL), 다음 명령이 다른 모델로 새로 띄운다. */
    @Test
    fun swappingRetiresTheRunningProcessAndTheNextCommandStartsTheOtherNetwork() = runBlocking {
        adapter.configure(EngineProfile())

        val changed = adapter.useNetwork(EngineNetwork.Human)
        adapter.newGame(BoardSize.Nine, com.worksoc.goaicoach.shared.domain.Ruleset.Japanese)

        assertTrue(changed, "the network changed")
        assertEquals(listOf("KILL"), runtime.gtp(1).signals, "the main-network process must be gone before the human one loads")
        assertEquals(listOf(EngineNetwork.Main, EngineNetwork.Human), runtime.gtpNetworks.toList())
        assertEquals(2, runtime.processes(Kind.Gtp).size)
    }

    /** 이미 그 망이면 아무것도 하지 않는다 — 수마다 불러도 프로세스가 다시 뜨지 않는다. */
    @Test
    fun askingForTheLoadedNetworkDoesNothing() = runBlocking {
        adapter.useNetwork(EngineNetwork.Human)
        adapter.configure(EngineProfile())

        val changed = adapter.useNetwork(EngineNetwork.Human)
        adapter.configure(EngineProfile())

        assertFalse(changed)
        assertEquals(1, runtime.processes(Kind.Gtp).size)
        assertTrue(runtime.gtp(1).signals.isEmpty())
    }

    /** 새 프로세스의 판은 비어 있다 — 어댑터는 판을 스스로 다시 두지 않는다(재동기화는 호출자 몫, `forceReset` 뒤와 같다). */
    @Test
    fun theNewProcessGetsNothingUntilTheCallerResyncs() = runBlocking {
        adapter.newGame(BoardSize.Nine, com.worksoc.goaicoach.shared.domain.Ruleset.Japanese)

        adapter.useNetwork(EngineNetwork.Human)

        assertEquals(1, runtime.processes(Kind.Gtp).size, "swapping alone must not start the next process")
    }

    /**
     * 갈아 올린 프로세스는 **두던 판의 크기로** 뜬다 — 19줄로 떠서 13줄로 바꾸면 갈아 올릴 때마다 약 1.2초를 더 쓴다(S23 실측).
     * 판을 아직 모르는 첫 기동은 예전처럼 크기 없이 뜨고, 미리 알려 주면([KataGoProcessEngineAdapter.expectBoardSize]) 그 크기로 뜬다.
     */
    @Test
    fun aProcessStartsAtTheBoardSizeOfTheGame() = runBlocking {
        adapter.configure(EngineProfile())
        adapter.newGame(BoardSize.Thirteen, com.worksoc.goaicoach.shared.domain.Ruleset.Japanese)
        adapter.useNetwork(EngineNetwork.Human)
        adapter.configure(EngineProfile())

        assertEquals(listOf(UnknownBoardSize, 13), runtime.gtpBoardSizes.toList())

        val hinted = FakeEngineProcessRuntime()
        val fresh = KataGoProcessEngineAdapter(hinted)
        fresh.expectBoardSize(BoardSize.Nine)
        fresh.configure(EngineProfile())
        assertEquals(listOf(9), hinted.gtpBoardSizes.toList())
        hinted.releaseAll()
    }

    /** analysis 프로세스는 주 모델을 쥐고 있다 — 사람 모델로 갈 때 같이 내린다. 남겨 두면 신경망 둘이 같이 올라가 있다. */
    @Test
    fun swappingToTheHumanNetworkAlsoRetiresTheAnalysisProcess() = runBlocking {
        adapter.analyze(JsonPathLimit)
        assertEquals(1, runtime.processes(Kind.Analysis).size)

        adapter.useNetwork(EngineNetwork.Human)

        assertEquals(listOf("KILL"), runtime.analysis(1).signals)
    }

    /** 사람 모델이 올라가 있는 동안은 JSON 분석이 필요한 한도여도 analysis 프로세스를 띄우지 않는다 — GTP 탐색으로 답한다. */
    @Test
    fun noAnalysisProcessStartsWhileTheHumanNetworkIsLoaded() = runBlocking {
        adapter.useNetwork(EngineNetwork.Human)

        adapter.analyze(JsonPathLimit)

        assertTrue(runtime.processes(Kind.Analysis).isEmpty(), "the analysis process would load the main network beside the human one")
        assertTrue(runtime.gtp(1).received.any { it.startsWith("kata-search_analyze") })
    }

    /** 사람 정책은 그 프로필을 걸고 평가 1회 — 둘 수 없는 자리(NAN)는 빠지고 통과 확률이 따로 온다. */
    @Test
    fun humanPolicySetsTheProfileThenReadsOneEvaluation() = runBlocking {
        adapter.useNetwork(EngineNetwork.Human)

        val policy = adapter.humanPolicy("rank_15k")

        assertEquals(listOf("kata-set-param humanSLProfile rank_15k", "kata-raw-nn 0"), runtime.gtp(1).received)
        assertEquals("rank_15k", policy.profile)
        assertEquals(80, policy.moves.size, "one point is occupied (NAN)")
        assertNull(policy.moves[BoardCoordinate(0, 0)])
        assertEquals(0.5, policy.moves.getValue(BoardCoordinate(0, 1)))
        assertEquals(0.002, policy.passProbability)
    }

    /**
     * 형세는 **가장 센 프로필**로 본다 — 급수 프로필이 걸린 채로 재면 그 급수끼리의 예상 결과가 나온다.
     * 같은 프로필을 잇달아 물으면 명령을 다시 보내지 않는다. 돌려주는 값에는 사람 모델의 임시 값이라는 표시가 붙는다.
     */
    @Test
    fun scoreEstimatesSwitchBackToTheJudgeProfileAndAreMarkedAsHuman() = runBlocking {
        adapter.useNetwork(EngineNetwork.Human)
        adapter.humanPolicy("rank_15k")

        val first = adapter.estimateScore(AnalysisLimit())
        adapter.estimateScore(AnalysisLimit())
        adapter.humanPolicy("rank_15k")
        adapter.humanPolicy("rank_15k")

        assertEquals(
            listOf(
                "kata-set-param humanSLProfile rank_15k",
                "kata-raw-nn 0",
                "kata-set-param humanSLProfile rank_9d",
                "kata-raw-nn 0",
                "kata-raw-nn 0",
                "kata-set-param humanSLProfile rank_15k",
                "kata-raw-nn 0",
                "kata-raw-nn 0",
            ),
            runtime.gtp(1).received,
        )
        assertEquals(EngineNetwork.Human, first.network)
        assertEquals(1.5, first.whiteScoreLead)
    }

    /** 갓 뜬 사람 모델 프로세스는 이미 가장 센 프로필이다(띄울 때 준다) — 첫 형세 추정에 프로필 명령이 붙지 않는다. */
    @Test
    fun aFreshHumanProcessAlreadyHasTheJudgeProfile() = runBlocking {
        adapter.useNetwork(EngineNetwork.Human)
        adapter.humanPolicy("rank_15k")
        adapter.forceReset()

        adapter.estimateScore(AnalysisLimit())

        assertEquals(listOf("kata-raw-nn 0"), runtime.gtp(2).received, "the old process's profile must not be assumed on the new one")
    }

    /** 주 모델의 형세 추정은 지금과 같다 — 프로필 명령이 없고 표시는 주 모델이다. */
    @Test
    fun mainNetworkEstimatesAreUnchanged() = runBlocking {
        val estimate = adapter.estimateScore(AnalysisLimit())

        assertEquals(listOf("kata-raw-nn 0"), runtime.gtp(1).received)
        assertEquals(EngineNetwork.Main, estimate.network)
    }

    /** 사람 모델이 안 올라가 있는데 사람 정책을 물으면 크게 실패한다 — 주 모델의 정책을 사람의 것처럼 돌려주지 않는다. */
    @Test
    fun humanPolicyRefusesWhenTheMainNetworkIsLoaded() {
        assertFailsWith<IllegalStateException> { runBlocking { adapter.humanPolicy("rank_15k") } }
        assertTrue(runtime.processes(Kind.Gtp).isEmpty())
    }

    /** 사람 모델 파일이 없는 기기 — 못 올린다고 답하고, 갈아 올리려 들면 던진다(떠 있던 주 모델 프로세스는 그대로). */
    @Test
    fun aDeviceWithoutTheHumanModelCannotSwap() = runBlocking {
        runtime.humanNetworkAvailable = false
        adapter.configure(EngineProfile())

        assertFalse(adapter.supportsHumanNetwork)
        assertFailsWith<IllegalStateException> { adapter.useNetwork(EngineNetwork.Human) }
        adapter.configure(EngineProfile())
        assertEquals(1, runtime.processes(Kind.Gtp).size)
        assertTrue(runtime.gtp(1).signals.isEmpty())
    }

    /** 프로필 이름에 줄바꿈·공백이 섞이면 보내지 않는다 — GTP에서는 명령이 둘이 된다. */
    @Test
    fun aMalformedProfileNameIsNeverSent() = runBlocking {
        adapter.useNetwork(EngineNetwork.Human)

        assertFailsWith<IllegalArgumentException> { adapter.humanPolicy("rank_15k\nquit") }
        assertNotNull(runtime.gtp(1))
        assertTrue(runtime.gtp(1).received.isEmpty())
    }

    private companion object {
        /** JSON 경로로 가는 한도 — 정책을 달라고 하면 어댑터가 analysis 프로세스를 쓴다(주 모델이 올라가 있을 때만). */
        val JsonPathLimit = AnalysisLimit(visits = 32, timeMillis = 1_000L, includePolicy = true)

        /** 9줄 `kata-raw-nn 0`의 답 — 왼쪽 위 한 자리는 돌이 있어 NAN, 그 옆이 0.5, 나머지는 작은 값. */
        val RawNnReply: String = buildString {
            append("= symmetry 0\nwhiteWin 0.55\nwhiteLoss 0.45\nwhiteLead 1.5\npolicy\n")
            for (row in 0 until 9) {
                append((0 until 9).joinToString(" ") { column ->
                    when {
                        row == 0 && column == 0 -> "NAN"
                        row == 0 && column == 1 -> "0.500000"
                        else -> "0.006000"
                    }
                })
                append("\n")
            }
            append("policyPass 0.002000\nwhiteOwnership\n")
            repeat(9) { append((0 until 9).joinToString(" ") { "0.100000" }).append("\n") }
            append("\n")
        }
    }
}
