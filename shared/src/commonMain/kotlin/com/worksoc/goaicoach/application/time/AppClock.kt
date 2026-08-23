package com.worksoc.goaicoach.application.time

import kotlin.time.Clock

/**
 * `shared`(commonMain)가 벽시계를 읽는 유일한 통로.
 *
 * `System.currentTimeMillis()`는 `java.lang.System`이라 import 문 없이도 androidTarget에서
 * 그냥 컴파일된다 — 그래서 `LayeringContractTest`의 `import java.` 검사에 한 번도 걸리지 않은
 * 채 commonMain 곳곳으로 번졌고, 기본 빌드에서는 제외되는
 * `:shared:compileKotlinIosSimulatorArm64`만 조용히 깨져 있었다(플랫폼 독립 원칙 위반).
 *
 * 호출부가 이미 갖고 있는 `nowMillis` 주입 시임(기본값 파라미터/람다)은 그대로 두고 그 **기본값**만
 * 이 함수를 거치게 한다. 즉 테스트가 시간을 고정하는 방법은 전과 동일하고, 플랫폼 시계를 실제로
 * 읽는 지점만 이 파일 하나로 모인다. 엔진 쪽 주입 포트([com.worksoc.goaicoach.application.engine.EngineClock])의
 * 기본 구현도 여기로 위임한다.
 *
 * 경과 시간(duration) 측정에는 이 함수 대신 `kotlin.time.TimeSource.Monotonic`을 쓴다 — 벽시계는
 * 뒤로 점프할 수 있고, 그쪽이 `System.nanoTime()`의 정확한 대체다.
 *
 * `kotlin.time.Clock`은 kotlin-stdlib에 들어 있어 새 의존성이 아니다 — kotlinx-datetime 도입
 * 여부와는 별개 결정이다([com.worksoc.goaicoach.application.attendance.utcDayIndex]의 주석 참고).
 */
fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
