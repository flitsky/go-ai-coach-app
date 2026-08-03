package com.worksoc.goaicoach.presentation

/** 영구 저장되는 UX 토글 번들 — 화면 상태 도출 로직([GameScreenState] 등)과는 다른 축이다. */
internal data class KaTrainUxOptions(
    val showCoordinates: Boolean = false,
    val showMoveNumbers: Boolean = false,
    val showLastMoveRing: Boolean = true,
    val showOwnershipOverlay: Boolean = true,
    val isDirectPlayEnabled: Boolean = true,
    // 착수 평가(색상 코딩) 표시 여부. 기본 꺼짐 — 사용자가 의도적으로 켰을 때만 노출한다.
    // 향후 신뢰도/평가 로직이 점진적으로 고도화될 예정인 기능이라 별도 토글로 분리했다.
    val showMoveReview: Boolean = false,
)
