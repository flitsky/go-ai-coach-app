package com.worksoc.goaicoach.kv

// 안드로이드 `persistence/AttendanceStore.kt`의 공용 사본(백로그 #294 — P2) — 저장소 클래스를 빼고 코덱만, import만 바꿨다(org.json → kv.json). 고칠 때는 둘을 함께 고친다(`KvStoreParityTest`).

import com.worksoc.goaicoach.application.attendance.AttendanceState
import com.worksoc.goaicoach.application.attendance.AttendanceStorePort
import com.worksoc.goaicoach.kv.json.JSONArray
import com.worksoc.goaicoach.kv.json.JSONObject

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
