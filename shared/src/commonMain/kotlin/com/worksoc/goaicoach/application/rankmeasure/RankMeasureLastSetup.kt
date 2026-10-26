package com.worksoc.goaicoach.application.rankmeasure

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.StoneColor

/**
 * 승급 대국 설정 창에서 **마지막으로 시작한** 진영과 판 크기(백로그 #286 — 사용자: *"19*19를 선택해서 플레이 했었을 경우, 앱 재실행하여
 * 메뉴 진입시에 그대로 유지"*). 설정 창은 이 값으로 열린다.
 *
 * ⚠️ 기력 상태([RankMeasureState])와 **다른 키**다 — 「급수 초기화하기」는 기력만 처음으로 되돌리고 고른 판은 남긴다.
 * 일반 대국 설정(`UserPreferencesSnapshot`)과도 섞지 않는다 — 승급 대국의 판은 일반 설정을 덮어쓰지 않는다(`leaveRankMeasureGame`).
 * 판 크기는 고른 그대로 남긴다 — 그 기력에서 못 두는 판이면 설정 창이 둘 수 있는 가장 작은 판으로 보인다(`rankMeasureBoardSizeFor`).
 */
data class RankMeasureLastSetup(
    val userColor: StoneColor = StoneColor.Black,
    val boardSize: BoardSize = BoardSize.Nine,
)
