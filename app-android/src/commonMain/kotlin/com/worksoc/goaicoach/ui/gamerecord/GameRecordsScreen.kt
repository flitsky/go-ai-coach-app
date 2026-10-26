package com.worksoc.goaicoach.ui.gamerecord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.worksoc.goaicoach.application.engine.EngineAnalysisClient
import com.worksoc.goaicoach.application.engine.EngineScoringClient
import com.worksoc.goaicoach.platform.BundledAssets
import com.worksoc.goaicoach.platform.appIoDispatcher
import com.worksoc.goaicoach.shared.gamerecord.GameRecord
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.history.ReplayAnalysisEngine
import com.worksoc.goaicoach.ui.l10n.GameRecordOpenFailure
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.gameRecordGameNumberFor
import com.worksoc.goaicoach.ui.l10n.gameRecordGroupTitleFor
import com.worksoc.goaicoach.ui.l10n.gameRecordOpenFailureFor
import com.worksoc.goaicoach.ui.l10n.gameRecordPlayersFor
import com.worksoc.goaicoach.ui.l10n.gameRecordResultFor
import com.worksoc.goaicoach.ui.l10n.gameRecordUntitledFor
import com.worksoc.goaicoach.ui.l10n.gameRecordsIntroFor
import com.worksoc.goaicoach.ui.l10n.gameRecordsOpenFileFor
import com.worksoc.goaicoach.ui.l10n.gameRecordsTitleFor
import com.worksoc.goaicoach.ui.study.StudyEntryRow
import com.worksoc.goaicoach.ui.study.StudyScreenHeader
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 홈 「기보 보기」(백로그 #269, 입구는 홈 타일 — 사용자 2026-10-18) — 앱에 싣는 명국 목록 + 「SGF 파일 열기」. 판을 누르면 그 위에
 * 보기 화면([GameRecordViewer])이 열린다.
 *
 * ⚠️ 보기 화면은 **이 화면의 상태**다(셸의 목적지는 이 목록 하나) — 다시보기·학습 허브와 같은 선례다. 시스템 뒤로 가기는 보기 화면이
 * 스스로 잡아 목록으로 돌아오고, 목록에서는 셸이 홈으로 보낸다.
 * ⚠️ 연 파일은 저장하지 않는다(1차) — 보기 화면을 닫으면 사라진다. 파일은 기기의 문서 선택 창으로 고르므로 저장소 권한이 필요 없고,
 * 기기 밖으로 나가는 것도 없다(개인정보 답안 그대로).
 */
@Composable
internal fun GameRecordsScreen(
    /** 지나간 국면의 형세·추천 수 — 대국 기록 화면과 같이 엔진 창구 둘을 받아 분석 창구(`ReplayAnalysisEngine`)는 이 화면이 짓는다. */
    engineScoring: EngineScoringClient,
    engineAnalysis: EngineAnalysisClient,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalUiStrings.current.language
    val analysisEngine = remember(engineScoring, engineAnalysis) { ReplayAnalysisEngine(engineScoring, engineAnalysis) }
    val platform = LocalAppPlatform.current
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf<OpenedGameRecord?>(null) }
    var failure by remember { mutableStateOf<GameRecordOpenFailure?>(null) }
    // 자동 진행의 간격 — 여기 두어 다른 판을 열어도 고른 간격이 남는다(이 화면을 떠나면 기본 1초로 — 스레드가 정한 범위).
    var autoPlaySeconds by rememberSaveable { mutableIntStateOf(DefaultGameRecordAutoPlaySeconds) }
    val assets = platform.assets
    val rows by produceState(initialValue = emptyList<BundledRow>(), assets) {
        value = withContext(appIoDispatcher) { BundledGameRecords.map { entry -> BundledRow(entry, loadBundled(assets, entry)) } }
    }
    val openFile = rememberSgfFilePicker(MaxFileBytes) { bytes ->
        run {
            when (val result = withContext(appIoDispatcher) { openFromBytes(bytes) }) {
                is FileOpen.Opened -> {
                    failure = null
                    opened = OpenedGameRecord(key = result.key, title = fileTitle(language, result.record), record = result.record)
                }
                is FileOpen.Failed -> failure = result.failure
            }
        }
    }

    opened?.let { current ->
        GameRecordViewer(
            opened = current,
            analysisEngine = analysisEngine,
            onBackClick = { opened = null },
            autoPlaySeconds = autoPlaySeconds,
            onAutoPlaySecondsChange = { autoPlaySeconds = it },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        StudyScreenHeader(title = gameRecordsTitleFor(language), onBackClick = onBackClick)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = AppSpacing.Space16, end = AppSpacing.Space16, bottom = AppSpacing.Space16),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                    Text(
                        text = gameRecordsIntroFor(language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    if (openFile != null) {
                        OutlinedButton(onClick = openFile, modifier = Modifier.fillMaxWidth()) {
                            Text(gameRecordsOpenFileFor(language))
                        }
                    }
                    failure?.let {
                        Text(
                            text = gameRecordOpenFailureFor(language, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            items(rows, key = { it.entry.asset }) { row ->
                val record = row.record
                StudyEntryRow(
                    title = bundledTitle(language, row.entry),
                    subtitle = record?.let { playersAndResult(language, it) } ?: "",
                    badge = null,
                    onClick = record?.let { loaded ->
                        {
                            scope.launch {
                                val evaluation = withContext(appIoDispatcher) { loadEvaluation(assets, row.entry) }
                                opened = OpenedGameRecord(
                                    key = "bundled:${row.entry.asset}",
                                    title = bundledTitle(language, row.entry),
                                    record = loaded,
                                    evaluation = evaluation,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

private data class BundledRow(val entry: BundledGameRecord, val record: GameRecord?)

private sealed interface FileOpen {
    data class Opened(val key: String, val record: GameRecord) : FileOpen

    data class Failed(val failure: GameRecordOpenFailure) : FileOpen
}

/** 묶음 이름 + (여러 판이면) 「N국」. */
private fun bundledTitle(language: UiLanguage, entry: BundledGameRecord): String {
    val group = gameRecordGroupTitleFor(language, entry.group)
    return entry.gameNumber?.let { "$group · ${gameRecordGameNumberFor(language, it)}" } ?: group
}

private fun playersAndResult(language: UiLanguage, record: GameRecord): String = listOfNotNull(
    gameRecordPlayersFor(language, record.info.blackName, record.info.whiteName),
    gameRecordResultFor(language, record.info.result) ?: record.info.result,
).joinToString(" · ")

/** 연 파일의 제목 — 대국 이름(`GN`), 없으면 대회·회차(`EV`·`RO`), 둘 다 없으면 「SGF 기보」. */
private fun fileTitle(language: UiLanguage, record: GameRecord): String =
    record.info.gameName
        ?: listOfNotNull(record.info.event, record.info.round).joinToString(" ").takeIf { it.isNotBlank() }
        ?: gameRecordUntitledFor(language)

/** 앱에 실은 기보는 모두 읽혀야 한다(`GameRecordCatalogContractTest`) — 그래도 못 읽으면 그 줄만 눌리지 않는다. */
private fun loadBundled(assets: BundledAssets, entry: BundledGameRecord): GameRecord? = runCatching {
    val bytes = assets.readBytes(entry.assetPath) ?: return@runCatching null
    (openGameRecord(bytes) as? GameRecordOpen.Opened)?.record
}.getOrNull()

/** 그 판의 미리 잰 형세(`*.eval`, #269) — 없거나 읽지 못하면 null이고, 그 판은 형세 기록 없이 열린다(엔진에 묻는 형세 보기는 그대로). */
private fun loadEvaluation(assets: BundledAssets, entry: BundledGameRecord): GameRecordEvaluation? = runCatching {
    assets.readBytes(entry.evaluationPath)?.let(::readGameRecordEvaluation)
}.getOrNull()

private fun openFromBytes(bytes: ByteArray?): FileOpen {
    if (bytes == null) return FileOpen.Failed(GameRecordOpenFailure.CannotOpen)
    return when (val result = openGameRecord(bytes)) {
        is GameRecordOpen.Opened -> FileOpen.Opened(key = "sgf:${bytes.contentHashCode()}", record = result.record)
        is GameRecordOpen.Failed -> FileOpen.Failed(result.failure)
    }
}

/** 기보 모음 파일(수천 판)도 첫 판만 읽으면 되므로 앞부분만 읽는다 — 스레드가 정한 상한. */
private const val MaxFileBytes = 4 * 1024 * 1024
