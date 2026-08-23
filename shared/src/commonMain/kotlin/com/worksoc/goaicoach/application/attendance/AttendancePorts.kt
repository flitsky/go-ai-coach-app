package com.worksoc.goaicoach.application.attendance

interface AttendanceStorePort {
    fun save(state: AttendanceState)
    fun load(): AttendanceState
}
