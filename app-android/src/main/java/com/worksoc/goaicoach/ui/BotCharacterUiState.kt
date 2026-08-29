package com.worksoc.goaicoach.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.botcharacter.BotCharacter
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.persistence.BotCollectionStore

/**
 * 화면 트리 전역에서 읽는 봇 캐릭터 수집 상태([LocalPremiumUiState]·[LocalConsumableUiState]와
 * 같은 방식으로 공급된다).
 *
 * #8이 도메인과 저장소만 만들고 **배선을 일부러 남겨 뒀던 자리**다("진입점 배선은 #10의 몫").
 * 여기서 저장소를 붙여 픽커(#10)가 "무엇을 갖고 있는가"를 읽을 수 있게 한다.
 */
internal data class BotCharacterUiState(
    val collection: BotCollectionState = BotCollectionState(),
) {
    /** 지금 이 캐릭터로 대국할 수 있는가. 기본 제공은 획득 기록 없이도 통과한다(#16). */
    fun isAvailable(character: BotCharacter): Boolean = collection.isAvailable(character)
}

internal val LocalBotCharacterUiState = staticCompositionLocalOf { BotCharacterUiState() }

/**
 * [BotCharacterUiState]의 배선 본체. `GoCoachApp.kt`는 라인/상태 훅 예산이 빠듯해
 * ([buildPremiumUiState]·[buildConsumableUiState]와 같은 이유) 저장소 생성과 상태 보유를
 * 전부 여기로 뺀다.
 *
 * 지금은 읽기 전용이다 — 획득은 출석 보상(`runBotCharacterUnlock`)이 자기 경로로 저장하고,
 * 광고 조각(#11)·구매(#18)가 붙으면 그때 쓰기 경로가 필요해진다.
 */
@Composable
internal fun buildBotCharacterUiState(context: Context): BotCharacterUiState {
    val store: BotCollectionStorePort = remember(context) { BotCollectionStore(context) }
    return BotCharacterUiState(collection = remember(store) { store.load() })
}

/**
 * 대국 상대(= AI 레벨) 선택 픽커(백로그 #10, 킥오프 플랜 7.1절).
 *
 * **캐릭터를 고르는 행위 자체가 곧 AI 레벨 선정이다** — 그래서 이 다이얼로그가 기존 난이도
 * 드롭다운을 대체한다. 한 줄에 이름과 티어명을 함께 적는 이유는 #9의 확정 사항이다: 이름만으로는
 * 어느 쪽이 센지 모호해 난이도 선택이라는 사실이 드러나지 않는다.
 *
 * **잠긴 캐릭터도 목록에서 숨기지 않는다.** 획득 경로가 셋으로 갈리므로(출석 / 광고 조각 / 유료)
 * 각각의 사유를 그대로 보여주는 것이 곧 "무엇을 하면 열리는가"의 안내가 된다 — 숨기면 사용자는
 * 그 캐릭터의 존재도, 얻는 방법도 모른다.
 *
 * ⚠️ 잠긴 캐릭터를 **여기서 열어주지는 않는다.** 광고 조각 적립은 #11, 구매는 #18의 몫이라
 * 지금은 안내까지가 전부다.
 */
@Composable
internal fun BotCharacterPickerDialog(
    selected: BotCharacter?,
    onSelect: (BotCharacter) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val bots = LocalBotCharacterUiState.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.botPickerTitle) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BotCharacterCatalog.fastBeginnerRoster.forEach { character ->
                    BotCharacterRow(
                        character = character,
                        isSelected = character.id == selected?.id,
                        isAvailable = bots.isAvailable(character),
                        onClick = {
                            onSelect(character)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.botPickerCloseAction) } },
    )
}

/** 픽커 한 줄. 잠긴 캐릭터는 흐리게 두고 탭을 막되, 획득 방법은 그대로 보여준다. */
@Composable
private fun BotCharacterRow(
    character: BotCharacter,
    isSelected: Boolean,
    isAvailable: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalUiStrings.current
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { if (isAvailable) it.clickable(onClick = onClick) else it.alpha(0.5f) }
        .padding(vertical = 8.dp)

    Column(modifier = rowModifier) {
        Text(
            text = strings.botCharacterLabel(character),
            style = MaterialTheme.typography.titleSmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = character.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 잠긴 캐릭터만 획득 방법을 덧붙인다 — 기본 제공은 안내할 것이 없다.
        if (!isAvailable) {
            strings.botUnlockHint(character.unlockSource)?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
