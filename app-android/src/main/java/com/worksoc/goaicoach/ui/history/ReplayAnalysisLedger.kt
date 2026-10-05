package com.worksoc.goaicoach.ui.history

/** 다시보기의 분석 버튼(형세 보기·추천 수)을 한 번 눌렀을 때 일어난 일. */
internal enum class ReplayAnalysisTap {
    /** 켜져 있던 표시를 껐다. 값을 받지 않는다. */
    TurnedOff,

    /** 값을 더 치르지 않고 켰다 — 차감 없는 접근(구독·광고 1시간)이거나 이미 값을 치른 국면이다. */
    TurnedOn,

    /** 1회권을 **걸고** 켰다. 차감은 결과가 나온 뒤에 한다([ReplayFeatureLedger.onTicketCharged]). */
    AwaitingTicket,

    /** 열 길이 없다 — 업셀(광고 1시간·구독)로 보낸다. 원장은 그대로다. */
    NeedsUpsell,
}

/**
 * 다시보기 한 번(화면이 열려 있는 동안)의 분석 기능 **하나**의 원장(backlog #218). 형세 보기와 추천 수가 하나씩 든다.
 *
 * ## 두 가지 접근이 한 버튼에 산다
 * - **차감 없는 접근**(구독·광고 1시간): 켜 두면 수순을 옮겨도 **켜진 채 따라간다**(U-60). [isOn] 하나면 된다.
 * - **1회권**: 한 장이 **국면 하나**를 연다(U-61) — 대국 화면의 규칙과 같다. 값을 치른 국면은 [paidMoves]에 남아,
 *   다시보기를 닫기 전까지는 다시 눌러도 무료다. 치르지 않은 수순으로 옮기면 표시는 사라지고, 치른 수순으로 돌아오면
 *   다시 보인다 — [isOn]은 *"볼 자격이 있는 자리에서는 보여 달라"* 는 뜻으로 남는다.
 *
 * ## ⚠️ 차감은 결과가 나온 뒤다
 * 엔진이 바쁘면 분석은 기다리지 않고 포기한다(`EngineOperationBusy`). 누르는 순간 차감하면 **아무것도 못 보고 한 장이
 * 나간다.** 그래서 누를 때는 걸어 두기만 하고([awaitingTicketAt]), 결과가 손에 들어온 뒤에 차감한다. 그 사이에 수순을
 * 옮기면 거둔다([onMoved]) — 값을 치르지 않았다.
 *
 * ## ⚠️ 대국 화면의 원장(`ConsumableUiState.markOneShot`·`paidAtMove`)을 쓰지 않는다
 * 그 원장은 **대국의 수순 번호**로 세고 셸 전역이다. 다시보기의 수순 번호를 거기 적으면, 돌아간 대국의 같은 수순에서
 * 표시가 켜지거나 값을 안 내고 통과한다. 이 원장은 화면 안에만 있고 저장하지 않는다.
 *
 * ⚠️ 판단을 컴포저블에서 뺀 이유는 `OneShotLedger`와 같다(#44) — *"켜기 → 옮기기 → 돌아오기 → 끄기 → 켜기"* 같은
 * **순서**에서만 드러나는 결함은 JVM 단위 테스트로 순서를 그대로 적어야 잡힌다(`ReplayAnalysisLedgerTest`).
 */
internal data class ReplayFeatureLedger(
    /** 사용자가 켜 두었다. 지금 이 수순에서 실제로 보이는지는 [isShownAt]이 말한다. */
    val isOn: Boolean = false,
    /** 1회권을 낸 수순들. */
    val paidMoves: Set<Int> = emptySet(),
    /** 1회권을 걸어 두고 결과를 기다리는 수순. 없으면 `null`. */
    val awaitingTicketAt: Int? = null,
) {
    /** 이 수순의 결과를 볼 자격이 있는가. [unlimited]는 차감 없는 접근(구독·광고 1시간)이다. */
    fun isEntitledAt(moveNumber: Int, unlimited: Boolean): Boolean = unlimited || moveNumber in paidMoves

    /** 이 수순에서 결과를 **판에 보여도 되는가** — 켜 두었고 자격이 있다. 걸어 둔 1회권은 아직 자격이 아니다. */
    fun isShownAt(moveNumber: Int, unlimited: Boolean): Boolean = isOn && isEntitledAt(moveNumber, unlimited)

    /** 이 수순의 결과를 **엔진에 물어야 하는가** — 보여도 되거나, 1회권을 걸고 기다리는 중이다. 버튼의 켜짐도 이것이다. */
    fun wantsResultAt(moveNumber: Int, unlimited: Boolean): Boolean =
        isOn && (isEntitledAt(moveNumber, unlimited) || awaitingTicketAt == moveNumber)

    /**
     * 버튼을 눌렀다. [hasTicket]은 이 기능의 1회권 재고가 남아 있는가다.
     *
     * 켜져 있는 것을 누르면 **끈다** — 자격을 따지기 전에 본다. 끄는 데 값을 받으면 "1회"가 반 번이 된다(#44).
     */
    fun tap(moveNumber: Int, unlimited: Boolean, hasTicket: Boolean): Pair<ReplayFeatureLedger, ReplayAnalysisTap> =
        when {
            wantsResultAt(moveNumber, unlimited) ->
                copy(isOn = false, awaitingTicketAt = null) to ReplayAnalysisTap.TurnedOff
            isEntitledAt(moveNumber, unlimited) ->
                copy(isOn = true, awaitingTicketAt = null) to ReplayAnalysisTap.TurnedOn
            hasTicket ->
                copy(isOn = true, awaitingTicketAt = moveNumber) to ReplayAnalysisTap.AwaitingTicket
            else -> this to ReplayAnalysisTap.NeedsUpsell
        }

    /** 수순을 옮겼다 — 다른 수순에 걸어 둔 1회권은 거둔다. 값을 치른 기록([paidMoves])과 [isOn]은 그대로다. */
    fun onMoved(moveNumber: Int): ReplayFeatureLedger =
        if (awaitingTicketAt != null && awaitingTicketAt != moveNumber) copy(awaitingTicketAt = null) else this

    /** 걸어 둔 1회권을 실제로 차감했다 — 이 수순은 이제 값을 치른 국면이다. */
    fun onTicketCharged(moveNumber: Int): ReplayFeatureLedger =
        copy(paidMoves = paidMoves + moveNumber, awaitingTicketAt = null)

    /** 걸어 둔 1회권을 차감하지 않고 거둔다 — 결과를 못 받았거나, 그 사이 차감 없는 접근이 열렸거나, 재고가 사라졌다. */
    fun onTicketReleased(): ReplayFeatureLedger = copy(awaitingTicketAt = null)
}
