package com.worksoc.goaicoach.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.R

/**
 * 유튜브 기초 강좌 한 편 — 썸네일은 네트워크 로딩 없이 앱에 번들된 drawable을 쓴다(사용자
 * 결정, 2026-08-12: 새 이미지 로딩 라이브러리를 추가하지 않고 고정 목록으로 운영 — 영상이
 * 바뀔 때만 코드와 함께 썸네일 이미지도 같이 교체한다).
 */
internal data class StudyVideoEntry(
    val youtubeUrl: String,
    val description: String,
    val thumbnailRes: Int,
)

// 2026-08-12 사용자 요청으로 조사: 국내 유튜브에서 "바둑 기초/입문" 검색 시 실제로 걸리는
// 결과 상위권은 대부분 마술/예능성 콘텐츠라 조회수만으로 고르면 강좌가 아닌 게 뽑힌다 —
// 그래서 실제 강좌 채널(바둑에듀) 안에서 조회수가 높은 순으로 3편을 골랐다(직접 확인한
// 조회수: #1 45만, #3 14만, #2 12만, 확인일 2026-08-12). 같은 채널의 1~3강이라 순서대로
// 보면 자연스러운 입문 커리큘럼이 되는 것도 장점.
internal val studyVideoEntries: List<StudyVideoEntry> = listOf(
    StudyVideoEntry(
        youtubeUrl = "https://www.youtube.com/watch?v=TEp_hxTHbV0",
        description = "바둑을 처음 배우는 분을 위한 10분 기초 규칙 강의 (바둑에듀)",
        thumbnailRes = R.drawable.study_thumb_baduk_intro_1,
    ),
    StudyVideoEntry(
        youtubeUrl = "https://www.youtube.com/watch?v=XOX0gOQ3FCg",
        description = "입구자·날일자 등 바둑 기초 행마를 10분에 익히기 (바둑에듀)",
        thumbnailRes = R.drawable.study_thumb_baduk_intro_3,
    ),
    StudyVideoEntry(
        youtubeUrl = "https://www.youtube.com/watch?v=tvmo7P1v9nc",
        description = "삶의 조건과 빅, 입문자가 꼭 알아야 할 바둑 기초 개념 (바둑에듀)",
        thumbnailRes = R.drawable.study_thumb_baduk_intro_2,
    ),
)

/**
 * 2 Depth: 학습 하기 화면 — 유튜브 바둑 기초 강좌 목록을 좌측 썸네일 + 우측 한줄 소개로
 * 보여주고, 탭하면 해당 영상을 유튜브 앱/브라우저로 연다.
 */
@Composable
internal fun StudyScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalUiStrings.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = strings.close,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = strings.study,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (studyVideoEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = strings.notImplementedMessage,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                studyVideoEntries.forEach { entry ->
                    StudyVideoRow(
                        entry = entry,
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.youtubeUrl))) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyVideoRow(entry: StudyVideoEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(entry.thumbnailRes),
            contentDescription = entry.description,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 96.dp, height = 54.dp)
                .clip(RoundedCornerShape(6.dp)),
        )

        Text(
            text = entry.description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
