package com.worksoc.goaicoach.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import platform.Foundation.NSProcessInfo

/**
 * iOS 이식 카탈로그(백로그 #294 — 이식 계획 W1). 공용으로 옮긴 화면 조각을 시뮬레이터에서 **하나씩** 열어 본다 —
 * 앱 전체가 iOS에서 뜨기 전(W22)까지 iOS 앱의 첫 화면이다. 웨이브마다 옮긴 것의 페이지를 [PortCatalogPages]에 더한다.
 *
 * `GOCOACH_PORT_CATALOG_PAGE=<페이지 id>` 환경 변수로 그 페이지를 곧바로 연다(`make ios-catalog PAGE=<id>`) — 스크린샷으로
 * 확인할 때 탭 좌표에 기대지 않으려고(에뮬레이터 확인과 같은 원칙: 좌표 말고 이름으로).
 */
internal class PortCatalogPage(
    val id: String,
    val title: String,
    val wave: String,
    val content: @Composable () -> Unit,
)

@Composable
internal fun PortCatalog(pages: List<PortCatalogPage> = PortCatalogPages) {
    val initial = remember { requestedPageId()?.let { id -> pages.firstOrNull { it.id == id } } }
    var open by remember { mutableStateOf(initial) }
    val page = open
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (page == null) {
                CatalogIndex(pages) { open = it }
            } else {
                Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        TextButton(onClick = { open = null }) { Text("‹ 목록") }
                        Text(text = "${page.title} · ${page.wave}", style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                    Box(modifier = Modifier.fillMaxSize()) { page.content() }
                }
            }
        }
    }
}

@Composable
private fun CatalogIndex(pages: List<PortCatalogPage>, onOpen: (PortCatalogPage) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text("포켓 바둑 코치 — iOS 이식", style = MaterialTheme.typography.headlineSmall)
                Text("옮긴 화면 조각 ${pages.size}개 · 백로그 #294", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(pages) { page ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(page) }.padding(vertical = 12.dp)) {
                Text(page.title, style = MaterialTheme.typography.titleMedium)
                Text("${page.wave} · ${page.id}", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}

private fun requestedPageId(): String? =
    (NSProcessInfo.processInfo.environment["GOCOACH_PORT_CATALOG_PAGE"] as? String)?.takeIf { it.isNotBlank() }

/** 카탈로그의 페이지 — 웨이브마다 옮긴 것을 더한다. */
internal val PortCatalogPages: List<PortCatalogPage> = listOf(
    PortCatalogPage(id = "skeleton", title = "뼈대 첫 화면", wave = "#292") { SkeletonApp() },
    PortCatalogPage(id = "design-tokens", title = "디자인 값 — 색 · 급수 배지 · 대국 버튼", wave = "W2") { CatalogDesignTokens() },
    PortCatalogPage(id = "platform-seams", title = "플랫폼 이음매 — 알림 · 뒤로가기 · 빌드 정보 · 서식", wave = "W4") { CatalogPlatformSeams() },
    PortCatalogPage(id = "stores", title = "저장소 — NSUserDefaults · 파일(껐다 켜도 남는가)", wave = "P1~P3") { CatalogStores() },
    PortCatalogPage(id = "strings", title = "문구 — 네 언어 · 명언 줄바꿈", wave = "W6") { CatalogStrings() },
    PortCatalogPage(id = "images", title = "그림 — 캐릭터 · 보상 벡터 · 스플래시 · 썸네일", wave = "W7") { CatalogImages() },
    PortCatalogPage(id = "splash", title = "스플래시 · 빠른 버튼(기동 추첨)", wave = "W8") { CatalogSplashAndQuickActions(variant = null) },
    PortCatalogPage(id = "board", title = "바둑판 — 착수 · 좌표 · 수순 번호 · 진동", wave = "W11") { CatalogBoard() },
)
