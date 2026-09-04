package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.application.preferences.DefaultAppFontScale
import com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot
import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.AutoPlayDelaySetting
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import com.worksoc.goaicoach.shared.BoardSize
import com.worksoc.goaicoach.shared.Ruleset
import com.worksoc.goaicoach.shared.SearchTimeLimit
import com.worksoc.goaicoach.shared.SearchTimeSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesCodecTest {
    @Test
    fun roundTripRestoresPlayerSetupAndDisplayToggles() {
        val setup = PlayerSetup(
            black = SidePlayerSetup(
                controller = SeatController.Human,
                humanGameType = HumanGameType.Teaching,
            ),
            white = SidePlayerSetup(
                controller = SeatController.Ai,
                playLevel = PlayLevelSetting(PlayLevelGroup.Beginner, level = 3),
            ),
        )
        val snapshot = UserPreferencesSnapshot(
            playerSetup = setup,
            ruleset = Ruleset.Chinese,
            topMovesEnabled = false,
            komi = 0.5,
            showCoordinates = false,
            showMoveNumbers = true,
            showLastMoveRing = false,
            showOwnershipOverlay = false,
            autoPlayDelayMillis = AutoPlayDelaySetting.Slow.millis,
            searchTimeSettings = SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds),
            showMoveReview = true,
            appFontScale = 1.3f,
        )

        val encoded = UserPreferencesCodec.encode(snapshot)
        val encodedJson = JSONObject(encoded)
        val restored = UserPreferencesCodec.decode(encoded)

        assertEquals(setup, restored?.playerSetup)
        assertEquals(Ruleset.Chinese, restored?.ruleset)
        assertEquals(false, restored?.topMovesEnabled)
        assertEquals(0.5, restored?.komi ?: -1.0, 0.0001)
        // ⚠️ 배율은 **문자열로** 저장한다 — `Float`를 `Double`로 넣으면 `1.2999999523162842`가
        // 되는 것을 실기에서 확인했다(#81). 왕복이 정확한지와 저장 형태가 사람이 읽을 수 있는지를
        // 함께 본다. ⚠️ #73 전까지 이 자리는 `gameSetupUxMode`였고, **배율 왕복에는 그물이
        // 없었다** — 삭제된 필드의 자리를 그것으로 메운다.
        assertEquals(1.3f, restored?.appFontScale)
        assertEquals("1.3", encodedJson.getString("appFontScale"))
        assertEquals(false, restored?.showCoordinates)
        assertEquals(true, restored?.showMoveNumbers)
        assertEquals(false, restored?.showLastMoveRing)
        assertEquals(false, restored?.showOwnershipOverlay)
        assertEquals(true, restored?.showMoveReview)
        assertEquals(AutoPlayDelaySetting.Slow.millis, restored?.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(SearchTimeLimit.WithinFiveSeconds), restored?.searchTimeSettings)
        assertEquals(2, encodedJson.getInt("schema"))
        val encodedSearchTime = encodedJson.getJSONObject("searchTimeSettings")
        assertEquals(SearchTimeLimit.WithinFiveSeconds.name, encodedSearchTime.getString("limit"))
        assertFalse(encodedSearchTime.has("timeCapEnabled"))
        assertFalse(encodedSearchTime.has("b16Millis"))
        assertFalse(encodedSearchTime.has("b32Millis"))
        assertFalse(encodedSearchTime.has("b64Millis"))
    }

    @Test
    fun missingOptionalFieldsUseUserFriendlyDefaults() {
        val restored = UserPreferencesCodec.decode("""{"schema":1}""")

        assertEquals(PlayerSetup(), restored?.playerSetup)
        assertEquals(Ruleset.Japanese, restored?.ruleset)
        assertFalse(restored?.topMovesEnabled ?: true)
        assertFalse(restored?.showCoordinates ?: true)
        assertFalse(restored?.showMoveNumbers ?: true)
        assertTrue(restored?.showLastMoveRing ?: false)
        assertTrue(restored?.showOwnershipOverlay ?: false)
        assertFalse(restored?.showMoveReview ?: true)
        assertEquals(AutoPlayDelaySetting.Default.millis, restored?.autoPlayDelayMillis)
        assertEquals(SearchTimeSettings(), restored?.searchTimeSettings)
        assertEquals(com.worksoc.goaicoach.shared.DefaultKomi, restored?.komi ?: -1.0, 0.0001)
        assertEquals(DefaultAppFontScale, restored?.appFontScale)
    }

    /**
     * ⚠️ **삭제된 필드가 아직 저장돼 있는 파일도 그대로 읽혀야 한다**(백로그 #73).
     *
     * `gameSetupUxMode`를 지울 때 **`schema`를 올리지 않았고 잔존 키를 청소하지도 않았다.** 그
     * 판단이 성립하는 근거가 이 테스트다 — 모르는 키는 무시되고, [UserPreferencesCodec.encode]가
     * 매번 새 `JSONObject`를 만들므로 그 키는 **다음 저장에서 물리적으로 사라진다.**
     *
     * ⚠️ **`schema`를 올렸다면 훨씬 나빴다** — `decode`의 `else -> return@runCatching null`이
     * 1/2 외의 값에서 **스냅샷을 통째로 버려** 판 크기·덤·표시 옵션·온보딩 완료까지 초기화된다.
     * 필드를 하나 빼는 데 마이그레이션이 필요하다는 착각이 그 사고로 이어진다.
     */
    @Test
    fun aSnapshotStillCarryingARemovedFieldDecodesUnchanged() {
        val restored = UserPreferencesCodec.decode(
            """{"schema":2,"gameSetupUxMode":"Simple","komi":0.5,"hasSeenOnboarding":true}""",
        )

        assertEquals(0.5, restored?.komi ?: -1.0, 0.0001)
        assertTrue(restored?.hasSeenOnboarding ?: false)
        // 지운 키가 남아 있어도 나머지가 살아남는다 — 스냅샷을 버리지 않는다.
        assertEquals(DefaultAppFontScale, restored?.appFontScale)
    }

    @Test
    fun schemaOneMigratesLegacyOffToOff() {
        val restored = UserPreferencesCodec.decode(
            """
            {
              "schema": 1,
              "searchTimeSettings": {
                "timeCapEnabled": false,
                "b16Millis": 1500,
                "b32Millis": 4000,
                "b64Millis": 7500
              }
            }
            """.trimIndent(),
        )

        assertEquals(SearchTimeSettings(SearchTimeLimit.Off), restored?.searchTimeSettings)
    }

    @Test
    fun schemaOneMigratesLargestLegacyCapToSupportedCeiling() {
        val restored = UserPreferencesCodec.decode(
            """
            {
              "schema": 1,
              "searchTimeSettings": {
                "timeCapEnabled": true,
                "b16Millis": 1500,
                "b32Millis": 4000,
                "b64Millis": 7500
              }
            }
            """.trimIndent(),
        )

        assertEquals(SearchTimeSettings(SearchTimeLimit.WithinTenSeconds), restored?.searchTimeSettings)
    }

    @Test
    fun malformedLegacyLimitValuesFallBackToSafeDefaults() {
        val restored = UserPreferencesCodec.decode(
            """
            {
              "schema": 1,
              "searchTimeSettings": {
                "timeCapEnabled": true,
                "b16Millis": -1,
                "b32Millis": "invalid",
                "b64Millis": 0
              }
            }
            """.trimIndent(),
        )

        assertEquals(SearchTimeSettings(SearchTimeLimit.WithinThreeSeconds), restored?.searchTimeSettings)
    }

    @Test
    fun malformedOrMissingSchemaTwoLimitUsesSafeDefault() {
        val malformed = UserPreferencesCodec.decode(
            """{"schema":2,"searchTimeSettings":{"limit":"invalid"}}""",
        )
        val missing = UserPreferencesCodec.decode("""{"schema":2}""")

        assertEquals(SearchTimeSettings(), malformed?.searchTimeSettings)
        assertEquals(SearchTimeSettings(), missing?.searchTimeSettings)
    }

    @Test
    fun invalidPayloadReturnsNull() {
        assertNull(UserPreferencesCodec.decode("{broken"))
        assertNull(UserPreferencesCodec.decode("""{"schema":99}"""))
    }

    // ── 접바둑 기본값(백로그 #52) ──────────────────────────────────────────────
    // 2026-08-18에는 "초심자 진입 난이도"를 이유로 그 판의 최대 접바둑이 기본값이었다.
    // 그 역할은 첫 실행 랜딩(#51)이 가져갔고, 묻지 않은 사용자에게는 호선으로 시작한다.

    @Test
    fun aFreshSnapshotStartsOnEvenTerms() {
        assertEquals(0, UserPreferencesSnapshot().handicapCount)
        // 판 크기를 따라가던 옛 기본값이 아니라는 것까지 못박는다 — 19x19로 만들어도 0이다.
        assertEquals(0, UserPreferencesSnapshot(boardSize = BoardSize.Nineteen).handicapCount)
    }

    /**
     * ⚠️ **이 항목이 실제로 고친 불일치다.** 데이터 클래스 기본값은 5였는데 디코드 폴백은 0이라,
     * "저장 파일이 아예 없으면 5, 키만 빠졌으면 0"이라는 두 기본값이 공존했다. 둘이 어긋나면
     * 같은 신규 사용자가 경로에 따라 다른 판으로 시작한다 — 화면에서는 조용히 지나간다.
     */
    @Test
    fun theDecodeFallbackMatchesTheSnapshotDefault() {
        val withoutHandicapKey = JSONObject(UserPreferencesCodec.encode(UserPreferencesSnapshot()))
            .apply { remove("handicapCount") }
            .toString()

        val decoded = UserPreferencesCodec.decode(withoutHandicapKey)

        assertEquals(UserPreferencesSnapshot().handicapCount, decoded?.handicapCount)
    }
}
