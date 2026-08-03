package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Google 브랜드 블루 — 버튼 배경이 아니라 [SocialLoginButton]의 왼쪽 글리프 색으로만 쓴다.
 * Google 로고 자체는 4색이지만(실제 벡터 에셋 없이는 재현 불가), 단색 placeholder로는
 * 이 블루가 가장 널리 쓰이는 근사치다 — 실제 로그인 연동 시 공식 에셋으로 교체 권장.
 */
internal val GoogleBrandBlue = Color(0xFF4285F4)

/**
 * Google/Apple/이메일 등 외부 로그인 수단 버튼의 공통 스타일. [OnboardingScreen]과
 * [SettingsScreen]에서 동일하게 재사용한다.
 *
 * 배경은 항상 무색(OutlinedButton, 테두리만)으로 통일한다 — Google/Apple 모두 실제로는
 * 버튼 전체를 브랜드색으로 채우는 걸 권장하지 않고(Google은 흰/무채색 배경 + 멀티컬러 로고,
 * Apple은 흑/백만 허용), 여러 로그인 수단을 나란히 보여줄 때 어느 하나도 "추천"처럼
 * 튀어서는 안 되기 때문이다. 브랜드 구분은 왼쪽 [leadingGlyph]의 색([glyphColor])에만 둔다.
 */
@Composable
internal fun SocialLoginButton(
    label: String,
    leadingGlyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyphColor: Color = Color.Unspecified,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = leadingGlyph,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = if (glyphColor == Color.Unspecified) LocalContentColor.current else glyphColor,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label)
        }
    }
}
