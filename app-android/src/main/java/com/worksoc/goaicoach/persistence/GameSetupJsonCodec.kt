package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.shared.domain.BoardSize
import com.worksoc.goaicoach.shared.domain.DefaultKomi
import com.worksoc.goaicoach.shared.domain.GameSetup
import com.worksoc.goaicoach.shared.domain.Ruleset
import org.json.JSONObject

/**
 * 판 정체성([GameSetup])의 저장 키 네 개 — 이어하기([SavedGameSessionCodec])와 대국 기록 index([GameHistoryIndexCodec])가
 * **이 한 곳으로만** 쓰고 읽는다(refactor backlog #22). 예전에는 두 코덱이 네 키를 각자 손으로 적어, 한쪽이 덤을
 * 빠뜨려도 컴파일이 통과했다(#1).
 *
 * ⚠️ **키 이름·없는 키의 기본값·넣는 순서가 전부 저장 포맷이다**(함정 69 — 스키마 번호는 올리지 않는다).
 * - 이름: 두 저장분 모두 `boardSize`·`ruleset`·`handicapCount`·`komi`. 룰은 enum 상수 이름이다(함정 1).
 * - 기본값: 판 크기 9·룰 일본·접바둑 0·덤 [DefaultKomi]. 덤 키가 없던 옛 이어하기(`40c4975c` 이전)는 줄곧 6.5로
 *   되살아났으므로 6.5여야 같은 판이 이어진다. 접바둑 키가 없던 첫 형식(`25269f04`)은 호선이다.
 * - **순서**: 기기의 `JSONObject`는 넣은 순서대로 쓴다. 두 저장분이 네 키를 넣어 온 순서가 달라서([Order]),
 *   순서를 하나로 맞추면 같은 상태의 저장 바이트가 달라진다. `DeviceGameSetupStorageGoldenTest`가 기기 바이트를 고정한다.
 *
 * 새 칸을 [GameSetup]에 더하면 `GameSetupJsonCodecTest`가 이 파일에 키가 없다고 빨개진다 — 키를 더하고, 그 키가
 * 없는 옛 저장분을 무엇으로 읽을지 기본값을 정할 것.
 */
internal object GameSetupJsonCodec {
    const val BoardSizeKey = "boardSize"
    const val RulesetKey = "ruleset"
    const val HandicapCountKey = "handicapCount"
    const val KomiKey = "komi"

    /** 저장분마다 네 키를 넣어 온 순서. 바꾸면 그 저장분의 기기 바이트가 바뀐다. */
    enum class Order {
        /** 이어하기(`active_game_snapshot`): 판 크기·룰·접바둑·덤. */
        HandicapThenKomi,

        /** 대국 기록 `index.json`의 한 줄: 판 크기·룰·덤·접바둑. */
        KomiThenHandicap,
    }

    fun write(json: JSONObject, setup: GameSetup, order: Order): JSONObject {
        json.put(BoardSizeKey, setup.boardSize.value)
        json.put(RulesetKey, setup.ruleset.name)
        when (order) {
            Order.HandicapThenKomi -> {
                json.put(HandicapCountKey, setup.handicapCount)
                json.put(KomiKey, setup.komi)
            }
            Order.KomiThenHandicap -> {
                json.put(KomiKey, setup.komi)
                json.put(HandicapCountKey, setup.handicapCount)
            }
        }
        return json
    }

    /** 지원하지 않는 판 크기면 던진다(`BoardSize`) — 두 코덱 모두 그 저장분 하나를 버리는 쪽으로 받는다. */
    fun read(json: JSONObject): GameSetup =
        GameSetup(
            boardSize = BoardSize(json.optInt(BoardSizeKey, BoardSize.Nine.value)),
            ruleset = enumOrDefault(json.optString(RulesetKey), Ruleset.Japanese),
            handicapCount = json.optInt(HandicapCountKey, 0),
            komi = json.optDouble(KomiKey, DefaultKomi),
        )
}
