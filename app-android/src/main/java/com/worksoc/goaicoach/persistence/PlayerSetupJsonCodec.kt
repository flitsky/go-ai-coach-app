package com.worksoc.goaicoach.persistence

import com.worksoc.goaicoach.match.HumanGameType
import com.worksoc.goaicoach.match.PlayerSetup
import com.worksoc.goaicoach.match.SeatController
import com.worksoc.goaicoach.match.SidePlayerSetup
import com.worksoc.goaicoach.shared.PlayLevelGroup
import com.worksoc.goaicoach.shared.PlayLevelSetting
import org.json.JSONObject

internal object PlayerSetupJsonCodec {
    fun encodePlayerSetup(playerSetup: PlayerSetup): JSONObject =
        JSONObject()
            .put("black", encodeSidePlayerSetup(playerSetup.black))
            .put("white", encodeSidePlayerSetup(playerSetup.white))

    fun decodePlayerSetup(json: JSONObject?): PlayerSetup =
        PlayerSetup(
            black = decodeSidePlayerSetup(json?.optJSONObject("black"), SeatController.Human),
            white = decodeSidePlayerSetup(json?.optJSONObject("white"), SeatController.Ai),
        )

    fun encodePlayLevel(playLevel: PlayLevelSetting): JSONObject =
        JSONObject()
            .put("group", playLevel.group.name)
            .put("level", playLevel.safeLevel)

    fun decodePlayLevel(json: JSONObject?): PlayLevelSetting {
        val group = enumOrDefault(json?.optString("group"), PlayLevelGroup.FastBeginner)
        val level = json?.optInt("level", 1) ?: 1
        return PlayLevelSetting(group = group, level = level).normalized()
    }

    private fun encodeSidePlayerSetup(side: SidePlayerSetup): JSONObject =
        JSONObject()
            .put("controller", side.controller.name)
            .put("humanGameType", side.humanGameType.name)
            .put("playLevel", encodePlayLevel(side.playLevel))

    private fun decodeSidePlayerSetup(
        json: JSONObject?,
        defaultController: SeatController,
    ): SidePlayerSetup =
        SidePlayerSetup(
            controller = enumOrDefault(json?.optString("controller"), defaultController),
            humanGameType = enumOrDefault(json?.optString("humanGameType"), HumanGameType.Normal),
            playLevel = decodePlayLevel(json?.optJSONObject("playLevel")),
        )
}

internal inline fun <reified T : Enum<T>> enumOrDefault(
    name: String?,
    default: T,
): T =
    name
        ?.takeIf { it.isNotBlank() }
        ?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }
        ?: default

/**
 * [enumOrDefault]와 달리 **모르는 값을 기본값으로 메우지 않는다.**
 *
 * ⚠️ 대국 기록의 승자(`winner`)처럼 *"값이 없다"* 와 *"흑이다"* 가 **서로 다른 뜻**인 자리에서는
 * 기본값을 끼워 넣으면 없는 사실을 지어내게 된다(백로그 #151).
 */
internal inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name
        ?.takeIf { it.isNotBlank() }
        ?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }
