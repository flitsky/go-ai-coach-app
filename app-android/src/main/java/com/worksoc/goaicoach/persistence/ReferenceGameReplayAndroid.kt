package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.gamehistory.GameReplayData

// 참고 기보의 본문은 안드로이드 에셋에서 읽는다(#294 — 목록 한 줄 · id는 공용 `ReferenceGameHistory.kt`).

fun loadReferenceGameReplay(context: Context): GameReplayData? =
    runCatching {
        context.assets.open(ReferenceGameAssetPath).bufferedReader().use { it.readText() }
    }.getOrNull()?.let { raw -> GameReplayCodec.decode(raw, ReferenceGameBoardSize) }
