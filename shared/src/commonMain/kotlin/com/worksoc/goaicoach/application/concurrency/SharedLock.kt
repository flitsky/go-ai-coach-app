package com.worksoc.goaicoach.application.concurrency

/**
 * commonMain에서 쓸 수 있는 최소 상호배제 잠금.
 *
 * `synchronized(x) { ... }`는 `kotlin.synchronized`(JVM 전용)라 androidTarget에서는 그냥
 * 컴파일되지만 iOS 타깃에서는 `Unresolved reference`가 난다 — `System`과 마찬가지로 import 문이
 * 없어서 `LayeringContractTest`의 텍스트 검사에도 안 걸리던 누수다.
 *
 * 안드로이드 동작은 이전과 완전히 동일하게 유지한다(모니터 락 그대로). 잠금이 필요한 상태를
 * 락 없는 자료구조로 바꾸는 판단은 하지 않았다 — 여기서는 플랫폼 의존성만 걷어낸다.
 *
 * `expect class`가 아니라 `공용 인터페이스 + expect 팩터리 함수`인 이유: `expect`/`actual`
 * **클래스**는 아직 Beta라 컴파일할 때마다 경고가 붙는다(KT-61573). 함수 쪽은 안정 API다.
 */
internal interface SharedLock {
    fun <T> withLock(block: () -> T): T
}

internal expect fun sharedLock(): SharedLock
