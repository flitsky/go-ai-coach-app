package com.worksoc.goaicoach.middleware

// ⚠️ refactor backlog #28(260923): PositionAnalysisCacheResolver가 middleware→application/analysis로
// 옮겨가며 이 테스트와 같은 패키지가 아니게 됐다 — 그래서 명시 import가 새로 필요하다. 이 테스트
// 파일 자체는 이 스레드의 담당 파일 밖(shared/src/commonMain이 아니라 commonTest)이라 옮기지 않고
// 이 한 줄만 고쳤다(LayeringContractTest.kt의 사례와 같은 이유 — 이동의 필연적 결과).
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheEntry
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheKey
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheOrigin
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheResolver
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheStore
import com.worksoc.goaicoach.application.analysis.TrustedPositionAnalysisCacheProvider
import com.worksoc.goaicoach.application.analysis.shouldReplacePositionAnalysisCacheEntry
import com.worksoc.goaicoach.shared.AnalysisLimit
import com.worksoc.goaicoach.shared.AnalysisResult
import com.worksoc.goaicoach.shared.EngineSearchMode
import com.worksoc.goaicoach.shared.EngineStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class PositionAnalysisCacheResolverTest {
    @Test
    fun reusableEntryPrefersLocalReusableCacheBeforeTrustedProviders() {
        val key = cacheKey()
        val local = cacheEntry(key = key, rootVisits = 16, origin = PositionAnalysisCacheOrigin.LocalUser)
        val trusted = cacheEntry(key = key, rootVisits = 32, origin = PositionAnalysisCacheOrigin.OperatorTrusted)
        val resolver = PositionAnalysisCacheResolver(
            localStore = InMemoryCacheStore(local),
            trustedProviders = listOf(InMemoryTrustedProvider(trusted)),
        )

        val entry = resolver.reusableEntryFor(key = key, nowMillis = Now)

        assertEquals(PositionAnalysisCacheOrigin.LocalUser, entry?.origin)
        assertEquals(16, entry?.rootVisits)
    }

    @Test
    fun reusableEntryFallsBackToBestTrustedProviderWhenLocalCacheMisses() {
        val key = cacheKey()
        val peer = cacheEntry(key = key, rootVisits = 64, origin = PositionAnalysisCacheOrigin.PeerShared)
        val operator = cacheEntry(key = key, rootVisits = 64, origin = PositionAnalysisCacheOrigin.OperatorTrusted)
        val resolver = PositionAnalysisCacheResolver(
            trustedProviders = listOf(InMemoryTrustedProvider(peer), InMemoryTrustedProvider(operator)),
        )

        val entry = resolver.reusableEntryFor(key = key, nowMillis = Now)

        assertEquals(PositionAnalysisCacheOrigin.OperatorTrusted, entry?.origin)
        assertEquals(64, entry?.rootVisits)
    }

    @Test
    fun statsTextCombinesLocalAndTrustedProviders() {
        val resolver = PositionAnalysisCacheResolver(
            localStore = InMemoryCacheStore(),
            trustedProviders = listOf(InMemoryTrustedProvider()),
        )

        val text = resolver.statsText(Now)

        assertTrue(text.contains("localEntries=0"))
        assertTrue(text.contains("trusted={trustedEntries=0}"))
    }

    private companion object {
        const val Now = 10_000L

        fun cacheKey(): PositionAnalysisCacheKey =
            PositionAnalysisCacheKey(
                positionFingerprint = "fp",
                searchMode = EngineSearchMode.JsonPositionAnalysis,
                limit = AnalysisLimit(visits = 32, timeMillis = 2_000L, candidateCount = 16),
            )

        fun cacheEntry(
            key: PositionAnalysisCacheKey,
            rootVisits: Int,
            origin: PositionAnalysisCacheOrigin,
            createdAtMillis: Long = Now,
        ): PositionAnalysisCacheEntry =
            PositionAnalysisCacheEntry(
                key = key,
                result = AnalysisResult(
                    status = EngineStatus.ready("cached"),
                    candidates = emptyList(),
                    summary = "cached",
                    rootVisits = rootVisits,
                ),
                createdAtMillis = createdAtMillis,
                requestedRootVisits = 32,
                rootVisits = rootVisits,
                origin = origin,
            )
    }
}

private class InMemoryCacheStore(
    initial: PositionAnalysisCacheEntry? = null,
) : PositionAnalysisCacheStore {
    private val entries = mutableMapOf<PositionAnalysisCacheKey, PositionAnalysisCacheEntry>()

    init {
        if (initial != null) {
            entries[initial.key] = initial
        }
    }

    override fun get(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? =
        entries[key]?.takeIf { entry -> entry.quality.isReusable && !entry.isExpired(nowMillis) }

    override fun put(
        entry: PositionAnalysisCacheEntry,
        nowMillis: Long,
    ) {
        val existing = entries[entry.key]
        if (shouldReplacePositionAnalysisCacheEntry(existing, entry)) {
            entries[entry.key] = entry
        }
    }

    override fun peek(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? =
        entries[key]?.takeIf { entry -> !entry.isExpired(nowMillis) }

    override fun statsText(nowMillis: Long): String =
        "localEntries=${entries.size}"
}

private class InMemoryTrustedProvider(
    initial: PositionAnalysisCacheEntry? = null,
) : TrustedPositionAnalysisCacheProvider {
    private val entries = mutableMapOf<PositionAnalysisCacheKey, PositionAnalysisCacheEntry>()

    init {
        if (initial != null) {
            entries[initial.key] = initial
        }
    }

    override fun get(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? =
        entries[key]?.takeIf { entry -> entry.quality.isReusable && !entry.isExpired(nowMillis) }

    override fun peek(
        key: PositionAnalysisCacheKey,
        nowMillis: Long,
    ): PositionAnalysisCacheEntry? =
        entries[key]?.takeIf { entry -> !entry.isExpired(nowMillis) }

    override fun statsText(nowMillis: Long): String =
        "trustedEntries=${entries.size}"
}
