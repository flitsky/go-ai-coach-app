package com.worksoc.goaicoach.kv

import com.worksoc.goaicoach.application.attendance.AttendanceState
import com.worksoc.goaicoach.application.attendance.AttendanceStorePort
import com.worksoc.goaicoach.application.botcharacter.BotCollectionState
import com.worksoc.goaicoach.application.botcharacter.BotCollectionStorePort
import com.worksoc.goaicoach.application.concurrency.sharedLock
import com.worksoc.goaicoach.application.consumable.ConsumableInventory
import com.worksoc.goaicoach.application.consumable.ConsumableStorePort
import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.application.preferences.UserPreferencesStorePort
import com.worksoc.goaicoach.application.preferences.migrateSettingsSchema
import com.worksoc.goaicoach.application.premium.port.PremiumStateStorePort
import com.worksoc.goaicoach.application.premium.state.PremiumState
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureState
import com.worksoc.goaicoach.application.rankmeasure.RankMeasureStorePort
import com.worksoc.goaicoach.application.savedgame.SavedGameSnapshot
import com.worksoc.goaicoach.application.savedgame.SavedGameStorePort
import com.worksoc.goaicoach.application.time.currentEpochMillis

// JSON 한 덩이를 키 하나에 두는 저장소의 공용 구현(백로그 #294 — P2). 묶음 이름 · 키 · 코덱이 안드로이드 `persistence/`의 같은 이름
// 저장소와 같다 — 코덱은 같은 파일의 공용 사본(`*Codecs.kt`)이다.

internal class KvAttendanceStore(factory: KeyValueStoreFactory) : AttendanceStorePort {
    private val kv = factory.open("go_ai_coach_attendance")

    override fun save(state: AttendanceState) {
        Lock.withLock { write(state) }
    }

    override fun update(transform: (AttendanceState) -> AttendanceState?): AttendanceState? =
        Lock.withLock { transform(read())?.also(::write) }

    fun clear() {
        Lock.withLock { kv.clear() }
    }

    override fun load(): AttendanceState = read()

    private fun read(): AttendanceState {
        val raw = kv.getString("attendance_state") ?: return AttendanceState()
        return AttendanceCodec.decode(raw) ?: AttendanceState()
    }

    private fun write(state: AttendanceState) = kv.putString("attendance_state", AttendanceCodec.encode(state))

    private companion object {
        // 안드로이드와 같이 프로세스에 하나 — 출석 갱신(읽고-고치고-쓰기)이 두 화면에서 겹쳐도 한 번만 반영된다.
        val Lock = sharedLock()
    }
}

internal class KvBotCollectionStore(factory: KeyValueStoreFactory) : BotCollectionStorePort {
    private val kv = factory.open("go_ai_coach_bot_collection")

    override fun save(state: BotCollectionState) = kv.putString("bot_collection_state", BotCollectionCodec.encode(state))

    fun clear() = kv.clear()

    override fun load(): BotCollectionState {
        val raw = kv.getString("bot_collection_state") ?: return BotCollectionState()
        return BotCollectionCodec.decode(raw) ?: BotCollectionState()
    }
}

internal class KvConsumableInventoryStore(factory: KeyValueStoreFactory) : ConsumableStorePort {
    private val kv = factory.open("go_ai_coach_consumables")

    override fun save(inventory: ConsumableInventory) = kv.putString("consumable_inventory", ConsumableInventoryCodec.encode(inventory))

    fun clear() = kv.clear()

    override fun load(): ConsumableInventory {
        val raw = kv.getString("consumable_inventory") ?: return ConsumableInventory()
        return ConsumableInventoryCodec.decode(raw) ?: ConsumableInventory()
    }
}

internal class KvPremiumStateStore(factory: KeyValueStoreFactory) : PremiumStateStorePort {
    private val kv = factory.open("go_ai_coach_premium_state")

    override fun save(state: PremiumState) = kv.putString("premium_state", PremiumStateCodec.encode(state))

    fun clear() = kv.clear()

    override fun load(): PremiumState {
        val raw = kv.getString("premium_state") ?: return PremiumState()
        val decoded = PremiumStateCodec.decode(raw) ?: return PremiumState()
        return decoded.takeIf { state -> state.isClockPlausibleAt(currentEpochMillis()) } ?: PremiumState()
    }
}

internal class KvRankMeasureStore(factory: KeyValueStoreFactory) : RankMeasureStorePort {
    private val kv = factory.open("go_ai_coach_rank_measure")

    override fun load(): RankMeasureState = kv.getString("rank_measure_state")?.let(RankMeasureCodec::decode) ?: RankMeasureState()

    override fun save(state: RankMeasureState) = kv.putString("rank_measure_state", RankMeasureCodec.encode(state))
}

internal class KvUserPreferencesStore(factory: KeyValueStoreFactory) : UserPreferencesStorePort {
    private val kv = factory.open("go_ai_coach_user_preferences")

    override fun save(snapshot: UserPreferencesSnapshot) = kv.putString("user_preferences", UserPreferencesCodec.encode(snapshot))

    override fun load(): UserPreferencesSnapshot {
        val raw = kv.getString("user_preferences") ?: return UserPreferencesSnapshot()
        val stored = UserPreferencesCodec.decode(raw) ?: return UserPreferencesSnapshot()
        val migrated = migrateSettingsSchema(stored)
        if (migrated !== stored) save(migrated)
        return migrated
    }
}

internal class KvSavedGameStore(factory: KeyValueStoreFactory) : SavedGameStorePort {
    private val kv = factory.open("go_ai_coach_session")

    override fun save(snapshot: SavedGameSnapshot) {
        if (!snapshot.isResumable && snapshot.finalScoreJudgement == null) {
            clear()
            return
        }
        kv.putString("active_game_snapshot", SavedGameSessionCodec.encode(snapshot))
    }

    override fun load(): SavedGameSnapshot? {
        val raw = kv.getString("active_game_snapshot") ?: return null
        return SavedGameSessionCodec.decode(raw)
            .also { snapshot ->
                if (snapshot == null || (!snapshot.isResumable && snapshot.finalScoreJudgement == null)) {
                    clear()
                }
            }
            ?.takeIf { snapshot -> snapshot.isResumable || snapshot.finalScoreJudgement != null }
    }

    override fun clear() = kv.remove("active_game_snapshot")

    fun readRawJson(): String? = kv.getString("active_game_snapshot")
}
