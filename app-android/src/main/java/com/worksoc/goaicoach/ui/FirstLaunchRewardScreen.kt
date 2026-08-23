package com.worksoc.goaicoach.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.worksoc.goaicoach.application.attendance.runAttendanceCheckIn
import com.worksoc.goaicoach.application.attendance.runAttendanceRewardGrant
import com.worksoc.goaicoach.persistence.AttendanceStore
import com.worksoc.goaicoach.persistence.PremiumStateStore

/**
 * "이 기기에서 출석 기록이 한 번도 없었는가"를 판정하고, 그렇다면 체크인·보상 지급을 실행한
 * 뒤 [FirstLaunchRewardScreen]을 보여줄지 결정한다. `ui/GoCoachApp.kt`는 이 함수 호출 한 줄과
 * 조건부 렌더링만 하면 된다 — 실제 로직/상태는 전부 여기서 처리해 셸의 라인·상태훅 예산을
 * 지킨다(`buildPremiumUiState`와 같은 이유, `LayeringContractTest.goCoachAppStaysWithinShrinkingUiShellBudget`).
 *
 * 체크인/지급 자체는 앱 시작 시 백그라운드에서 도는 `AttendanceCheckInCoordinator`도 동시에
 * 수행하지만, 둘 다 멱등이라(같은 날 재실행 무시, `claimedTiers`로 중복 지급 방지) 경합이 나도
 * 최종 상태가 갈라지지 않는다. 보상 내용은 이미 결정돼 있으므로(1일차 = 무르기 무제한) 지급이
 * 실제로 끝나길 기다리지 않고 즉시 결과를 보여준다 — 그 사이 지급이 실패해도 다음 실행에서
 * 스스로 복구되고(`runAttendanceRewardGrant`), 무르기 자체는 기존 클레임 다이얼로그가 방어적
 * 폴백으로 남아 있다(`OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` 4.4절).
 */
@Composable
internal fun rememberFirstLaunchRewardGate(context: Context): FirstLaunchRewardGate {
    val attendanceStore = remember(context) { AttendanceStore(context) }
    val premiumStore = remember(context) { PremiumStateStore(context) }
    val isFirstEverLaunch = remember(context) { attendanceStore.load().attendanceCount == 0 }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(isFirstEverLaunch) {
        if (!isFirstEverLaunch) return@LaunchedEffect
        val checkInResult = runAttendanceCheckIn(
            request = AttendanceCheckInRequest(nowEpochMillis = System.currentTimeMillis()),
            store = attendanceStore,
        )
        runAttendanceRewardGrant(
            state = checkInResult.state,
            attendanceStore = attendanceStore,
            premiumStore = premiumStore,
        )
    }

    return FirstLaunchRewardGate(
        shouldShow = isFirstEverLaunch && !dismissed,
        onContinue = { dismissed = true },
    )
}

internal data class FirstLaunchRewardGate(
    val shouldShow: Boolean,
    val onContinue: () -> Unit,
)

/** Phase 1 범위: 오늘 받은 보상 결과만 보여준다(킥오프 플랜 5장) — 연출/애니메이션은 스코프 밖. */
@Composable
internal fun FirstLaunchRewardScreen(onContinue: () -> Unit) {
    val strings = LocalUiStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = strings.firstLaunchRewardTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "• " + strings.firstLaunchRewardUndoUnlimited,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinue) {
            Text(strings.firstLaunchRewardContinue)
        }
    }
}
