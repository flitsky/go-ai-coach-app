package com.worksoc.goaicoach.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.engine.EngineDownloadStatus
import com.worksoc.goaicoach.engine.currentEngineDownloadTracker
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import com.worksoc.goaicoach.ui.designsystem.AppBorderWidth
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.guide.GuideBlockingOverlays
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiStringsDownloadGuide

/**
 * 이 화면을 그릴 것인가. 엔진이 **스텁으로 정해졌으면** 곧바로 걷는다 — Play 밖 설치(debug·사이드로드)는 받기가 1초 안에
 * 영구 실패로 끝나고, 여기 머물면 갇힌다. 스텁 안내는 걷힌 뒤 셸의 팝업 사슬이 띄운다(`EngineUnavailableNoticeDialog`).
 */
internal fun engineSetupGateVisible(required: Boolean, closedByUser: Boolean, engineMode: EngineMode): Boolean =
    required && !closedByUser && engineMode != EngineMode.Stub

/**
 * 「시작하기」를 열 것인가 — 받기가 끝났거나, 받을 것이 없었는데(판정 직후 다른 길로 채워진 경우) 엔진이 떴을 때.
 * ⚠️ 받기가 끝나도 **자동으로 넘기지 않는다**(U-77) — 조작법을 보던 사용자의 화면을 빼앗지 않는다.
 */
internal fun engineSetupCanStart(status: EngineDownloadStatus, engineMode: EngineMode): Boolean =
    status == EngineDownloadStatus.Completed || (status == EngineDownloadStatus.Idle && engineMode == EngineMode.LocalProcess)

/**
 * 엔진 준비 화면(백로그 #249 ①) — 엔진 모델이 없으면 앱을 켤 때마다 **홈 위에 전체 화면으로** 뜬다.
 * 받는 동안 바둑판 조작법을 보여 주고, 맨 아래 진행 카드가 상태를 알린다. 「나중에」는 늘 있다(U-76).
 *
 * 실행 루틴(사용자 2026-10-11): 스플래시 → **(엔진 파일이 없으면) 이 화면** → 「시작하기」 → 출석 보상 → 첫돌이 가이드 → 홈.
 * 띄울지·닫았는지는 **셸이 든다**(`GoCoachScreen` — 출석 팝업도 같은 값으로 미룬다, 원장 `VISIBILITY_GATE`).
 * 셸은 이것을 화면들 **위에** 그리고, 스플래시는 그 위에 얹힌다 — 스플래시가 걷히면 홈이 아니라 이 화면이 보인다.
 *
 * @param onClose 「나중에」·「시작하기」·뒤로 가기 — 셋 다 이 화면을 걷는다.
 */
@Composable
internal fun EngineSetupGateScreen(
    engineMode: EngineMode,
    onClose: () -> Unit,
) {
    val status by currentEngineDownloadTracker().status.collectAsState()
    // 홈의 첫돌이 말풍선(③)이 이 화면 아래에서 「봤음」으로 소진되지 않게 한다(함정 40 — 팝업 게이트는 GuideBlockingOverlays).
    GuideBlockingOverlays.TrackWhileShown()
    RegisterCellularConfirmationHandler()
    BackHandler(onBack = onClose)

    EngineSetupGateContent(
        status = status,
        canStart = engineSetupCanStart(status, engineMode),
        onLater = onClose,
        onStart = onClose,
    )
}

@Composable
private fun EngineSetupGateContent(
    status: EngineDownloadStatus,
    canStart: Boolean,
    onLater: () -> Unit,
    onStart: () -> Unit,
) {
    val language = LocalUiStrings.current.language
    // ⚠️ Material3 `Surface`는 눌러도 아래(홈)로 터치를 흘리지 않는다 — 이 화면이 홈을 덮는 동안 홈은 눌리지 않는다.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(AppSpacing.Space20),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = UiStringsDownloadGuide.boardControlsGuide(language),
                        fontSize = AppTextSize.Text18,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = UiStringsDownloadGuide.setupSubtitle(language),
                        fontSize = AppTextSize.Text13,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (!canStart) {
                    TextButton(onClick = onLater) { Text(UiStringsDownloadGuide.later(language)) }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.Space16))

            // 판다가 바둑판 위에서 조작법을 시연한다(#249 ③) — 설정의 「바둑판 조작법」과 같은 가이드다.
            BoardControlsGuide(modifier = Modifier.weight(1f))

            Spacer(modifier = Modifier.height(AppSpacing.Space16))

            EngineSetupProgressCard(status = status, canStart = canStart, onStart = onStart)
        }
    }
}

/** 화면 맨 아래의 진행 카드 — 받는 중 · Wi-Fi 대기 · 실패는 홈 배너와 같은 부품이고, 끝나면 「시작하기」가 된다. */
@Composable
private fun EngineSetupProgressCard(
    status: EngineDownloadStatus,
    canStart: Boolean,
    onStart: () -> Unit,
) {
    val language = LocalUiStrings.current.language
    when {
        canStart -> Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadius.Corner12))
                .border(AppBorderWidth.Hairline, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(AppRadius.Corner12)),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = AppSpacing.Space16, vertical = AppSpacing.Space12),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = UiStringsDownloadGuide.aiReady(language),
                    fontSize = AppTextSize.Text14,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onStart, shape = RoundedCornerShape(AppRadius.Corner8)) {
                    Text(UiStringsDownloadGuide.start(language), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Completed는 위의 canStart가 먼저 받는다 — 여기는 받기 요청 전(수 ms)의 Idle뿐이다.
        status == EngineDownloadStatus.Idle -> Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadius.Corner8)),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        ) {
            Column(modifier = Modifier.padding(horizontal = AppSpacing.Space12, vertical = AppSpacing.Space8)) {
                Text(
                    text = UiStringsDownloadGuide.preparing(language),
                    fontSize = AppTextSize.Text12,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(AppSpacing.Space6))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
            }
        }

        else -> EngineDownloadStatusBanner(status = status)
    }
}
