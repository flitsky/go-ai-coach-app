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

/** 언어별 날짜 표기 어순이 달라 [UiLanguage]마다 다른 패턴/로케일을 쓴다(연도는 생략 — 목록용 짧은 표기). */
private fun dateTimeFormat(language: UiLanguage): SimpleDateFormat =
    when (language) {
        UiLanguage.Korean -> SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN)
        UiLanguage.English -> SimpleDateFormat("MMM d, HH:mm", Locale.ENGLISH)
        UiLanguage.Japanese -> SimpleDateFormat("M月d日 HH:mm", Locale.JAPANESE)
        UiLanguage.ChineseSimplified -> SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
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
    // [날짜] [시간] [보드판사이즈] [플레이한 진영] [접바둑 설정] [결과] — 예: "8월 24일 00:34 13x13 흑 5점 접바둑 기권"
    val summary = listOf(
        dateTimeFormat(strings.language).format(Date(entry.playedAtMillis)),
        "${entry.boardSize}x${entry.boardSize}",
        strings.colorLabel(entry.humanColor),
        handicapPhrase(strings, entry.handicapCount),
        strings.gameHistoryResultLabel(entry.result, entry.margin),
    ).joinToString(" ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = summary, color = MaterialTheme.colorScheme.onSurface)
    }
}
