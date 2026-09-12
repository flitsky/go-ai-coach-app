package com.worksoc.goaicoach.ui

import kotlin.math.min

/**
 * 대국 화면을 **폰 배치**로 그릴지 **넓은 배치**로 그릴지(백로그 #141, 2026-09-12 사용자 확정).
 *
 * ## 기준 — 기기 종류가 아니라 "폰 배치로 판이 가로폭을 채우는가"
 * 폰 배치는 판 위아래로 조작부가 쌓여 판 아닌 부분이 세로 [PhoneLayoutNonBoardHeightDp]를 먹는다.
 * 남는 세로가 가로폭의 [PhoneLayoutMinBoardFillRatio]에 못 미치면(= 판이 가로폭을 못 채우고
 * 좌우가 빈다) 넓은 배치로 간다. 그래서 보통 폰·세로로 긴 태블릿은 폰 배치 그대로이고,
 * 폴드 펼침(세로·가로)·가로 태블릿만 넓은 배치가 된다.
 * ⚠️ 폭이 [WideLayoutMinWidthDp] 밑이면 언제나 폰 배치다 — 넓은 배치의 한 줄짜리 위 줄과 네 칸
 *   아래 줄은 좁은 화면에 들어가지 않는다. 짧은 폰에서 조금 넘치는 것은 #139의 판 맞춤이 받는다.
 *
 * ## 넓은 배치의 모양 — 남는 변이 어디냐에 따라 둘 (2026-09-12 사용자 확정)
 * 판을 먼저 최대로 잡고 조작부는 **남는 변**에 붙인다.
 * - [WideStacked] (P1) — 세로로 펼친 폴드는 거의 정사각형이라 **위아래**가 남는다: 위 한 줄
 *   (☰ · 흑 · 수순/점수 · 백), 아래 두 줄(도구 넷 / 착수 칸 · 기권 · 통과 · 무르기).
 * - [WideColumns] (L1) — 가로로 돌리면 **좌우**가 남는다: 얇은 위 줄, 판 양옆에 기둥 둘
 *   (왼쪽 흑 좌석·형세·추천 / 오른쪽 백 좌석·착수 칸·무르기·통과·기권).
 *
 * ⚠️ **가로/세로는 뷰포트 모양으로 가른다**(`widthDp > heightDp`) — 기기 방향 API가 아니라. 분할
 *   화면·접힘 상태에서 방향과 실제 모양이 어긋나는데, 배치가 따라야 하는 것은 **모양**이다.
 *
 * ⚠️ **판정은 뷰포트 크기만 본다** — 그래프를 펼치거나 범례가 떠도 배치가 바뀌지 않게, 그리고
 *   배치를 바꾼 결과가 다시 판정을 흔들지 않게(#139의 측정값을 쓰지 않는 이유).
 */
internal enum class GameScreenLayout {
    Phone,
    WideStacked,
    WideColumns,
    ;

    /** 넓은 배치인가 — 화면 껍데기(스크롤 없음·여백)가 같은 둘. */
    val isWide: Boolean get() = this != Phone
}

internal fun gameScreenLayoutFor(widthDp: Float, heightDp: Float): GameScreenLayout {
    if (widthDp < WideLayoutMinWidthDp) return GameScreenLayout.Phone
    val boardWidthDp = widthDp - 2 * GameScreenEdgePadding.value
    val phoneBoardDp = min(boardWidthDp, heightDp - PhoneLayoutNonBoardHeightDp)
    if (phoneBoardDp >= boardWidthDp * PhoneLayoutMinBoardFillRatio) return GameScreenLayout.Phone
    return if (widthDp > heightDp) GameScreenLayout.WideColumns else GameScreenLayout.WideStacked
}

/** 넓은 배치를 쓸 수 있는 최소 폭. 큰 화면의 관례적 경계(sw600dp)와 같다. */
internal const val WideLayoutMinWidthDp = 600f

/**
 * 이 화면에서 **회전을 허용할까**(백로그 #147, 2026-09-12 사용자 확정: *"권장안으로 진행"*).
 *
 * 폰은 세로 고정, 큰 화면은 자유다. 판정 기준을 [WideLayoutMinWidthDp] **하나로 공유**하는 것이 요점이다 —
 * 배치가 갈리는 폭과 회전이 열리는 폭이 어긋나면 *"가로로 돌렸는데 폰 배치가 뜨는"* 화면이 생긴다.
 *
 * ## 왜 폰은 계속 잠그나
 * 폰 가로는 어느 배치로도 답이 없다 — 세로가 360dp 남짓이라 판이 그만큼 작아지고, 좌우 기둥(L1)은 폭이
 * 600dp 이상일 때만 쓴다.
 *
 * ## 왜 큰 화면은 여는 게 맞나
 * targetSdk 36에서 **Android 16은 sw≥600dp 화면의 세로 고정을 무시한다** — 폴드를 펼쳐 돌리면 이미 가로로
 * 그려지고 L1이 뜬다. 이 정책은 그것을 명시적으로 받아들이고, 안드로이드 14·15 폴드에도 같은 동작을 준다.
 * ⚠️ **회전으로 대국이 날아가던 사고(2026-09-09)는 별개로 이미 고쳐져 있다** — `configChanges`가 액티비티
 * 재생성을 막는다. 그 잠금을 푸는 것과 그 사고는 이제 관계가 없다.
 *
 * @param smallestScreenWidthDp **방향과 무관한** 폭(두 방향 중 좁은 쪽). ⚠️ `screenWidthDp`를 쓰면 돌리는
 *   순간 기준이 바뀌어 잠금이 오락가락한다.
 */
internal fun allowsRotation(smallestScreenWidthDp: Int): Boolean =
    smallestScreenWidthDp >= WideLayoutMinWidthDp

/**
 * 폰 배치에서 **판이 아닌 모든 것**의 세로 합(dp). 2026-09-11 실측(#139): 헤더·점수 카드·판 위 토글·
 * 좌석 카드·버튼 두 줄과 여백을 합쳐 약 426dp. 폰 배치의 조작부를 크게 바꾸면 다시 잴 것.
 */
internal const val PhoneLayoutNonBoardHeightDp = 426f

/** 폰 배치로 판이 가로폭의 이만큼은 채워야 폰 배치를 유지한다. */
internal const val PhoneLayoutMinBoardFillRatio = 0.9f
