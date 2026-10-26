package com.worksoc.goaicoach.ui.gamerecord

import androidx.compose.runtime.Composable

/**
 * SGF 파일 고르기(백로그 #294 — S21). 고르면 앞 [maxBytes]바이트를 읽어 [onPicked]에 넘긴다(못 읽으면 `null`). 고르지 않고 닫으면 부르지 않는다.
 * 안드로이드는 문서 고르기(`OpenDocument`) + 콘텐츠 리졸버. 이 플랫폼에 고르는 창이 아직 없으면 `null`이고, 화면은 「파일 열기」 단추를 감춘다.
 */
@Composable
internal expect fun rememberSgfFilePicker(maxBytes: Int, onPicked: suspend (ByteArray?) -> Unit): (() -> Unit)?
