package com.worksoc.goaicoach.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.application.attendance.AttendanceBoard
import com.worksoc.goaicoach.application.attendance.buildAttendanceBoard
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.attendance.grantedAmountOf
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.premium.PremiumState
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
 * ⚠️ **어떻게 닫든 지급된다**(2026-08-31 정책 변경, `ATTENDANCE_REWARD_POLICY.md` 1장).
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
 * ⚠️ [onPremiumChanged]도 **그 한 줄 안에** 넘긴다 — 예산에 여유가 0이라 줄이 늘면 그 테스트가 깨진다.
 *
 * 체크인 자체는 앱 시작 시 백그라운드에서 도는 `AttendanceCheckInCoordinator`도 동시에 수행하지만
 * 멱등이라(같은 UTC 날짜 안에서는 카운트가 오르지 않는다) 경합이 나도 최종 상태가 갈라지지 않는다.
 * 지급은 이제 그쪽에서 하지 않는다 — Claim이 유일한 지급 경로다.
 */
/**
 * [onPremiumChanged] — 지급으로 프리미엄 원장이 바뀌었을 때 **화면이 들고 있는 상태를 되읽게**
 * 하는 통로(백로그 #65).
 *
 * ⚠️ **없으면 3일차 보상이 그 세션 동안 먹지 않는다.** 지급은 저장소에 직접 쓰는데
 * `GoCoachApp`의 `premiumState`는 앱 실행당 한 번만 로드되므로, 무르기 영구 해금을 **받고도**
 * 게이팅이 계속 `Locked`로 판정한다(`FeatureAccessPolicy`가 그 인메모리 값을 본다). 저장소에는
 * 들어가 있어 다음 실행에서는 정상이라, "지급이 안 됐다"로 오진하기 쉬운 종류의 결함이었다.
 * 재고에 대해 [ConsumableUiState.refresh]가 하는 일과 **정확히 같은 처방**이다 — 아래 지급
 * 직후 주석이 같은 함정을 이미 기록해 두고 있었는데, 프리미엄 쪽만 빠져 있었다.
 */
/**
 * 출석 보상 팝업을 **다시 띄우라는 신호**(백로그 #71) — 개발자 테스트의 "출석 하루 진행" 버튼이
 * 쓴다.
 *
 * ## ⚠️ 저장소를 되감기만 하면 팝업이 다시 뜨지 않는다
 * 아래 [AttendanceRewardClaimDialog]가 받을 것을 계산하는 곳은 `LaunchedEffect(attendanceStore)`
 * 하나이고, 그 키인 저장소는 `remember(context)`라 **컴포지션 수명 내내 불변**이다. 게다가
 * 지급하면 `pending`이 비워지는데 그 상태 역시 키 없는 `remember`다. 즉 **디스크를 되감아도
 * 화면은 그 사실을 알 방법이 없다** — "저장하면 알아서 뜬다"고 가정하면 버튼이 아무 일도 안
 * 하는 것처럼 보인다.
 *
 * ## ⚠️ 그래서 신호를 이 파일 안에 둔다 — 셸이 아니라
 * `GoCoachApp.kt`는 라인 예산 **880/880**, 상태훅 **46/46**으로 여유가 정확히 0이다(함정 3번).
 * 모듈 내 `object`로 두면 셸에 **한 줄도** 늘지 않는다.
 *
 * ⚠️ **`Activity.recreate()`로 콜드부트를 흉내내지 말 것** — `MainActivity`의
 * `LaunchedEffect(Unit)`이 엔진 부트스트랩을 다시 돌려 "Preparing …"으로 떨어지고 **진행 중
 * 대국이 날아간다.** 이 신호는 그 대신이다.
 */
internal object AttendanceClaimReplaySignal {
    /** 올릴 때마다 팝업 계산이 한 번 더 돈다. 값 자체에는 뜻이 없다 — 변하기만 하면 된다. */
    var revision by mutableStateOf(0)
        private set

    /**
     * 체크인을 **실제로 돌린 뒤의** 출석일. 개발자 버튼의 부제가 이 값을 읽는다.
     *
     * ⚠️ **버튼 쪽에서 저장소를 직접 읽으면 한 일차 뒤처진다** — 되감기는 표시만 지우고 실제
     * 증가는 아래 [AttendanceRewardClaimDialog]의 effect가 하므로, 누른 직후에 읽으면 **증가
     * 전 값**이 잡힌다(2026-09-04 실기에서 부제가 "지금 3일차"에 멈춰 있는 것으로 드러났다).
     * 그래서 **증가를 아는 쪽이 알려 준다** — effect 순서에 기대지 않는 유일한 방법이다.
     */
    var lastCheckedInDay by mutableStateOf<Int?>(null)
        private set

    fun request() {
        revision++
    }

    fun publishCheckedInDay(day: Int) {
        lastCheckedInDay = day
    }
}

@Composable
internal fun AttendanceRewardClaimDialog(
    context: Context,
    onPremiumChanged: (PremiumState) -> Unit,
) {
    val consumables = LocalConsumableUiState.current
    val bots = LocalBotCharacterUiState.current
    val attendanceStore = remember(context) { AttendanceStore(context) }
    val premiumStore = remember(context) { PremiumStateStore(context) }
    val consumableStore = remember(context) { ConsumableInventoryStore(context) }
    val botStore = remember(context) { BotCollectionStore(context) }
    var pending by remember { mutableStateOf(emptyList<AttendanceRewardTier>()) }
    // 도장판(#55)은 "받을 것"만이 아니라 **10회차 전체**를 그리므로 보드와 재고를 함께 읽는다.
    var board by remember { mutableStateOf<AttendanceBoard?>(null) }
    var inventory by remember { mutableStateOf(ConsumableInventory()) }
    // 판이 캐릭터 얼굴을 그리므로(#57) 수집 상태도 화면이 들고 있어야 한다 — 아직 못 얻은
    // 캐릭터는 흑백, 조각 경로는 모은 만큼만 색이 돈다.
    var collection by remember { mutableStateOf(BotCollectionState()) }

    LaunchedEffect(attendanceStore, AttendanceClaimReplaySignal.revision) {
        val checkIn = runAttendanceCheckIn(
            request = AttendanceCheckInRequest(nowEpochMillis = System.currentTimeMillis()),
            store = attendanceStore,
        )
        // 컬렉션까지 넘겨야 이미 다 모은 캐릭터의 조각이 팝업에 실리지 않는다 — 조각은 7일차마다
        // 영원히 반복되므로 이 필터가 없으면 매주 의미 없는 줄이 하나씩 남는다.
        // 개발자 버튼의 부제가 뒤처지지 않게, 체크인 결과를 알려 준다(위 KDoc 참고).
        AttendanceClaimReplaySignal.publishCheckedInDay(checkIn.state.attendanceCount)
        val loaded = botStore.load()
        collection = loaded
        pending = AttendanceRewardPolicy.pendingTiers(checkIn.state, loaded)
        board = buildAttendanceBoard(checkIn.state, loaded)
        inventory = consumableStore.load()
    }

    if (pending.isEmpty()) return
    val shownBoard = board ?: return

    AttendanceRewardClaimDialogContent(
        board = shownBoard,
        collection = collection,
        inventory = inventory,
        tiers = pending,
        // ⚠️ 확인 버튼과 뒤로 가기·바깥 탭이 **같은 함수**를 부른다 — 정책상 닫는 방법에 따라
        // 결과가 달라지면 안 된다(위 KDoc 참고).
        onClaim = {
            // 저장소에서 다시 읽어 지급한다 — 다이얼로그가 떠 있는 동안 백그라운드 체크인으로
            // 일차가 하나 더 늘었을 수 있고, 그 경우까지 이 한 번의 Claim으로 받아 가는 게 맞다.
            val result = runAttendanceRewardGrant(
                state = attendanceStore.load(),
                attendanceStore = attendanceStore,
                premiumStore = premiumStore,
                consumableStore = consumableStore,
                botStore = botStore,
            )
            // ⚠️ 지급은 저장소에 **직접** 쓴다 — 화면이 들고 있는 재고에게 알려 주지 않으면
            // 다음 실행 전까지 옛 값이 남는다(마이 페이지에서 "도장은 찍혔는데 0개"로 드러났다).
            consumables.refresh()
            // ⚠️ 프리미엄도 같은 이유로 되읽는다(#65) — 3일차 보상이 무르기 영구 해금이고,
            // 이 한 줄이 없으면 받은 그 세션 내내 무르기가 잠긴 채로 남는다.
            // ⚠️ 이 호출은 **`onClaim` 안**에 있어야 한다 — 확인 버튼과 뒤로 가기·바깥 탭이 모두
            // 이 함수를 타므로(위 `onDismissRequest`), 버튼 쪽에만 걸면 닫아서 받은 사용자에게
            // 조용히 누락된다.
            onPremiumChanged(premiumStore.load())
            // ⚠️ 컬렉션도 되읽는다 — 캐릭터/조각은 이 다이얼로그가 `botStore`에 직접 썼고,
            // 픽커가 든 사본은 그것을 모른다(그 사본이 낡는 문제를 `refresh`가 위해 존재한다).
            bots.refresh()
            // **획득 축전은 대기열에 넣기만 한다**(백로그 #69). 여기서 바로 띄우려 들면 지금
            // 떠 있는 Claim 팝업과 **두 윈도우가 같은 프레임에** 공존한다 — Compose 다이얼로그는
            // 선언 순서로 z축이 정해지지 않아(함정 7번) 어느 쪽이 위로 올지 보장이 없다.
            // 바로 아래 `pending = emptyList()`가 이 팝업을 내리고, 그 다음 컴포지션에서
            // `buildBotCharacterUiState`가 대기열 맨 앞을 띄운다.
            // ⚠️ 밀린 회차가 캐릭터를 둘 이상 줄 수 있다(7·28일차) — 목록 그대로 넘겨 **지급
            // 순서대로 하나씩** 축전한다(2026-09-03 사용자 결정).
            bots.enqueueAcquired(result.acquiredCharacters)
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
    collection: BotCollectionState,
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
                AttendanceStampBoard(board, collection)
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
