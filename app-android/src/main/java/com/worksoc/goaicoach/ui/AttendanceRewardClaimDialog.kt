package com.worksoc.goaicoach.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
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
 * (`OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` 5.1절).
 *
 * 이 파일은 원래 최초 실행 전용 전체화면(`FirstLaunchRewardScreen`, 백로그 #5)이었다. 보상 지급이
 * **자동 지급 → Claim 방식**으로 바뀌면서(5.1절) 세 가지가 달라졌다:
 * 1. 노출 조건이 "최초 실행인가"가 아니라 **"받을 보상이 남아 있는가"**다 — 매일 뜬다.
 * 2. 전체화면이 아니라 홈 위에 겹치는 다이얼로그다(매일 앞을 가로막지 않도록, 2026-08-24 사용자 확정).
 * 3. 화면에 들어오는 것만으로 지급되지 않는다 — **Claim 버튼을 눌러야 지급된다.**
 *
 * 그냥 닫으면(`나중에`/바깥 탭) 지급되지 않고 미지급으로 남아 다음 실행에 다시 뜬다 — 5.1절이
 * 명시한 동작이며, `claimedTiers`는 "팝업을 봤는가"가 아니라 "실제로 지급됐는가"만 기록한다.
 * 밀린 일차는 만료 없이 보관되고 **한 팝업에 모아 한 번의 Claim으로 전부 지급**한다(사용자 확정).
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

    LaunchedEffect(attendanceStore) {
        val checkIn = runAttendanceCheckIn(
            request = AttendanceCheckInRequest(nowEpochMillis = System.currentTimeMillis()),
            store = attendanceStore,
        )
        // 컬렉션까지 넘겨야 이미 다 모은 캐릭터의 조각이 팝업에 실리지 않는다 — 조각은 7일차마다
        // 영원히 반복되므로 이 필터가 없으면 매주 의미 없는 줄이 하나씩 남는다.
        pending = AttendanceRewardPolicy.pendingTiers(checkIn.state, botStore.load())
    }

    if (pending.isEmpty()) return

    AttendanceRewardClaimDialogContent(
        tiers = pending,
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
        // 지급하지 않고 목록만 비운다 — claimedTiers가 그대로라 다음 실행에 다시 뜬다.
        onDismiss = { pending = emptyList() },
    )
}

/** Phase 1 범위: 받을 보상 목록과 Claim 버튼만(킥오프 플랜 5장) — 연출/애니메이션은 스코프 밖. */
@Composable
private fun AttendanceRewardClaimDialogContent(
    tiers: List<AttendanceRewardTier>,
    onClaim: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.attendanceRewardTitle) },
        text = {
            // 밀린 일차가 여러 개면 목록이 길어질 수 있어 스크롤을 준다.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tiers.forEach { tier ->
                    Text(
                        text = strings.attendanceRewardDayLabel(tier.tier),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    tier.rewards.forEach { reward ->
                        Text(
                            text = "• " + strings.attendanceRewardLabel(reward),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClaim) { Text(strings.attendanceRewardClaimAction) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.attendanceRewardLaterAction) }
        },
    )
}
