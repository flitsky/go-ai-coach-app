package com.worksoc.goaicoach.ui.play

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.session.GameSessionTurnTimeState
import com.worksoc.goaicoach.platform.formatDeviceLocale
import com.worksoc.goaicoach.presentation.GameScreenState
import com.worksoc.goaicoach.presentation.GameUiEvent
import com.worksoc.goaicoach.shared.domain.BoardCoordinate
import com.worksoc.goaicoach.shared.domain.Move
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.ui.board.PlayStoneButton
import com.worksoc.goaicoach.ui.board.PlayStoneFace
import com.worksoc.goaicoach.ui.board.playStoneFaceOf
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.GameStatusPalette
import com.worksoc.goaicoach.ui.designsystem.StonePalette
import com.worksoc.goaicoach.ui.foundation.FeatureFlags
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings

@Composable
internal fun GameStatusPanel(
    screenState: GameScreenState,
    turnTimeState: GameSessionTurnTimeState,
    tentativeMove: BoardCoordinate?,
    blackTotalMillis: Long,
    whiteTotalMillis: Long,
    onEvent: (GameUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val currentTurnPlayer = turnTimeState.currentTurnPlayer
    val capturedByBlack = screenState.gameState.capturedBy(StoneColor.Black)
    val capturedByWhite = screenState.gameState.capturedBy(StoneColor.White)

    // ⚠️ **좌석 카드 둘 「사이」가 아니라 그 「위」다**(백로그 #187, 2026-09-22 사용자 지시).
    // 카드 사이에 칸을 하나 더 만들면 흑·백이 폭을 반씩 갖는 배분(#143)이 깨지고, 그 폭은
    // 1.3배에서 이미 빠듯하다(#107이 같은 자리에서 `Captures: 0`을 잘라 먹었다).
    // 그래서 **배분은 그대로 두고 가운데에 겹쳐** 그린다.
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 줄의 높이를 좌석 카드의 높이로 못박는다 — 가운데 돌 버튼이 **그 높이의 몇 할**로 제 크기를 정한다.
                .height(IntrinsicSize.Min)
                .padding(vertical = AppSpacing.Space4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerSeatCard(
                // #143이 가운데 착수 칸을 지우면서 **흑·백이 폭을 반씩** 갖는다(사용자 지시).
                // 확인 모드를 되살리면(플래그) 가운데 칸이 다시 들어와 셋이 나눠 갖는다.
                modifier = Modifier.weight(1f),
                isActiveTurn = currentTurnPlayer == StoneColor.Black && !screenState.isGameEnded,
                stoneGlyph = "●",
                stoneGlyphColor = StonePalette.BlackGlyph,
                label = strings.sideLabel(screenState.playerSetup.black, StoneColor.Black),
                elapsedMillisText = formatMillis(blackTotalMillis),
                capturedCount = capturedByBlack,
                capturesLabel = strings.captures,
                alignEnd = false,
            )

            // 중앙: **돌 버튼 하나**([PlaySlot]) — 착수 확인을 켜고 끄다가, 가착수가 놓이면 그 수를 확정한다.
            if (FeatureFlags.isPlayConfirmModeEnabled) PlaySlot(
                screenState = screenState,
                tentativeMove = tentativeMove,
                onEvent = onEvent,
                // ⚠️ **weight가 없다 — 이 칸은 돌 하나만큼만 갖고, 남는 폭은 전부 좌석 카드 둘이 나눈다**
                // (2026-10-09 사용자 지시: 흑·백 패널이 넓게 보이게). 셋이 1:1:1로 나누던 때는 1.3배에서
                // 좌석 카드의 `Captures: 0`이 잘렸다(#107).
                // 돌의 지름은 **좌석 카드 높이의 70%**다(같은 날 사용자 지시) — 고정값이 아니라 카드를 따라 자란다.
                // 양옆 여백은 돌이 카드에 붙어 보이지 않을 만큼 둔다(같은 날 사용자 지시: "약간의 공간").
                modifier = Modifier
                    .padding(horizontal = PlayStoneSideGap)
                    .fillMaxHeight(PlayStoneSeatHeightShare)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
            )

            PlayerSeatCard(
                modifier = Modifier.weight(1f),
                isActiveTurn = currentTurnPlayer == StoneColor.White && !screenState.isGameEnded,
                stoneGlyph = "○",
                stoneGlyphColor = StonePalette.WhiteGlyph,
                label = strings.sideLabel(screenState.playerSetup.white, StoneColor.White),
                elapsedMillisText = formatMillis(whiteTotalMillis),
                capturedCount = capturedByWhite,
                capturesLabel = strings.captures,
                alignEnd = true,
            )
        }

        // ⚠️ **종국에만 뜬다** — 대국 중에는 좌석 카드 가운데를 가리면 안 된다(시계·사석이 있다).
        // ⚠️ **0수면 그리지 않는다**(백로그 #191) — 새 대국 준비 구간에도 `isGameEnded`가 참이라 승자 없는 배지가
        //   「무승부」로 떴다. 끝난 판은 수가 반드시 하나 이상이다(기권 1수·양통과 2수) — 0수인데 끝났다는 것이 그 과도 상태다.
        //   `isGameEnded` 자체는 건드리지 않는다(종료 버튼 묶음·계가 오버레이가 그 켜지는 순서에 기댄다).
        // ⚠️ **결과를 모르면 그리지 않는다**(2026-10-08) — 양통과로 끝난 판은 계가 판정이 몇 초 뒤에 온다(사석을 가려야 한다). 그 사이의
        //   배지는 승자가 없어 「무승부」로 떴다 — 판정 결과 창이 뜨기 직전에 잠깐. 진짜 무승부는 판정이 **있고** 승자가 없는 판이다.
        val isResultKnown = isFinalResultKnown(screenState.gameState, screenState.finalScoreJudgement)
        if (shouldShowFinalResultBadge(screenState.isGameEnded, screenState.gameState.moves.size, isResultKnown)) {
            FinalResultBadge(
                gameState = screenState.gameState,
                judgement = screenState.finalScoreJudgement,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * **착수 칸** — 돌 모양 버튼 **하나**다(2026-10-09 사용자 지시). 그 전에는 [착수 모드 스위치]와 [착수] 버튼 둘이
 * 모드에 따라 크기를 맞바꿨다(#37).
 *
 * 한 버튼이 세 얼굴을 갖는다([PlayStoneFace]) — 무엇을 그리고 눌렀을 때 무엇을 하는지가 얼굴 하나로 함께 정해진다:
 * - **착수 확인 켜짐**: 진한 흰 돌 + `착수\n확인`. 누르면 끈다(바로 착수).
 * - **착수 확인 꺼짐**: 흐린 흰 돌 + 흐린 `착수\n확인`. 누르면 켠다.
 * - **착수 확정**: 착수 확인이 켜진 채 판에 가착수가 놓였을 때 — 초록 고리 + `착수`. 누르면 그 수를 둔다.
 *
 * ⚠️ **글자는 `착수 확인`으로 고정이고, 켜짐·꺼짐은 돌의 진하기가 말한다**(2026-10-09 사용자 지시) — 처음에는 글자를
 *   `바로 착수`로 바꿨는데, 글자가 바뀌면 "다른 버튼"으로 읽히고 한 기능의 켜짐·꺼짐으로 읽히지 않는다.
 *   그래서 처음 정했던 「켜짐 = 흐린 돌」도 뒤집혔다 — 글자가 고정이면 **흐린 쪽이 꺼짐**이어야 한다.
 * ⚠️ 켜고 끌 때의 안내 토스트는 여기가 아니라 `GamePlaySection`이 띄운다 — 메뉴의 스위치로 바꿔도 같은 말이 떠야 한다.
 * ⚠️ **크기는 부르는 쪽이 준다**([modifier]) — 폰은 좌석 카드 높이의 70%, 넓은 배치(#141)는 고정 크기.
 */
@Composable
internal fun PlaySlot(
    screenState: GameScreenState,
    tentativeMove: BoardCoordinate?,
    onEvent: (GameUiEvent) -> Unit,
    modifier: Modifier,
) {
    val strings = LocalUiStrings.current
    val isDirectPlay = screenState.uxOptions.isDirectPlayEnabled
    val face = playStoneFaceOf(isDirectPlay = isDirectPlay, hasTentativeMove = tentativeMove != null)
    PlayStoneButton(
        face = face,
        label = if (face == PlayStoneFace.CommitMove) strings.playMove else strings.confirmPlayOnStone,
        enabled = !screenState.isGameEnded,
        onClick = {
            if (face == PlayStoneFace.CommitMove) {
                tentativeMove?.let {
                    onEvent(GameUiEvent.SubmitMove(Move.Play(screenState.gameState.nextPlayer, it)))
                }
            } else {
                onEvent(
                    GameUiEvent.ChangeUxOptions(
                        screenState.uxOptions.copy(isDirectPlayEnabled = !isDirectPlay),
                    ),
                )
            }
        },
        modifier = modifier,
    )
}

/** 폰 배치에서 돌 버튼의 지름 ÷ 좌석 카드의 높이(2026-10-09 사용자 지시: 70% — 90%, 80%를 거쳐 줄였다. 버튼이 크게 느껴졌다). */
private const val PlayStoneSeatHeightShare = 0.7f

/** 폰 배치에서 돌 버튼과 양옆 좌석 카드 사이의 여백. */
private val PlayStoneSideGap = AppSpacing.Space10

/** 넓은 배치(#141)의 돌 버튼 지름 — 옆에 높이를 빌릴 좌석 카드가 없어 고정값이다. 두 줄 라벨이 1.3배에서도 들어간다. */
internal val WidePlayStoneSize = 64.dp

/**
 * 넓은 배치(#141)의 **좌석 카드** — 폰 배치의 [PlayerSeatCard]를 줄였다. 차례 표시(초록 테두리·배경)는
 * 폰과 같은 토큰이다.
 * - 위아래 배치(P1)의 위 줄: **두 줄** — `● 흑 (유저)` / `00:12 · 사석 0`.
 * - 좌우 기둥(L1)의 기둥: [stacked] — 기둥 폭이 100dp 남짓이라 **세 줄**로 접는다. 한 줄에 시계와
 *   사석을 같이 쓰면 그 폭에서 말줄임으로 잘린다.
 *
 * ⚠️ **높이는 바닥값이다**(함정 9번) — 배율이 오르면 줄이 자라야 한다. 사용자가 실물을 보고 세로를
 *   늘릴지 검토하기로 했고(2026-09-12) *"이대로 진행"* 으로 지금 값을 유지했다 — 늘린 만큼 판이 준다.
 */
@Composable
internal fun CompactSeatCard(
    isActiveTurn: Boolean,
    stoneGlyph: String,
    stoneGlyphColor: Color,
    label: String,
    elapsedMillis: Long,
    capturedCount: Int,
    capturesLabel: String,
    stacked: Boolean,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = CompactSeatCardMinHeight),
        color = if (isActiveTurn) ActiveStateContainerColor else InactiveStateContainerColor,
        border = if (isActiveTurn) ActiveStateBorder else InactiveStateBorder,
        shape = RoundedCornerShape(AppRadius.Corner8),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppSpacing.Space10, vertical = AppSpacing.Space5),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stoneGlyph, style = MaterialTheme.typography.titleSmall, color = stoneGlyphColor)
                Spacer(modifier = Modifier.width(AppSpacing.Space4))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GameStatusPalette.SeatLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (stacked) formatMillis(elapsedMillis) else "${formatMillis(elapsedMillis)} · $capturesLabel: $capturedCount",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isActiveTurn) FontWeight.Bold else FontWeight.Normal,
                color = if (isActiveTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stacked) {
                Text(
                    text = "$capturesLabel: $capturedCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 넓은 배치 좌석 카드의 바닥 높이. */
private val CompactSeatCardMinHeight = 48.dp

/**
 * 흑/백 진영 정보 카드. 대국 차례일 때 프라이머리 색으로 강조된다.
 */
@Composable
private fun PlayerSeatCard(
    modifier: Modifier,
    isActiveTurn: Boolean,
    stoneGlyph: String,
    stoneGlyphColor: Color,
    label: String,
    elapsedMillisText: String,
    capturedCount: Int,
    capturesLabel: String,
    alignEnd: Boolean,
) {
    val bg = if (isActiveTurn) ActiveStateContainerColor else InactiveStateContainerColor
    val border = if (isActiveTurn) ActiveStateBorder else InactiveStateBorder
    val timeColor = if (isActiveTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    Surface(
        modifier = modifier,
        color = bg,
        border = border,
        shape = RoundedCornerShape(AppRadius.Corner8)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppSpacing.Space8, vertical = AppSpacing.Space6),
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
        ) {
            // 상단: 진영 표시와 대국 시간을 분리해 좁은 카드에서도 읽기 쉽게 유지한다.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!alignEnd) {
                    Text(stoneGlyph, style = MaterialTheme.typography.titleMedium, color = stoneGlyphColor)
                    Spacer(modifier = Modifier.width(AppSpacing.Space4))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GameStatusPalette.SeatLabel,
                )
                if (alignEnd) {
                    Spacer(modifier = Modifier.width(AppSpacing.Space4))
                    Text(stoneGlyph, style = MaterialTheme.typography.titleMedium, color = stoneGlyphColor)
                }
            }
            Text(
                text = elapsedMillisText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActiveTurn) FontWeight.Bold else FontWeight.Normal,
                color = timeColor,
            )

            Spacer(modifier = Modifier.height(AppSpacing.Space4))

            Text(
                text = "$capturesLabel: $capturedCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatMillis(millis: Long): String {
    val seconds = (millis + 50L) / 1000L
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return formatDeviceLocale("%02d:%02d", minutes, remainingSeconds)
}

/**
 * 대국 상태판 **턴 카드의 활성 색**. 보드 위 토글(#39)이 *"켜짐/최대"* 를 같은 색으로 말해야
 * 해서(2026-08-31 사용자 지시) 인라인 값이던 것을 여기로 뺐다.
 *
 * ⚠️ **두 곳이 각자 색을 적어 두면 조용히 갈린다.** 한쪽 알파만 손대도 "같은 색"이라는 약속이
 * 깨지는데 그건 눈으로만 드러난다 — 그래서 값을 한 군데로 모았다.
 */
internal val ActiveStateContainerColor: Color
    @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

internal val ActiveStateBorder: BorderStroke
    @Composable get() = BorderStroke(AppBorderWidth.Emphasis, MaterialTheme.colorScheme.primary)

internal val InactiveStateContainerColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

internal val InactiveStateBorder: BorderStroke
    @Composable get() = BorderStroke(AppBorderWidth.Hairline, GameStatusPalette.InactiveStateBorder)

/** 결과 배지를 그릴 때인가(#191) — 끝났고, 수가 하나 이상이고, **결과를 알 때**만([isFinalResultKnown] — 계가 판정을 기다리는 동안은 아니다). */
internal fun shouldShowFinalResultBadge(isGameEnded: Boolean, moveCount: Int, isResultKnown: Boolean): Boolean =
    isGameEnded && moveCount > 0 && isResultKnown
