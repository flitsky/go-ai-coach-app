package com.worksoc.goaicoach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.attendance.AttendanceBoard
import com.worksoc.goaicoach.application.attendance.AttendanceBoardCell
import com.worksoc.goaicoach.application.attendance.AttendanceCellState

/**
 * 출석 도장판 그림(백로그 #55에서 만들고 #56에서 분리했다).
 *
 * ⚠️ **두 곳이 같은 그림을 쓴다** — Claim 팝업(`AttendanceRewardClaimDialog`)과 마이 페이지의
 * 읽기 전용 판(`MyPageScreen`). 한쪽에만 고치면 같은 화면이 두 모양으로 갈린다.
 *
 * ⚠️ **이 컴포저블은 지급하지 않는다.** 지급은 팝업의 Claim만 한다(킥오프 5.1절 —
 * "화면에 들어오는 것만으로 지급되지 않는다"). 여기에 탭 동작을 붙이지 말 것.
 */
@Composable
internal fun AttendanceStampBoard(board: AttendanceBoard, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StampRow(cells = board.daily, compact = true)
        StampRow(cells = board.weekly, compact = false)
    }
}

/**
 * 도장판 한 행. [compact]가 참이면 여섯 칸(1~6일차), 거짓이면 네 칸(주 단위)이다 —
 * 두 행이 **같은 전체 너비**를 나눠 쓰므로 네 칸 쪽이 자연히 넓고 높아진다.
 */
@Composable
private fun StampRow(cells: List<AttendanceBoardCell>, compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        cells.forEach { cell ->
            StampCell(cell = cell, compact = compact, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StampCell(cell: AttendanceBoardCell, compact: Boolean, modifier: Modifier) {
    val strings = LocalUiStrings.current
    val stamped = cell.state == AttendanceCellState.Stamped
    val claimable = cell.state == AttendanceCellState.Claimable
    val shape = RoundedCornerShape(if (compact) 8.dp else 12.dp)
    val background = when {
        stamped -> MaterialTheme.colorScheme.surfaceVariant
        claimable -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            // 여섯 칸은 정사각, 네 칸은 더 넓고 높게 — 보상 내용을 두어 줄 담아야 한다.
            .then(if (compact) Modifier.aspectRatio(1f) else Modifier.height(108.dp))
            .clip(shape)
            .background(background)
            .border(
                BorderStroke(
                    width = if (claimable) 2.dp else 1.dp,
                    color = if (claimable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                shape,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = strings.attendanceRewardDayLabel(cell.tier),
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stamped) {
            // 도장. 글자 하나라 어느 언어에서도 잘리지 않는다.
            Text(
                text = StampMark,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                // 좁은 6칸은 짧은 표기, 넓은 4칸은 전체 문구 — 4칸이 넓은 이유가 여기서 드러난다.
                text = cell.rewards.joinToString("\n") { reward ->
                    if (compact) {
                        attendanceRewardShortLabelFor(strings.language, reward)
                    } else {
                        strings.attendanceRewardLabel(reward)
                    }
                }.ifBlank { attendanceUpcomingNoticeFor(strings.language) },
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 3 else 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 도장 표시. 이모지가 아니라 문자라 폰트가 없어도 네모로 깨지지 않는다. */
private const val StampMark: String = "\u2713"
