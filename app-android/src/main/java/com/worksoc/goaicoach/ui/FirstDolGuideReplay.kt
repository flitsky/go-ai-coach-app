package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worksoc.goaicoach.application.guide.GuideSetupFacts
import com.worksoc.goaicoach.application.guide.GuideStep
import com.worksoc.goaicoach.application.guide.GuideSurface
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.persistence.UserPreferencesStore

/**
 * **가이드 다시보기**(백로그 #128, 사용자 확정 ⓑ: 진입점은 마이페이지에만).
 *
 * ## ⚠️ `Dialog`여야 한다 — 창 안 오버레이로 그리면 뒤로가기에 진다
 *
 * 셸의 `BackHandler`(`GoCoachApp.kt`)가 **마이페이지에서 활성**이고 `exitToHome()`을 부른다.
 * 같은 창 안에 오버레이로 그리면 뒤로가기가 이 화면을 닫는 대신 **홈으로 나가 버린다** —
 * 설계 심사가 코드로 확인해 지시한 항목이다. 별도 윈도우(`Dialog`)는 자기 뒤로가기를 갖는다.
 *
 * ## ⚠️ 그림이 아니라 **실물을 조립한다**
 *
 * ③은 홈의 **진짜 `MenuCard`** 를 같은 문구(`strings.startMatch`)로 그리고, ⑤ 넷은 **판 위에 적히는
 * 그 라벨 함수**(`playMagnifierLabelFor` 등)를 그대로 부른다. 정적 삽화나 캡처를 쓰지 않는 이유는
 * 이 저장소의 이력이다 — 화면이 바뀔 때 그림만 낡아 **다시보기가 거짓을 말하는** 사고를 네 번
 * 겪었다(#87·#97·#124·#127, §0 B-2). 실물을 부르면 화면이 바뀌는 순간 여기도 함께 바뀐다.
 *
 * ## ⚠️ 진행도를 **되감지 않는다**
 *
 * 여기서 `armed`나 `seen_steps`를 건드리면 다시보기를 한 번 볼 때마다 **자동 재생이 다시 무장돼**
 * 홈·대국에서 말풍선이 또 뜬다. 이 화면은 저장소를 **읽지도 쓰지도 않는다** — 문구만 그린다.
 *
 * ## 왜 마법사가 아니라 한 장의 스크롤인가
 *
 * 재생할 단계가 일곱이라(②③④ + ⑤ 넷) 마법사로 만들면 **일곱 번 눌러야** 하고, 정작 다시보기의
 * 목적은 *"아까 그거 뭐였지"* 를 **골라 읽는** 것이다. 그래서 화면별로 묶어 한 장에 늘어놓는다.
 */
@Composable
internal fun FirstDolGuideReplayDialog(onClose: () -> Unit) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    // ⚠️ **읽기만 한다.** ④ 문구가 말하는 접바둑·좌석은 **지금 저장된 값**이어야 참이다 —
    // 다시보기가 옛 값을 외워 두면 사용자가 설정을 바꾼 뒤 거짓을 말한다.
    val facts = remember(context) {
        val saved = UserPreferencesStore(context).load()
        GuideSetupFacts(
            handicapCount = saved.handicapCount,
            humanPlaysBlack = saved.playerSetup.black.controller == SeatController.Human,
        )
    }
    val toolLabels = GuideToolLabels(
        magnifier = playMagnifierLabelFor(strings.language),
        boardSubject = boardSizeSubjectFor(strings.language),
        eval = strings.eval,
        topMoves = strings.topMovesAction,
    )

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FirstDolAvatar(size = 32.dp)
                        Text(
                            text = strings.guideReplayAction,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = onClose) { Text(strings.close) }
                }
                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // 화면별로 묶는다 — 사용자가 *"그 화면에서 본 것"* 으로 찾는다.
                    ReplaySection(title = strings.myPageTitle) {
                        ReplayLine(guideMyPageGreetingFor(strings.language))
                    }
                    ReplaySection(title = strings.attendanceRewardTitle) {
                        ReplayLine(guideBodyFor(strings.language, GuideStep.AttendanceClaim))
                    }
                    ReplaySection(title = strings.startMatch) {
                        ReplayLine(guideBodyFor(strings.language, GuideStep.HomeStartMatch))
                        // ③이 가리키는 그 카드를 **진짜로** 그린다(위 KDoc). 누르면 다시보기를
                        // 닫는 편이 자연스러울 수도 있지만, 여기서는 **보여주는 것**이 목적이라
                        // 아무 일도 하지 않는다.
                        MenuCard(
                            title = strings.startMatch,
                            subtitle = strings.homeStartMatchSubtitle,
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleColor = Color.White,
                            subtitleColor = Color.White.copy(alpha = 0.85f),
                            onClick = {},
                        )
                    }
                    ReplaySection(title = strings.matchSetup) {
                        ReplayLine(guideBodyFor(strings.language, GuideStep.MatchSetup, facts = facts))
                    }
                    ReplaySection(title = strings.gameSection) {
                        GuideStep.entries
                            .filter { it.surface == GuideSurface.InGame }
                            .forEach { step ->
                                ReplayLine(
                                    guideBodyFor(strings.language, step, toolLabels = toolLabels),
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplaySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun ReplayLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FirstDolAvatar(size = 26.dp, seamColor = MaterialTheme.colorScheme.surfaceVariant)
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 마이페이지의 **가이드 다시보기** 행. 상태를 갖지 않는다 — 여는 쪽이 상태를 든다. */
@Composable
internal fun GuideReplayRow(onClick: () -> Unit) {
    val strings = LocalUiStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FirstDolAvatar(size = 26.dp)
            Text(
                text = strings.guideReplayAction,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
