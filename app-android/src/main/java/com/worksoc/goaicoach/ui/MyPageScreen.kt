package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.consumable.ConsumableCatalog

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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        ) {
                            Text(
                                text = strings.consumableRewardName(item),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
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
        }
    }
}
