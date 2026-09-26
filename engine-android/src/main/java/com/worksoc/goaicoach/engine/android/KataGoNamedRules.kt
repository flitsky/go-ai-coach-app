package com.worksoc.goaicoach.engine.android

import com.worksoc.goaicoach.shared.domain.HandicapBonusRule

/**
 * KataGo 이름 룰(`kata-set-rules chinese`, 쿼리의 `"rules": "japanese"`)이 **이미 품은** 접바둑 보정 방식
 * (refactor backlog #106). chinese는 `"N"`, japanese는 `"0"` — `kata-get-rules`가 앱의 로컬 GTP 엔진에서
 * 내는 값과 같다(#65 실측).
 *
 * 보정 방식은 앱의 `Ruleset.handicapBonusRule` 한 곳이 정한다. 엔진은 이름 룰만 받으면 그 룰의 기본값을
 * 쓰므로, 명령·쿼리는 **기본값과 다를 때만** 덮어쓰기를 싣는다([handicapBonusOverride]) — GTP는
 * `kata-set-rule whiteHandicapBonus`, JSON 분석 쿼리는 `rules`보다 우선하는 최상위 `whiteHandicapBonus`.
 * 지금 룰셋은 전부 기본값과 같아 명령·쿼리가 예전과 바이트 단위로 같다 — `KataGoNamedRulesTest`가 지킨다.
 *
 * ⚠️ 이름 룰의 다른 값(패·자살·friendlyPassOk 등)을 여기 옮겨 적지 마라. 문서 표와 소스가 다른 값이 있다
 * (v1.16.4의 japanese `friendlyPassOk`는 GTP_Extensions.md 표에선 true, `rules.cpp`에선 false). 덮어쓸 것은
 * 보정 방식 하나뿐이고 KataGo가 그것만 따로 받는 자리를 둘 다 갖고 있다.
 */
internal object KataGoNamedRules {
    private val defaultHandicapBonusByName: Map<String, HandicapBonusRule> = mapOf(
        "chinese" to HandicapBonusRule.N,
        "japanese" to HandicapBonusRule.Zero,
    )

    /** 이름 룰 [katagoName]이 기본으로 쓰는 보정 방식. 표에 없는 이름이면 던진다 — 새 룰셋은 여기에 먼저 적는다. */
    fun defaultHandicapBonus(katagoName: String): HandicapBonusRule =
        requireNotNull(defaultHandicapBonusByName[katagoName]) {
            "KataGo 이름 룰 '$katagoName'의 기본 접바둑 보정이 KataGoNamedRules 표에 없다."
        }

    /** [rule]이 이름 룰 기본값과 다르면 그 값, 같으면 `null`(덮어쓸 것이 없다). */
    fun handicapBonusOverride(katagoName: String, rule: HandicapBonusRule): HandicapBonusRule? =
        rule.takeIf { it != defaultHandicapBonus(katagoName) }
}

/** KataGo `whiteHandicapBonus` 값 표기 — `kata-set-rule`과 분석 쿼리가 같은 글자를 쓴다. */
internal val HandicapBonusRule.katagoValue: String
    get() = when (this) {
        HandicapBonusRule.N -> "N"
        HandicapBonusRule.NMinusOne -> "N-1"
        HandicapBonusRule.Zero -> "0"
    }
