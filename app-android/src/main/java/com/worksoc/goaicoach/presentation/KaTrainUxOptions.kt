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
    // 반상을 누르는 순간(터치 다운) 주는 약한 햅틱(#36). 기본 켜짐 — 착수는 이 앱에서 가장
    // 잦은 동작이고, 눌린 것이 전달됐다는 확인은 조용한 편보다 있는 편이 낫다. 시스템
    // 햅틱을 꺼 둔 사용자에게는 어차피 울리지 않는다(`performHapticFeedback`이 존중한다).
    val isPlayHapticEnabled: Boolean = true,
    /**
     * 보드를 화면 폭 끝까지 그릴지(#38). **기본 켜짐**(2026-08-30 사용자 결정) — 보드가 이
     * 화면의 주인공이라 크게 보는 쪽을 기본으로 둔다. 끄면 화면 좌우 여백(16dp) 안쪽에 그린다.
     */
    val isBoardMaxSize: Boolean = true,
)
