package com.worksoc.goaicoach.application.guide

/**
 * 업데이트한 뒤 「새로워진 점」을 무엇을 보일지(백로그 #257, 사용자 2026-10-13).
 *
 * 앱에는 서버가 없어 「새로워진 점」은 **앱에 실린 버전별 묶음**이다 — [available]이 그 묶음들의 versionCode다. 이 함수는 그중
 * **아직 보지 않은 것**을 새것부터 돌려준다. 규칙(사용자가 고른 것):
 * - **문구를 준비한 버전만** — 묶음이 없는 버전(작은 수정)은 조용히 넘어간다.
 * - **여러 버전을 건너뛰고 업데이트했으면** 그 사이의 묶음을 함께 보인다.
 * - **새로 설치한 사람에게는 보이지 않는다** — 달라진 것을 비교할 앞 버전이 없다.
 *
 * 스레드가 메운 빈 곳: 이 기능이 생기기 전의 버전에서 업데이트한 사람(본 버전 기록이 없는데 새 설치가 아니다)은 **지금 버전 이하의 가장 새 묶음 하나**만
 * 본다 — 그 사람이 어느 버전에서 왔는지 알 수 없어서, 오래된 묶음까지 쏟아 놓지 않는다.
 *
 * @param available 앱에 실린 묶음들의 versionCode(순서 무관).
 * @param lastSeenVersionCode 「새로워진 점」을 마지막으로 본(또는 새 설치로 기록한) 버전. 기록이 없으면 `null`.
 * @param currentVersionCode 지금 실행 중인 앱의 versionCode — 이보다 새 묶음은 보이지 않는다.
 * @param isFreshInstall 이 설치가 한 번도 업데이트되지 않았는가.
 */
fun whatsNewPending(
    available: List<Int>,
    lastSeenVersionCode: Int?,
    currentVersionCode: Int,
    isFreshInstall: Boolean,
): List<Int> {
    if (lastSeenVersionCode == null && isFreshInstall) return emptyList()
    val upToNow = available.filter { it <= currentVersionCode }.distinct().sortedDescending()
    if (lastSeenVersionCode == null) return upToNow.take(1)
    return upToNow.filter { it > lastSeenVersionCode }
}
