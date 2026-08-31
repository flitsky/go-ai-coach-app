package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.preferences.SelfRatedSkill
import com.worksoc.goaicoach.application.preferences.applyLandingSetup
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.persistence.UserPreferencesStore

/**
 * 첫 실행 랜딩(백로그 #51). 자기 실력과 계가 방식을 고르면 그 답이 초기 설정으로 들어간다.
 *
 * ⚠️ **상태를 이 파일이 전부 들고 있다.** `GoCoachApp.kt`는 상태 훅 예산이 거의 소진돼 있어
 * (`LayeringContractTest.goCoachAppStaysWithinShrinkingUiShellBudget`), 셸에는 화면 전환 분기만
 * 남기고 선택 상태는 여기서 관리한다 — `AttendanceRewardClaimDialog`와 같은 방식이다.
 *
 * ⚠️ **건너뛰기가 있어야 한다(2026-08-31 사용자 확정).** 이 화면은 기존 사용자에게도 한 번
 * 뜨는데, 답을 강제하면 **이미 맞춰 둔 설정이 덮어써진다.** 건너뛰면 [onSkip]이 설정을 그대로
 * 두고 "봤다"는 사실만 남긴다.
 *
 * ⚠️ **언어 선택이 질문보다 먼저 그려져야** 외국어 사용자가 질문을 읽을 수 있다 — 그래서
 * 언어 선택기를 화면 맨 위에 둔다.
 *
 * 세 번째 질문 자리는 **비워 뒀다**(사용자: 지금 떠오르는 질문 없음). 후보는 바둑판 크기다.
 */
/**
 * 랜딩을 **앱 화면 전체보다 바깥에서** 가로막는 문(백로그 #51).
 *
 * ⚠️ **이 위치가 이 항목의 핵심이다.** 처음에는 랜딩을 `GoCoachScreen` 안의 목적지 하나로
 * 넣었는데, **저장한 값이 곧바로 덮어써져 아무것도 반영되지 않았다**(실기에서 확인: 최상급을
 * 골랐는데 흑=유저·5점 그대로였다). 이유는 `GoCoachScreen`이 컴포지션되는 순간
 * `preferencesStore.load()`로 **랜딩 이전** 값을 읽어 게임 상태를 세우고, 자동저장
 * `LaunchedEffect`가 첫 컴포지션에서 곧바로 돌면서 그 옛 값을 다시 써 버리기 때문이다.
 *
 * 그래서 랜딩이 끝난 **뒤에야** `GoCoachScreen`이 처음 컴포지션되도록 바깥에 둔다 — 그러면
 * 그 `load()`가 랜딩이 쓴 값을 읽고, 자동저장도 같은 값을 되쓴다. 화면 전환 목적지로 넣는
 * 방식으로 되돌리지 말 것.
 *
 * 출석 팝업이 랜딩 위에 겹치던 문제도 이 구조에서 저절로 사라진다 — 그 팝업은
 * `GoCoachScreen` 안에 있어 랜딩 동안에는 아예 컴포지션되지 않는다.
 */
@Composable
internal fun LandingGate(
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { UserPreferencesStore(context) }
    var completed by remember { mutableStateOf(store.load().hasSeenOnboarding) }

    if (completed) {
        content()
        return
    }
    LandingScreen(
        selectedLanguage = selectedLanguage,
        onLanguageChange = onLanguageChange,
        onComplete = { skill, ruleset ->
            store.save(applyLandingSetup(store.load(), skill, ruleset))
            completed = true
        },
        // 건너뛰면 설정은 그대로 두고 "봤다"는 사실만 남긴다 — 기존 사용자가 이미 맞춰 둔
        // 값이 덮어써지지 않게 하는 것이 이 갈래의 목적이다(2026-08-31 사용자 확정).
        onSkip = {
            store.save(store.load().copy(hasSeenOnboarding = true))
            completed = true
        },
    )
}

@Composable
internal fun LandingScreen(
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
    onComplete: (SelfRatedSkill, Ruleset) -> Unit,
    onSkip: () -> Unit,
) {
    var skill by remember { mutableStateOf<SelfRatedSkill?>(null) }
    var ruleset by remember { mutableStateOf(Ruleset.Japanese) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // ⚠️ 상태바 인셋을 안 주면 우상단 언어 칩이 시계·배터리 아이콘에 물린다(실기 확인).
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            // 홈/설정과 같은 선택기를 그대로 쓴다 — 랜딩용으로 새로 만들 이유가 없다.
            LanguageDropdownChip(selectedLanguage = selectedLanguage, onLanguageChange = onLanguageChange)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = landingTitleFor(selectedLanguage),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = landingSubtitleFor(selectedLanguage),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))
        LandingQuestion(text = landingSkillQuestionFor(selectedLanguage))
        Spacer(Modifier.height(12.dp))
        // 다섯 보기를 두 줄로 흘린다 — 한 줄에 넣으면 "최상급"/"Very strong"이 잘린다.
        SkillChoices(
            language = selectedLanguage,
            selected = skill,
            onSelect = { skill = it },
        )

        Spacer(Modifier.height(28.dp))
        LandingQuestion(text = landingRulesetQuestionFor(selectedLanguage))
        Spacer(Modifier.height(12.dp))
        RulesetChoices(
            language = selectedLanguage,
            selected = ruleset,
            onSelect = { ruleset = it },
        )

        Spacer(Modifier.height(36.dp))
        Button(
            onClick = { skill?.let { onComplete(it, ruleset) } },
            // 실력을 안 고르면 넣을 값이 없다 — 계가만 저장하고 넘기면 "설정 완료"가 거짓말이 된다.
            enabled = skill != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(landingStartActionFor(selectedLanguage))
        }
        TextButton(onClick = onSkip) {
            Text(landingSkipActionFor(selectedLanguage))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LandingQuestion(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SkillChoices(
    language: UiLanguage,
    selected: SelfRatedSkill?,
    onSelect: (SelfRatedSkill) -> Unit,
) {
    // 5개를 3+2로 나눈다. FlowRow를 쓰지 않는 이유는 실험적 API라 이 화면 하나 때문에
    // opt-in을 늘리고 싶지 않아서다 — 보기 수가 고정(5)이라 나누는 편이 단순하다.
    val rows = listOf(SelfRatedSkill.entries.take(3), SelfRatedSkill.entries.drop(3))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { choice ->
                    FilterChip(
                        selected = choice == selected,
                        onClick = { onSelect(choice) },
                        label = { Text(landingSkillLabelFor(language, choice)) },
                    )
                }
            }
        }
    }
    // 고르는 즉시 무엇이 설정되는지 보여 준다 — 팝업으로 가로막는 대신 그 자리에서 알린다.
    selected?.let { choice ->
        Spacer(Modifier.height(12.dp))
        Text(
            text = landingSkillResultFor(language, choice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RulesetChoices(
    language: UiLanguage,
    selected: Ruleset,
    onSelect: (Ruleset) -> Unit,
) {
    val strings = LocalUiStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Ruleset.entries.forEach { choice ->
            FilterChip(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                label = { Text(strings.rulesetLabel(choice)) },
            )
        }
    }
}
