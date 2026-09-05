package com.worksoc.goaicoach.application.lifecycle

/**
 * 개발자 모드가 켜져 있는 동안 앱을 **주기적으로 최초 설치 상태로 되돌리는** 판정(백로그 #99).
 *
 * ## 왜 있는가 — 함정 11번에 대한 답이다
 * 개발자 테스트 1차 섹션은 **release 빌드에도 실린다**(10탭은 런타임 게이트일 뿐이다). 지금까지는
 * *"우연히 10번 두드릴 일은 없다"* 는 **진입 장벽 하나**로만 막고 있었다. 이 정책이 그 자리를
 * 메운다 — 알아내도 **[ResetIntervalHours]시간마다 전부 잃으므로 이득이 남지 않는다.**
 * 사용자 표현으로는 *"반복 작업이 귀찮아지게"* 다(2026-09-05 발주).
 *
 * ## ⚠️ 시각이 아니라 **구간 번호**로 판정한다
 * *"UTC 3의 배수 시각"* 을 글자대로 *"지금 시각이 3의 배수인가"* 로 구현하면 **그 시각에 앱을
 * 켜지 않은 사람은 영원히 초기화되지 않는다.** 그래서 마지막 초기화가 **어느 구간**에서 일어났는지
 * 기억하고, 지금이 **다른 구간**이면 초기화한다. 하루 8구간(0·3·6·9·12·15·18·21시 UTC)이다.
 *
 * ⚠️ **기기 시계를 되돌려도 우회되지 않는다** — 구간이 달라지는 것은 앞으로 감든 뒤로 감든
 * 마찬가지이기 때문이다. 같은 구간 안에 계속 머무르게 시계를 조작하는 것은 가능하지만, 그것이
 * 곧 *"귀찮게 만든다"* 는 이 항목의 목적을 달성한다(함정 12번 — 이 앱의 시계는 주입 시임이 없다).
 *
 * ⚠️ **debug 빌드에서는 이 정책을 부르지 않는다**(2026-09-05 사용자 결정) — 개발자 본인의 실기
 * 테스트가 [ResetIntervalHours]시간마다 날아가면 안 된다. 그 판단은 호출부가 하고, 이 함수는
 * 순수하게 구간만 센다.
 */
object DeveloperModeResetPolicy {

    /** 초기화 구간 길이(시간). 하루 24 / 3 = **8번**이다. */
    const val ResetIntervalHours: Int = 3

    private const val MillisPerHour: Long = 60L * 60L * 1000L
    private val intervalMillis: Long = ResetIntervalHours * MillisPerHour

    /**
     * [utcMillis]가 속한 구간 번호. UTC 기준 에포크부터 [ResetIntervalHours]시간 단위로 끊는다.
     *
     * ⚠️ **에포크 기준이라 구간 경계가 UTC 0·3·6…시와 맞는다** — 에포크(1970-01-01 00:00 UTC)가
     * 정확히 구간 경계이고 24가 3으로 나누어떨어지기 때문이다.
     *
     * ⚠️ **`Math.floorDiv`가 아니라 Kotlin stdlib의 `floorDiv`를 쓴다** — `Math`는 JVM 전용이라
     * commonMain에서 쓰면 **iOS 타겟이 깨진다**(2026-09-05에 실제로 밟았다). 일반 나눗셈(`/`)도
     * 안 된다: 음수 시각(에포크 이전)에서 0 쪽으로 잘려 구간이 어긋난다.
     */
    fun intervalIndexOf(utcMillis: Long): Long = utcMillis.floorDiv(intervalMillis)

    /**
     * 지금 초기화해야 하는가.
     *
     * @param lastResetUtcMillis 마지막 초기화(또는 개발자 모드를 켠) 시각. `null`이면 **초기화하지
     *   않는다** — 켜자마자 지우면 사용자가 무슨 일이 일어났는지 알 수 없다. 켜는 쪽이 기준점을
     *   심는 것이 이 설계의 전제다.
     */
    fun shouldReset(lastResetUtcMillis: Long?, nowUtcMillis: Long): Boolean {
        if (lastResetUtcMillis == null) return false
        return intervalIndexOf(nowUtcMillis) != intervalIndexOf(lastResetUtcMillis)
    }
}
