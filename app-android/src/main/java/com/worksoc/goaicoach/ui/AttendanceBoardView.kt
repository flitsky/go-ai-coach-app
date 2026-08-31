package com.worksoc.goaicoach.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.R
import com.worksoc.goaicoach.application.attendance.AttendanceBoard
import com.worksoc.goaicoach.application.attendance.AttendanceBoardCell
import com.worksoc.goaicoach.application.attendance.AttendanceCellState
import com.worksoc.goaicoach.application.attendance.AttendanceReward
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.application.consumable.ConsumableItem
import com.worksoc.goaicoach.application.premium.FeatureId

/**
 * 출석 도장판 그림(#55에서 만들고 #56에서 분리, **#57에서 글자판 → 그림판으로 다시 썼다**).
 *
 * ⚠️ **두 곳이 같은 그림을 쓴다** — Claim 팝업(`AttendanceRewardClaimDialog`)과 마이 페이지의
 * 읽기 전용 판(`MyPageScreen`). 한쪽에만 고치면 같은 화면이 두 모양으로 갈린다.
 *
 * ⚠️ **이 컴포저블은 지급하지 않는다.** 지급은 팝업의 Claim만 한다
 * (`docs/spec/ATTENDANCE_REWARD_POLICY.md` 1장). 여기에 탭 동작을 붙이지 말 것.
 *
 * ## #57에서 글자를 버린 이유
 * #55는 칸마다 보상을 **글자로** 적었는데, 여섯 칸 폭(360dp 기기에서 칸당 약 45dp)에서는
 * 짧은 표기("형세 30")조차 말줄임으로 뭉개졌다. 언어마다 길이가 달라(`추천`/`Moves`/`推荐`)
 * 한국어에 맞추면 다른 언어가 깨지는 구조적 문제이기도 했다.
 * **글리프는 길이가 없다** — 네 언어가 같은 폭을 쓰고, 뜻은 `contentDescription`이 지킨다.
 *
 * [collection]이 필요한 이유는 5·6일차와 7·28일차가 **캐릭터 얼굴**을 그리기 때문이다.
 * 아직 못 얻은 캐릭터는 흑백이고, 조각 경로는 모은 만큼만 색이 돈다(#50 재사용).
 */
@Composable
internal fun AttendanceStampBoard(
    board: AttendanceBoard,
    collection: BotCollectionState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StampRow(cells = board.daily, collection = collection, compact = true)
        StampRow(cells = board.weekly, collection = collection, compact = false)
    }
}

/**
 * 도장판 한 행. [compact]가 참이면 여섯 칸(1~6일차), 거짓이면 네 칸(주 단위)이다 —
 * 두 행이 **같은 전체 너비**를 나눠 쓰므로 네 칸 쪽이 자연히 넓다.
 */
@Composable
private fun StampRow(cells: List<AttendanceBoardCell>, collection: BotCollectionState, compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        cells.forEach { cell ->
            StampCell(cell = cell, collection = collection, compact = compact, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StampCell(
    cell: AttendanceBoardCell,
    collection: BotCollectionState,
    compact: Boolean,
    modifier: Modifier,
) {
    val strings = LocalUiStrings.current
    val stamped = cell.state == AttendanceCellState.Stamped
    val claimable = cell.state == AttendanceCellState.Claimable
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    val background = when {
        stamped -> MaterialTheme.colorScheme.surfaceVariant
        claimable -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            // ⚠️ #55의 `aspectRatio(1f)`를 버렸다. 45dp 정사각에는 회차·글리프·개수가 함께
            // 들어가지 못해 셋 중 하나가 늘 잘렸다. 세로로 조금 긴 칸이 도장 자리처럼도 보인다.
            .height(if (compact) CompactCellHeight else WideCellHeight)
            .clip(shape)
            .background(background)
            .border(
                BorderStroke(
                    width = if (claimable) 2.dp else 1.dp,
                    color = if (claimable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                shape,
            )
            .padding(horizontal = 3.dp, vertical = 6.dp)
            // 칸 하나를 한 덩어리로 읽어 준다 — 글리프만으로는 뜻이 없으므로 여기서 말로 바꾼다.
            .clearAndSetSemantics { contentDescription = describeCell(strings, cell) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            // ⚠️ 좁은 칸에는 **숫자만** 쓴다. "第 6 天"은 45dp 칸에서 잘리는데, 여섯 칸이
            // 1~6 순서로 늘어서 있으니 숫자만으로도 몇 일차인지 읽힌다. 온전한 회차 이름은
            // 넓은 칸과 `contentDescription`이 가진다.
            text = if (compact) cell.tier.toString() else strings.attendanceRewardDayLabel(cell.tier),
            style = if (compact) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelMedium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            RewardFaces(
                rewards = cell.rewards,
                collection = collection,
                compact = compact,
                dimmed = stamped,
                seamColor = background,
            )
            // ⚠️ 도장을 찍어도 **보상 그림을 지우지 않는다.** #55는 도장을 찍으면 내용을
            // 감췄는데, 그러면 다 채운 판이 똑같은 체크 열 개가 되어 "무엇을 받아 왔는지"가
            // 통째로 사라졌다. 지금은 흐려진 그림 위에 인장이 겹친다.
            if (stamped) StampSeal(compact = compact)
        }
    }
}

/**
 * 한 칸이 그리는 보상들.
 *
 * ⚠️ **좁은 칸은 첫 보상 하나만 그린다.** 확정표상 1~6일차는 회차마다 보상이 정확히 하나뿐이라
 * (`AttendanceRewardPolicyTest.everyCompactBoardTierCarriesExactlyOneReward`가 고정한다)
 * 이 단순화가 성립한다. 표를 고쳐 좁은 회차에 보상이 둘 이상 생기면 **그 테스트가 먼저 깨진다.**
 */
@Composable
private fun RewardFaces(
    rewards: List<AttendanceReward>,
    collection: BotCollectionState,
    compact: Boolean,
    dimmed: Boolean,
    seamColor: Color,
) {
    if (rewards.isEmpty()) return
    if (compact) {
        RewardFace(rewards.first(), collection, compact = true, dimmed = dimmed, seamColor = seamColor)
        return
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        rewards.forEach { reward ->
            RewardFace(reward, collection, compact = false, dimmed = dimmed, seamColor = seamColor)
        }
    }
}

/** 보상 하나의 그림. 캐릭터가 걸린 보상은 얼굴로, 나머지는 글리프 + 개수로 그린다. */
@Composable
private fun RewardFace(
    reward: AttendanceReward,
    collection: BotCollectionState,
    compact: Boolean,
    dimmed: Boolean,
    seamColor: Color,
) {
    val strings = LocalUiStrings.current
    val faceAlpha = if (dimmed) StampedFaceAlpha else 1f
    val character = rewardCharacterOf(reward)
    if (character != null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BotCharacterAvatar(
                character = character,
                modifier = Modifier.alpha(faceAlpha),
                size = if (compact) CompactAvatarSize else WideAvatarSize,
                available = collection.isClaimed(character.id),
                reveal = shardRevealOf(character, collection.shardsFor(character.id)),
                seamColor = seamColor,
            )
            // 좁은 칸에는 이름을 넣을 자리가 없다 — 넓은 칸(7·28일차)에만 이름을 붙인다.
            if (!compact) {
                Text(
                    text = botCharacterNameFor(strings.language, character.id),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        return
    }

    val glyph = rewardGlyphRes(reward) ?: return
    // 1개짜리에 "1"을 달면 눈만 시끄럽다 — 여러 개일 때만 수를 밝힌다.
    val amount = rewardAmountOf(reward)?.takeIf { it > 1 }
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = faceAlpha)
    if (compact) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Icon(painterResource(glyph), contentDescription = null, Modifier.size(CompactGlyphSize), tint = tint)
            // ⚠️ 개수가 없어도 줄은 **비운 채로 남긴다.** 없애면 그 칸만 글리프가 아래로 내려와
            // (무르기·조각 회차) 여섯 칸의 그림 높이가 들쭉날쭉해진다.
            Text(
                text = amount?.toString().orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = tint,
            )
        }
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(glyph), contentDescription = null, Modifier.size(WideGlyphSize), tint = tint)
        if (amount != null) {
            Text(
                text = amount.toString(),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                color = tint,
            )
        }
    }
}

/** 받아 간 칸에 겹치는 인장. 그림을 가리지 않도록 테두리 원 안에 표시 하나만 둔다. */
@Composable
private fun BoxScope.StampSeal(compact: Boolean) {
    val seal = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            // ⚠️ **가운데에 두지 말 것.** 처음에는 좁은 칸의 인장을 그림 한가운데에 찍었는데,
            // 22dp 글리프 위에 24dp 원이 앉아 **보상 그림이 통째로 가려졌다**(실기 확인) —
            // 도장을 겹치기로 한 이유(무엇을 받았는지 계속 보이게 한다)가 그 자리에서 무너진다.
            .align(Alignment.TopEnd)
            .size(if (compact) CompactSealSize else WideSealSize)
            .clip(CircleShape)
            .background(seal.copy(alpha = SealFillAlpha))
            .border(BorderStroke(1.5.dp, seal), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = StampMark,
            // 인장이 작아 `labelLarge`로는 원 밖으로 넘친다 — 좁은 칸에는 한 단계 작은 글자.
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = seal,
        )
    }
}

/**
 * 4계층(External Integration) — 보상 종류를 Android 그림 리소스로 옮기는 유일한 지점(#57).
 *
 * ⚠️ **`Resources.getIdentifier`를 쓰지 않는다.** [botAvatarRes]와 같은 이유다 — 이름을 실행
 * 중에 찾으면 R8이 리소스를 지웠는지 알 수 없고, `playInternal`에도 R8이 켜져 있다(#47).
 *
 * 캐릭터가 걸린 보상은 `null`이다 — 그쪽은 글리프가 아니라 **얼굴**로 그린다.
 */
@DrawableRes
internal fun rewardGlyphRes(reward: AttendanceReward): Int? = when (reward) {
    is AttendanceReward.PermanentFeature -> featureGlyphRes(reward.featureId)
    is AttendanceReward.Consumable -> consumableGlyphRes(reward.item)
    is AttendanceReward.BotCharacterShards -> null
    is AttendanceReward.BotCharacterUnlock -> null
}

/**
 * 소모품 한 종의 글리프. **출석 도장판과 마이 페이지 재고 목록이 이 표를 공유한다**(#60) —
 * 같은 1회권이 두 화면에서 다른 모양으로 보이면 같은 것으로 안 읽힌다.
 *
 * ⚠️ 이 `when`은 `else -> null`로 닫혀 있어 **컴파일러가 빠뜨림을 잡아 주지 않는다.** 카탈로그에
 * 소모품을 더하면 그 자리만 조용히 빈다 — `AttendanceBoardViewTest`가 그 그물이다.
 */
@DrawableRes
internal fun consumableGlyphRes(item: ConsumableItem): Int? = when (item) {
    ConsumableCatalog.EvalOnce -> R.drawable.reward_eval
    ConsumableCatalog.TopMovesOnce -> R.drawable.reward_top_moves
    ConsumableCatalog.PremiumOnce -> R.drawable.reward_ad_skip
    // 카탈로그에 없는 소모품(다운그레이드로 흘러든 옛 저장값 등)은 그림이 없다.
    else -> null
}

@DrawableRes
private fun featureGlyphRes(featureId: FeatureId): Int = when (featureId) {
    FeatureId.Undo -> R.drawable.reward_undo
    FeatureId.Eval -> R.drawable.reward_eval
    FeatureId.TopMoves -> R.drawable.reward_top_moves
    FeatureId.MoveReview -> R.drawable.reward_move_review
}

/** 이 보상이 걸고 있는 캐릭터. 조각도 해금도 같은 얼굴을 쓴다 — 다른 것은 색이 얼마나 도느냐다. */
internal fun rewardCharacterOf(reward: AttendanceReward): BotCharacter? = when (reward) {
    is AttendanceReward.BotCharacterShards -> reward.character
    is AttendanceReward.BotCharacterUnlock -> reward.character
    else -> null
}

/** 칸에 적을 수량. 수량이라는 개념이 없는 보상(영구 해금·캐릭터)은 `null`. */
internal fun rewardAmountOf(reward: AttendanceReward): Int? = when (reward) {
    is AttendanceReward.Consumable -> reward.amount
    is AttendanceReward.BotCharacterShards -> reward.amount
    else -> null
}

/**
 * 칸 하나를 소리로 읽어 주는 말. 글리프에는 글자가 없으므로 **이것이 유일한 뜻 전달 경로**다 —
 * 지우면 도장판이 스크린 리더에서 숫자 열 개가 된다.
 */
private fun describeCell(strings: UiStrings, cell: AttendanceBoardCell): String {
    val rewards = cell.rewards.joinToString(", ") { strings.attendanceRewardLabel(it) }
    val state = when (cell.state) {
        AttendanceCellState.Stamped -> attendanceStampedNoticeFor(strings.language)
        AttendanceCellState.Claimable -> strings.attendanceRewardClaimAction
        AttendanceCellState.Upcoming -> attendanceUpcomingNoticeFor(strings.language)
    }
    return listOf(strings.attendanceRewardDayLabel(cell.tier), rewards, state)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

/** 도장 표시. 이모지가 아니라 문자라 폰트가 없어도 네모로 깨지지 않는다. */
private const val StampMark: String = "✓"

/**
 * 받아 간 칸의 그림은 흐려지되 **사라지지는 않는다** — 무엇을 받았는지가 계속 읽혀야 한다.
 * ⚠️ 0.32에서 올렸다. 흑백 아바타(5·6일차)가 그 값에서는 형체를 알아볼 수 없었다(실기 확인).
 */
private const val StampedFaceAlpha: Float = 0.45f

private const val SealFillAlpha: Float = 0.12f

private val CompactCellHeight = 68.dp
private val WideCellHeight = 108.dp
private val CompactGlyphSize = 20.dp
private val WideGlyphSize = 16.dp
private val CompactAvatarSize = 26.dp
private val WideAvatarSize = 40.dp
private val CompactSealSize = 16.dp
private val WideSealSize = 20.dp
