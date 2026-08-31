package com.worksoc.goaicoach.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.application.attendance.AttendanceBoard
import com.worksoc.goaicoach.application.attendance.AttendanceBoardCell
import com.worksoc.goaicoach.application.attendance.AttendanceCellState
import com.worksoc.goaicoach.application.attendance.buildAttendanceBoard
import com.worksoc.goaicoach.application.attendance.grantedAmountOf
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.attendance.AttendanceCheckInRequest
import com.worksoc.goaicoach.application.attendance.AttendanceRewardPolicy
import com.worksoc.goaicoach.application.attendance.AttendanceRewardTier
import com.worksoc.goaicoach.application.attendance.runAttendanceCheckIn
import com.worksoc.goaicoach.application.attendance.runAttendanceRewardGrant
import com.worksoc.goaicoach.persistence.AttendanceStore
import com.worksoc.goaicoach.persistence.BotCollectionStore
import com.worksoc.goaicoach.persistence.ConsumableInventoryStore
import com.worksoc.goaicoach.persistence.PremiumStateStore

/**
 * 출석 체크인을 실행하고, **아직 받아 가지 않은 보상이 있으면** Claim 다이얼로그를 띄운다
 * (`260823-260830_OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN.md` 5.1절).
 *
 * 이 파일은 원래 최초 실행 전용 전체화면(`FirstLaunchRewardScreen`, 백로그 #5)이었다. 보상 지급이
 * **자동 지급 → Claim 방식**으로 바뀌면서(5.1절) 세 가지가 달라졌다:
 * 1. 노출 조건이 "최초 실행인가"가 아니라 **"받을 보상이 남아 있는가"**다 — 매일 뜬다.
 * 2. 전체화면이 아니라 홈 위에 겹치는 다이얼로그다(매일 앞을 가로막지 않도록, 2026-08-24 사용자 확정).
 * 3. 화면에 들어오는 것만으로 지급되지 않는다 — **Claim 버튼을 눌러야 지급된다.**
 *
 * ⚠️ **어떻게 닫든 지급된다**(2026-08-31 정책 변경, `docs/ATTENDANCE_REWARD_POLICY.md` 1장).
 * 데일리 보상은 **고정 불변의 자동 수령**이고 목적은 모으게 하는 것이 아니라 **제때 쓰게 하는
 * 것**이라, 받는 시점을 사용자가 조절할 여지를 두지 않는다. 그래서 `나중에` 버튼을 없앴고,
 * **뒤로 가기·바깥 탭도 같은 지급 경로를 탄다** — 버튼만 없애면 정책은 문서에만 있고 숨은 보류
 * 경로가 남기 때문이다.
 * · 이전(킥오프 5.1절)에는 "닫으면 미지급으로 남아 다음 실행에 다시 뜬다"였다. **되돌리지 말 것** —
 *   그 목적("무엇을 받았는지 보게 한다")은 팝업이 내용을 보여주는 것만으로 이미 달성된다.
 * 밀린 일차는 만료 없이 보관되고 **한 팝업에 모아 한 번에 전부 지급**한다(사용자 확정).
 *
 * `GoCoachApp.kt`는 이 함수 호출 한 줄만 하면 된다 — 저장소 네 개와 상태를 전부 여기서 들고 있어
 * 셸의 라인·상태훅 예산을 지킨다(`LayeringContractTest.goCoachAppStaysWithinShrinkingUiShellBudget`).
 *
 * 체크인 자체는 앱 시작 시 백그라운드에서 도는 `AttendanceCheckInCoordinator`도 동시에 수행하지만
 * 멱등이라(같은 UTC 날짜 안에서는 카운트가 오르지 않는다) 경합이 나도 최종 상태가 갈라지지 않는다.
 * 지급은 이제 그쪽에서 하지 않는다 — Claim이 유일한 지급 경로다.
 */
@Composable
internal fun AttendanceRewardClaimDialog(context: Context) {
    val attendanceStore = remember(context) { AttendanceStore(context) }
    val premiumStore = remember(context) { PremiumStateStore(context) }
    val consumableStore = remember(context) { ConsumableInventoryStore(context) }
    val botStore = remember(context) { BotCollectionStore(context) }
    var pending by remember { mutableStateOf(emptyList<AttendanceRewardTier>()) }
    // 도장판(#55)은 "받을 것"만이 아니라 **10회차 전체**를 그리므로 보드와 재고를 함께 읽는다.
    var board by remember { mutableStateOf<AttendanceBoard?>(null) }
    var inventory by remember { mutableStateOf(ConsumableInventory()) }

    LaunchedEffect(attendanceStore) {
        val checkIn = runAttendanceCheckIn(
            request = AttendanceCheckInRequest(nowEpochMillis = System.currentTimeMillis()),
            store = attendanceStore,
        )
        // 컬렉션까지 넘겨야 이미 다 모은 캐릭터의 조각이 팝업에 실리지 않는다 — 조각은 7일차마다
        // 영원히 반복되므로 이 필터가 없으면 매주 의미 없는 줄이 하나씩 남는다.
        val collection = botStore.load()
        pending = AttendanceRewardPolicy.pendingTiers(checkIn.state, collection)
        board = buildAttendanceBoard(checkIn.state, collection)
        inventory = consumableStore.load()
    }

    if (pending.isEmpty()) return
    val shownBoard = board ?: return

    AttendanceRewardClaimDialogContent(
        board = shownBoard,
        inventory = inventory,
        tiers = pending,
        // ⚠️ 확인 버튼과 뒤로 가기·바깥 탭이 **같은 함수**를 부른다 — 정책상 닫는 방법에 따라
        // 결과가 달라지면 안 된다(위 KDoc 참고).
        onClaim = {
            // 저장소에서 다시 읽어 지급한다 — 다이얼로그가 떠 있는 동안 백그라운드 체크인으로
            // 일차가 하나 더 늘었을 수 있고, 그 경우까지 이 한 번의 Claim으로 받아 가는 게 맞다.
            runAttendanceRewardGrant(
                state = attendanceStore.load(),
                attendanceStore = attendanceStore,
                premiumStore = premiumStore,
                consumableStore = consumableStore,
                botStore = botStore,
            )
            pending = emptyList()
        },

    )
}

/**
 * 출석 도장판(백로그 #55). 위 여섯 칸은 1~6일차, 아래 네 칸은 7·14·21·28일차다.
 *
 * ⚠️ **아래 행이 넓은 데는 이유가 있다** — 주 단위 보상이라 내용이 크고(캐릭터 해금이 둘),
 * 여섯 칸 폭에 우겨넣으면 무엇을 받는지가 안 읽힌다. 그래서 **같은 전체 너비를 네 칸이 나눠 쓴다**
 * (사용자 지정). 두 행의 좌우 여백이 어긋나면 판으로 안 보이므로 폭 계산을 함부로 바꾸지 말 것.
 *
 * ⚠️ **판을 보여주는 것만으로 지급되지 않는다** — 지급은 Claim 버튼이 한다(킥오프 5.1절).
 */
@Composable
private fun AttendanceRewardClaimDialogContent(
    board: AttendanceBoard,
    inventory: ConsumableInventory,
    tiers: List<AttendanceRewardTier>,
    onClaim: () -> Unit,
) {
    val strings = LocalUiStrings.current
    AlertDialog(
        // 뒤로 가기·바깥 탭도 지급이다 — 보류 경로를 남기지 않는다(정책 1장).
        onDismissRequest = onClaim,
        // 판이 여섯 칸 너비를 쓰므로 기본 다이얼로그 폭으로는 칸이 뭉개진다.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.94f),
        title = { Text(strings.attendanceRewardTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StampRow(cells = board.daily, compact = true)
                StampRow(cells = board.weekly, compact = false)
                if (board.beyondBoard.isNotEmpty()) {
                    Text(
                        text = attendanceBoardBeyondNoticeFor(strings.language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                // 실제로 받는 것을 따로 적는다 — 판은 "전체 여정", 이쪽은 "지금 들어올 것"이다.
                ClaimDetail(tiers = tiers, inventory = inventory)
            }
        },
        confirmButton = {
            Button(onClick = onClaim) { Text(strings.attendanceRewardClaimAction) }
        },
    )
}

/**
 * 도장판 한 행. [compact]가 참이면 여섯 칸(1~6일차), 거짓이면 네 칸(주 단위)이다 —
 * 두 행이 **같은 전체 너비**를 나눠 쓰므로 네 칸 쪽이 자연히 넓고 높아진다.
 */
@Composable
private fun StampRow(cells: List<AttendanceBoardCell>, compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        cells.forEach { cell ->
            StampCell(cell = cell, compact = compact, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StampCell(cell: AttendanceBoardCell, compact: Boolean, modifier: Modifier) {
    val strings = LocalUiStrings.current
    val stamped = cell.state == AttendanceCellState.Stamped
    val claimable = cell.state == AttendanceCellState.Claimable
    val shape = RoundedCornerShape(if (compact) 8.dp else 12.dp)
    val background = when {
        stamped -> MaterialTheme.colorScheme.surfaceVariant
        claimable -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            // 여섯 칸은 정사각, 네 칸은 더 넓고 높게 — 보상 내용을 두어 줄 담아야 한다.
            .then(if (compact) Modifier.aspectRatio(1f) else Modifier.height(108.dp))
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
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = strings.attendanceRewardDayLabel(cell.tier),
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stamped) {
            // 도장. 글자 하나라 어느 언어에서도 잘리지 않는다.
            Text(
                text = StampMark,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                // 좁은 6칸은 짧은 표기, 넓은 4칸은 전체 문구 — 4칸이 넓은 이유가 여기서 드러난다.
                text = cell.rewards.joinToString("\n") { reward ->
                    if (compact) {
                        attendanceRewardShortLabelFor(strings.language, reward)
                    } else {
                        strings.attendanceRewardLabel(reward)
                    }
                }.ifBlank { attendanceUpcomingNoticeFor(strings.language) },
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 3 else 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 지금 Claim하면 들어올 것들. ⚠️ **상한에 걸리는 항목은 그 사실을 밝힌다** — 밝히지 않으면
 * "3개"라고 안내해 놓고 0개가 들어간다(#55 ⓑ).
 */
@Composable
private fun ClaimDetail(tiers: List<AttendanceRewardTier>, inventory: ConsumableInventory) {
    val strings = LocalUiStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tiers.forEach { tier ->
            Text(
                text = strings.attendanceRewardDayLabel(tier.tier),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            tier.rewards.forEach { reward ->
                val granted = grantedAmountOf(reward, inventory)
                val atCap = granted == 0
                Text(
                    text = "• " + strings.attendanceRewardLabel(reward) +
                        if (atCap) " (" + attendanceAtStockCapNoticeFor(strings.language) + ")" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (atCap) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/** 도장 표시. 이모지가 아니라 문자라 폰트가 없어도 네모로 깨지지 않는다. */
private const val StampMark: String = "\u2713"
