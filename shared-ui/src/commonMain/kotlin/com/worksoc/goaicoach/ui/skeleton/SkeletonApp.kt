package com.worksoc.goaicoach.ui.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.shared.policy.KgsRank

/**
 * iOS 뼈대의 첫 화면(백로그 #292) — **임시다.** #294가 `app-android`의 홈을 이 모듈로 옮기면 그 홈이 이 자리를 대신한다.
 *
 * 하는 일은 하나: 공유 코드(`:shared`)의 값이 iOS 화면까지 오는지 보인다 — 승급 대국 사다리([KgsRank], 20급 ~ 9단 29칸).
 * 문구는 한국어 하나로 적었다 — 4개 언어 문구(`ui/l10n/`)는 #294가 화면과 함께 옮긴다.
 */
@Composable
fun SkeletonApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "포켓 바둑 코치", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = rankLadderLine(), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "iOS 뼈대 · 백로그 #292", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal fun rankLadderLine(): String =
    "승급 대국 ${rankLabel(KgsRank.Weakest)} ~ ${rankLabel(KgsRank.Strongest)} · ${KgsRank.all.size}칸"

private fun rankLabel(rank: KgsRank): String = "${rank.number}${if (rank.isDan) "단" else "급"}"
