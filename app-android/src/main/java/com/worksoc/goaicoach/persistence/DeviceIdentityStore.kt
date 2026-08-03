package com.worksoc.goaicoach.persistence

import android.content.Context
import com.worksoc.goaicoach.application.device.DeviceIdentity
import com.worksoc.goaicoach.application.device.DeviceIdentityStorePort
import java.util.UUID

/**
 * 4계층(External Integration) Extended API 본체 — [DeviceIdentityStorePort]의 실제 구현.
 * 하드웨어 식별자(ANDROID_ID 등)를 읽지 않고 자체 생성한 UUID를 SharedPreferences에
 * 영속화한다 — 특별한 권한이 필요 없고, 이 앱 설치 범위를 벗어난 추적에 쓰일 수 없다.
 */
internal class DeviceIdentityStore(context: Context) : DeviceIdentityStorePort {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    override fun loadOrCreate(): DeviceIdentity {
        val existing = prefs.getString(IdKey, null)
        if (existing != null) {
            return DeviceIdentity(existing)
        }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(IdKey, generated).apply()
        return DeviceIdentity(generated)
    }

    private companion object {
        const val PrefsName = "go_ai_coach_device_identity"
        const val IdKey = "device_id"
    }
}
