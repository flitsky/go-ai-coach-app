package com.worksoc.goaicoach.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.platform.NoticeHandle
import com.worksoc.goaicoach.platform.appBuildInfo
import com.worksoc.goaicoach.platform.formatDeviceLocale
import com.worksoc.goaicoach.platform.formatRootLocale
import com.worksoc.goaicoach.platform.isDebugBuild
import com.worksoc.goaicoach.platform.localTimeOfDay
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.foundation.PlatformBackHandler
import com.worksoc.goaicoach.ui.foundation.PlatformPredictiveBackHandler
import kotlinx.coroutines.CancellationException

/** 카탈로그 「플랫폼 이음매」(W4) — 알림 한 줄 · 뒤로가기(가장자리 밀기) · 빌드 정보 · 시각 · 서식을 시뮬레이터에서 직접 본다. */
@Composable
internal fun CatalogPlatformSeams() {
    val platform = LocalAppPlatform.current
    var handle by remember { mutableStateOf<NoticeHandle?>(null) }
    var backCount by remember { mutableIntStateOf(0) }
    var guardBack by remember { mutableStateOf(false) }
    var predictive by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var lastGesture by remember { mutableStateOf("—") }
    if (guardBack) {
        PlatformBackHandler { backCount++ }
    }
    PlatformPredictiveBackHandler(enabled = predictive) { events ->
        try {
            events.collect { progress = it }
            lastGesture = "놓음(완료)"
        } catch (e: CancellationException) {
            lastGesture = "거둠(취소)"
            throw e
        } finally {
            progress = 0f
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("알림 한 줄(토스트 대신)")
        Button(onClick = { handle = platform.notice.show("짧은 알림 — 2초") }) { Text("짧게") }
        Button(onClick = { handle = platform.notice.show("긴 알림 — 3.5초 동안 남는다", long = true) }) { Text("길게") }
        OutlinedButton(onClick = { handle?.cancel() }) { Text("지금 알림 거두기") }
        Text("뒤로가기 — 화면 왼쪽 가장자리에서 밀기")
        OutlinedButton(onClick = { guardBack = !guardBack }) { Text(if (guardBack) "뒤로가기 잡는 중(${backCount}회)" else "뒤로가기 잡기 켜기") }
        OutlinedButton(onClick = { predictive = !predictive }) { Text(if (predictive) "예측 뒤로가기 켜짐 — $lastGesture" else "예측 뒤로가기 켜기") }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        val build = appBuildInfo
        Text("빌드: ${build.versionName} (${build.versionCode}) · ${build.buildType} · 디버그=$isDebugBuild")
        val now = localTimeOfDay()
        Text("지금: " + formatRootLocale("%d:%02d:%02d", now.hour, now.minute, now.second))
        Text("서식: " + formatDeviceLocale("%.1f집 · %05d · %3\$s · %%", 6.5, 42, "셋째"))
    }
}
