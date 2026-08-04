package com.worksoc.goaicoach.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Google 브랜드 블루 — 버튼 배경이 아니라 [SocialLoginButton]의 왼쪽 글리프 색으로만 쓴다.
 * (Google 로고 자체를 실제 에셋으로 그릴 수 있는 경우엔 [leadingIconRes]가 우선이라
 * 이 색은 안 쓰인다 — 벡터 에셋이 없는 다른 수단의 단색 placeholder용으로 남겨둔다.)
 */
internal val GoogleBrandBlue = Color(0xFF4285F4)

/**
 * Google/Apple/이메일 등 외부 로그인 수단 버튼의 공통 스타일. [OnboardingScreen]과
 * [SettingsScreen]에서 동일하게 재사용한다.
 *
 * 배경은 항상 무색(OutlinedButton, 테두리만)으로 통일한다 — Google/Apple 모두 실제로는
 * 버튼 전체를 브랜드색으로 채우는 걸 권장하지 않고(Google은 흰/무채색 배경 + 멀티컬러 로고,
 * Apple은 흑/백만 허용), 여러 로그인 수단을 나란히 보여줄 때 어느 하나도 "추천"처럼
 * 튀어서는 안 되기 때문이다. 아이콘+라벨 그룹 전체를 버튼 안에서 가운데 정렬한다.
 *
 * 왼쪽 아이콘은 [leadingIconRes](실제 벡터/래스터 에셋, 있으면 우선)나 [leadingGlyph](텍스트/
 * 이모지 placeholder) 중 하나로 그린다. Google "G" 공식 에셋(`googleg_standard_color_18`,
 * Play Services Base AAR)을 새 의존성 없이 쓸 수 있는지 확인했으나 이 프로젝트의 Firebase
 * Auth 버전(BOM 34.16.0)은 더 이상 그 모듈을 전이 의존성으로 끌어오지 않아 실제로는
 * 클래스패스에 없었다 — 그래서 현재는 Google/Apple/이메일 모두 텍스트 placeholder이고,
 * [leadingIconRes]는 실제 로그인 SDK 연동 시 그 SDK가 제공하는 아이콘을 붙일 자리로 남겨둔다.
 */
@Composable
internal fun SocialLoginButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingGlyph: String? = null,
    leadingIconRes: Int? = null,
    glyphColor: Color = Color.Unspecified,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                if (leadingIconRes != null) {
                    Image(
                        painter = painterResource(id = leadingIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                } else if (leadingGlyph != null) {
                    Text(
                        text = leadingGlyph,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = if (glyphColor == Color.Unspecified) LocalContentColor.current else glyphColor,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}
