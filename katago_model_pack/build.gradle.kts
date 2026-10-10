plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName.set("katago_model_pack")
    dynamicDelivery {
        deliveryType.set("fast-follow")
    }
}
