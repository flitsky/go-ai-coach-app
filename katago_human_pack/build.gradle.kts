plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName.set("katago_human_pack")
    dynamicDelivery {
        deliveryType.set("fast-follow")
    }
}
