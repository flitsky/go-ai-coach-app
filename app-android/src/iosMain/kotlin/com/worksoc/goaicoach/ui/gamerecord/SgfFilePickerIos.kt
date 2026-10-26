package com.worksoc.goaicoach.ui.gamerecord

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import com.worksoc.goaicoach.ui.designsystem.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileHandle
import platform.Foundation.NSURL
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingFromURL
import platform.Foundation.readDataOfLength
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

/**
 * iOS: 「파일」 앱의 문서 고르기(`UIDocumentPickerViewController`) — 안드로이드의 `OpenDocument`처럼 아무 파일이나 고르게 둔다
 * (SGF에는 시스템 형식 이름이 없다). 사본으로 받으므로(`asCopy`) 보안 범위 접근이 필요 없다. 앞 [maxBytes]바이트만 읽는다.
 */
@Composable
internal actual fun rememberSgfFilePicker(maxBytes: Int, onPicked: suspend (ByteArray?) -> Unit): (() -> Unit)? {
    val controller = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val latestOnPicked = rememberUpdatedState(onPicked)
    // 고르는 창의 델리게이트는 약한 참조다 — 컴포지션이 쥔다.
    val delegate = remember {
        SgfPickerDelegate { url ->
            scope.launch { latestOnPicked.value(withContext(Dispatchers.Default) { readCapped(url, maxBytes) }) }
        }
    }
    return remember(controller, delegate) {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            controller.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class SgfPickerDelegate(private val onPicked: (NSURL) -> Unit) :
    NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPicked)
    }

    // 고르지 않고 닫으면 부르지 않는다(공용 KDoc).
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

@OptIn(ExperimentalForeignApi::class)
private fun readCapped(url: NSURL, maxBytes: Int): ByteArray? = runCatching {
    val handle = NSFileHandle.fileHandleForReadingFromURL(url, error = null) ?: return@runCatching null
    try {
        handle.readDataOfLength(maxBytes.toULong()).toByteArray()
    } finally {
        handle.closeFile()
    }
}.getOrNull()
