package com.worksoc.goaicoach.ui

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
