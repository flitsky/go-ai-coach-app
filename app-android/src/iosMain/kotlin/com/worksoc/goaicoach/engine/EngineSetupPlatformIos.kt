package com.worksoc.goaicoach.engine

internal actual fun isStoreInstallationErrorCode(errorCode: Int?): Boolean = false

internal actual fun engineSetupLog(message: String, error: Throwable?) {
    println(if (error == null) "EnginePad: $message" else "EnginePad: $message — $error")
}
