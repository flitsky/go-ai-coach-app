package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

/** 화면(창)의 폭 · 높이(dp) — 안드로이드는 `LocalConfiguration`, iOS는 창 크기를 밀도로 나눈 값이다(백로그 #294 — S17). */
@Composable
expect fun screenWidthDp(): Int

@Composable
expect fun screenHeightDp(): Int

/** 가장자리까지 덮는 전체 화면 대화상자(조작법 안내 등) — 안드로이드는 창 장식에 맞추지 않고, iOS는 안전 영역 여백을 주지 않는다. */
expect fun fullScreenDialogProperties(): DialogProperties
