package com.worksoc.goaicoach.ui.gamerecord

import androidx.compose.runtime.Composable

// iOS의 문서 고르기(UIDocumentPicker)는 다음 단계(#294 W23) — 그때까지 「파일 열기」 단추를 감춘다.
@Composable
internal actual fun rememberSgfFilePicker(maxBytes: Int, onPicked: suspend (ByteArray?) -> Unit): (() -> Unit)? = null
