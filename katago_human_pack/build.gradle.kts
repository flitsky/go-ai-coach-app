plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName.set("katago_human_pack")
    dynamicDelivery {
        // 기존 사용자는 이미 기기에 모델이 있으므로 앱이 fetch를 부르지 않아 스토어 업데이트 재다운로드 0바이트(백로그 #245 U-71).
        // 새 사용자는 첫 실행 시 앱이 fetch로 다운로드한다.
        deliveryType.set("on-demand")
    }
}
