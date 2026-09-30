package com.worksoc.goaicoach.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.net.toUri
import com.worksoc.goaicoach.R
import com.worksoc.goaicoach.ui.designsystem.AppRadius
import com.worksoc.goaicoach.ui.designsystem.AppSpacing
import com.worksoc.goaicoach.ui.designsystem.AppTextSize
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesEngineSectionFor
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesIntroFor
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesLibrarySectionFor
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesLoadFailedFor
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesTitleFor
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesViewLicenseFor

/**
 * 설정 → 「오픈소스 라이선스」(백로그 #195). 앱이 싣는 오픈소스와 그 라이선스를 **한 줄의 스크롤 목록**으로 보인다.
 *
 * - 맨 위 구획은 **KataGo 엔진·신경망**과 엔진 안에 컴파일된 C++ 부품이다(`config/aboutlibraries/`의 손 항목).
 *   KataGo 코드·신경망 두 줄은 **처음부터 펼쳐 둔다** — 고지 전문이 열자마자 보여야 한다.
 * - 그 아래는 Gradle 플러그인이 빌드마다 모은 라이브러리(`R.raw.aboutlibraries`)다. 누르면 전문이 펼쳐진다.
 *
 * ⚠️ **`ScreenDestination`이 아니다** — 설정 화면의 하위 상태다(선례: 학습 허브 #163, 다시보기 #156).
 * 그래서 시스템 뒤로가기를 **여기서** 잡는다: 셸의 `BackHandler`는 홈으로 튄다.
 */
@Composable
internal fun OpenSourceLicensesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalUiStrings.current.language
    val closeLabel = LocalUiStrings.current.close
    val context = LocalContext.current
    // ⚠️ 한 번만 읽는다(~120KB). 실패는 삼키지 않고 아래에서 문구로 보인다(함정 40).
    val entries = remember(context) { runCatching { loadOpenSourceEntries(context) }.getOrDefault(emptyList()) }
    // 펼침 상태 — 화면을 벗어나면 버린다. KataGo 두 줄만 처음부터 펼친다.
    val expanded = remember { mutableStateMapOf(*BundledNativeOrder.take(2).map { it to true }.toTypedArray()) }

    BackHandler { onBackClick() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = AppSpacing.Space8, vertical = AppSpacing.Space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = closeLabel,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = openSourceLicensesTitleFor(language),
                fontSize = AppTextSize.Text20,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = AppSpacing.Space8),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = AppSpacing.Space16,
                end = AppSpacing.Space16,
                bottom = AppSpacing.Space24,
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8),
        ) {
            item(key = "intro") {
                Text(
                    text = if (entries.isEmpty()) openSourceLicensesLoadFailedFor(language) else openSourceLicensesIntroFor(language),
                    fontSize = AppTextSize.Text13,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            val (engine, apps) = entries.partition { it.isBundledEngine }
            if (engine.isNotEmpty()) {
                item(key = "section-engine") { SectionTitle(openSourceLicensesEngineSectionFor(language)) }
                items(engine, key = { it.id }) { entry ->
                    OpenSourceEntryRow(entry, expanded[entry.id] == true, language) { expanded[entry.id] = expanded[entry.id] != true }
                }
            }
            if (apps.isNotEmpty()) {
                item(key = "section-apps") { SectionTitle(openSourceLicensesLibrarySectionFor(language)) }
                items(apps, key = { it.id }) { entry ->
                    OpenSourceEntryRow(entry, expanded[entry.id] == true, language) { expanded[entry.id] = expanded[entry.id] != true }
                }
            }
        }
    }
}

private fun loadOpenSourceEntries(context: Context): List<OpenSourceEntry> {
    val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
    return openSourceEntriesFrom(json)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = AppTextSize.Text14,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = AppSpacing.Space12),
    )
}

@Composable
private fun OpenSourceEntryRow(
    entry: OpenSourceEntry,
    isExpanded: Boolean,
    language: UiLanguage,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.Corner10))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle)
            .padding(AppSpacing.Space12),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4),
    ) {
        Text(
            text = listOfNotNull(entry.name, entry.version).joinToString(" "),
            fontSize = AppTextSize.Text14,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entry.authors?.let { authors ->
            Text(text = authors, fontSize = AppTextSize.Text12, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = entry.licenses.joinToString(" · ") { it.name },
            fontSize = AppTextSize.Text12,
            color = MaterialTheme.colorScheme.primary,
        )
        if (isExpanded) {
            entry.description?.let { description ->
                Text(text = description, fontSize = AppTextSize.Text12, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entry.licenses.forEach { license ->
                if (license.content != null) {
                    Text(
                        text = license.content,
                        // 고정폭 글꼴을 쓰지 않는다 — 원문이 80자에서 줄을 끊어 두어, 폰 폭에서는 고정폭이 한 줄을 둘로 쪼갠다.
                        fontSize = AppTextSize.Text12,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppSpacing.Space4),
                    )
                }
                // 원문이 목록에 없는 약관(Google SDK 약관 등)은 주소로 보낸다 — 원문이 있어도 출처는 남긴다.
                license.url?.let { url ->
                    Text(
                        text = if (license.content == null) "${openSourceLicensesViewLicenseFor(language)} ($url)" else url,
                        fontSize = AppTextSize.Text12,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                        },
                    )
                }
            }
        }
    }
}
