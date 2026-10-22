package com.worksoc.goaicoach.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.StudyCategory
import com.worksoc.goaicoach.ui.l10n.gameRecordsStudySubtitleFor
import com.worksoc.goaicoach.ui.l10n.gameRecordsTitleFor
import com.worksoc.goaicoach.ui.l10n.studyLessonTrackFor
import com.worksoc.goaicoach.ui.quickactions.AppQuickActions

/**
 * 학습 하기 **허브** — 홈의 오른쪽 페이지다(백로그 #280, 사용자 2026-10-22 · 처음은 #163의 목적지). 줄의 차례는 [StudyHubRows] 한 곳이 정한다.
 * 머리말(← 제목)이 없다 — 페이지에는 돌아갈 곳이 없고, 이름은 홈의 아래 탭이 말한다.
 *
 * ⚠️ **분류를 누르면 페이지 안이 아니라 화면 전체로 연다**([StudyScreen]). 페이지 안에서 열면 판 끌기 · 도해 넘기기가
 * 페이지 넘기기와 부딪친다. 고른 분류는 [StudyEntry]에 맡기고 [onOpenCategory]가 셸의 「학습」 목적지로 간다.
 */
@Composable
internal fun StudyHubPage(
    onOpenCategory: () -> Unit,
    onGameRecordsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space16),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
    ) {
        StudyHubRows.forEach { row ->
            when (row) {
                is StudyHubRow.Category -> {
                    val category = row.category
                    StudyEntryRow(
                        title = strings.studyCategoryTitle(category),
                        subtitle = strings.studyCategorySubtitle(category),
                        badge = strings.studyComingSoon.takeIf { !category.available },
                        onClick = {
                            StudyEntry.request(category)
                            onOpenCategory()
                        }.takeIf { category.available },
                    )
                }
                // 「명국 기보」(옛 「기보 보기」)는 분류가 아니라 셸의 목적지다(#269) — 명국 감상은 공부라 이 페이지로 왔다(#280).
                StudyHubRow.GameRecords -> StudyEntryRow(
                    title = gameRecordsTitleFor(strings.language),
                    subtitle = gameRecordsStudySubtitleFor(strings.language),
                    badge = null,
                    onClick = onGameRecordsClick,
                )
            }
        }
    }
}

/** 학습 하기 페이지의 한 줄 — 분류 하나, 또는 「명국 기보」(셸의 목적지). */
internal sealed interface StudyHubRow {
    data class Category(val category: StudyCategory) : StudyHubRow

    data object GameRecords : StudyHubRow
}

/**
 * 학습 하기 페이지의 **줄 차례 — 이 표 한 곳이 정한다**(#280). 분류는 빠짐없이 한 번씩 선다(`HomePagesContractTest`).
 * 바둑 규칙 · 기초 행마 · 유튜브 강좌 · 사활 풀기 · 명국 기보 — 사용자가 이름과 함께 적어 준 차례다(2026-10-22, 그전에는 유튜브가 맨 위).
 */
internal val StudyHubRows: List<StudyHubRow> = listOf(
    StudyHubRow.Category(StudyCategory.Rules),
    StudyHubRow.Category(StudyCategory.Fundamentals),
    StudyHubRow.Category(StudyCategory.YoutubeLessons),
    StudyHubRow.Category(StudyCategory.LifeAndDeath),
    StudyHubRow.GameRecords,
)

/**
 * 허브 페이지가 고른 분류를 셸의 「학습」 목적지에 넘기는 **한 번짜리 요청**(백로그 #280) — `FinishedGameFlow`(대국 기록의 「복기 하기」)와 같은 틀이다.
 *
 * ⚠️ 셸에 들게 하지 않는다 — 셸이 학습의 하위 분류를 알게 되고(`StudyHubContractTest`) 셸 상태 원장에 한 칸이 는다(함정 3).
 */
internal object StudyEntry {
    private var pending: StudyCategory? = null

    fun request(category: StudyCategory) {
        pending = category
    }

    /** 꺼내는 즉시 비운다 — 비우지 않으면 다음에 「학습」 목적지로 올 때 옛 분류가 또 열린다. */
    fun take(): StudyCategory? = pending.also { pending = null }
}

/**
 * 학습 하기의 **하위 화면**(영상 · 규칙 · 행마 · 사활) — 셸의 「학습」 목적지가 화면 전체로 그린다(백로그 #280 — 허브는 홈의 페이지가 됐다).
 *
 * ⚠️ **뒤로가기 경로가 둘이다.** 시스템 뒤로가기는 셸의 `BackHandler`가 잡아 `exitToHome()`으로 보내고, 상단 화살표는 그 길을 타지 않는다.
 * 하위 화면은 **자기 `BackHandler`를 갖고** 안쪽 단계(단원 → 문제)를 제 손으로 닫는다 — 맨 위 단계의 둘은 [onBackClick](홈의 학습 페이지)으로 모인다.
 */
@Composable
internal fun StudyScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 한 번만 꺼낸다 — 다시 그릴 때마다 꺼내면 둘째부터 비어 있다.
    val category = remember { StudyEntry.take() }
    when (category) {
        StudyCategory.YoutubeLessons -> StudyVideoListScreen(onBackClick = onBackClick, modifier = modifier)
        // ⚠️ **분류마다 화면을 파지 않는다** — 규칙과 행마는 형식이 같아(정적 문구 + 도해
        // 넘김) **한 화면이 갈래만 바꿔** 그린다. 분류를 갈래로 옮기는 자리는
        // `studyLessonTrackFor` 하나다(백로그 #183).
        StudyCategory.Rules, StudyCategory.Fundamentals -> {
            val track = studyLessonTrackFor(category)
            if (track != null) {
                StudyLessonTrackScreen(track = track, category = category, onBackClick = onBackClick, modifier = modifier)
            } else {
                LeaveAtOnce(onBackClick)
            }
        }
        // 「사활 공부하기」는 형식이 달라(판에 직접 두는 문제 풀이 — U-51) 화면이 따로다(백로그 #184).
        StudyCategory.LifeAndDeath -> LifeAndDeathTrackScreen(onBackClick = onBackClick, modifier = modifier)
        // 요청 없이 왔다(허브를 거치지 않았다) — 보여 줄 것이 없으니 곧바로 홈으로 돌아간다. 아무 분류나 여는 것은 거짓말이다(함정 40).
        null -> LeaveAtOnce(onBackClick)
    }
}

@Composable
private fun LeaveAtOnce(onBackClick: () -> Unit) {
    LaunchedEffect(Unit) { onBackClick() }
}

/**
 * 학습 트리의 머리말 — 허브와 하위 화면이 **같은 것을 쓴다.** 제목만 다르고 화살표의 생김새·
 * 여백·상태바 패딩이 같아야, 한 칸 들어갔다 나오는 동안 머리말이 튀지 않는다.
 */
@Composable
internal fun StudyScreenHeader(title: String, onBackClick: () -> Unit) {
    val strings = LocalUiStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = AppSpacing.Space8, vertical = AppSpacing.Space12),
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
            text = title,
            fontSize = AppTextSize.Text20,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            // 남는 폭은 제목이 갖고, 오른쪽 끝은 빠른 버튼 묶음(#270)이 먼저 제 폭을 잡는다.
            modifier = Modifier
                .weight(1f)
                .padding(start = AppSpacing.Space8),
        )
        AppQuickActions()
    }
}

/**
 * 목록의 한 줄 — 허브와 「바둑 규칙 배우기」 단원 목록 · 「기본 사활」 단원 목록이 **같은 것을 쓴다**(백로그 #164·#184).
 * [onClick]이 `null`이면 **아직 없는 분류**다 — 회색으로 내리고 배지(「준비 중」)를 붙인다. 열린 줄의 [badge]는 진행도(「기본 사활」의 `3 / 5`)다.
 *
 * ⚠️ **`clickable`을 달고 `enabled = false`로 끄지 않는다** — 아예 달지 않는다. 그래야 리플
 * 잔상도 남지 않고, 접근성 서비스가 "누를 수 있는 것"으로 읽어 주지도 않는다.
 * ⚠️ 줄 수를 제한하지 않는 이유는 강좌 목록과 같다(백로그 #107) — 글꼴 배율이 올라가면
 * 행이 접히는 만큼 자란다.
 */
@Composable
internal fun StudyEntryRow(
    title: String,
    subtitle: String,
    badge: String?,
    onClick: (() -> Unit)?,
) {
    val enabled = onClick != null
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val subtitleColor = if (enabled) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Corner10))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4),
        ) {
            Text(
                text = title,
                fontSize = AppTextSize.Text16,
                fontWeight = FontWeight.Bold,
                color = titleColor,
            )
            Text(
                text = subtitle,
                fontSize = AppTextSize.Text13,
                color = subtitleColor,
            )
        }

        if (badge != null) {
            Text(
                text = badge,
                fontSize = AppTextSize.Text12,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppRadius.Corner6))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = AppSpacing.Space8, vertical = AppSpacing.Space4),
            )
        }
    }
}
