package com.worksoc.goaicoach.shared.policy

/**
 * 5계층 — **KGS 급수 한 칸**(백로그 #217). 사람 모델(KataGo Human SL)의 공식 프로필이 가진 29칸이다:
 * 20급(가장 약함) … 1급, 1단 … 9단(가장 셈).
 *
 * [step]은 약한 쪽부터 센 숫자다 — 1 = 20급, 20 = 1급, 21 = 1단, 29 = 9단. 급과 단은 숫자의 방향이 반대라
 * (급은 작을수록, 단은 클수록 세다) 그대로 더하고 빼면 1급 다음이 0급이 된다. 그래서 세기의 덧셈·비교는 [step]으로 한다.
 *
 * ⚠️ **공식 KGS 프로필 값이다 — 공인 기력이 아니다.** 프로필은 "KGS에서 그 급수인 사람이 두는 수"를 흉내 내도록 학습됐을 뿐,
 * 그 급수의 사람과 똑같이 이긴다는 보증이 아니다(사용자 2026-10-04: 공식 값을 믿고 쓰되 화면에 밝힌다).
 * 단 구간은 정책만으로는 이름값보다 약할 수 있다(리서치: 5단 프로필 ≈ KGS 1단) — 실험실에서 재고 보강한다.
 */
data class KgsRank(val step: Int) : Comparable<KgsRank> {
    init {
        require(step in WeakestStep..StrongestStep) { "KGS rank step must be $WeakestStep..$StrongestStep, was $step" }
    }

    val isDan: Boolean get() = step > KyuSteps

    /** 급수·단수의 숫자 — 5급이면 5, 3단이면 3. */
    val number: Int get() = if (isDan) step - KyuSteps else KyuSteps + 1 - step

    /** 사람 모델의 프로필 이름 — `rank_5k`, `rank_3d`. */
    val profile: String get() = "rank_$number${if (isDan) "d" else "k"}"

    /** [steps]칸 더 센 급수 — 9단을 넘지 않는다. */
    fun strongerBy(steps: Int): KgsRank = KgsRank((step + steps).coerceIn(WeakestStep, StrongestStep))

    /** [steps]칸 더 약한 급수 — 20급 아래로 내려가지 않는다. */
    fun weakerBy(steps: Int): KgsRank = strongerBy(-steps)

    override fun compareTo(other: KgsRank): Int = step.compareTo(other.step)

    companion object {
        const val WeakestStep: Int = 1
        const val StrongestStep: Int = 29

        /** 급 구간의 칸 수(20급~1급). */
        private const val KyuSteps: Int = 20

        val Weakest: KgsRank = KgsRank(WeakestStep)
        val Strongest: KgsRank = KgsRank(StrongestStep)

        fun kyu(number: Int): KgsRank = KgsRank(KyuSteps + 1 - number)

        fun dan(number: Int): KgsRank = KgsRank(KyuSteps + number)

        /** 범위를 벗어난 [step]을 끝 칸으로 당긴다 — 저장된 값을 읽을 때 쓴다(깨진 값으로 죽지 않는다). */
        fun ofStepCoerced(step: Int): KgsRank = KgsRank(step.coerceIn(WeakestStep, StrongestStep))

        val all: List<KgsRank> = (WeakestStep..StrongestStep).map(::KgsRank)
    }
}

/**
 * 이 단계가 **급수를 직접 고른 상대**([PlayLevelGroup.CustomRank])라면 그 급수, 아니면 `null`.
 * 그 그룹의 단계 번호가 곧 [KgsRank.step]이다.
 */
fun PlayLevelSetting.customRank(): KgsRank? =
    if (group == PlayLevelGroup.CustomRank) KgsRank.ofStepCoerced(safeLevel) else null

/** 이 급수를 직접 고른 상대의 단계 설정. */
fun KgsRank.toPlayLevelSetting(): PlayLevelSetting =
    PlayLevelSetting(group = PlayLevelGroup.CustomRank, level = step)
