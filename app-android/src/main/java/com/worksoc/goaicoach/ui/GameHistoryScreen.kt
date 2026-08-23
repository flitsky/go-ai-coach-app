package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * `OFFLINE_ENGAGEMENT_FEATURES_KICKOFF_PLAN_260823_1521.md` 6장). 목록은 이 화면이 직접
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

private val RowDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

@Composable
private fun GameHistoryRow(entry: GameHistoryEntry, strings: UiStrings) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = RowDateFormat.format(Date(entry.playedAtMillis)),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${entry.boardSize}x${entry.boardSize}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = strings.gameHistoryResultLabel(entry.winner, entry.margin),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
