package com.worksoc.goaicoach.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.matchOpponentCharacter
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.designsystem.BotCharacterSquareIcon
import com.worksoc.goaicoach.ui.guide.FirstDolCharacterId
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.reviewGameActionFor
import com.worksoc.goaicoach.ui.l10n.reviewMistakeBadgeDescriptionFor
import com.worksoc.goaicoach.ui.l10n.reviewRecommendationMessageFor
import kotlin.math.min

/*
 * 대국 뒤 「복기 하기」 추천(백로그 #200, 2026-09-30 사용자 최우선) — 판정 결과 팝업을 **닫은 뒤에도** 복기로 가는
 * 길을 한 번 더 보여 준다. 개수는 `:shared`의 `countReviewRecommendationMistakes`가 세고(5집 이상 실착, 누가 두었든),
 * 여기는 그 수를 **어떻게 보여 줄지**만 정한다.
 *
 * - ① 판정 결과 팝업의 「복기 하기」 — 글자를 굵게, 오른쪽 위에 동그라미 숫자([ReviewMistakeBadgeBox]).
 * - ② 「확인」으로 닫은 뒤 — 그 판의 캐릭터가 판 오른쪽 아래에서 말풍선으로 권한다([ReviewRecommendationOverlay]).
 * - 개수가 `0`이거나 `null`(잴 자료가 없었다)이면 **둘 다 지금까지와 똑같다** — 배지도 굵은 글자도 말풍선도 없다.
 */

/** 배지 숫자 — 1~8은 그대로, 9 이상은 `9+`(2026-09-30 사용자). 0·`null`이면 배지가 없다. */
internal fun reviewMistakeBadgeLabel(mistakeCount: Int?): String? =
    when {
        mistakeCount == null || mistakeCount < 1 -> null
        mistakeCount >= ReviewMistakeBadgeCap -> "$ReviewMistakeBadgeCap+"
        else -> mistakeCount.toString()
    }

private const val ReviewMistakeBadgeCap = 9

/**
 * 「복기 하기」 버튼을 감싸 **오른쪽 위에 배지**를 얹는다. 버튼(`TextButton`) 자체는 부르는 쪽이 그린다 —
 * 판정 결과 팝업의 버튼이 둘이라는 계약(`FinishedGameFlowContractTest`)이 그 파일의 `TextButton(`을 센다.
 *
 * ⚠️ 배지를 버튼 **안**(글자 옆)에 두지 않는다 — `TextButton`은 제 모양으로 내용을 잘라서(클립), 모서리에 걸친
 * 배지가 반쯤 잘린다. 바깥 `Box`의 형제로 두면 버튼 모서리에 걸쳐 그려지고, 누르지 않는 그림이라 탭은 그대로
 * 아래 버튼으로 간다.
 */
@Composable
internal fun ReviewMistakeBadgeBox(
    mistakeCount: Int?,
    language: UiLanguage,
    button: @Composable () -> Unit,
) {
    val label = reviewMistakeBadgeLabel(mistakeCount)
    Box {
        button()
        if (label != null && mistakeCount != null) {
            val description = reviewMistakeBadgeDescriptionFor(language, mistakeCount)
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = AppSpacing.Space4, y = -AppSpacing.Space2)
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                Text(text = label)
            }
        }
    }
}

/**
 * 말풍선에 설 캐릭터 — **그 판의 AI 상대**(`matchOpponentCharacter`: AI 좌석의 레벨 → 캐릭터, AI끼리면 백).
 * 사람끼리 둔 판이거나 캐릭터가 없는 레벨이면 **첫돌이**([FirstDolCharacterId])다 — 앱 곳곳에서 말풍선으로
 * 안내하는 캐릭터가 첫돌이라, 상대가 없는 판에서 *"복기해 보세요"* 를 말할 자리도 그것이 자연스럽다.
 */
internal fun reviewRecommendationCharacterFor(playerSetup: PlayerSetup): BotCharacter? =
    matchOpponentCharacter(playerSetup) ?: BotCharacterCatalog.byId(FirstDolCharacterId)

/** 말풍선이 판 아래(또는 옆)에 설 자리. [side]는 어느 규칙으로 정해졌는지 — 실기 확인·테스트용이다. */
internal data class ReviewRecommendationPlacement(val x: Int, val y: Int, val side: Side) {
    enum class Side {
        /** 판 **아래**, 판 오른쪽 끝에 맞춰 — 폰 배치의 기본. */
        BelowBoard,

        /** 판 **오른쪽**, 판 아래 끝에 맞춰 — 아래에 자리가 없고 판 옆에 말풍선 폭만큼 빈 곳이 있을 때(가로로 아주 긴 화면). */
        RightOfBoard,

        /**
         * 판 밖에 통째로 둘 자리가 없다 — **화면 오른쪽 아래 끝**에 둔다. 판 모서리에 맞추는 것보다 판을 덜 가린다(판
         * 아래·옆의 빈 띠만큼 판 밖으로 비켜난다). 넓은 배치가 여기다: 세로로 펼친 폴드는 판이 남는 높이를 전부 갖고 아래엔
         * 버튼 한 줄뿐이며(1812×2176 실측, 판 아래 약 69dp), 가로로 펼치면 오른쪽 기둥이 말풍선보다 좁다(2176×1812).
         * 끝난 판이라 두는 입력은 없고, ✕ **또는 판 아무 데나 탭**하면 닫힌다([ReviewRecommendationOverlay]).
         */
        OverlapsBoard,
    }
}

/**
 * 말풍선 자리를 고른다 — **판을 가리지 않는 것**이 첫째다(카드 ⚠️: *"판 밖이거나 탭하면 닫힘"*).
 *
 * 1. 판 **아래**에 말풍선 높이만큼 남으면 거기(판 오른쪽 끝에 맞춘다) — 폰 배치는 판 아래에 상태판·버튼이 있어 늘 여기다.
 * 2. 아니면 판 **오른쪽**에 폭만큼 남으면 거기(판 아래 끝에 맞춘다).
 * 3. 둘 다 없으면 화면 오른쪽 아래 끝 — 판을 조금 가리지만 가장 덜 가리는 자리다. ✕나 판 탭으로 닫힌다.
 *
 * 좌표는 전부 **오버레이 안** 픽셀이고, 결과는 오버레이 밖으로 나가지 않게 조인다. 가로는 [edgeMargin]만큼 화면 끝에서
 * 띄운다 — 「바둑판 최대」면 판이 화면 끝까지 가서, 판 끝에 맞추면 캐릭터가 화면 테두리에 붙는다(실기에서 보였다).
 */
internal fun reviewRecommendationPlacement(
    containerWidth: Int,
    containerHeight: Int,
    board: Rect,
    bubbleWidth: Int,
    bubbleHeight: Int,
    gap: Int,
    edgeMargin: Int,
): ReviewRecommendationPlacement {
    fun clampX(x: Float): Int {
        val margin = if (containerWidth - bubbleWidth >= edgeMargin * 2) edgeMargin else 0
        return x.toInt().coerceIn(margin, (containerWidth - bubbleWidth - margin).coerceAtLeast(margin))
    }
    fun clampY(y: Float): Int = y.toInt().coerceIn(0, (containerHeight - bubbleHeight).coerceAtLeast(0))
    val right = min(board.right, containerWidth.toFloat())
    return when {
        containerHeight - board.bottom >= bubbleHeight + gap -> ReviewRecommendationPlacement(
            x = clampX(right - bubbleWidth),
            y = clampY(board.bottom + gap),
            side = ReviewRecommendationPlacement.Side.BelowBoard,
        )
        containerWidth - board.right >= bubbleWidth + gap -> ReviewRecommendationPlacement(
            x = clampX(board.right + gap),
            y = clampY(board.bottom - bubbleHeight),
            side = ReviewRecommendationPlacement.Side.RightOfBoard,
        )
        else -> ReviewRecommendationPlacement(
            x = clampX((containerWidth - bubbleWidth).toFloat()),
            y = clampY((containerHeight - bubbleHeight).toFloat()),
            side = ReviewRecommendationPlacement.Side.OverlapsBoard,
        )
    }
}

/**
 * `GoBoard`가 실제로 그리는 **정사각형 판**. 판 자리(`BoxWithConstraints`)는 넓은 배치에서 판보다 크고, 판은 그 안
 * **가운데**에 `min(가로, 세로)` 정사각형으로 선다(`GoBoard.kt`의 `contentAlignment = Center`). 자리 끝에 맞추면
 * 판에서 떨어진 곳에 말풍선이 선다.
 */
internal fun boardSquareWithin(slot: Rect): Rect {
    val side = min(slot.width, slot.height)
    val left = slot.left + (slot.width - side) / 2f
    val top = slot.top + (slot.height - side) / 2f
    return Rect(left = left, top = top, right = left + side, bottom = top + side)
}

/**
 * ② 판 오른쪽 아래의 캐릭터 + 말풍선. **대국 화면 위에 겹쳐** 그린다(`GoCoachContent`가 본문 뒤에 두어 위에 온다, 함정 38).
 *
 * [boardSlotInRoot]·[originInRoot]는 **값이 아니라 읽는 함수**다 — 스크롤할 때마다 판 좌표가 바뀌는데, 그것을
 * 컴포지션에서 읽으면 대국 화면 전체가 매 프레임 다시 짜인다. 여기서는 측정 단계에서만 읽어 이 오버레이만 다시 잰다.
 *
 * 말풍선을 누르면 복기로 간다(「복기 하기」와 같은 길). ✕는 닫기만 한다. 오버레이의 나머지는 **누르는 것을 가로채지
 * 않는다** — 새 대국·재 대국 버튼은 그대로 눌린다. 예외는 말풍선이 판에 겹칠 때(폴드)의 판 위뿐이다(탭하면 닫힌다).
 */
@Composable
internal fun ReviewRecommendationOverlay(
    boardSlotInRoot: () -> Rect?,
    originInRoot: () -> Offset,
    character: BotCharacter?,
    mistakeCount: Int,
    language: UiLanguage,
    closeLabel: String,
    onOpenReview: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            // 판 위 탭 받개 — 말풍선이 판에 **겹칠 때만** 놓인다(아래 배치). 끝난 판은 두는 입력이 없으니 판을
            // 누르는 것은 "치워 달라"는 뜻으로 읽는다(카드: *"판 밖이거나 탭하면 닫힘"*). 놓이지 않으면 누르는 것을 받지 않는다.
            Box(modifier = Modifier.pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
            ReviewRecommendationBubble(
                character = character,
                message = reviewRecommendationMessageFor(language, mistakeCount),
                actionLabel = reviewGameActionFor(language),
                closeLabel = closeLabel,
                onOpenReview = onOpenReview,
                onDismiss = onDismiss,
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val margin = GameScreenEdgePadding.roundToPx()
        val maxWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0)
        val (boardTapCatcher, bubbleMeasurable) = measurables
        val bubble = bubbleMeasurable.measure(Constraints(maxWidth = maxWidth))
        val slot = boardSlotInRoot()
        val origin = originInRoot()
        val board = slot?.let { boardSquareWithin(it.translate(-origin.x, -origin.y)) }
        val placement = board?.let {
            reviewRecommendationPlacement(
                containerWidth = constraints.maxWidth,
                containerHeight = constraints.maxHeight,
                board = it,
                bubbleWidth = bubble.width,
                bubbleHeight = bubble.height,
                gap = AppSpacing.Space8.roundToPx(),
                edgeMargin = margin,
            )
        }
        val catcher = if (board != null && placement?.side == ReviewRecommendationPlacement.Side.OverlapsBoard) {
            boardTapCatcher.measure(Constraints.fixed(board.width.toInt().coerceAtLeast(0), board.height.toInt().coerceAtLeast(0)))
        } else {
            null
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            // 판 자리를 아직 모르면(첫 프레임) 아무것도 놓지 않는다 — 엉뚱한 자리에 한 프레임 번쩍이지 않게.
            if (board == null || placement == null) return@layout
            catcher?.place(IntOffset(board.left.toInt(), board.top.toInt()))
            bubble.place(IntOffset(placement.x, placement.y))
        }
    }
}

/**
 * 말풍선 + 캐릭터 한 벌. 캐릭터는 **오른쪽**에 선다 — 말풍선의 오른쪽 아래 모서리를 좁게 깎아 그쪽에서 말이 나오는
 * 모양이 되게 했다(꼬리를 따로 그리지 않는다, `GuideBubble`과 같은 판단).
 *
 * ⚠️ 높이를 고정하지 않는다(함정 9) — 영어·글꼴 크게(1.3)에서 문장이 다섯 줄이 된다(계기 테스트 `bubble_en_1.3.png`).
 */
@Composable
private fun ReviewRecommendationBubble(
    character: BotCharacter?,
    message: String,
    actionLabel: String,
    closeLabel: String,
    onOpenReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    val bubbleShape = RoundedCornerShape(
        topStart = AppRadius.Corner14,
        topEnd = AppRadius.Corner14,
        bottomEnd = ReviewBubbleSpeakerCorner,
        bottomStart = AppRadius.Corner14,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = ReviewBubbleMaxWidth)
                .shadow(ReviewBubbleShadow, bubbleShape)
                .background(MaterialTheme.colorScheme.surface, bubbleShape)
                .border(AppBorderWidth.Hairline, MaterialTheme.colorScheme.outlineVariant, bubbleShape)
                .clip(bubbleShape)
                .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onOpenReview)
                .padding(start = AppSpacing.Space12, top = AppSpacing.Space8, bottom = AppSpacing.Space10, end = AppSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space6),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
                Text(
                    text = message,
                    modifier = Modifier.weight(1f).padding(top = AppSpacing.Space2),
                    fontSize = AppTextSize.Text14,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .size(ReviewBubbleCloseSize)
                        .clip(CircleShape)
                        .clickable(onClickLabel = closeLabel, role = Role.Button, onClick = onDismiss)
                        .clearAndSetSemantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(ReviewBubbleCloseIconSize),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 누르면 어디로 가는지 — **화면의 버튼 글자 그대로** 인용한다(함정 39).
            Text(
                text = "$actionLabel ›",
                fontSize = AppTextSize.Text14,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (character != null) {
            BotCharacterSquareIcon(character = character, modifier = Modifier.size(ReviewBubbleCharacterSize))
        }
    }
}

/** 말풍선 폭 상한 — 폰(411dp)에서 캐릭터와 함께 여백 안에 들어가고, 넓은 화면에서 한 줄로 길게 늘어지지 않게. */
private val ReviewBubbleMaxWidth = 260.dp

/** 캐릭터 원화 한 변. 홈 카드 아이콘(68dp)과 비슷한 크기라 같은 그림으로 읽힌다. */
private val ReviewBubbleCharacterSize = 64.dp

/** 캐릭터 쪽(오른쪽 아래) 모서리만 좁게 — 말이 그쪽에서 나온다. */
private val ReviewBubbleSpeakerCorner = 4.dp

/** `GuideBubble`과 같은 그림자 — 판·상태판 위에 떠 있는 것이 보이게. */
private val ReviewBubbleShadow = 4.dp

private val ReviewBubbleCloseSize = 32.dp
private val ReviewBubbleCloseIconSize = 18.dp
