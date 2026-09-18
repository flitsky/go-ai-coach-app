package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.persistence.GameHistoryStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 3 Depth: 대국 기록 화면 — Phase 1 범위는 단순 리스트 표시만이다(기보 재분석은 다음 단계,
 * `260823-260830_OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN.md` 6장). 목록은 이 화면이 직접
 * `GameHistoryStore`에서 읽어온다 — 별도 상태 훅 예산이 빠듯한 `GoCoachApp.kt`에 데이터를
 * 들고 있지 않고, 화면 진입 시점에 한 번 로드한다.
 */
@Composable
internal fun GameHistoryScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    val entries = remember(context) {
        GameHistoryStore(context).loadAll().sortedByDescending { it.playedAtMillis }
    }

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
                text = strings.gameHistoryTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = strings.gameHistoryEmptyMessage,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries) { entry ->
                    GameHistoryRow(entry, strings)
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * 언어별 날짜 표기 어순이 달라 [UiLanguage]마다 다른 패턴/로케일을 쓴다(연도는 생략 — 목록용 짧은 표기).
 *
 * ⚠️ **시각(HH:mm)을 뺐다**(백로그 #151, 2026-09-18 사용자). 한 줄에 다섯 조각이 들어가는데
 * 분 단위까지 붙으면 폭만 먹고 고르는 데 도움이 안 된다 — 같은 날 여러 판은 **차례로 놓인다.**
 */
private fun dateFormat(language: UiLanguage): SimpleDateFormat =
    when (language) {
        UiLanguage.Korean -> SimpleDateFormat("M월 d일", Locale.KOREAN)
        UiLanguage.English -> SimpleDateFormat("MMM d", Locale.ENGLISH)
        UiLanguage.Japanese -> SimpleDateFormat("M月d日", Locale.JAPANESE)
        UiLanguage.ChineseSimplified -> SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE)
    }

/** "5점 접바둑"/"호선"처럼 대국 설정 요약에 쓰는 접바둑 값 — [UiStrings.gameModeLabel]과 달리 "대국 방식:" 접두어 없이 목록 행에 바로 쓸 짧은 조각. */
private fun handicapPhrase(strings: UiStrings, handicapCount: Int): String =
    if (handicapCount == 0) {
        strings.handicapEvenGameLabel
    } else {
        "${strings.compactHandicapValueLabel(handicapCount)} ${strings.handicap}"
    }

@Composable
private fun GameHistoryRow(entry: GameHistoryEntry, strings: UiStrings) {
    // [날짜] [보드판 크기] [흑백 세팅] [호선/접바둑] [승리한 진영]
    // 예: "9월 18일 · 13x13 · 사람:AI · 3점 접바둑 · 백 불계승"
    //
    // ⚠️ **2026-09-18에 사용자가 다시 정한 배열이다**(백로그 #151). 바뀐 것은 셋이다 —
    // 시각을 뺐고, "플레이한 진영(흑)"을 **실제 대국 세팅(사람:AI)** 으로 바꿨고, 결과를
    // **사람 기준(승/패/기권)에서 진영 기준(백 불계승)** 으로 옮겼다.
    // ⚠️ 사람 기준을 되살리지 말 것 — 이제 **사람:사람·AI:AI 대국도 기록되므로** "승/패"가
    // 누구의 승패인지 말할 수 없는 줄이 생긴다.
    //
    // ⚠️ **구분자는 공백이 아니라 ` · `다**(백로그 #108, 사용자 결정 2026-09-06 — "명확하게").
    // 공백으로만 이으면 영어가 한 문장처럼 읽힌다. 다섯 항목이 서로 다른 축이라는 것이
    // 눈에 보여야 한다. 폭이 문제가 되면 구분자가 아니라 **문구를 줄일 것.**
    val summary = listOf(
        dateFormat(strings.language).format(Date(entry.playedAtMillis)),
        "${entry.boardSize}x${entry.boardSize}",
        strings.seatMatchupLabel(entry.playerSetup),
        handicapPhrase(strings, entry.handicapCount),
        strings.gameHistoryOutcomeLabel(entry.winner, entry.isResign, entry.margin),
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = summary, color = MaterialTheme.colorScheme.onSurface)
    }
}
