package com.worksoc.goaicoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.application.gamehistory.ScoreSwingHighlight
import com.worksoc.goaicoach.application.gamehistory.buildGameReplayTimeline
import com.worksoc.goaicoach.application.gamehistory.canStartBranchedGameAt
import com.worksoc.goaicoach.application.gamehistory.deriveReplayMoveEvaluations
import com.worksoc.goaicoach.application.gamehistory.deriveScoreSwingHighlights
import com.worksoc.goaicoach.application.premium.FeatureId
import com.worksoc.goaicoach.presentation.KaTrainUxOptions
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.GameState
import com.worksoc.goaicoach.shared.StoneColor

/**
 * 4 Depth: 한 판을 수순대로 되짚는 화면(백로그 #156).
 *
 * ## 상태를 셸로 올리지 않는다
 * 재생 위치·토글 셋은 전부 **이 화면이 소유**한다. `GoCoachApp.kt`의 상태 훅 예산은 42/42로
 * 여유가 0이라(함정 3), 한 줄만 올려도 `LayeringContractTest`가 깨진다.
 *
 * ## ⚠️ 표시 옵션도 셸의 것을 쓰지 않는다
 * [KaTrainUxOptions]를 **지역 인스턴스로** 만든다. 셸의 `uxOptions`를 고치면 자동저장이 그대로
 * 받아 적어 **대국 중 설정이 영구히 바뀐다**(함정 2) — 다시보기에서 수순 번호를 켰다는 이유로
 * 다음 대국의 판에 번호가 남으면 안 된다.
 *
 * ## ⚠️ 판 위 착수 평가 색은 여기서 열지 않는다
 * `GoBoard`가 `uxOptions.showMoveReview && premium.isActive`로 **스스로** 가른다. 이 화면은
 * `showMoveReview`만 켜 두고 프리미엄 판정은 건드리지 않는다 — 여는 순간 대국 화면에서 파는
 * 기능을 뒷문으로 내주는 것이 된다(함정 14). 무료로 주는 것은 아래 **변곡점 목록**이고,
 * 그건 `#156`의 목적이 명시한 지표다(2026-09-20 리메이크 — "실착"에서 "변곡점"으로).
 */
@Composable
internal fun GameReplayScreen(
    entry: GameHistoryEntry,
    replay: GameReplayData,
    onBackClick: () -> Unit,
    /**
     * 「현 지점부터 커스텀 새 대국하기」(백로그 #172) — 지금 보고 있는 [GameState]를 그대로 준다.
     *
     * ⚠️ **스냅샷을 여기서 짓지 않는다.** 분기 대국은 결국 셸이 목적지를 `InGame`으로 바꿔야
     * 시작되는데, 이 화면은 셸을 모르고(#156이 지킨 경계) 셸도 다시보기를 모른다
     * (`GameReplayContractTest`). 그래서 **국면 하나만** 위로 올려 보내고, 대국 기록 화면이
     * 그것을 `buildBranchedGameSnapshot(...)`으로 옮겨 담아 셸에 넘긴다.
     *
     * `null`이면 버튼 자체를 그리지 않는다 — 배선되지 않은 자리에 죽은 버튼을 두지 않는다.
     */
    onBranchFromHere: ((GameState) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current

    // ⚠️ **중첩 `BackHandler`다.** 셸(`GoCoachApp.kt:714`)의 핸들러는 목적지가 Home이 아니면
    // 무조건 `exitToHome()`을 불러 **복기를 건너뛰고 홈으로 튄다.** 디스패처는 나중에 등록된
    // 콜백부터 부르고, 이 화면은 셸의 `when(currentDestination)` 안에서 **뒤에** 컴포즈되므로
    // 여기가 먼저 잡는다. 이 저장소의 중첩 `BackHandler` **첫 사례**라 실기로 확인할 것.
    BackHandler { onBackClick() }

    val timeline = remember(entry.id, replay) {
        buildGameReplayTimeline(
            boardSize = BoardSize(entry.boardSize),
            ruleset = entry.ruleset,
            handicapCount = entry.handicapCount,
            komi = entry.komi,
            moves = replay.moves,
        )
    }
    // ⚠️ **저장된 `replay.moveEvaluations`를 읽지 않는다**(2026-09-20 개정) — 그건 대국이
    // 끝나던 순간의 코드로 캐시해 둔 값이라, 임계값·집계 방식을 바꿔도 이미 기록된 대국에는
    // 반영되지 않는다. `moves`·`scoreSnapshots`(원 데이터)만 믿고 지금 로직으로 매번 다시
    // 계산한다 — `deriveReplayMoveEvaluations` 참고. 이건 판 위 착수 평가 색(구독자 전용,
    // 사람이 둔 수만) 전용이다 — 아래 「변곡점」 목록과는 다른 개념·다른 데이터다.
    val moveEvaluations = remember(entry, replay) { deriveReplayMoveEvaluations(entry, replay) }
    // ⚠️ **「변곡점」은 [moves]도 사람 진영도 보지 않는다**(2026-09-20 사용자 리메이크) —
    // `scoreSnapshots` 하나만으로 사람:사람·사람:AI·AI:AI 모든 대국에 똑같이 뜬다.
    val scoreSwings = remember(replay) { deriveScoreSwingHighlights(replay.scoreSnapshots) }

    // ⚠️ **마지막 수에서 연다.** 목록 행이 방금 말한 것이 결과이고, 그 국면에서 시작해야 화면이
    // 이어진다. 처음부터 보려면 `⏮`가 한 번이다.
    var moveNumber by remember(entry.id) { mutableIntStateOf(timeline.lastMoveNumber) }
    var showMoveNumbers by remember { mutableStateOf(false) }
    // ⚠️ **처음부터 펼쳐 연다**(2026-09-19 사용자) — 슬라이더를 없애면서 그 역할(위치를
    // 한눈에 훑는 것)을 이 그래프가 대신하기로 했다. 접힌 요약 바로 여는 것은 슬라이더가
    // 있던 시절의 기본값이라 이제 안 맞는다. 여전히 눌러서 접을 수는 있다 — 시작 상태만 다르다.
    var isScoreExpanded by remember { mutableStateOf(true) }

    val state = timeline.stateAt(moveNumber)
    val uxOptions = remember(showMoveNumbers) {
        KaTrainUxOptions(
            showMoveNumbers = showMoveNumbers,
            showMoveReview = true,
            showOwnershipOverlay = false,
            isDirectPlayEnabled = false,
            isDelayedPlayEnabled = false,
            isPlayHapticEnabled = false,
            isPlayEffectEnabled = false,
            isPlayMagnifierEnabled = false,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // ⚠️ **맨 아래 칸이 제스처 바에 깔린다**(targetSdk 36부터 앱이 시스템 바 영역까지
            // 그린다, #25). 실착 칩이 바로 그 자리라 실기에서 반쯤 가려졌다 — 위쪽은 헤더의
            // `statusBarsPadding()`이 맡고 아래는 이 한 줄이 맡는다(설정·학습 화면과 같은 방식).
            .navigationBarsPadding(),
        // ⚠️ **섹션 사이 간격을 한 상수로 통일한다**(2026-09-19 사용자: "공백이 많지 않게
        // 일관성 있게"). 예전에는 섹션마다 제각각 top/bottom 패딩을 들고 있어 간격이
        // 8·12·16dp로 흩어져 있었다 — 여기 한 곳만 고치면 전부 같이 움직인다.
        verticalArrangement = Arrangement.spacedBy(ReplaySectionGap),
    ) {
        ReplayHeader(
            title = gameReplayTitleFor(strings.language),
            subtitle = gameHistorySummaryLine(entry, strings),
            closeLabel = strings.close,
            onBackClick = onBackClick,
        )

        if (timeline.isTruncated) {
            Text(
                text = gameReplayTruncatedFor(strings.language, timeline.truncatedAtMoveNumber!!),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // ⚠️ **이 앱에서 배너가 처음 화면에 붙는 자리다**(2026-09-19 사용자). 2026-08-08에 홈에서
        // 떼면서(`59d880c`) *"다음에 붙일 곳은 복기 화면"* 이라고 적어 둔 그 자리다
        // (`PREMIUM_MODE.md` 「배너 광고 재노출 위치 — 보류 결정」).
        // ⚠️ **헤더 아래다** — 위로 올리면 나가는 길(뒤로가기)이 광고 밑에 깔린다.
        // ⚠️ **구독자에게는 뜨지 않는다** — 판정은 `SubscriptionAwareBannerAd`가 갖는다.
        //
        // ⚠️ **차례가 2026-09-19에 「큰 실수」(2026-09-20 「변곡점」으로 리메이크) 앞으로 다시
        // 바뀌었다** — 광고 → 변곡점 → 사석·승률 → 판 → 조작부. `GameReplayContractTest`가 이
        // 순서를 못박는다.
        SubscriptionAwareBannerAd()

        // ⚠️ **광고와 칩 사이의 완충은 이제 이 섹션 간격(`ReplaySectionGap`) 하나가 맡는다** —
        // 예전에 블런더 섹션 자신이 `top = 12.dp`로 더 얹어 두던 것을 걷어냈다. AdMob의
        // 「실수 클릭 유도 배치 금지」는 여전히 지킨다 — 간격이 0이 아니면 충분하다.
        ReplayScoreSwingSection(
            hasScoreData = replay.scoreSnapshots.isNotEmpty(),
            swings = scoreSwings,
            currentMoveNumber = moveNumber,
            strings = strings,
            onJumpTo = { target -> moveNumber = target.coerceIn(0, timeline.lastMoveNumber) },
        )

        // ⚠️ **판보다 위다**(2026-09-19) — "흑 사석 · 승률 · 백 사석"을 변곡점 바로 아래,
        // 판 바로 위에 둔다. 형세 그래프(펼치면 나오는 것)도 같은 컴포넌트라 여기 함께 온다.
        ReplayScoreSection(
            replay = replay,
            moveNumber = moveNumber,
            capturedByBlack = state.capturedBy(StoneColor.Black),
            capturedByWhite = state.capturedBy(StoneColor.White),
            isExpanded = isScoreExpanded,
            onExpandedChange = { isScoreExpanded = it },
            strings = strings,
        )

        // ⚠️ **스크롤 부모를 두지 않는다.** 판은 `pointerInput`이 `awaitFirstDown().consume()`을
        // `inputEnabled` 판정보다 **먼저** 하므로(`GoBoard.kt:238`), 읽기 전용이어도 판 위에서
        // 시작한 끌기를 삼킨다 — 스크롤 안에 넣으면 판 위에서 화면이 안 굴러간다(함정 44).
        // 대신 판이 남는 높이를 받고, 조작부는 자기 높이만 쓴다.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            GoBoard(
                gameState = state,
                candidateMoves = emptyList(),
                moveReviews = moveEvaluations,
                ownershipEstimate = null,
                uxOptions = uxOptions,
                inputEnabled = false,
                engineActivityIndicator = null,
                modifier = Modifier.fillMaxSize(),
                onCoordinateTap = {},
                isGameEnded = moveNumber == timeline.lastMoveNumber,
            )
        }

        ReplayControls(
            moveNumber = moveNumber,
            lastMoveNumber = timeline.lastMoveNumber,
            strings = strings,
            showMoveNumbers = showMoveNumbers,
            onMoveNumberChange = { next -> moveNumber = next.coerceIn(0, timeline.lastMoveNumber) },
            onToggleMoveNumbers = { showMoveNumbers = !showMoveNumbers },
        )

        // ⚠️ **맨 아래다 — 이동 도구와 섞지 않는다.** 위의 네 버튼은 이 화면 안에서 위치를
        // 옮기는 것이고, 이것은 **화면을 떠나 새 대국을 시작하는** 버튼이다. 같은 줄에 두면
        // 「다음 수」 옆에서 잘못 눌린다.
        onBranchFromHere?.let { startBranch ->
            ReplayBranchSection(
                state = state,
                moveNumber = moveNumber,
                language = strings.language,
                onStartBranch = { startBranch(state) },
            )
        }
    }
}

/**
 * 「현 지점부터 커스텀 새 대국하기」(백로그 #172).
 *
 * ⚠️ **끝난 국면에서는 비활성이고, 그 사유를 글로 적는다**(함정 42) — 다시보기는 마지막 수에서
 * 열리므로(#156) 기권·종국으로 끝난 판에서는 **들어오자마자** 눌리지 않는 버튼을 만난다.
 * 판정은 `canStartBranchedGameAt`(shared) 하나가 갖는다 — 화면이 자기 조건을 따로 쓰면
 * 스냅샷을 짓는 쪽과 어긋난다.
 */
@Composable
private fun ReplayBranchSection(
    state: GameState,
    moveNumber: Int,
    language: UiLanguage,
    onStartBranch: () -> Unit,
) {
    val canBranch = canStartBranchedGameAt(state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(
            onClick = onStartBranch,
            enabled = canBranch,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = gameReplayBranchLabelFor(language, moveNumber),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!canBranch) {
            Text(
                text = gameReplayBranchBlockedFor(language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/**
 * 섹션 사이 세로 간격의 유일한 정본(2026-09-19 사용자: "일관성 있게 줄여주기").
 * 바꾸려면 여기 한 줄만 고치면 화면 전체가 같이 움직인다.
 */
private val ReplaySectionGap = 8.dp

@Composable
private fun ReplayHeader(
    title: String,
    subtitle: String,
    closeLabel: String,
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 대국 기록 목록과 같은 자리·같은 방식(#25).
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = closeLabel,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun ReplayControls(
    moveNumber: Int,
    lastMoveNumber: Int,
    strings: UiStrings,
    showMoveNumbers: Boolean,
    onMoveNumberChange: (Int) -> Unit,
    onToggleMoveNumbers: () -> Unit,
) {
    val language = strings.language
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ⚠️ **타이틀은 "17수"가 아니라 "17"이다**(2026-09-19 사용자) — 접미사(한국어 "수",
        // 일본어·중국어 "手")를 뗐다. 언어마다 다르게 골라 떼면 넷 중 셋만 짧아지므로 넷 다
        // 같은 규칙(접미사 없음)으로 맞췄다 — 이 화면 전용 표기이고, `strings.moveCountSuffix`는
        // 다른 화면(`GameMenuSection.kt`·`GamePlaySection.kt`)에서 그대로 쓴다.
        //
        // ⚠️ **체크박스가 이전의 "수순 번호" 버튼을 대신한다.** 버튼 하나를 없애는 대신 타이틀
        // 줄 오른쪽 끝에 얹어 **간결하게** 만들었다(2026-09-19 사용자) — 아래 버튼 줄은 이동
        // 전용으로 남고, 화면 옵션(수순 번호)은 타이틀 옆으로 옮겨 성격이 갈린다.
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (moveNumber == 0) {
                    gameReplayStartPositionFor(language)
                } else {
                    "${strings.moveCountPrefix} $moveNumber / $lastMoveNumber"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // ⚠️ 체크박스 자체(20dp 안팎)는 손가락으로 누르기엔 좁다 — 라벨까지 묶어
                    // **줄 전체를 관문으로** 둔다. `Checkbox`의 `onCheckedChange`는 `null`로
                    // 비워 이중 리스너(체크박스 + 바깥 Row)가 서로 다른 값을 부르는 사고를 막는다.
                    .clickable(onClick = onToggleMoveNumbers),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = showMoveNumbers, onCheckedChange = null)
                Text(
                    text = gameReplayShowMoveNumbersLabelFor(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ⚠️ **슬라이더는 2026-09-19에 걷어냈다**(사용자: "'수순' 아래의 그래프바를 전격
        // 제거한다"). 자리를 옮긴 것도 아니고 되살릴 계획도 없다 — 위치를 훑는 역할은
        // **위에서 항상 펼쳐져 있는 형세 그래프**가 대신한다(같은 요청의 둘째 문장: 그
        // 그래프를 처음부터 확장 상태로 보여 슬라이더가 빠진 자리를 커버한다). 이동은
        // 아래 4버튼과 실착 칩(그 수로 점프)만 남는다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ActionButton(
                label = "⏮",
                enabled = moveNumber > 0,
                onClick = { onMoveNumberChange(0) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "◀",
                enabled = moveNumber > 0,
                onClick = { onMoveNumberChange(moveNumber - 1) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "▶",
                enabled = moveNumber < lastMoveNumber,
                onClick = { onMoveNumberChange(moveNumber + 1) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "⏭",
                enabled = moveNumber < lastMoveNumber,
                onClick = { onMoveNumberChange(lastMoveNumber) },
                modifier = Modifier.weight(1f),
            )
        }

        // ⚠️ **예비 버튼이다 — 지금은 항상 비활성**(2026-09-19 사용자: "나중에 엔진도 개입시켜서
        // 적극적으로 리플레이 분석할 수 있게 하고자 합니다"). 이 자리에 실을 기능(그 수순에서
        // 형세 재분석·추천 수 조회)은 대국 화면과 달리 **지나간 국면을 엔진에 다시 물어야** 해서
        // 성격이 다르다 — 비동기 분석·대기 표시·캐시가 새로 필요하다(백로그 #156이 남긴 메모).
        // ⚠️ 라벨은 대국 화면과 같은 표(`UiStrings.featureShortName`)를 그대로 쓴다 — 나중에
        // 배선할 때 같은 기능은 같은 이름이어야 한다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ActionButton(
                label = strings.featureShortName(FeatureId.Eval),
                enabled = false,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = strings.featureShortName(FeatureId.TopMoves),
                enabled = false,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 형세 지표. **켜고 끄는 것은 그래프 자신이 한다** — 대국 화면과 같은 컴포넌트를 쓴다.
 *
 * ⚠️ **형세 기록이 없으면 그래프를 아예 그리지 않는다.** 접힌 바는 값이 없을 때 `0.0`을 적는데,
 * 0.0은 "호각"이라는 뜻이라 **재지 않은 판을 호각이었다고 말하게 된다**(#156 착수 전 경고).
 */
@Composable
private fun ReplayScoreSection(
    replay: GameReplayData,
    moveNumber: Int,
    capturedByBlack: Int,
    capturedByWhite: Int,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    strings: UiStrings,
) {
    val upToNow = remember(replay, moveNumber) {
        replay.scoreSnapshots.filter { it.hasScoreData && it.moveNumber <= moveNumber }
    }
    val hasAnyScoreData = remember(replay) { replay.scoreSnapshots.any { it.hasScoreData } }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (!hasAnyScoreData) {
            Text(
                text = gameReplayNoScoreDataFor(strings.language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            return@Column
        }
        ScoreTimelineGraph(
            snapshots = upToNow,
            capturedByBlack = capturedByBlack,
            capturedByWhite = capturedByWhite,
            whiteWinRate = upToNow.lastOrNull()?.whiteWinRate,
            isExpanded = isExpanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 「변곡점」 구간(2026-09-20 사용자 리메이크 — "큰 실수"를 대체한다). 칩을 누르면 그 수로 뛴다.
 *
 * ⚠️ **누가 두었는지 보지 않는다** — [ScoreSwingHighlight]는 [ScoreSnapshot] 하나만으로
 * 고르므로, 사람:사람·사람:AI·AI:AI 어느 조합의 대국에도 똑같이 뜬다(옛 "실착" 목록은
 * 사람이 둔 수에만 붙었다).
 *
 * ⚠️ 형세 기록이 **하나도 없는 것**과 **기록은 있는데 변곡점이 없는 것**은 다른 말이다 — 앞의
 * 것을 "변곡점 없음"으로 적으면 잴 자료가 없었던 판을 형세가 안정적이던 판으로 바꿔 말하게 된다.
 */
@Composable
private fun ReplayScoreSwingSection(
    hasScoreData: Boolean,
    swings: List<ScoreSwingHighlight>,
    currentMoveNumber: Int,
    strings: UiStrings,
    onJumpTo: (Int) -> Unit,
) {
    val language = strings.language
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${gameReplayScoreSwingSectionFor(language)} \u00B7 ${gameReplayScoreSwingCriterionFor(language)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        when {
            !hasScoreData -> Text(
                text = gameReplayNoScoreDataForSwingsFor(language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            swings.isEmpty() -> Text(
                text = gameReplayNoScoreSwingsFor(language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(swings, key = { it.moveNumber }) { highlight ->
                    ReplayToggleButton(
                        label = gameReplayScoreSwingChipLabelFor(
                            language,
                            highlight.moveNumber,
                            highlight.swing,
                        ),
                        // 지금 서 있는 수의 칩은 **채워서** 표시한다. 프리미엄 테두리를 강조에
                        // 쓰지 않는다 — 그 금색은 "프리미엄 축의 기능"이라는 뜻이 이미 있다.
                        isOn = highlight.moveNumber == currentMoveNumber,
                        onClick = { onJumpTo(highlight.moveNumber) },
                        minHeight = ScoreSwingChipMinHeight,
                        contentPadding = ScoreSwingChipContentPadding,
                    )
                }
            }
        }
    }
}

/**
 * 켜짐이 **배경으로** 보이는 작은 버튼. 대국 화면의 `ToggleActionButton`과 생김새를 맞췄지만,
 * 그쪽은 `GameActionButtonState`·`GameUiEvent`를 받아 대국 세션에 묶여 있어 여기서는 못 쓴다.
 *
 * ⚠️ 높이를 고정하지 않는다(함정 9) — 글꼴 배율 2.0에서 글자 상자가 잘린다. 라벨은 한 줄로
 * 유지하되 넘치면 말줄임한다(대국 화면 버튼들과 같은 처리).
 */
@Composable
private fun ReplayToggleButton(
    label: String,
    isOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = ActionButtonMinHeight,
    contentPadding: PaddingValues = ActionButtonContentPadding,
) {
    val content = @Composable {
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (isOn) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = minHeight),
            shape = ActionButtonShape,
            contentPadding = contentPadding,
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = minHeight),
            shape = ActionButtonShape,
            contentPadding = contentPadding,
        ) { content() }
    }
}

/**
 * 변곡점 칩의 최소 높이 — 이동 버튼([ActionButtonMinHeight], 48dp)보다 **20% 낮다**
 * (2026-09-19 사용자: *"세로 여백이 많아 보인다"*, 2026-09-20 "변곡점"으로 리메이크해도 유지).
 *
 * ⚠️ **48dp를 밑도는 것은 의도다.** Material의 권장 터치 영역이 48dp이므로 이 칩은 그보다 작고,
 * 그래서 **화면의 주 조작부에는 이 높이를 쓰지 않는다** — ⏮◀▶⏭와 수순 번호는 48dp 그대로다.
 * 칩은 "바로 그 수로 뛰는 지름길"이라 같은 일을 슬라이더·이동 버튼으로도 할 수 있다.
 * ⚠️ `height`가 아니라 `heightIn(min=)`이다(함정 9) — 글꼴 배율이 커지면 칩도 함께 자란다.
 */
private val ScoreSwingChipMinHeight: Dp = ActionButtonMinHeight * 0.8f

/** 낮아진 칩에 맞춘 안쪽 여백 — 높이만 줄이고 패딩을 그대로 두면 글자가 상자에 낌다. */
private val ScoreSwingChipContentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
