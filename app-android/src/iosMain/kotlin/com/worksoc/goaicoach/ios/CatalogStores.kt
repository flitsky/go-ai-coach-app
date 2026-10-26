package com.worksoc.goaicoach.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.gamehistory.GameHistoryEntry
import com.worksoc.goaicoach.application.time.currentEpochMillis
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.GameSetup
import com.worksoc.goaicoach.shared.domain.Ruleset
import com.worksoc.goaicoach.shared.domain.StoneColor
import com.worksoc.goaicoach.shared.policy.ThemeMode
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform

/**
 * 카탈로그 「저장소」(P1~P3) — iOS 저장소에 쓰고 읽는다. 앱을 껐다 켜도 값이 남는지(NSUserDefaults · 파일) 여기서 본다.
 * 화면 모드는 바뀐 값 알림(`modeChanges`)으로 그린다 — 안드로이드 빠른 설정과 같은 길이다.
 */
@Composable
internal fun CatalogStores() {
    val stores = LocalAppPlatform.current.stores
    val theme by remember { stores.themeMode.modeChanges() }.collectAsState(initial = stores.themeMode.load())
    var tick by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // tick을 읽어 버튼을 누를 때마다 아래 값을 다시 읽는다.
        tick.let { }
        Text("화면 모드(알림으로 그림): ${theme.id}")
        Button(onClick = { stores.themeMode.save(ThemeMode.entries[(theme.ordinal + 1) % ThemeMode.entries.size]) }) { Text("화면 모드 바꾸기") }
        val music = stores.backgroundMusic.isEnabled()
        Text("배경음악: ${if (music) "켬" else "끔"} · 언어: ${stores.uiLanguage.loadName() ?: "(기기)"}")
        OutlinedButton(onClick = { stores.backgroundMusic.setEnabled(!music); tick++ }) { Text("배경음악 켬/끔") }
        Text("기기 id: ${stores.deviceIdentity.loadOrCreate().id.take(8)}… · 새로 설치: ${stores.whatsNew.isFreshInstall()}")
        val attendance = stores.attendance.load()
        Text("출석 ${attendance.attendanceCount}회")
        OutlinedButton(onClick = { stores.attendance.update { it.copy(attendanceCount = it.attendanceCount + 1) }; tick++ }) { Text("출석 +1") }
        val history = stores.gameHistory.loadAll()
        Text("대국 기록 ${history.size}판" + (history.lastOrNull()?.let { " · 마지막 ${it.id}" } ?: ""))
        OutlinedButton(onClick = {
            val now = currentEpochMillis()
            stores.gameHistory.appendCompletedGame(
                GameHistoryEntry(
                    id = "$now-ios",
                    playedAtMillis = now,
                    setup = GameSetup.standard(BoardSize.Nine, Ruleset.Japanese, handicapCount = 0, komi = 6.5),
                    playerSetup = PlayerSetup(),
                    moveCount = 0,
                    humanColor = StoneColor.Black,
                    winner = null,
                ),
            )
            tick++
        }) { Text("빈 대국 기록 하나 붙이기") }
        val prefs = stores.userPreferences.load()
        Text("설정 묶음: 글꼴 ${prefs.appFontScale} · 판 ${prefs.boardSize}")
    }
}
