package com.worksoc.goaicoach.kv

/**
 * 앱 전용 파일 자리(백로그 #294 — 이식 계획 S33) — 안드로이드 `filesDir`에 해당한다. 경로는 그 뿌리 아래의 상대 경로(`/`로 나눈다).
 * iOS는 Application Support 아래 `files/`다. 안드로이드 저장소는 이것을 쓰지 않는다(오늘의 `java.io.File` 그대로).
 *
 * 실패는 던지지 않고 값으로 돌려준다(`null` · `false` · 0) — 안드로이드 저장소가 `runCatching`으로 하던 것과 같다.
 */
interface AppFileStore {
    fun exists(path: String): Boolean

    fun readText(path: String): String?

    /** 임시 파일(`<path>.tmp`)에 쓰고 디스크까지 내린 뒤(fsync) 이름을 바꾼다 — 쓰다 죽어도 옛 파일이 남는다. */
    fun writeAtomically(path: String, text: String): Boolean

    fun appendText(path: String, text: String): Boolean

    /** 파일 크기(바이트). 없으면 0. */
    fun length(path: String): Long

    /** 디렉터리 안의 이름들. 디렉터리가 없으면 `null`. */
    fun list(path: String): List<String>?

    fun delete(path: String): Boolean

    fun deleteRecursively(path: String): Boolean

    fun mkdirs(path: String): Boolean
}
