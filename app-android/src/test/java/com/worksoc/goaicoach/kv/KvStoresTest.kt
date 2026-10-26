package com.worksoc.goaicoach.kv

import com.worksoc.goaicoach.application.attendance.AttendanceState
import com.worksoc.goaicoach.application.guide.GuideStep
import com.worksoc.goaicoach.shared.policy.ThemeMode
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS 저장소(`kv`)의 동작 — 기본값 · 왕복 · 바뀐 값 알림 · 파일 저장소(백로그 #294 — P1~P3). 키-값은 메모리 구현,
 * 파일은 임시 폴더의 JVM 구현으로 돌린다(iOS 구현은 같은 계약의 NSUserDefaults · POSIX다).
 */
class KvStoresTest {
    private val kv = InMemoryKeyValueStoreFactory()

    @Test
    fun settingsStartAtTheAndroidDefaultsAndRoundTrip() {
        val theme = KvThemeModeStore(kv)
        val music = KvBackgroundMusicStore(kv)
        val developer = KvDeveloperModeStore(kv)
        assertEquals(ThemeMode.fromId(null), theme.load())
        assertTrue(music.isEnabled())
        assertFalse(developer.isEnabled())
        assertNull(developer.lastResetUtcMillis())

        theme.save(ThemeMode.entries.last())
        music.setEnabled(false)
        developer.markResetBaseline(42L)
        assertEquals(ThemeMode.entries.last(), KvThemeModeStore(kv).load())
        assertFalse(KvBackgroundMusicStore(kv).isEnabled())
        assertEquals(42L, KvDeveloperModeStore(kv).lastResetUtcMillis())
    }

    @Test
    fun modeChangesEmitsTheCurrentValueThenEachSave() = runBlocking {
        withTimeout(5_000) {
            val store = KvThemeModeStore(kv)
            val seen = Channel<ThemeMode>(Channel.UNLIMITED)
            val job = launch { store.modeChanges().collect { seen.send(it) } }
            assertEquals(ThemeMode.fromId(null), seen.receive())
            // 첫 값을 받았으면 구독은 이미 걸려 있다 — 바로 다음 저장도 놓치지 않는다.
            store.save(ThemeMode.entries.last())
            assertEquals(ThemeMode.entries.last(), seen.receive())
            job.cancel()
            assertEquals(ThemeMode.entries.last(), store.modeChanges().first())
        }
    }

    @Test
    fun progressStoresKeepTheirSets() {
        val guide = KvGuideProgressStore(kv)
        guide.markSeen(GuideStep.entries.first())
        assertEquals(setOf(GuideStep.entries.first().id), KvGuideProgressStore(kv).load().seenSteps)
        val lifeAndDeath = KvLifeAndDeathProgressStore(kv)
        lifeAndDeath.markSolved("b")
        assertEquals(setOf("a", "b"), lifeAndDeath.markSolved("a"))
        val profile = KvUserProfileStore(kv)
        profile.saveNickname("  ")
        assertNull(profile.nickname())
    }

    @Test
    fun deviceIdIsCreatedOnceAndAttendanceUpdatesUnderTheLock() {
        val first = KvDeviceIdentityStore(kv).loadOrCreate()
        assertEquals(first, KvDeviceIdentityStore(kv).loadOrCreate())
        assertEquals(36, first.id.length)

        val attendance = KvAttendanceStore(kv)
        attendance.update { it.copy(attendanceCount = it.attendanceCount + 1) }
        assertEquals(1, KvAttendanceStore(kv).load().attendanceCount)
        attendance.clear()
        assertEquals(AttendanceState(), attendance.load())
    }

    @Test
    fun gameHistoryWritesTheIndexAndReplayAtomicallyAndSweepsOrphans() {
        val root = Files.createTempDirectory("kv-files").toFile()
        try {
            val files = JvmAppFileStore(root)
            val store = KvGameHistoryStore(files)
            val entry = GameSetupGoldenFixtures.historyEntries().first()
            store.appendCompletedGame(entry, null)
            assertEquals(listOf(entry), KvGameHistoryStore(files).loadAll())
            assertTrue(File(root, "game_history/index.json").isFile)
            assertFalse(File(root, "game_history/index.json.tmp").exists())

            File(root, "game_history/replay/orphan.json").writeText("{}")
            store.loadAll()
            assertFalse(File(root, "game_history/replay/orphan.json").exists())

            store.updateNote(entry.id, "  다시 볼 판 ")
            assertEquals("다시 볼 판", store.loadAll().single().note)
        } finally {
            root.deleteRecursively()
        }
    }
}

/** 시험용 [AppFileStore] — 안드로이드 `GameHistoryStore`와 같은 임시 파일 → 이름 바꾸기. */
internal class JvmAppFileStore(private val root: File) : AppFileStore {
    private fun file(path: String) = File(root, path)

    override fun exists(path: String): Boolean = file(path).exists()

    override fun readText(path: String): String? = runCatching { file(path).readText() }.getOrNull()

    override fun writeAtomically(path: String, text: String): Boolean {
        val target = file(path)
        val temp = File(target.parentFile, target.name + ".tmp")
        return runCatching {
            temp.writeText(text)
            check(temp.renameTo(target))
        }.isSuccess
    }

    override fun appendText(path: String, text: String): Boolean = runCatching { file(path).appendText(text) }.isSuccess

    override fun length(path: String): Long = file(path).length()

    override fun list(path: String): List<String>? = file(path).list()?.toList()

    override fun delete(path: String): Boolean = file(path).delete()

    override fun deleteRecursively(path: String): Boolean = file(path).deleteRecursively()

    override fun mkdirs(path: String): Boolean = file(path).mkdirs()
}
