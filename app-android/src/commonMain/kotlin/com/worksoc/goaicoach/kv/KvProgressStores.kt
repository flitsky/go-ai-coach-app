package com.worksoc.goaicoach.kv

import com.worksoc.goaicoach.application.dailydraw.DailyDrawStorePort
import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneProgress
import com.worksoc.goaicoach.application.dailyfortune.DailyFortuneStorePort
import com.worksoc.goaicoach.application.dailyquote.DailyQuoteProgress
import com.worksoc.goaicoach.application.dailyquote.DailyQuoteStorePort
import com.worksoc.goaicoach.application.device.DeviceIdentity
import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort
import com.worksoc.goaicoach.application.guide.GuideProgress
import com.worksoc.goaicoach.application.guide.GuideProgressStorePort
import com.worksoc.goaicoach.application.guide.GuideStep
import com.worksoc.goaicoach.application.guide.LifeAndDeathProgressPort
import com.worksoc.goaicoach.application.profile.UserNicknamePolicy
import com.worksoc.goaicoach.application.profile.UserProfileStorePort
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// 진행도 · 정체 저장소의 공용 구현(백로그 #294 — P1). 안드로이드 `persistence/`의 같은 이름 저장소와 묶음 이름 · 키 · 표기가 같다.

internal class KvDailyQuoteStore(factory: KeyValueStoreFactory) : DailyQuoteStorePort {
    private val kv = factory.open("go_ai_coach_daily_quote")

    override fun load(): DailyQuoteProgress = DailyQuoteProgress(
        order = kv.getString("order").toIds(),
        shown = kv.getString("shown").toIds().toSet(),
        todayDay = kv.getLong("today_day", -1L).takeIf { it != -1L },
        todayId = kv.getString("today_id"),
        dismissedDay = kv.getLong("dismissed_day", -1L).takeIf { it != -1L },
    )

    override fun save(progress: DailyQuoteProgress) {
        kv.putString("order", progress.order.joinToString(","))
        kv.putString("shown", progress.shown.joinToString(","))
        kv.putLong("today_day", progress.todayDay ?: -1L)
        kv.putString("today_id", progress.todayId)
        kv.putLong("dismissed_day", progress.dismissedDay ?: -1L)
    }

    private fun String?.toIds(): List<String> = this?.split(",")?.filter { it.isNotBlank() }.orEmpty()
}

internal class KvDailyFortuneStore(factory: KeyValueStoreFactory) : DailyFortuneStorePort {
    private val kv = factory.open("go_ai_coach_daily_fortune")

    override fun load(): DailyFortuneProgress = DailyFortuneProgress(
        day = kv.getLong("day", -1L).takeIf { it != -1L },
        fortuneIndex = kv.getInt("fortune_index", -1).takeIf { it != -1 },
        refreshesUsed = kv.getInt("refreshes_used", 0),
    )

    override fun save(progress: DailyFortuneProgress) {
        kv.putLong("day", progress.day ?: -1L)
        kv.putInt("fortune_index", progress.fortuneIndex ?: -1)
        kv.putInt("refreshes_used", progress.refreshesUsed)
    }
}

internal class KvGuideProgressStore(factory: KeyValueStoreFactory) : GuideProgressStorePort {
    private val kv = factory.open("go_ai_coach_guide")

    override fun load(): GuideProgress = GuideProgress(
        armed = kv.getBoolean("armed", false),
        seenSteps = kv.getStringSet("seen_steps") ?: emptySet(),
    )

    override fun arm() = kv.putBoolean("armed", true)

    override fun markSeen(step: GuideStep) = kv.putStringSet("seen_steps", load().seenSteps + step.id)
}

internal class KvLifeAndDeathProgressStore(factory: KeyValueStoreFactory) : LifeAndDeathProgressPort {
    private val kv = factory.open("go_ai_coach_life_and_death")

    override fun solved(): Set<String> = kv.getString("solved")?.split(",")?.filter { it.isNotBlank() }?.toSet().orEmpty()

    override fun markSolved(problemId: String): Set<String> {
        val next = solved() + problemId
        kv.putString("solved", next.sorted().joinToString(","))
        return next
    }
}

internal class KvUserProfileStore(factory: KeyValueStoreFactory) : UserProfileStorePort {
    private val kv = factory.open("go_ai_coach_user_profile")

    override fun nickname(): String? = kv.getString("nickname")?.takeIf { it.isNotBlank() }

    override fun saveNickname(raw: String) = kv.putString("nickname", UserNicknamePolicy.sanitize(raw))
}

internal class KvDailyDrawStore(factory: KeyValueStoreFactory) : DailyDrawStorePort {
    private val kv = factory.open("go_ai_coach_daily_draw")

    override fun lastDrawnAtMillis(): Long? = kv.getLong("last_drawn_at_millis", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }

    override fun markDrawn(atMillis: Long) = kv.putLong("last_drawn_at_millis", atMillis)
}

internal class KvDeviceIdentityStore(factory: KeyValueStoreFactory) : DeviceIdentityStorePort {
    private val kv = factory.open("go_ai_coach_device_identity")

    @OptIn(ExperimentalUuidApi::class)
    override fun loadOrCreate(): DeviceIdentity {
        kv.getString("device_id")?.let { return DeviceIdentity(it) }
        val generated = Uuid.random().toString()
        kv.putString("device_id", generated)
        return DeviceIdentity(generated)
    }
}
