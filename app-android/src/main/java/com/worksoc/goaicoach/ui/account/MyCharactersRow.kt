package com.worksoc.goaicoach.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.ui.designsystem.ActionButtonShape
import com.worksoc.goaicoach.ui.designsystem.AppElevation
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.BotCharacterAvatar
import com.worksoc.goaicoach.ui.designsystem.shardRevealOf
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.myCharactersTitleFor
import com.worksoc.goaicoach.ui.monetization.BotCharacterRosterHost
import com.worksoc.goaicoach.ui.monetization.LocalBotCharacterUiState

/**
 * 내 정보의 「내 캐릭터」 줄(#256, 사용자 2026-10-13) — 다섯 얼굴(가진 것은 색, 못 가진 것은 흑백·조각 진행)과 「가진 수/전체」.
 * 누르면 상대 고르기와 **같은 카드 캐러셀**이 열린다 — **보기 + 광고로 조각 모으기**만 되고 고르기는 없다(고르기는 캐릭터 대국에서만:
 * 여기서 상대를 바꾸면 이어 두던 대국의 상대까지 바뀐다). 캐러셀과 광고 흐름은 [BotCharacterRosterHost] 한 벌이다.
 *
 * ⚠️ 열림 상태는 이 줄이 든다 — 광고 Activity에 다녀와도 내 정보 화면은 그대로 남아 호스트의 스코프가 살아 있다(#20의 전제).
 */
@Composable
internal fun MyCharactersRow(modifier: Modifier = Modifier) {
    val language = LocalUiStrings.current.language
    val bots = LocalBotCharacterUiState.current
    var showRoster by remember { mutableStateOf(false) }
    val roster = BotCharacterCatalog.fastBeginnerRoster
    val owned = roster.count { bots.isAvailable(it) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showRoster = true },
        shape = ActionButtonShape,
        tonalElevation = AppElevation.Level1,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
        ) {
            Text(
                text = myCharactersTitleFor(language),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // 얼굴 다섯은 한 덩어리로 촘촘히 — 360dp 폭에서 제목이 밀리지 않게 한다.
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                roster.forEach { character ->
                    val available = bots.isAvailable(character)
                    BotCharacterAvatar(
                        character = character,
                        size = 24.dp,
                        available = available,
                        // 조각 경로는 모은 만큼만 색이 돈다 — 출석 도장판·픽커 카드와 같은 그림(#50).
                        reveal = if (available) null else shardRevealOf(character, bots.shardsFor(character)),
                    )
                }
            }
            Text(
                text = "$owned/${roster.size}",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    BotCharacterRosterHost(
        visible = showRoster,
        onVisibleChange = { showRoster = it },
        selected = null,
        onSelect = null,
        title = myCharactersTitleFor(language),
    )
}
