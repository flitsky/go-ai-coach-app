package com.worksoc.goaicoach.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
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
 * (`ATTENDANCE_REWARD_POLICY.md` 1장). 여기에 탭 동작을 붙이지 말 것.
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
 *
 * ## 높이를 내용에 맡기되, 한 행은 같은 높이로 묶는다 (#64 ⓐ)
 * 칸 높이가 고정 `dp`였을 때 **글자만 폰트 배율을 따라 커지고 칸은 그대로**여서, 배율 1.3배에서
 * 좁은 칸의 개수 줄(`30`·`3`)과 넓은 칸의 셋째 보상 줄(`▶| 3`)이 아래 절반부터 잘렸다
 * (2026-09-01 실기 확인). [IntrinsicSize.Min]이 이 행에서 **가장 높은 칸**을 먼저 재고,
 * 각 칸의 `fillMaxHeight()`가 나머지를 거기에 맞춘다.
 *
 * ⚠️ **행 단위로 묶는 것이 요점이다.** 칸마다 `heightIn`만 걸면 글리프 칸(1~4일차)은 자라고
 * 얼굴 칸(5·6일차)은 바닥값에 머물러 **한 행의 칸 높이가 들쭉날쭉해진다** — #57이 빈 줄까지
 * 남겨 가며 맞춰 둔 그 정렬이 무너진다.
 */
@Composable
private fun StampRow(cells: List<AttendanceBoardCell>, collection: BotCollectionState, compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        cells.forEach { cell ->
            StampCell(
                cell = cell,
                collection = collection,
                compact = compact,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
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
            // ⚠️ **`height`가 아니라 `heightIn(min=)`이다**(#64 ⓐ) — 이것은 배율 1.0배의 모양을
            // 그대로 지키는 **바닥값**일 뿐이고, 글자가 커지면 칸이 따라 자란다. 실제 높이는
            // [StampRow]가 행 단위로 정한다.
            .heightIn(min = if (compact) CompactCellMinHeight else WideCellMinHeight)
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

/**
 * 받아 간 칸에 겹치는 인장. 그림을 가리지 않도록 테두리 원 안에 표시 하나만 둔다.
 *
 * ## ⚠️ 인장도 #64 ⓐ와 같은 뿌리로 잘리고 있었다 (2026-09-01 사용자 승인으로 함께 고침)
 * 원은 고정 `dp`인데 안의 [StampMark]만 폰트 배율을 따라 커져서, 배율 2.0배에서는 체크가 통째로
 * 잘리고 **빈 동그라미만** 남았다 — 이 칸의 유일한 "받아 감" 표시가 그 배율에서 사라졌다.
 *
 * ⚠️ **고친 축은 원이 아니라 글자다.** 처음에는 [CompactSealSize]를 배율만큼 키웠는데, 배율
 * 2.0배에서 32dp 원이 20dp 글리프 위에 앉아 **1·3·5·6일차의 보상 그림이 통째로 가려졌다**(실기
 * 확인) — 아래 `TopEnd` 주석이 경고하는 바로 그 사고가 크기 축으로 재현된 것이다. 그래서 원을
 * 그대로 두고 **표시를 `dp`에 묶었다**: `dp.toSp()`는 지금 밀도로 환산한 값이라 배율이 얼마든
 * 화면에 찍히는 크기가 같다.
 *
 * ✅ **이 표시는 글자가 아니라 그림이다** — 칸 전체가 `clearAndSetSemantics`로 묶여 있어 뜻은
 * [describeCell]이 말로 전하고, 인장은 스크린 리더에 읽히지도 않는다. 그래서 배율을 안 따라가도
 * 잃는 것이 없다. 반대로 보상 그림을 가리면 #57이 도장을 겹치기로 한 이유가 무너진다.
 */
@Composable
private fun BoxScope.StampSeal(compact: Boolean) {
    val seal = MaterialTheme.colorScheme.primary
    // 인장이 작아 `labelLarge`로는 원 밖으로 넘친다 — 좁은 칸에는 한 단계 작은 글자.
    val markSize = with(LocalDensity.current) {
        (if (compact) CompactMarkSize else WideMarkSize).toSp()
    }
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
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = markSize,
                // ⚠️ 글자 크기만 묶고 줄 높이를 두면 **줄 높이가 배율을 따라 커져** 글자 상자가
                // 원보다 높아지고, 잘림이 그대로 돌아온다. 폰트 기본값(`Unspecified`)은 글자
                // 크기에 비례하므로 함께 묶인다.
                lineHeight = TextUnit.Unspecified,
            ),
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

/**
 * 칸 높이의 **바닥값**(#64 ⓐ에서 `height` → `heightIn(min=)`으로 성격이 바뀌었다).
 * 배율 1.0배에서 내용이 요구하는 높이보다 조금 커서, 그 배율의 모양은 예전 그대로다.
 * ⚠️ 상한이 아니다 — 여기서 값을 올리면 **모든 배율에서** 칸이 함께 커진다.
 */
private val CompactCellMinHeight = 68.dp
private val WideCellMinHeight = 108.dp
private val CompactGlyphSize = 20.dp
private val WideGlyphSize = 16.dp
private val CompactAvatarSize = 26.dp
private val WideAvatarSize = 40.dp
private val CompactSealSize = 16.dp
private val WideSealSize = 20.dp

/**
 * 인장 표시([StampMark])의 크기 — **`sp`가 아니라 `dp`다**(#64). 원 안에 갇힌 그림이라 배율을
 * 따라가면 안 되고, 배율 1.0배에서 쓰던 `labelSmall`(11sp)·`labelMedium`(12sp)과 같은 값이다.
 */
private val CompactMarkSize = 11.dp
private val WideMarkSize = 12.dp
