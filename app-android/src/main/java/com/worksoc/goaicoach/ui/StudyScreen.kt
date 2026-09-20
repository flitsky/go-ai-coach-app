package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 학습 허브의 하위 분류(백로그 #163, U-16 — 2026-09-20 사용자 결정).
 *
 * ⚠️ **순서가 곧 화면의 순서다** — 선언 순서로 그린다. 지금 열 수 있는 것은 [YoutubeLessons]
 * 하나뿐이고 나머지 셋은 **자리를 먼저 보여 준다**(회색 + 「준비 중」 배지, 비활성).
 * 사용자 결정으로 *"누르면 토스트"* 가 아니라 **눌리지 않는** 쪽을 골랐다 — 앞으로 무엇이
 * 올지 알리되, 누를 수 있는 것처럼 보여 헛손질을 만들지는 않는다.
 *
 * ⚠️ **#164가 채울 때 여기에 줄을 더하지 말 것** — 줄은 이미 있다. [available]을 `true`로
 * 돌리고 [StudyScreen]의 `when`에 화면을 이어 주면 된다.
 */
internal enum class StudyCategory(val available: Boolean) {
    /** 2026-09-20까지 이 목록이 곧 「학습 하기」였다 — 콘텐츠가 통째로 한 칸 내려왔다. */
    YoutubeLessons(available = true),
    Rules(available = false),
    Fundamentals(available = false),
    LifeAndDeath(available = false),
}

/**
 * 2 Depth: 학습 하기 **허브**(백로그 #163) — 하위 분류를 세우고, 고른 분류의 화면을 연다.
 *
 * ⚠️ **하위 분류는 이 화면의 상태다 — 셸의 목적지가 아니다.** 셸(`GoCoachApp.kt`)의 상태 훅
 * 예산이 42/42로 여유 0이라(함정 3) 목적지를 늘리면 `LayeringContractTest`가 깨진다. 같은
 * 판단의 선례가 다시보기다(백로그 #156).
 *
 * ⚠️ **뒤로가기 경로가 둘이라는 것이 이 조각의 본체다.** 시스템 뒤로가기는 셸의 `BackHandler`
 * 가 잡아 `exitToHome()`으로 보내고, 상단 화살표는 그 길을 타지 않는다. 그래서 하위 화면은
 * **자기 `BackHandler`를 갖고** 둘을 한 곳(허브)으로 모은다 — 화살표만 고치면 시스템
 * 뒤로가기가 허브를 건너뛰고 홈으로 튄다.
 */
@Composable
internal fun StudyScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    var opened by remember { mutableStateOf<StudyCategory?>(null) }

    when (opened) {
        StudyCategory.YoutubeLessons -> {
            StudyVideoListScreen(onBackClick = { opened = null }, modifier = modifier)
            return
        }
        // 나머지 셋은 `available = false`라 열리지 않는다 — 행이 눌리지 않으므로 여기 올 수
        // 없다. #164가 화면을 들고 올 때 이 자리에 가지를 더한다.
        else -> Unit
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        StudyScreenHeader(title = strings.study, onBackClick = onBackClick)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StudyCategory.entries.forEach { category ->
                StudyCategoryRow(
                    title = strings.studyCategoryTitle(category),
                    subtitle = strings.studyCategorySubtitle(category),
                    comingSoonLabel = strings.studyComingSoon.takeIf { !category.available },
                    onClick = { opened = category }.takeIf { category.available },
                )
            }
        }
    }
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
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * 허브의 한 줄. [onClick]이 `null`이면 **아직 없는 분류**다 — 회색으로 내리고 배지를 붙인다.
 *
 * ⚠️ **`clickable`을 달고 `enabled = false`로 끄지 않는다** — 아예 달지 않는다. 그래야 리플
 * 잔상도 남지 않고, 접근성 서비스가 "누를 수 있는 것"으로 읽어 주지도 않는다.
 * ⚠️ 줄 수를 제한하지 않는 이유는 강좌 목록과 같다(백로그 #107) — 글꼴 배율이 올라가면
 * 행이 접히는 만큼 자란다.
 */
@Composable
private fun StudyCategoryRow(
    title: String,
    subtitle: String,
    comingSoonLabel: String?,
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
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = subtitleColor,
            )
        }

        if (comingSoonLabel != null) {
            Text(
                text = comingSoonLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
