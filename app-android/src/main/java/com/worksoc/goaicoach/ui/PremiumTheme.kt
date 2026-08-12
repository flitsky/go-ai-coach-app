package com.worksoc.goaicoach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 프리미엄 모드 전용 시각 아이덴티티 상수. 대국 설정의 프리미엄 카드, 인게임 잠긴 버튼
 * 테두리 등 프리미엄과 관련된 색/보더/그라디언트는 하드코딩하지 않고 이 파일만 참조한다
 * — 톤을 바꿀 때 한 곳만 고치면 되도록.
 */
internal val PremiumGold = Color(0xFFCA9B3E)
internal val PremiumGoldLight = Color(0xFFF3E1AC)
internal val PremiumGoldDeep = Color(0xFF8A6416)

internal val PremiumCardShape = RoundedCornerShape(16.dp)

internal val PremiumGoldGradient =
    Brush.horizontalGradient(listOf(PremiumGoldDeep, PremiumGold, PremiumGoldLight))

/**
 * 인게임에서 프리미엄 전용 버튼이 잠겨 있을 때 두르는 금색 테두리. 버튼 전체를 흐리게
 * 만들지 않고(탭 유도를 죽이지 않도록) 이 테두리만으로 "프리미엄" 인지가 되어야 하므로
 * 일반 버튼 테두리(ActionButtonBorder)보다 눈에 띄게 굵고 진하게 잡는다.
 */
internal val PremiumLockedBorder = BorderStroke(2.dp, PremiumGold.copy(alpha = 0.9f))
