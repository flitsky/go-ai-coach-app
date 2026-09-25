package com.worksoc.goaicoach.application.engine

/**
 * 기기 벤치마크 프로필의 저장 계약.
 *
 * ⚠️ **저장 원문·파일 경로를 돌려주는 메서드를 여기 두지 않는다**(refactor backlog #86).
 * 둘 다 문자열이라 타입으로는 값이지만 저장 형식과 매체다. 디버그 리포트용 원문은
 * [EngineBenchmarkController]의 `storedBenchmarkText`로 따로 들어오고, 경로는 어댑터 밖으로
 * 나가지 않는다(`docs/ARCHITECTURE.md` ⓐ).
 */
interface EngineBenchmarkStorePort {
    fun exists(): Boolean

    fun save(profile: EngineBenchmarkProfile)
    fun load(): EngineBenchmarkProfile?
}
