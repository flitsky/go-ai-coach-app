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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worksoc.goaicoach.R

/**
 * 유튜브 기초 강좌 한 편 — 썸네일은 네트워크 로딩 없이 앱에 번들된 drawable을 쓴다(사용자
 * 결정, 2026-08-12: 새 이미지 로딩 라이브러리를 추가하지 않고 고정 목록으로 운영 — 영상이
 * 바뀔 때만 코드와 함께 썸네일 이미지도 같이 교체한다).
 */
internal data class StudyVideoEntry(
    /**
     * 소개 문구 표의 키(백로그 #33). URL이 아니라 짧은 이름을 쓰는 이유는, 영상을 교체할 때
     * **URL과 썸네일만 갈아 끼우고 문구는 그대로 두는** 경우가 흔하기 때문이다 — 주제가 같은
     * 다른 강의로 바꿀 때 네 언어를 다시 쓰지 않아도 된다.
     */
    val id: String,
    val youtubeUrl: String,
    val thumbnailRes: Int,
)

// 2026-08-12 사용자 요청으로 조사: 국내 유튜브에서 "바둑 기초/입문" 검색 시 실제로 걸리는
// 결과 상위권은 대부분 마술/예능성 콘텐츠라 조회수만으로 고르면 강좌가 아닌 게 뽑힌다 —
// 그래서 실제 강좌 채널(바둑에듀) 안에서 조회수가 높은 순으로 3편을 골랐다(직접 확인한
// 조회수: #1 45만, #3 14만, #2 12만, 확인일 2026-08-12). 같은 채널의 1~3강이라 순서대로
// 보면 자연스러운 입문 커리큘럼이 되는 것도 장점.
//
// ⚠️ 소개 문구는 여기 없다(백로그 #33) — 네 언어 표가 `UiStringsStudyVideos.kt`에 있다.
// 한국어 리터럴로 두었더니 영어·일본어·중국어 사용자도 이 세 줄만 한글로 봤다.
internal val studyVideoEntries: List<StudyVideoEntry> = listOf(
    StudyVideoEntry(
        id = "rules",
        youtubeUrl = "https://www.youtube.com/watch?v=TEp_hxTHbV0",
        thumbnailRes = R.drawable.study_thumb_baduk_intro_1,
    ),
    StudyVideoEntry(
        id = "shapes",
        youtubeUrl = "https://www.youtube.com/watch?v=XOX0gOQ3FCg",
        thumbnailRes = R.drawable.study_thumb_baduk_intro_3,
    ),
    StudyVideoEntry(
        id = "life_and_death",
        youtubeUrl = "https://www.youtube.com/watch?v=tvmo7P1v9nc",
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
    // 소개는 표시 언어를 따른다(백로그 #33) — 썸네일의 접근성 설명도 같은 문구를 쓴다.
    val description = LocalUiStrings.current.studyVideoDescription(entry)

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
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 96.dp, height = 54.dp)
                .clip(RoundedCornerShape(6.dp)),
        )

        Text(
            text = description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // ⚠️ **줄 수를 제한하지 않는다**(백로그 #107). `maxLines = 2` + 말줄임이었는데
            // 1.3배에서 세 편이 **전부** 잘렸다(`… for a…`). 소개가 잘리면 그 강좌가 무엇을
            // 다루는지 알 수 없어 **목록의 존재 이유가 사라진다.**
            // ⚠️ 여기는 **폭 경쟁이 없다** — 화면의 3분의 2가 비어 있었다. 다른 자리(격자 칸·
            // 착수 모드 스위치)처럼 문구를 줄여 풀 문제가 아니라, **캡 자체가 근거 없는 제한**이다.
            // 행은 고정 높이가 아니므로 접히는 만큼 자란다(함정 9번).
        )
    }
}
