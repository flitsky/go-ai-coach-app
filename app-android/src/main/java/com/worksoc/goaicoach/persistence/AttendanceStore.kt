package com.worksoc.goaicoach.persistence

import android.content.Context
import android.content.SharedPreferences
import com.worksoc.goaicoach.application.attendance.AttendanceState
import com.worksoc.goaicoach.application.attendance.AttendanceStorePort
import org.json.JSONArray
import org.json.JSONObject

/**
 * ## ⚠️ 두 스레드가 같은 키를 읽고-고치고-쓴다 — 쓰기는 전부 [Lock] 안에서 한다 (refactor backlog #21)
 * foreground 체크인은 `AttendanceCheckInCoordinator`가 **`Dispatchers.IO`** 에서, Claim·화면 쪽 체크인은
 * `AttendanceRewardClaimDialog`가 **메인 스레드**에서 돈다. 둘이 겹치면 늦게 쓰는 쪽이 먼저 쓴 쪽을 지운다 —
 * Claim이 지워지면 **같은 회차가 다시 지급되고**(1회권은 멱등이 아니라 한 번 더 쌓인다), 체크인이 지워지면
 * 그날 출석이 되돌아간다. `AttendanceStoreConcurrencyTest`가 두 순서를 모두 재현한다.
 *
 * ⚠️ **락은 인스턴스가 아니라 클래스에 하나다** — 저장소 인스턴스는 호출부마다 따로 만들지만(코디네이터,
 * 다이얼로그, 개발자 도구…) `SharedPreferences`는 이름당 프로세스에 하나라, 인스턴스 락은 아무것도 막지 않는다.
 *
 * ### 함정 71을 따져 본 결과 — 평범한 락으로 충분하다
 * 그 함정은 **락을 쥔 채 외부(엔진 프로세스)를 기다리는** 호출이 다른 전부를 타임아웃까지 얼린 것이다.
 * 여기 임계 구역은 prefs 읽기 → 순수 변환 → 코덱 → `apply()`(메모리 반영 후 디스크는 비동기) 뿐이라
 * **기다릴 외부가 없다.** `synchronized` 안에서는 suspend가 컴파일되지 않고, 변환은 다른 저장소를
 * 건드리지 않아 락 순서 교착도 없다. 메인 스레드가 기다리는 최악은 IO 쪽 한 번의 읽기-쓰기(1ms 미만)이고,
 * prefs 파일의 첫 로드 대기는 락이 없어도 메인이 똑같이 기다리던 것이다. [clear]의 `commit()`만 디스크를
 * 동기로 쓰는데, 그 호출부는 코디네이터가 뜨기 전의 `Application.onCreate`와 개발자 도구뿐이다.
 */
internal class AttendanceStore internal constructor(
    private val prefs: SharedPreferences,
) : AttendanceStorePort {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE),
    )

    override fun save(state: AttendanceState) {
        synchronized(Lock) { write(state) }
    }

    override fun update(transform: (AttendanceState) -> AttendanceState?): AttendanceState? =
        synchronized(Lock) {
            transform(read())?.also(::write)
        }

        /**
     * 출석 일수·수령 회차 저장분을 통째로 지워 **설치 직후와 같은 상태로 되돌린다**(정식 릴리즈 초기화, 백로그 #63).
     *
     * ⚠️ 기본값을 `save`하지 않고 키를 **제거**한다 — 기본값을 써 넣으면 "한 번도 저장한 적 없음"과
     * "기본값을 저장함"이 저장소에서 구분되지 않고, 나중에 스키마가 늘 때 그 둘의 의미가 갈릴 수 있다.
     * 신규 설치를 그대로 재현하는 쪽이 초기화의 정의에 맞다.
     */
    fun clear() {
        synchronized(Lock) { prefs.edit().clear().commit() }
    }

    /** 한 번의 읽기라 락이 필요 없다 — 읽고 나서 고쳐 쓸 거라면 [update]를 쓸 것. */
    override fun load(): AttendanceState = read()

    private fun read(): AttendanceState {
        val raw = prefs.getString(StateKey, null) ?: return AttendanceState()
        return AttendanceCodec.decode(raw) ?: AttendanceState()
    }

    private fun write(state: AttendanceState) {
        prefs.edit()
            .putString(StateKey, AttendanceCodec.encode(state))
            .apply()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_attendance"
        const val StateKey = "attendance_state"

        /** 프로세스에 하나 — 위 KDoc의 "락은 클래스에 하나" 참고. */
        val Lock = Any()
    }
}

internal object AttendanceCodec {
    private const val CurrentSchemaVersion = 1

    fun encode(state: AttendanceState): String =
        JSONObject()
            .put("schema", CurrentSchemaVersion)
            .put("attendanceCount", state.attendanceCount)
            .put("lastCheckInUtcDay", state.lastCheckInUtcDay ?: JSONObject.NULL)
            .put("claimedTiers", JSONArray(state.claimedTiers.toList()))
            .toString()

    fun decode(raw: String): AttendanceState? =
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", -1) != CurrentSchemaVersion) return@runCatching null

            AttendanceState(
                attendanceCount = json.optInt("attendanceCount", 0),
                lastCheckInUtcDay = if (json.has("lastCheckInUtcDay") && !json.isNull("lastCheckInUtcDay")) {
                    json.optLong("lastCheckInUtcDay")
                } else {
                    null
                },
                claimedTiers = decodeClaimedTiers(json.optJSONArray("claimedTiers")),
            )
        }.getOrNull()

    private fun decodeClaimedTiers(array: JSONArray?): Set<Int> {
        if (array == null) return emptySet()
        return buildSet {
            for (i in 0 until array.length()) {
                add(array.optInt(i))
            }
        }
    }
}
