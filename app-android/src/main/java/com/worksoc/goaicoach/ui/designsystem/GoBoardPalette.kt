package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.ui.graphics.Color

/**
 * 바둑판 **위**의 돌·표식 색(refactor backlog #51). 판의 바탕·격자·마지막 수 표시처럼 테마로 바꿀 수 있는
 * 색은 [GoBoardColors]가 들고, 여기 있는 것은 **돌 자체의 질감**과 판 위 글자다 — 흑은 검고 백은 희어야
 * 하므로 테마를 따라 바뀌지 않는다.
 *
 * 값은 `GoBoard.kt`에 흩어져 있던 리터럴을 **그대로** 옮긴 것이다(전후 스크린샷이 픽셀까지 같다).
 * `…Argb`는 `android.graphics.Paint`에 넣는 정수 색이다(좌표·수순 번호는 네이티브 캔버스로 그린다).
 * ⚠️ `Color.rgb(…)`는 **게터**로 둔다 — 객체 초기화에서 부르면 JVM 단위 테스트가 이 객체에 닿는 순간
 *   안드로이드 스텁(`not mocked`)으로 터진다. 원래도 그리는 순간에만 불리던 값이다.
 */
internal object GoBoardPalette {
    /** 판 위 안내 글자 상자의 바탕(투명도는 부르는 쪽이 준다)과 그 위 글자. */
    val LabelScrim = Color.Black
    val OnLabelScrim = Color.White

    /** 형세(소유권) 표시의 흑·백 영역. */
    val OwnershipBlack = Color(0xFF1F2327)
    val OwnershipWhite = Color(0xFFFFFFFF)

    val StoneShadow = Color(0x33000000)
    val GhostStoneShadow = Color(0x11000000)

    val BlackStoneGradient = listOf(
        Color(0xFF646464),
        Color(0xFF303030),
        Color(0xFF101010),
        Color(0xFF030303),
    )
    val WhiteStoneGradient = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF3F1EA),
        Color(0xFFE0DDD3),
        Color(0xFFC7C2B6),
    )

    /** 대국이 끝난 판의 돌 — 한 톤 가라앉혀 "끝났다"가 보이게 한다. */
    val BlackStoneEndedGradient = listOf(
        Color(0xFF787878),
        Color(0xFF393939),
        Color(0xFF131313),
        Color(0xFF030303),
    )
    val WhiteStoneEndedGradient = listOf(
        Color(0xFFCCCCCC),
        Color(0xFFC2C0BB),
        Color(0xFFB3B0A8),
        Color(0xFF9F9B91),
    )

    val BlackStoneEdge = Color(0xFF5E5E5E)
    val BlackStoneEndedEdge = Color(0xFF707070)
    val WhiteStoneEdge = Color(0xFF8F8A7C)
    val WhiteStoneEndedEdge = Color(0xFF726E63)

    val SpotLabelArgb: Int = android.graphics.Color.BLACK
    val CoordinateLabelArgb: Int get() = android.graphics.Color.rgb(74, 47, 23)
    val MoveNumberOnBlackArgb: Int = android.graphics.Color.WHITE
    val MoveNumberOnWhiteArgb: Int get() = android.graphics.Color.rgb(24, 24, 24)
    val MoveNumberOutlineOnBlackArgb: Int get() = android.graphics.Color.rgb(18, 18, 18)
    val MoveNumberOutlineOnWhiteArgb: Int = android.graphics.Color.WHITE
}

/** 착수 평가(최선~악수) 표식 색. */
internal object MoveReviewPalette {
    val Excellent = Color(0xFF2E7D32)
    val Good = Color(0xFF8BC34A)
    val Inaccuracy = Color(0xFFFDD835)
    val Mistake = Color(0xFFEF6C00)
    val Blunder = Color(0xFFC62828)
    val Unknown = Color(0xFF607D8B)
}
