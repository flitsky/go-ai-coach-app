package com.worksoc.goaicoach.ui.designsystem

import androidx.compose.ui.graphics.Color

/*
 * 테마 역할(`MaterialTheme.colorScheme`)로 표현되지 않는 **고정색**의 정본(refactor backlog #51).
 *
 * 2026-09-28까지 이 값들은 화면 파일 곳곳에 `Color(0xFF…)`·`Color.White`로 흩어져 있었다. 이 파일은
 * 그것을 **값 그대로** 옮겨 왔을 뿐이다 — 이름을 붙였지 색을 고치지 않았다(전후 스크린샷이 픽셀까지
 * 같다: `DesignTokenScreenshotTest`). 톤을 바꾸고 싶다면 여기 한 곳만 고치면 된다.
 *
 * ⚠️ **먼저 테마 역할을 찾을 것.** 브랜드 초록 바탕 위 흰 글자는 `colorScheme.onPrimary`가 이미 같은
 * 값이라 그쪽을 쓴다. 여기 있는 것은 테마가 **몰라야 하는** 색이다 — 돌의 흑백(테마를 따라 바뀌면 안
 * 된다), 카메라 사진 위 오버레이, 외부 브랜드(Google), 한 화면만의 차트 색.
 *
 * ⚠️ `ui.designsystem` 밖에서 색 리터럴을 새로 쓰면 `DesignTokenLiteralRatchetTest`가 빨개진다.
 * 바둑판 자체의 색은 [GoBoardColors]·[GoBoardPalette], 프리미엄 금색은 `PremiumTheme.kt`에 있다.
 */

/** 앱 전역에서 테마 역할이 없는 강조 바탕 위 글자색. */
internal object AppPalette {
    /**
     * 진한 강조 바탕 위 글자 — **바탕이 브랜드 초록일 수도 프리미엄 금색일 수도 있는** 자리(로비의 대국 시작
     * 버튼)라 `onPrimary`로 묶을 수 없다. 금색 바탕에는 테마 역할이 없다.
     */
    val OnAccent = Color.White
}

/**
 * Google 브랜드 블루 — 외부 브랜드 가이드의 값이라 테마를 따르지 않는다. 버튼 배경이 아니라
 * `SocialLoginButton`의 왼쪽 글리프 색으로만 쓴다(Google 로고를 실제 에셋으로 그릴 수 있으면
 * `leadingIconRes`가 우선이라 이 색은 안 쓰인다 — 벡터 에셋이 없는 수단의 단색 placeholder용).
 * 2026-09-28까지 `ui.account`에 있었다(refactor backlog #51).
 */
internal val GoogleBrandBlue = Color(0xFF4285F4)

/**
 * 바둑판 **밖**에서 그리는 돌(좌석 카드 글리프·승자 테두리·통과 알림·판 보정 범례).
 *
 * ⚠️ **백 글리프는 흰색이 아니라 회색이다**([WhiteGlyph]) — 좌석 카드와 결과 배지의 바탕이 밝은
 * `surfaceVariant`라 흰 테두리·글리프는 **있으나 마나**가 된다(#190, `FinalResultBadgeContractTest`).
 * 둘이 이 한 값을 같이 쓰므로 더는 한쪽만 고쳐져 백이 두 색이 되는 일이 없다.
 */
internal object StonePalette {
    val Black = Color.Black
    val White = Color.White

    /** 밝은 바탕 위 흰 돌의 윤곽(판 보정 범례). */
    val WhiteOutline = Color.Gray

    val BlackGlyph = Color.Black
    val WhiteGlyph = Color.Gray
}

/** 홈 로고(흑백 두 돌과 둘레)의 색 — 바둑판 돌([GoBoardPalette])과는 별개의 그림이다. */
internal object HomeLogoPalette {
    val Backdrop = Color(0xFFF5F0E6)
    val RingBorder = Color(0xFFE5DDD0)
    val BlackStoneGradient = listOf(
        Color(0xFF7A7A7A),
        Color(0xFF3D3D3D),
        Color(0xFF161616),
        Color(0xFF000000),
    )
    val WhiteStoneBorder = Color(0xFFD3C9B8)
    val WhiteStoneGradient = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF7F3EB),
        Color(0xFFD6CCC0),
    )
}

/**
 * 사용자 아바타 원. ⚠️ [Backgrounds]는 **전부 흰 글자([Initial])가 읽히는 어두운 색이다** — 밝은 색을
 * 더하면 원 안 글자가 사라진다.
 */
internal object AvatarPalette {
    val Initial = Color.White

    /** 이름이 없을 때의 회색 — "아직 안 정했다"가 색으로도 보이게 한다. */
    val Empty = Color(0xFF9E9E9E)

    val Backgrounds: List<Color> = listOf(
        Color(0xFF1B7F5A),
        Color(0xFF2E6DB4),
        Color(0xFF8A4FBE),
        Color(0xFFB4542E),
        Color(0xFF3F6B2B),
        Color(0xFFA83E5B),
    )
}

/** 대국 화면 좌석 카드·착수 버튼. */
internal object GameStatusPalette {
    val SeatLabel = Color(0xFF1F1F1F)
    val PlayButtonDisabledContainer = Color(0xFFECEFF1)
    val PlayButtonDisabledContent = Color(0xFFB0BEC5)
    val InactiveStateBorder = Color(0xFFCFD8DC)
}

/** 형세 그래프 패널(슬레이트 계열 — 이 차트만의 색이다). */
internal object ScoreGraphPalette {
    val Background = Color(0xFFF8FAFC)
    val Border = Color(0xFFE2E8F0)
    val GridLine = Color(0xFFE2E8F0)
    val Title = Color(0xFF475569)
    val Label = Color(0xFF64748B)
    val ScoreLine = Color(0xFF3B82F6)
    val ActiveDot = Color(0xFFEF4444)
    val JigoLine = Color(0xFF94A3B8)
}

/**
 * 카메라 바둑판 인식(실험실 기능) — **사진 위**에 그리므로 테마와 무관한 어두운 바탕·흰 글자다.
 */
internal object VisionPalette {
    val Backdrop = Color.Black
    val EditorBackdrop = Color(0xFF121212)
    val OnBackdrop = Color.White
    val OnBackdropMuted = Color.LightGray

    /** 인식 가이드 사각형·코너 핀의 기본색. */
    val Guide = Color(0xFF4CAF50)

    /** 코너 강조·드래그 중인 핀. */
    val GuideHighlight = Color(0xFFFFD700)

    val PinCenter = Color.Black
    val ShutterBusy = Color.Gray
    val ShutterProgress = Color.DarkGray
}
