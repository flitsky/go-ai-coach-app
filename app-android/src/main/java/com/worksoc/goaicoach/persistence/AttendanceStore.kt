package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.attendance.AttendanceState
import com.worksoc.goaicoach.application.attendance.AttendanceStorePort
import org.json.JSONArray
import org.json.JSONObject

internal class AttendanceStore(context: Context) : AttendanceStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun save(state: AttendanceState) {
        prefs.edit()
            .putString(StateKey, AttendanceCodec.encode(state))
            .apply()
    }

        /**
     * 출석 일수·수령 회차 저장분을 통째로 지워 **설치 직후와 같은 상태로 되돌린다**(정식 릴리즈 초기화, 백로그 #63).
     *
     * ⚠️ 기본값을 `save`하지 않고 키를 **제거**한다 — 기본값을 써 넣으면 "한 번도 저장한 적 없음"과
     * "기본값을 저장함"이 저장소에서 구분되지 않고, 나중에 스키마가 늘 때 그 둘의 의미가 갈릴 수 있다.
     * 신규 설치를 그대로 재현하는 쪽이 초기화의 정의에 맞다.
     */
    fun clear() {
        prefs.edit().clear().commit()
    }

    override fun load(): AttendanceState {
        val raw = prefs.getString(StateKey, null) ?: return AttendanceState()
        return AttendanceCodec.decode(raw) ?: AttendanceState()
    }

    private companion object {
        const val PrefsName = "go_ai_coach_attendance"
        const val StateKey = "attendance_state"
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
