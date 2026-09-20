package com.worksoc.goaicoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.application.gamehistory.buildBranchedGameSnapshot
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.persistence.GameHistoryStore
import com.worksoc.goaicoach.persistence.ReferenceGameHistoryId
import com.worksoc.goaicoach.persistence.loadReferenceGameReplay
import com.worksoc.goaicoach.persistence.referenceGameHistoryEntry
import com.worksoc.goaicoach.shared.GameState
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
    /**
     * 저장 슬롯(`SavedGameStorePort`는 **한 판만** 담는다)에 진행 중인 **다른** 대국이 있는가.
     * 홈의 「대국 하기」가 쓰는 것과 **같은 신호**다 — 분기 대국도 그 슬롯을 밀어내므로
     * 같은 경고를 같은 문구로 띄운다(백로그 #172, 구 U-38).
     */
    hasResumableSession: Boolean,
    /**
     * 분기 대국을 시작한다(백로그 #172). 셸이 복원 경로에 태우고 목적지를 `InGame`으로 바꾼다.
     *
     * ⚠️ **`GameReplayData`를 셸에 넘기지 않는다** — 셸은 다시보기를 몰라야 하고
     * (`GameReplayContractTest`), 그래서 이 화면이 [SavedGameSnapshot]까지 지어서 올린다.
     */
    onStartBranchedGame: (SavedGameSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current
    // ⚠️ **참고 기보는 항상 맨 앞이다** — 실기 기록 정렬(재생 시각 내림차순)에 끼워 넣는 게
    // 아니라, 그 앞에 붙인다. 그래야 나중에 실제로 둔 판이 쌓여도 순서가 안 밀린다.
    //
    // ⚠️ **`var`다 — 한 줄 평을 저장한 뒤 다시 읽지 않고 그 자리에서 갱신한다**(2026-09-20).
    // 전체를 다시 읽으면 정렬이 다시 계산돼 화면이 깜빡이고, 그사이 다른 기록이 추가됐다면
    // 그 판이 슬쩍 끼어드는 모양이 된다.
    var entries by remember(context) {
        mutableStateOf(
            listOf(referenceGameHistoryEntry()) +
                GameHistoryStore(context).loadAll().sortedByDescending { it.playedAtMillis },
        )
    }

    // ⚠️ **다시보기는 이 화면의 하위 상태다 — 셸의 목적지가 아니다**(백로그 #156).
    // `GoCoachApp.kt`의 상태 훅 예산이 42/42로 여유 0이라(함정 3), 목적지를 하나 더 만들면
    // 셸에 상태가 늘어 `LayeringContractTest`가 깨진다. 뒤로가기는 `GameReplayScreen`의
    // 중첩 `BackHandler`가 잡는다.
    //
    // ⚠️ **본문까지 한 상태에 담는다.** 목록은 메타데이터만 읽고 본문은 열릴 때 그 한 판만
    // 읽는데(#151), 그 읽기를 컴포지션 안에 두면 *"파일이 없더라도 일단 열린 상태"* 가 먼저
    // 생겨서 컴포지션 중에 상태를 되돌리게 된다 — 리컴포지션을 스스로 부르는 모양이다.
    // 누르는 순간 둘을 함께 집으면 그 틈이 아예 없다.
    var opened by remember { mutableStateOf<Pair<GameHistoryEntry, GameReplayData>?>(null) }
    // ⚠️ **경고 팝업의 상태도 여기 둔다 — 셸로 올리지 않는다**(백로그 #172). `GoCoachApp.kt`의
    // 상태 훅 예산은 42/42로 **여유가 0**이라(함정 3), 셸에 `var showOverwrite`를 하나 더하는
    // 순간 `LayeringContractTest`가 깨진다. 확인을 받고 나서야 셸을 부르면 셸은 상태가 필요 없다.
    //
    // ⚠️ **국면을 담아 둔다 — 부울이 아니다.** 팝업을 여는 순간과 「확인」을 누르는 순간 사이에
    // 사용자가 다시보기에서 수순을 옮길 수 있으므로, 누를 때 다시 읽으면 **경고와 다른 자리**에서
    // 대국이 시작된다. 물어본 그 국면을 그대로 들고 있다가 그것으로 시작한다.
    var pendingBranch by remember { mutableStateOf<GameState?>(null) }
    opened?.let { (entry, replay) ->
        // `hasResumableSession`이면 먼저 묻고, 아니면 곧바로 갈라진다 — 확인 팝업을 두 번
        // 겹치지 않는다(2026-09-20 결정: 버튼 라벨이 이미 "17수부터 새 대국"이라 말한다).
        val branch: (GameState) -> Unit = { state ->
            if (hasResumableSession) {
                pendingBranch = state
            } else {
                onStartBranchedGame(branchedSnapshotOf(entry, replay, state))
            }
        }
        GameReplayScreen(
            entry = entry,
            replay = replay,
            onBackClick = { opened = null },
            onBranchFromHere = branch,
            modifier = modifier,
        )
        // ⚠️ 팝업은 다시보기 화면 **위에** 그려야 한다 — `return` 앞에서 함께 컴포즈한다.
        //   제목은 홈의 것을 **그대로** 쓴다(구 U-38: 같은 일에 같은 머리말). 본문만 다르다 —
        //   홈의 문구는 *"대국 설정으로 이동하시겠습니까?"* 로 끝나는데 분기는 설정 화면을
        //   거치지 않는다(함정 39). 사유는 `gameReplayBranchOverwriteMessageFor`의 KDoc.
        pendingBranch?.let { state ->
            AlertDialog(
                onDismissRequest = { pendingBranch = null },
                title = { Text(strings.overwriteWarningTitle, fontWeight = FontWeight.Bold) },
                text = {
                    Text(gameReplayBranchOverwriteMessageFor(strings.language, state.moves.size))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingBranch = null
                            onStartBranchedGame(branchedSnapshotOf(entry, replay, state))
                        },
                    ) {
                        Text(strings.confirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingBranch = null }) {
                        Text(strings.cancel)
                    }
                },
            )
        }
        return
    }

    // `hasReplay`가 이미 걸러 주지만 색인과 파일이 어긋날 수 있어(지우다 만 상태) 여기서도
    // 한 번 더 막는다 — 본문이 없으면 열지 않는다.
    val open: (GameHistoryEntry) -> Unit = { entry ->
        val replay = if (entry.id == ReferenceGameHistoryId) {
            loadReferenceGameReplay(context)
        } else {
            GameHistoryStore(context).loadReplay(entry.id)
        }
        replay?.takeIf { !it.isEmpty }?.let { nonEmptyReplay -> opened = entry to nonEmptyReplay }
    }

    // ⚠️ **참고 기보는 이 자리에 못 온다** — 그 행은 `onNoteClick`을 아예 안 받는다(고정값,
    // 수정 불가). 그래서 여기서는 항상 실기록이고, `GameHistoryStore.updateNote`를 믿고 쓴다.
    var editingNoteFor by remember { mutableStateOf<GameHistoryEntry?>(null) }
    editingNoteFor?.let { entry ->
        var noteText by remember(entry.id) { mutableStateOf(entry.note.orEmpty()) }
        val save = {
            val saved = noteText.trim().ifEmpty { null }
            GameHistoryStore(context).updateNote(entry.id, saved)
            entries = entries.map { if (it.id == entry.id) it.copy(note = saved) else it }
            editingNoteFor = null
        }
        AlertDialog(
            onDismissRequest = { editingNoteFor = null },
            title = { Text(gameHistoryNoteDialogTitleFor(strings.language), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    singleLine = true,
                    placeholder = { Text(gameHistoryNotePlaceholderFor(strings.language)) },
                )
            },
            confirmButton = { TextButton(onClick = save) { Text(strings.confirm) } },
            dismissButton = { TextButton(onClick = { editingNoteFor = null }) { Text(strings.cancel) } },
        )
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
                    GameHistoryRow(
                        entry = entry,
                        strings = strings,
                        onClick = { open(entry) }.takeIf { entry.hasReplay },
                        // ⚠️ 참고 기보(id == ReferenceGameHistoryId)는 한 줄 평이 고정값이라
                        // 수정 UI를 안 준다(2026-09-20 사용자 요청 — "수정 불가 처리 필수").
                        onNoteClick = { editingNoteFor = entry }
                            .takeIf { entry.id != ReferenceGameHistoryId },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * 분기 대국의 시작점(백로그 #172) — 계산은 전부 shared의 [buildBranchedGameSnapshot]이 한다.
 *
 * ⚠️ **`topMovesEnabled`는 여기서 정하지 않는다.** 복원 경로가 스냅샷의 값을 설정에 되쓰므로
 * (`applySavedGameRestore`), 지금 값을 모르는 이 화면이 골라 넣으면 사용자의 설정이 조용히
 * 꺼진다 — 셸이 넘겨받아 자기 값으로 덮는다(`GoCoachApp.kt`의 `copy(topMovesEnabled = ...)`).
 */
private fun branchedSnapshotOf(
    entry: GameHistoryEntry,
    replay: GameReplayData,
    state: GameState,
): SavedGameSnapshot =
    buildBranchedGameSnapshot(
        branchState = state,
        playerSetup = entry.playerSetup,
        scoreSnapshots = replay.scoreSnapshots,
        topMovesEnabled = false,
        nowMillis = System.currentTimeMillis(),
    )

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

/**
 * 한 줄 요약 — **목록 행과 다시보기 헤더가 같은 문구를 쓴다**(백로그 #156).
 *
 * ⚠️ 두 벌로 적으면 한쪽만 고쳐지는 사고가 난다 — 이 저장소가 판 렌더에서 같은 이유로 호출을
 * 하나로 묶어 둔 것과 같은 판단이다(`GamePlaySection.kt`의 `board` 람다).
 */
@Composable
internal fun gameHistorySummaryLine(entry: GameHistoryEntry, strings: UiStrings): String {
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
    // ⚠️ **참고 기보는 날짜 자리에 날짜를 안 적는다**(2026-09-20 사용자 요청) — 그 판이 언제
    // 두어졌는지는 의미가 없고, "이건 실기록이 아니라 예시"라는 게 한눈에 보여야 한다.
    val dateOrReferenceLabel = if (entry.id == ReferenceGameHistoryId) {
        gameHistoryReferenceLabelFor(strings.language)
    } else {
        dateFormat(strings.language).format(Date(entry.playedAtMillis))
    }
    val summary = listOf(
        dateOrReferenceLabel,
        "${entry.boardSize}x${entry.boardSize}",
        strings.seatMatchupLabel(entry.playerSetup),
        handicapPhrase(strings, entry.handicapCount),
        strings.gameHistoryOutcomeLabel(entry.winner, entry.isResign, entry.margin),
    ).joinToString(" \u00B7 ")
    return summary
}

/**
 * 2줄 행(2026-09-20 사용자 요청) — 첫 줄은 기존 요약(날짜·판 크기·세팅·접바둑·결과), 둘째
 * 줄은 한 줄 평이다. 둘은 **서로 다른 것을 누른다** — 첫 줄은 다시보기, 둘째 줄은 한 줄 평
 * 입력이라 한 행 안에 클릭 영역을 둘로 가른다.
 *
 * ⚠️ **[onClick]이 `null`이면 첫 줄은 안 눌린다** — 2026-09-18 이전 기록에는 수순이 저장된
 * 적이 없어(#151) 다시보기가 영영 열리지 않는다. 눌러도 아무 일이 없는 행보다, 아예 눌리지
 * 않고 꼬리표도 없는 편이 *"이 줄은 다르다"* 를 말한다.
 *
 * ⚠️ **[onNoteClick]이 `null`이면 둘째 줄도 안 눌린다** — 번들 참고 기보의 한 줄 평은 고정값이라
 * 수정할 수 없다(사용자 지시 — "수정 불가 처리 필수").
 */
@Composable
private fun GameHistoryRow(
    entry: GameHistoryEntry,
    strings: UiStrings,
    onClick: (() -> Unit)?,
    onNoteClick: (() -> Unit)?,
) {
    val summary = gameHistorySummaryLine(entry, strings)
    // ⚠️ **"참고 기보" 부분만 진하게+강조색**(2026-09-20 사용자 요청) — 나머지 조각(판 크기·
    // 세팅·접바둑·결과)은 실기록 행과 같은 평범한 글자여야 한다. 그 라벨은 항상 요약 줄의
    // **맨 앞 접두어**이므로(`gameHistorySummaryLine`), 통째로 지어 넣지 않고 그 자리만 잘라
    // AnnotatedString으로 다시 잇는다 — 라벨 문구가 언어별로 달라도 그대로 맞는다.
    val summaryText = if (entry.id == ReferenceGameHistoryId) {
        val label = gameHistoryReferenceLabelFor(strings.language)
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                append(label)
            }
            append(summary.removePrefix(label))
        }
    } else {
        AnnotatedString(summary)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = summaryText,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (onClick != null) {
                Text(
                    text = "${gameReplayRowBadgeFor(strings.language)} \u203A",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val hasNote = !entry.note.isNullOrBlank()
        Text(
            text = entry.note.takeIf { hasNote } ?: gameHistoryNotePlaceholderFor(strings.language),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onNoteClick != null) Modifier.clickable(onClick = onNoteClick) else Modifier)
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        )
    }
}
