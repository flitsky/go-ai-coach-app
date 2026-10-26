package com.worksoc.goaicoach.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.botcharacter.BotCharacterCatalog
import com.worksoc.goaicoach.ui.designsystem.AppImage
import com.worksoc.goaicoach.ui.designsystem.BotCharacterAvatar
import com.worksoc.goaicoach.ui.designsystem.painterFor
import com.worksoc.goaicoach.ui.l10n.DailyQuoteCatalog
import com.worksoc.goaicoach.ui.l10n.LocalUiStrings
import com.worksoc.goaicoach.ui.l10n.ProvideUiLanguage
import com.worksoc.goaicoach.ui.l10n.QuoteLineBreak
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.UiStrings
import com.worksoc.goaicoach.ui.quickactions.AppQuickActions
import com.worksoc.goaicoach.ui.splash.AppSplash
import com.worksoc.goaicoach.ui.splash.SplashVariant

/** 카탈로그 「문구」(W6) — 네 언어의 앱 이름 · 설정 제목과 오늘의 명언 한 줄(iOS는 구절 줄바꿈 없이 `Simple`). */
@Composable
internal fun CatalogStrings() {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val quote = DailyQuoteCatalog.quotes.first()
        UiLanguage.entries.forEach { language ->
            val strings = UiStrings.forLanguage(language)
            Text("${language.menuLabel} · ${strings.appTitle} · ${strings.settingsTitle}", style = MaterialTheme.typography.titleSmall)
            val line = quote.lineFor(language)
            Text(
                "${line.text} — ${line.by}",
                style = MaterialTheme.typography.bodyMedium.merge(TextStyle(lineBreak = QuoteLineBreak)),
            )
            HorizontalDivider()
        }
        ProvideUiLanguage { language, _ ->
            Text("저장된 화면 언어(ProvideUiLanguage): ${language.menuLabel} — ${LocalUiStrings.current.appTitle}")
        }
    }
}

/** 카탈로그 「그림」(W7) — 앱 묶음의 비트맵(캐릭터 · 스플래시 · 강의 썸네일)과 코드로 옮긴 보상 벡터. */
@Composable
internal fun CatalogImages() {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("캐릭터(BotCharacterAvatar)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotCharacterCatalog.all.forEach { character -> BotCharacterAvatar(character = character, size = 56.dp) }
        }
        Text("보상 아이콘(벡터)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AppImage.reward_eval, AppImage.reward_top_moves, AppImage.reward_ad_skip, AppImage.reward_undo, AppImage.reward_move_review)
                .forEach { image -> Icon(painterFor(image), contentDescription = image.name, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Text("스플래시 · 강의 썸네일(비트맵)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AppImage.splash_spotlight_night, AppImage.splash_hanok_dojo_day1, AppImage.splash_hanok_dojo_day2,
                AppImage.study_thumb_baduk_intro_1, AppImage.study_thumb_baduk_intro_2, AppImage.study_thumb_baduk_intro_3,
            ).forEach { image ->
                Image(painterFor(image), contentDescription = image.name, modifier = Modifier.size(width = 100.dp, height = 140.dp), contentScale = ContentScale.Crop)
            }
        }
    }
}

/** 카탈로그 「스플래시 · 빠른 버튼」(W8) — 위에 빠른 버튼 줄(화면 모드 · 배경음악), 아래에 스플래시 한 장. */
@Composable
internal fun CatalogSplashAndQuickActions(variant: SplashVariant?) {
    ProvideUiLanguage { _, _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            AppQuickActions(modifier = Modifier.padding(8.dp))
            HorizontalDivider()
            Box(modifier = Modifier.fillMaxWidth().height(560.dp)) {
                AppSplash(variant = variant)
            }
        }
    }
}
