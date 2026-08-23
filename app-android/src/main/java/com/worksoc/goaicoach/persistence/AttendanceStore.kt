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
