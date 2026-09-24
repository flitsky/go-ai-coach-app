package com.worksoc.goaicoach.application.attendance

interface AttendanceStorePort {
    fun save(state: AttendanceState)
    fun load(): AttendanceState

    /**
     * 읽기-고치기-쓰기를 **한 덩어리로** 한다(refactor backlog #21). [transform]은 지금 저장된 상태를
     * 받아 새 상태를 돌려주고, `null`이면 아무것도 쓰지 않는다.
     *
     * ⚠️ **이 저장소는 두 스레드가 쓴다** — foreground 체크인(`AttendanceCheckInCoordinator`,
     * `Dispatchers.IO`)과 Claim·화면 쪽 체크인(메인 스레드). `load` 뒤 `save`를 따로 부르면 그 사이에
     * 끼어든 쪽의 쓰기를 지운다(Claim이 지워지면 **같은 회차가 다시 지급된다**). 그래서 저장된 값에
     * 기대 고쳐 쓰는 경로는 전부 이 함수를 지난다. [save]는 상태를 통째로 정하는 쓰기에만 쓴다.
     *
     * 구현은 [transform]을 **정확히 한 번**, 다른 쓰기와 겹치지 않게 부른다. [transform]은 순수해야
     * 한다 — 안에서 다른 저장소를 건드리거나 오래 걸리는 일을 하지 말 것(구현이 락을 쥐고 부른다).
     *
     * @return 저장한 새 상태. [transform]이 `null`을 돌려줘 쓰지 않았으면 `null`.
     */
    fun update(transform: (AttendanceState) -> AttendanceState?): AttendanceState?
}
