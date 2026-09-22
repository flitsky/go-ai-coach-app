package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 내 아바타(백로그 #165) — **닉네임 첫 글자 + 색**(U-20, 2026-09-22 사용자 결정).
 *
 * ⚠️ **그림도 권한도 쓰지 않는 것이 이 선택의 요점이다.** 갤러리 사진은 사진 권한 · 백업 제외
 * 규칙(함정 37) · 데이터 보안 양식(함정 60)까지 번져 **이미 출시된 앱의 정책 문서를 고쳐야**
 * 하고, 봇 캐릭터 그림을 빌려 쓰면 좌석 카드에서 **내 아바타와 상대 캐릭터가 같은 그림**으로
 * 둘 뜨는 경우가 생긴다(#155가 그 자리를 노리고 있다).
 */
@Composable
internal fun UserAvatar(
    nickname: String?,
    modifier: Modifier = Modifier,
    /**
     * ⚠️ **dp다 — sp가 아니다**(함정 9). 원의 지름은 글꼴 배율을 따라 커지면 안 된다.
     * 안쪽 글자만 지름에 비례해 키운다.
     */
    size: Dp = DefaultSize,
    contentDescription: String? = null,
) {
    val initial = avatarInitialOf(nickname)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColorOf(nickname))
            .then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            // ⚠️ 원이 dp로 고정이므로 **글자도 지름에 묶는다** — sp로 두면 글꼴 배율 1.3배에서
            // 글자가 원 밖으로 자란다(함정 9가 "원 안 글자는 dp로"라고 적은 그대로).
            fontSize = (size.value * InitialSizeRatio).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** 기본 지름 — 마이 페이지의 한 줄 높이에 맞춘 값. */
internal val DefaultSize: Dp = 48.dp

/**
 * 원 지름 대비 글자 크기. 값이 크면 두 글자(영문 이니셜 하나여도 넓은 글자)가 원에 닿는다.
 * ⚠️ `size.value`는 **dp 수치**이고 그것을 `sp`로 읽는다 — 배율을 따르지 않게 하려는 의도다.
 */
private const val InitialSizeRatio = 0.42f

/**
 * 원 안에 그릴 글자 — 닉네임의 **첫 글자 하나**.
 *
 * ⚠️ **코드 포인트로 자른다.** `first()`로 자르면 이모지·일부 한자(surrogate pair)가 **반 토막**
 * 나서 두부(￭)가 그려진다 — 닉네임에 이모지를 넣는 사람은 반드시 있다.
 * ⚠️ 이름이 없으면 물음표가 아니라 **점 하나**다. 물음표는 오류처럼 읽힌다.
 */
internal fun avatarInitialOf(nickname: String?): String {
    val trimmed = nickname?.trim().orEmpty()
    if (trimmed.isEmpty()) return EmptyInitial
    val firstCodePoint = trimmed.codePointAt(0)
    return String(Character.toChars(firstCodePoint)).uppercase()
}

/**
 * 원의 바탕색 — 닉네임에서 **결정적으로** 고른다(같은 이름이면 늘 같은 색).
 *
 * ⚠️ `String.hashCode()`는 JVM에서 값이 고정돼 있어 기기·실행마다 같다 — 색이 앱을 껐다 켤
 * 때마다 바뀌면 "내 아바타"라는 느낌이 무너진다. 난수를 쓰지 말 것.
 */
internal fun avatarColorOf(nickname: String?): Color {
    val trimmed = nickname?.trim().orEmpty()
    if (trimmed.isEmpty()) return EmptyAvatarColor
    val index = (trimmed.hashCode().toLong() and 0xFFFFFFFFL) % AvatarColors.size
    return AvatarColors[index.toInt()]
}

/** 이름이 없을 때의 점 하나. */
private const val EmptyInitial = "·"

/** 이름이 없을 때의 회색 — "아직 안 정했다"가 색으로도 보이게 한다. */
private val EmptyAvatarColor = Color(0xFF9E9E9E)

/**
 * 바탕색 후보. ⚠️ **전부 흰 글자가 읽히는 어두운 색이다** — 밝은 색을 더하면 원 안 글자가
 * 사라진다. 색을 더할 때는 흰 글자와의 대비를 눈으로 확인할 것.
 */
private val AvatarColors: List<Color> = listOf(
    Color(0xFF1B7F5A),
    Color(0xFF2E6DB4),
    Color(0xFF8A4FBE),
    Color(0xFFB4542E),
    Color(0xFF3F6B2B),
    Color(0xFFA83E5B),
)
