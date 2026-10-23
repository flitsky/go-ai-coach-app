package com.worksoc.goaicoach.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.gamehistory.GameHistoryWinRate
import com.worksoc.goaicoach.application.gamehistory.WinRatePeriod
import com.worksoc.goaicoach.application.gamehistory.gameHistoryWinRate
import com.worksoc.goaicoach.persistence.GameHistoryStore
import com.worksoc.goaicoach.persistence.ReferenceGameHistoryId
import com.worksoc.goaicoach.shared.policy.KgsRank
import com.worksoc.goaicoach.ui.designsystem.ActionButtonShape
import com.worksoc.goaicoach.ui.designsystem.AppElevation
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.designsystem.RankTierBadge
import com.worksoc.goaicoach.ui.home.LocalRankMeasureUiState
import com.worksoc.goaicoach.ui.home.RankResetButton
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.kgsRankLabelFor
import com.worksoc.goaicoach.ui.l10n.myPageNoRecordFor
import com.worksoc.goaicoach.ui.l10n.rankMeasurePeakRankFor
import com.worksoc.goaicoach.ui.l10n.rankMeasureTitleFor
import com.worksoc.goaicoach.ui.l10n.winRateSummaryFor

/**
 * 머리말이 그리는 것 — 저장소에서 읽어 온 값이다. [rank]가 `null`이면 이 기기에서 승급 대국을 둘 수 없다는 뜻이고
 * (사람 모델이 없다 — 홈에도 그 카드가 없다), 그때는 기력과 승급 대국의 줄을 그리지 않는다.
 */
internal data class MyPageProfile(
    val rank: KgsRank?,
    val peakRank: KgsRank?,
    val winRate: GameHistoryWinRate,
)

/**
 * 마이 페이지의 **머리말**(백로그 #240, 2026-10-09 사용자) — 왼쪽에 내 기력, 오른쪽에 전적 세 줄(캐릭터 대국 · 승급 대국 · 최고 기력).
 *
 * 기력은 홈의 「승급 대국」 카드와 **같은 구간의 테두리**([RankTierBadge])로 보인다 — 아바타가 설 자리에 기력이 선다.
 * 전적은 대국 기록 화면의 승률과 **같은 셈**이다(`gameHistoryWinRate` — 사람 대 AI · 11수 이상 · 결과를 아는 판).
 * 여기서는 기간을 고르지 않고 **누적**만 보인다. 기간별로 보려면 대국 기록 화면이 있다.
 *
 * ⚠️ 저장소를 여기서 직접 읽는다 — 이 화면은 상태를 `GoCoachApp.kt`에 두지 않는다(그 파일의 훅 여유가 0이다, `MyPageScreen`의 KDoc).
 * 화면을 여는 시점의 기록 한 번이면 된다 — 이 화면에 있는 동안에는 대국이 끝나지 않는다.
 */
@Composable
internal fun MyPageProfileHeader(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rankMeasure = LocalRankMeasureUiState.current
    // ⚠️ 번들 참고 기보는 뺀다 — 사용자가 둔 판이 아니다(대국 기록 화면의 승률과 같은 처리).
    val winRate = remember(context) {
        gameHistoryWinRate(
            entries = GameHistoryStore(context).loadAll().filter { it.id != ReferenceGameHistoryId },
            period = WinRatePeriod.AllTime,
            nowMillis = System.currentTimeMillis(),
        )
    }
    MyPageProfileCard(
        profile = MyPageProfile(
            rank = rankMeasure.state.rank.takeIf { rankMeasure.isAvailable },
            peakRank = rankMeasure.state.peakRank,
            winRate = winRate,
        ),
        modifier = modifier,
        // 급수 초기화(사용자 2026-10-15) — 승급 대국을 열 수 있는 기기에서만(기력이 보일 때만).
        resetAction = if (rankMeasure.isAvailable) ({ RankResetButton(rankMeasure) }) else null,
    )
}

/**
 * [MyPageProfileHeader]의 그림 — 값만 받아 그린다(구간마다의 모습을 나란히 찍어 볼 수 있게 나눴다).
 *
 * **늘 다 보인다 — 접고 펴지 않는다**(사용자 2026-10-23, #281 — #280의 「처음에는 간소, 누르면 자세히」와 「자세히 보기 ⌄ / 접기 ⌃」를 없앴다):
 * 기력 테두리 옆에 최고 기력 · 승급 대국 · 캐릭터 대국 전적, 카드 아래 줄의 **오른쪽 끝에 「급수 초기화하기」**.
 * 테두리 아래의 「현재 기력」 글자는 없앴다(사용자 — 테두리가 이미 말한다). 기력이 없는 기기(사람 모델이 없다)는 캐릭터 대국 한 줄뿐이다.
 */
@Composable
internal fun MyPageProfileCard(
    profile: MyPageProfile,
    modifier: Modifier = Modifier,
    /** 카드 아래 줄 오른쪽 끝의 동작 — 「급수 초기화하기」. 없으면 자리를 비우지 않는다. */
    resetAction: (@Composable () -> Unit)? = null,
) {
    val strings = LocalUiStrings.current
    val language = strings.language
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ActionButtonShape,
        tonalElevation = AppElevation.Level1,
    ) {
        Column {
        Row(
            modifier = Modifier.padding(start = AppSpacing.Space16, end = AppSpacing.Space16, top = AppSpacing.Space12, bottom = AppSpacing.Space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12),
        ) {
            profile.rank?.let { rank ->
                RankTierBadge(rank = rank, label = kgsRankLabelFor(language, rank), modifier = Modifier.size(ProfileRankBadgeSize))
            }
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                // 줄의 이름은 대국 페이지의 두 카드와 같다 — 어느 메뉴에서 둔 판인지가 그대로 읽힌다.
                val rankMeasureRecord = (rankMeasureTitleFor(language) to (winRateSummaryFor(language, profile.winRate.rankMeasure) ?: myPageNoRecordFor(language)))
                    .takeIf { profile.rank != null }
                val regularRecord = strings.startMatch to (winRateSummaryFor(language, profile.winRate.regular) ?: myPageNoRecordFor(language))
                val allRecords = listOfNotNull(rankMeasureRecord, regularRecord)
                // 이름과 전적을 한 줄에 나란히 두되, **한 줄이라도 넘치면 전부 위아래로 쌓는다** — 긴 언어(영어의 `Character Match`) ·
                // 세 자리 판 수 · 큰 글꼴 배율에서 전적이 `27승 / 19패`처럼 중간에서 접히지 않게.
                val labelStyle = LocalTextStyle.current.copy(fontSize = AppTextSize.Text12)
                val summaryStyle = LocalTextStyle.current.copy(fontSize = AppTextSize.Text13, fontWeight = FontWeight.SemiBold)
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val labelWidth = with(density) { allRecords.maxOf { measurer.measure(it.first, labelStyle, maxLines = 1).size.width }.toDp() }
                val summaryWidth = with(density) { allRecords.maxOf { measurer.measure(it.second, summaryStyle, maxLines = 1).size.width }.toDp() }
                val sideBySide = labelWidth + AppSpacing.Space8 + summaryWidth <= maxWidth
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space6)) {
                    // 최고 기력이 첫 줄이다(사용자 2026-10-22).
                    if (profile.rank != null) {
                        Text(
                            text = rankMeasurePeakRankFor(language, profile.peakRank),
                            fontSize = AppTextSize.Text13,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    allRecords.forEach { (label, summary) ->
                        if (sideBySide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                                Text(
                                    text = label,
                                    style = labelStyle,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.width(labelWidth).alignByBaseline(),
                                )
                                Text(text = summary, style = summaryStyle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.alignByBaseline())
                            }
                        } else {
                            Column {
                                Text(text = label, style = labelStyle, color = MaterialTheme.colorScheme.secondary)
                                Text(text = summary, style = summaryStyle, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
        // 아래 줄 — 오른쪽 끝에 「급수 초기화하기」(사용자 2026-10-23, #281). 끝 여백이 작은 것은 버튼(`TextButton`)이 제 안쪽 여백을 가져서다.
        // 초기화할 것이 없으면(처음 상태 · 기력이 없는 기기) 버튼이 그려지지 않고 아래 여백만 남는다.
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = AppSpacing.Space8).padding(end = AppSpacing.Space4, bottom = AppSpacing.Space4),
            contentAlignment = Alignment.CenterEnd,
        ) {
            resetAction?.invoke()
        }
        }
    }
}

/** 머리말의 기력 테두리 한 변 — 홈 카드(60dp)보다 조금 크다. 이 화면에서는 이것이 「나」의 얼굴이다. */
private val ProfileRankBadgeSize = 72.dp
