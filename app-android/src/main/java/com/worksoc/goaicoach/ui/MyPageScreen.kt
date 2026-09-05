package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.attendance.buildAttendanceBoard
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog
import com.worksoc.goaicoach.persistence.AttendanceStore
import com.worksoc.goaicoach.persistence.BotCollectionStore

/**
 * 3 Depth: 마이 페이지 — 지금은 **보유한 1회권 재고**만 보여준다(백로그 #24).
 *
 * **왜 새 목적지인가**(2026-08-30 사용자 확정, ⓒ안): 설정 화면 한 절이나 홈 카드로 끼워 넣는
 * 것이 더 쌌지만, 여기는 앞으로 **출석 현황과 캐릭터 컬렉션이 붙을 자리**다. 그 둘이 들어올 때
 * 설정 화면을 다시 쪼개는 것보다 지금 자리를 잡아 두는 편이 낫다.
 *
 * **왜 대국 화면에서 뺐는가**: #17이 재고 바를 사용처 바로 위에 상시 띄웠던 것은 "차감이 눈앞에서
 * 보이게" 하려는 의도였지만, 대국 내내 필요한 정보가 아닌 데다 바로 아래 버튼과 같은 말을 두 번
 * 했다. 남은 수는 이제 버튼 자신이 괄호로 말한다(`UiStrings.featureButtonLabel`).
 *
 * `GameHistoryScreen`과 같은 이유로 상태를 `GoCoachApp.kt`에 두지 않는다 — 재고는
 * [LocalConsumableUiState]가 이미 트리 전역에 공급하고 있으므로 여기서 읽기만 한다.
 */
@Composable
internal fun MyPageScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val consumables = LocalConsumableUiState.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // targetSdk 36부터 시스템 바 영역까지 앱이 그린다 — 이 한 줄이 없으면 제목과
                // 뒤로가기가 상태 표시줄(시계·배터리) 아래에 깔린다(#25). 설정·학습 화면이
                // 쓰는 것과 같은 자리·같은 방식이다.
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = strings.close,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = strings.myPageTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AttendanceBoardSection()
            Text(
                text = strings.myPageInventoryTitle,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ActionButtonShape,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 재고가 0인 것도 **숨기지 않는다.** 대국 화면의 옛 재고 바는 0을 감췄지만
                    // (`ConsumableInventoryBar`), 여기서는 "무엇을 가질 수 있는가"를 보여주는 것도
                    // 목적이라 0도 그대로 적는다 — 없다는 사실 자체가 정보다.
                    ConsumableCatalog.all.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 이름 앞에 **출석 도장판과 같은 글리프**를 붙인다(#60) — 위(도장판)는
                            // 그림으로, 아래(재고)는 글자로만 말하고 있어 같은 1회권이 두 모양으로
                            // 보였다. 표는 `consumableGlyphRes` 하나를 공유한다.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                consumableGlyphRes(item)?.let { glyph ->
                                    Icon(
                                        painter = painterResource(glyph),
                                        // 바로 옆 이름이 같은 것을 말한다 — 두 번 읽히면 소음이다.
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = strings.consumableRewardName(item),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = strings.consumableRewardAmount(consumables.countOf(item)),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Text(
                text = strings.myPageInventoryHint,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            
            // **기기에만 저장된다는 고지**(백로그 #74 ⓒ, 2026-09-05 사용자 발주).
            //
            // ⚠️ **자리를 여기로 고른 이유**: 이 화면이 바로 잃게 되는 것들(출석 도장판·모은
            // 캐릭터·1회권)을 나열하는 곳이다. 정책을 그것들과 **같은 화면**에서 읽어야
            // *"내가 지금 보고 있는 이것들"* 로 연결된다 — 별도 공지 화면에 두면 볼 이유가 없다.
            // ⚠️ 새 목적지를 만들지 않았다 — `GoCoachApp.kt`의 라인 예산이 880/880이다(함정 3번).
            // ⚠️ **구매 복원은 아직 적지 않는다** — `isPurchaseEnabled`가 꺼져 있어 구매 자체가
            //   불가능하다. 복원 문장은 #74 ⓐ가 열릴 때 함께 붙인다.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ActionButtonShape,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = strings.localOnlyDataNoticeTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = strings.localOnlyDataNoticeBody,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 출석 도장판을 **읽기 전용**으로 보여준다(백로그 #56).
 *
 * #55가 만든 판은 Claim 팝업 안에만 있어서, 다 받고 나면 자기 진행도를 볼 길이 없었다.
 * 여기서는 같은 판을 언제든 볼 수 있게 한다.
 *
 * ⚠️ **체크인을 하지 않는다.** 저장된 상태만 읽는다 — 출석 판정은 앱 시작 시 한 번
 * (`AttendanceRewardClaimDialog`)이라는 구조를 건드리면 화면을 여는 것만으로 일차가 오르는
 * 사고가 난다.
 *
 * ⚠️ **Claim 버튼을 두지 않는다.** 지급 경로가 둘이 되면 밀린 회차 계산이 갈린다 —
 * 받을 것이 있으면 다음 실행에 팝업이 알아서 뜬다(킥오프 5.1절).
 */
@Composable
private fun AttendanceBoardSection() {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    // 화면을 여는 시점의 저장값 한 번이면 된다 — 이 화면에 있는 동안 출석이 바뀌지 않는다.
    // 판이 캐릭터 얼굴을 그리므로(#57) 수집 상태를 판과 그림 양쪽에 넘긴다.
    val collection = remember(context) { BotCollectionStore(context).load() }
    val board = remember(context) {
        buildAttendanceBoard(AttendanceStore(context).load(), collection)
    }

    Text(
        text = attendanceBoardSectionTitleFor(strings.language),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ActionButtonShape,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AttendanceStampBoard(board, collection)
            Text(
                text = attendanceBoardBeyondNoticeFor(strings.language),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
