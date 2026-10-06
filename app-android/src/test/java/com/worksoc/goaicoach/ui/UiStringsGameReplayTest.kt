package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.application.gamehistory.ScoreSwingMaxCount
import com.worksoc.goaicoach.ui.l10n.ReplayNavigation
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.gameHistoryNoteDialogTitleFor
import com.worksoc.goaicoach.ui.l10n.gameHistoryNotePlaceholderFor
import com.worksoc.goaicoach.ui.l10n.gameHistoryReferenceLabelFor
import com.worksoc.goaicoach.ui.l10n.gameReplayAnalysisBusyFor
import com.worksoc.goaicoach.ui.l10n.gameReplayAnalysisFailedFor
import com.worksoc.goaicoach.ui.l10n.gameReplayBranchBlockedFor
import com.worksoc.goaicoach.ui.l10n.gameReplayBranchLabelFor
import com.worksoc.goaicoach.ui.l10n.gameReplayBranchOverwriteMessageFor
import com.worksoc.goaicoach.ui.l10n.gameReplayNavigationLabelFor
import com.worksoc.goaicoach.ui.l10n.gameReplayNoScoreDataFor
import com.worksoc.goaicoach.ui.l10n.gameReplayNoScoreDataForSwingsFor
import com.worksoc.goaicoach.ui.l10n.gameReplayNoScoreSwingsFor
import com.worksoc.goaicoach.ui.l10n.gameReplayNoTopMovesFor
import com.worksoc.goaicoach.ui.l10n.gameReplayRemeasuringScoresFor
import com.worksoc.goaicoach.ui.l10n.gameReplayRowBadgeFor
import com.worksoc.goaicoach.ui.l10n.gameReplayScoreSectionFor
import com.worksoc.goaicoach.ui.l10n.gameReplayScoreSwingChipLabelFor
import com.worksoc.goaicoach.ui.l10n.gameReplayScoreSwingCriterionFor
import com.worksoc.goaicoach.ui.l10n.gameReplayScoreSwingSectionFor
import com.worksoc.goaicoach.ui.l10n.gameReplayShowMoveNumbersLabelFor
import com.worksoc.goaicoach.ui.l10n.gameReplayStartPositionFor
import com.worksoc.goaicoach.ui.l10n.gameReplayTitleFor
import com.worksoc.goaicoach.ui.l10n.gameReplayTruncatedFor
import com.worksoc.goaicoach.ui.l10n.gameReplayUnavailableFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #156 — 대국 다시보기 문구.
 *
 * ⚠️ **이 파일이 그 문구들의 유일한 그물이다.** `UiStringsTest`의 리플렉션 그물은 [UiStrings]의
 * `String` 필드만 훑으므로, `UiStringsGameReplay.kt`의 표와 함수는 **하나도 보지 않는다**
 * (함정 10). 지우면 한 언어가 비어도 아무도 모른다.
 */
class UiStringsGameReplayTest {

    private val languages = UiLanguage.entries

    @Test
    fun everyReplayStringExistsInEveryLanguage() {
        languages.forEach { language ->
            listOf(
                "제목" to gameReplayTitleFor(language),
                "목록 꼬리표" to gameReplayRowBadgeFor(language),
                "본문 없음" to gameReplayUnavailableFor(language),
                "시작 국면" to gameReplayStartPositionFor(language),
                "형세 절 제목" to gameReplayScoreSectionFor(language),
                "형세 없음" to gameReplayNoScoreDataFor(language),
                "변곡점 절 제목" to gameReplayScoreSwingSectionFor(language),
                "형세 다시 재는 중" to gameReplayRemeasuringScoresFor(language),
                "형세 기록 없음" to gameReplayNoScoreDataForSwingsFor(language),
                "변곡점 없음" to gameReplayNoScoreSwingsFor(language),
                "판정 기준" to gameReplayScoreSwingCriterionFor(language),
                "변곡점 칩 라벨" to gameReplayScoreSwingChipLabelFor(language, 48, -12.5),
                "끊긴 기록" to gameReplayTruncatedFor(language, 48),
                "수순 표시 체크박스 라벨" to gameReplayShowMoveNumbersLabelFor(language),
                "분기 버튼(17수)" to gameReplayBranchLabelFor(language, 17),
                "분기 버튼(시작 국면)" to gameReplayBranchLabelFor(language, 0),
                "분기 불가 사유" to gameReplayBranchBlockedFor(language),
                "분기 덮어쓰기 경고" to gameReplayBranchOverwriteMessageFor(language, 17),
                "참고 기보 라벨" to gameHistoryReferenceLabelFor(language),
                "한줄평 안내문" to gameHistoryNotePlaceholderFor(language),
                "한줄평 다이얼로그 제목" to gameHistoryNoteDialogTitleFor(language),
                "분석 — 엔진 바쁨" to gameReplayAnalysisBusyFor(language, ticketKept = false),
                "분석 — 엔진 바쁨(1회권 걸었음)" to gameReplayAnalysisBusyFor(language, ticketKept = true),
                "분석 — 답 없음" to gameReplayAnalysisFailedFor(language, ticketKept = false),
                "분석 — 답 없음(1회권 걸었음)" to gameReplayAnalysisFailedFor(language, ticketKept = true),
                "분석 — 판에 올릴 추천 수 없음" to gameReplayNoTopMovesFor(language, ticketKept = false),
                "분석 — 판에 올릴 추천 수 없음(1회권 걸었음)" to gameReplayNoTopMovesFor(language, ticketKept = true),
            ).forEach { (what, text) ->
                assertTrue("$language / $what 문구가 비었다", text.isNotBlank())
            }
            ReplayNavigation.entries.forEach { step ->
                assertTrue(
                    "$language / $step 이동 라벨이 비었다",
                    gameReplayNavigationLabelFor(language, step).isNotBlank(),
                )
            }
        }
    }

    /**
     * ⚠️ **1회권을 걸었던 사람에게는 표 얘기를 한다**(백로그 #218) — 차감은 결과가 나온 뒤라 한 장도 안 나갔다. 사유 뒤에
     * 한 줄을 덧붙였더니 토스트의 두 줄 제한에 표 얘기가 잘려서, 문장을 따로 뒀다. 두 문장이 같아지면 그 안내가 사라진 것이다.
     */
    @Test
    fun theAnalysisToastsSayTheTicketWasKeptWhenOneWasStaked() {
        languages.forEach { language ->
            listOf<Pair<String, (Boolean) -> String>>(
                "엔진 바쁨" to { kept -> gameReplayAnalysisBusyFor(language, kept) },
                "답 없음" to { kept -> gameReplayAnalysisFailedFor(language, kept) },
                "추천 수 없음" to { kept -> gameReplayNoTopMovesFor(language, kept) },
            ).forEach { (what, text) ->
                assertTrue(
                    "$language / $what — 1회권을 걸었을 때의 문구가 걸지 않았을 때와 같다. 표가 안 나갔다는 말이 빠졌다.",
                    text(true) != text(false),
                )
            }
        }
    }

    /**
     * ⚠️ **개수 상한을 문구에 박지 않는다**(백로그 #207). 5개는 [ScoreSwingMaxCount] 하나가 정본이고
     * 네 언어가 그 값을 따라와야 한다 — 숫자를 손으로 적어 두면 상한을 바꾼 다음 스레드가
     * **버튼은 7개인데 화면은 최대 5개라고 말하는** 상태를 만든다.
     */
    @Test
    fun theScoreSwingTitleFollowsTheMaxCountConstant() {
        // ⚠️ **숫자 경계로 찾는다** — "25"는 문자열로 "5"를 포함하므로 `contains("5")`는
        // 25개 문구를 옛 5개 문구로 오판한다.
        fun mentionsNumber(text: String, number: Int): Boolean =
            Regex("(?<!\\d)$number(?!\\d)").containsMatchIn(text)

        languages.forEach { language ->
            assertTrue(
                "$language 제목이 기본 상한(5)을 말하지 않는다",
                mentionsNumber(gameReplayScoreSwingSectionFor(language), 5),
            )
            assertTrue(
                "$language 제목이 바뀐 상한(25)을 따라오지 않는다",
                mentionsNumber(gameReplayScoreSwingSectionFor(language, 25), 25),
            )
            assertFalse(
                "$language 제목에 옛 상한이 남았다",
                mentionsNumber(gameReplayScoreSwingSectionFor(language, 25), 5),
            )
            // 고르는 방식 문구에는 숫자가 없다 — 옛 문구처럼 임계(집수)를 손으로 적어 두지 않았는지.
            assertFalse(
                "$language 고르는 방식 문구에 숫자가 박혔다: ${gameReplayScoreSwingCriterionFor(language)}",
                gameReplayScoreSwingCriterionFor(language).any { it.isDigit() },
            )
        }
        assertEquals(5, ScoreSwingMaxCount)
    }

    /**
     * 한국어 세 문구는 **사용자가 고른 그대로**다(2026-10-01, 백로그 #207) — 바꾸려면 먼저 묻는다.
     * ⚠️ 「없을 때」에 "최적 수순"을 쓰지 않은 이유는 `UiStringsGameReplay.kt`의 `NoScoreSwings` KDoc.
     */
    @Test
    fun koreanScoreSwingCopyIsWhatTheUserChose() {
        val korean = UiLanguage.Korean
        assertEquals(
            "변곡점(최대 5개) · 변동이 큰 순으로 골라 수순대로",
            "${gameReplayScoreSwingSectionFor(korean)} \u00B7 ${gameReplayScoreSwingCriterionFor(korean)}",
        )
        assertEquals("변곡점 없이 안정적으로 진행되었습니다.", gameReplayNoScoreSwingsFor(korean))
        assertEquals(
            "이 대국에는 형세 기록이 부족해 변곡점을 표시할 수 없습니다.",
            gameReplayNoScoreDataForSwingsFor(korean),
        )
    }

    /**
     * 변곡점 칩 라벨 — 정수는 소수점을 떼고, 소수는 한 자리만, **부호는 항상 붙는다**
     * (2026-09-20 사용자 리메이크: `1수: -9.5`처럼 방향까지 보여준다).
     */
    @Test
    fun scoreSwingChipLabelShowsSignAndAtMostOneDecimal() {
        assertEquals("1수: -9.5", gameReplayScoreSwingChipLabelFor(UiLanguage.Korean, 1, -9.5))
        assertEquals("15수: +3.5", gameReplayScoreSwingChipLabelFor(UiLanguage.Korean, 15, 3.5))
        assertEquals("9수: -10", gameReplayScoreSwingChipLabelFor(UiLanguage.Korean, 9, -10.0))
        assertEquals("Move 9: -10", gameReplayScoreSwingChipLabelFor(UiLanguage.English, 9, -10.03))
    }

    /**
     * ⚠️ **분기 버튼은 수순 번호를 말해야 한다**(백로그 #172) — 확인 팝업을 한 번 더 두는
     * 대신 라벨이 *"어느 자리에서 갈라지는가"* 를 말하기로 했다. 번호가 빠지면 그 결정이
     * 근거를 잃는다.
     */
    @Test
    fun theBranchLabelNamesTheMoveItStartsFrom() {
        languages.forEach { language ->
            assertTrue(
                "$language 분기 버튼이 수순 번호를 말하지 않는다: ${gameReplayBranchLabelFor(language, 17)}",
                gameReplayBranchLabelFor(language, 17).contains("17"),
            )
            assertFalse(
                "$language 시작 국면 라벨에 숫자 0이 붙었다 — 붙일 번호가 없는 자리다.",
                gameReplayBranchLabelFor(language, 0).contains("0"),
            )
        }
    }

    /**
     * ⚠️ **분기 경고는 `UiStrings.overwriteWarningMessage`를 베껴 쓰면 안 된다** — 그 문구는
     * *"대국 설정으로 이동하시겠습니까?"* 로 끝나는데 분기는 설정 화면을 거치지 않는다.
     * 일어나지 않을 일을 묻는 안내가 되는 자리라 그물을 단다(함정 39).
     */
    @Test
    fun theBranchOverwriteWarningNeverPromisesTheSetupScreen() {
        val banned = mapOf(
            UiLanguage.Korean to "대국 설정",
            UiLanguage.English to "match setup",
            UiLanguage.Japanese to "対局設定",
            UiLanguage.ChineseSimplified to "对局设置",
        )
        languages.forEach { language ->
            val text = gameReplayBranchOverwriteMessageFor(language, 17)
            assertFalse(
                "$language 분기 경고가 설정 화면으로 간다고 말한다: $text",
                text.contains(banned.getValue(language), ignoreCase = true),
            )
            assertTrue(
                "$language 분기 경고가 어느 수에서 시작하는지 말하지 않는다: $text",
                text.contains("17"),
            )
        }
    }

    /**
     * ⚠️ **본문이 없는 옛 기록에 "준비 중"이라고 말하지 않는다** — 기다려도 생기지 않는다.
     * 2026-09-18 이전 기록에는 수순이 저장된 적이 없다(#151). 이 앱의 기조는 *"시간이 지나면
     * 저절로 풀리는가"* 로 안내를 가르고, 이것은 안 풀리는 쪽이다.
     */
    @Test
    fun theMissingRecordNoticeNeverPromisesItIsComing() {
        val banned = listOf("준비 중", "곧", "coming", "soon", "準備", "即将", "稍后")
        languages.forEach { language ->
            val text = gameReplayUnavailableFor(language)
            banned.forEach { word ->
                assertFalse(
                    "$language 안내가 기다리면 된다고 말한다('$word'): $text",
                    text.contains(word, ignoreCase = true),
                )
            }
        }
    }
}
