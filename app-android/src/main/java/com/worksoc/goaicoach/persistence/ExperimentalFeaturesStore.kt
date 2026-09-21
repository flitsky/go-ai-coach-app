package com.worksoc.goaicoach.persistence

import android.content.Context
import android.content.SharedPreferences

/**
 * 실험실(Labs / Experimental) 기능 활성화 여부를 저장하고 관리하는 독립 저장소.
 *
 * ## ⚠️ 왜 [UserPreferencesStore]와 분리된 독립 SharedPreferences인가
 * [UserPreferencesStore]는 대국 세션의 자동저장(Autosave) 파이프라인과 결합되어 있어,
 * 전체 [com.worksoc.goaicoach.application.preferences.UserPreferencesSnapshot]을 통째로
 * 재조립하여 저장하는 구조를 가집니다. 그 조립 흐름에 배선되지 않은 필드는 저장 시점에
 * 조용히 기본값으로 되돌아가는 위험이 있습니다 ([DeveloperModeStore]와 [UiLanguageStore]의 선례).
 *
 * 따라서 개발 중이거나 소프트랜딩을 위해 격리된 실험실 기능 플래그들은
 * 이 독립된 SharedPreferences 파일에 안전하게 보관합니다.
 */
internal class ExperimentalFeaturesStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    )

    /**
     * 카메라 바둑판 사진 인식 및 분석(#179) 실험실 기능 활성화 여부.
     * 기본값은 false(비활성화)이며, 설정 화면에서 사용자가 켰을 때만 홈 화면에 카드가 노출됩니다.
     */
    fun isCameraBoardScanEnabled(): Boolean =
        prefs.getBoolean(CameraBoardScanKey, false)

    fun setCameraBoardScanEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(CameraBoardScanKey, enabled).apply()
    }

    internal companion object {
        const val PrefsName = "go_ai_coach_experimental_features"
        const val CameraBoardScanKey = "camera_board_scan_enabled"
    }
}
