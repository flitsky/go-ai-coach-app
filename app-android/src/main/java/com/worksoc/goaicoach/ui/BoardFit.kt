package com.worksoc.goaicoach.ui

import androidx.compose.ui.unit.dp

/**
 * 대국 화면이 **한 화면에 다 들어오도록** 판의 최대 높이를 정한다(백로그 #139, 1차).
 *
 * ## 왜 필요한가
 * 대국 화면은 `verticalScroll` 안이라 판이 받는 세로 제약이 **무한**이다 — 그래서 `GoBoard`의
 * `min(가로, 세로)`는 언제나 가로폭이 된다(함정 45). 세로로 긴 폰에서는 멀쩡하지만, 폴드 안쪽 화면
 * (≈690×829dp)에서는 판이 세로 ~680dp가 되어 **조작부 전부가 화면 밖**으로 밀렸고, 판 위 끌기가
 * 화면을 굴려 *"바둑판이 자꾸 움직인다"* 는 제보가 됐다.
 *
 * ## 어떻게 재나
 * 판이 아닌 모든 것의 높이(`content − board`)는 **판 크기와 무관**하다(헤더·그래프·카드·버튼).
 * 그래서 `viewport − (content − board)`가 판에 줄 수 있는 높이이고, 한 번 재면 수렴한다 —
 * 판이 줄면 content도 같은 만큼 줄어 둘의 차가 그대로이기 때문이다.
 * ⚠️ 단 `contentPx`는 **본래 높이**여야 한다 — 화면 높이로 늘어난 값을 넣으면 상한이 지금 판 크기에
 *   갇혀 다시 커지지 못한다. 재는 쪽(`GoCoachContent`)이 `wrapContentHeight`로 그 늘림을 푼다.
 *
 * ⚠️ **보통 폰에서는 아무 일도 안 한다** — 자리가 남으면 이 값이 가로폭보다 커서 `min(가로, 세로)`가
 * 그대로 가로폭을 고른다. 판이 작아지는 것은 **넘칠 때뿐**이다.
 * ⚠️ **바닥이 있다**([MinFittedBoardSide]) — 분할 화면처럼 세로가 아주 짧으면 판을 그 밑으로 줄이지
 *   않고 예전처럼 화면을 스크롤하게 둔다. 두기 어려울 만큼 작은 판보다 스크롤이 낫다.
 * ⚠️ 넓은 화면에서 좌우에 남는 여백을 조작부 자리로 쓰는 배치는 **이 함수의 몫이 아니다** —
 *   백로그 후속 항목(넓은 화면 배치)이다. 이것은 "잘리지 않게"까지만 한다.
 *
 * @return 판의 최대 높이(px). 아직 한 번도 재지 못했으면 `null`(= 제한 없음, 예전 동작).
 */
internal fun fittedBoardMaxHeightPx(
    viewportPx: Int,
    contentPx: Int,
    boardPx: Int,
    minBoardPx: Int,
): Int? {
    if (viewportPx <= 0 || contentPx <= 0 || boardPx <= 0) return null
    val nonBoardPx = contentPx - boardPx
    return (viewportPx - nonBoardPx).coerceAtLeast(minBoardPx)
}

/** 한 화면에 맞추느라 줄여도 판이 이보다 작아지지는 않는다 — 그 밑이면 화면을 스크롤한다. */
internal val MinFittedBoardSide = 280.dp
