package com.worksoc.goaicoach.application.profile

/** 닉네임 저장소(백로그 #294 — S7). 저장할 때 다듬는 규칙은 [UserNicknamePolicy]가 든다. */
interface UserProfileStorePort {
    fun nickname(): String?

    fun saveNickname(raw: String)
}
