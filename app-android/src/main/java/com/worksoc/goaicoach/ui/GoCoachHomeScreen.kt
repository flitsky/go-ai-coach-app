package com.worksoc.goaicoach.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 0 Depth: 홈 화면 (Home Screen)
 * - 사용자가 앱 진입 시 최초로 마주하는 엔트리 화면입니다.
 * - "대국 하기" (대국 설정 로비로 이동) 및 "학습 하기" (준비 중 토스트 피드백) 메뉴를 제공합니다.
 */
@Composable
internal fun GoCoachHomeScreen(
    onStartMatchClick: () -> Unit,
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            HomeLanguageQuickToggle(
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GoStoneLogoBadge()

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = strings.appTitle,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Text(
                text = strings.homeTagline,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // "대국 하기" (Start Match) 카드
        MenuCard(
            title = strings.startMatch,
            subtitle = strings.homeStartMatchSubtitle,
            containerColor = MaterialTheme.colorScheme.primary,
            titleColor = Color.White,
            subtitleColor = Color.White.copy(alpha = 0.85f),
            onClick = onStartMatchClick,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // "학습 하기" (Study Mode) 카드
        MenuCard(
            title = strings.study,
            subtitle = strings.homeStudySubtitle,
            containerColor = MaterialTheme.colorScheme.surface,
            titleColor = MaterialTheme.colorScheme.onSurface,
            subtitleColor = MaterialTheme.colorScheme.secondary,
            onClick = {
                Toast.makeText(context, strings.notImplementedMessage, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun HomeLanguageQuickToggle(
    selectedLanguage: UiLanguage,
    onLanguageChange: (UiLanguage) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HomeLanguageQuickToggleOption(
            label = "KO",
            selected = selectedLanguage == UiLanguage.Korean,
            onClick = { onLanguageChange(UiLanguage.Korean) },
        )
        HomeLanguageQuickToggleOption(
            label = "EN",
            selected = selectedLanguage == UiLanguage.English,
            onClick = { onLanguageChange(UiLanguage.English) },
        )
    }
}

@Composable
private fun HomeLanguageQuickToggleOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun GoStoneLogoBadge() {
    Box(
        modifier = Modifier
            .size(96.dp)
            .shadow(elevation = 6.dp, shape = CircleShape)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF1A1A1A), CircleShape),
            )
            Box(
                modifier = Modifier
                    .offset(x = (-14).dp)
                    .size(42.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    subtitle: String,
    containerColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(containerColor)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
