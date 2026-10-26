package com.worksoc.goaicoach.platform

import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import kotlinx.coroutines.CoroutineDispatcher

/** 파일 · 에셋을 읽는 일의 스레드(백로그 #294) — 안드로이드는 `Dispatchers.IO` 그대로, iOS는 `Dispatchers.Default`(Kotlin/Native에는 IO가 공개돼 있지 않다). */
expect val appIoDispatcher: CoroutineDispatcher

/** 앱에 실은 파일(안드로이드 `assets/`) — 경로는 그 뿌리 아래 상대 경로다. 없거나 못 읽으면 `null`(S20). */
interface BundledAssets {
    fun readBytes(path: String): ByteArray?

    /** 대국 기록 맨 앞의 참고 기보 본문(`ReferenceGameHistory.kt`) — 안드로이드는 그 저장 코덱으로, iOS는 공용 사본 코덱으로 읽는다. */
    fun referenceGameReplay(): GameReplayData?

    /** 오픈소스 목록(aboutlibraries JSON) — 안드로이드는 빌드가 만든 `R.raw.aboutlibraries`. 없으면 `null`(목록이 비고 안내 문구가 뜬다). */
    fun licensesJson(): String?
}

/** 바이트를 글자로 — [charsetName](SGF의 `CA` 같은 IANA 이름)을 모르면 UTF-8이다(S22). */
expect fun decodeText(bytes: ByteArray, charsetName: String?): String
