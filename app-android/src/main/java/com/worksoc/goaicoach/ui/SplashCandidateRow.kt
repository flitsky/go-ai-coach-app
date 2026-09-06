package com.worksoc.goaicoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 개발자 모드 1차의 **시작 화면 후보 재생기**(백로그 #126). `1`~`9`를 누르면 그 후보를
 * 전체 화면으로 한 번 재생한다.
 *
 * ## ⚠️ 왜 파일이 따로인가 — 예산이 그렇게 시켰다
 * `SettingsScreen.kt`(훅 13)도 `DeveloperTestSection.kt`(훅 4)도 **여유가 0**이다(#102,
 * `LayeringContractTest`). 재생 중인 후보를 담을 훅 하나를 어느 쪽에 얹어도 그물에 걸리는데,
 * **그 그물이 원하는 답은 "예산을 올려라"가 아니라 "역할을 하나 더 만들어라"** 다. 그래서 이
 * 컴포저블이 자기 상태를 스스로 갖는다.
 *
 * ## ⚠️ 기동과 **같은 재생기**를 쓴다
 * [SplashPlayer]를 그대로 부른다. 미리보기용 재생기를 따로 만들면 **여기서 고른 것과 실제로
 * 실리는 것이 어긋날 수 있고**, 그 어긋남은 고르고 난 한참 뒤에야 드러난다.
 *
 * ## ⚠️ 이것은 임시 도구다
 * 하나가 정해지면 [SplashVariant.Current]에 박고 **이 행을 남길지 지울지 판단해야 한다** —
 * 1차 섹션은 `release` 빌드에도 그대로 실린다(함정 11번). 남기더라도 그것은 **결정**이어야지
 * 잊어서 남는 것이면 안 된다.
 */
@Composable
internal fun SplashCandidateRow() {
    val strings = LocalUiStrings.current
    var preview by remember { mutableStateOf<SplashVariant?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = strings.settingsDevSplashTitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = strings.settingsDevSplashSubtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
        // ⚠️ **버튼 라벨은 숫자만 쓴다** — 후보 이름 9개를 4개 언어에 넣으면 `UiStrings`에 36줄이
        // 늘어나는데, 이 화면은 고르고 나면 역할이 끝난다. 무엇이 무엇인지는 [SplashVariant]의
        // KDoc이 든다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SplashVariant.entries.forEach { candidate ->
                TextButton(
                    onClick = { preview = candidate },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                ) {
                    Text(candidate.number.toString(), fontSize = 14.sp)
                }
            }
        }
    }

    // ⚠️ `usePlatformDefaultWidth = false`가 없으면 다이얼로그가 좌우 여백을 두고 앉아
    // **화면을 가득 채우는 연출을 재현하지 못한다** — 미리보기의 뜻이 사라진다.
    preview?.let { candidate ->
        Dialog(
            onDismissRequest = { preview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SplashPlayer(
                variant = candidate,
                onFinished = { preview = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
