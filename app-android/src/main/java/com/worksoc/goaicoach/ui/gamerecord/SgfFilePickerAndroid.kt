package com.worksoc.goaicoach.ui.gamerecord

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberSgfFilePicker(maxBytes: Int, onPicked: suspend (ByteArray?) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnPicked = rememberUpdatedState(onPicked)
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { latestOnPicked.value(withContext(Dispatchers.IO) { readCapped(context, uri, maxBytes) }) }
    }
    return { openFile.launch(arrayOf("*/*")) }
}

private fun readCapped(context: Context, uri: Uri, maxBytes: Int): ByteArray? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input -> input.readNBytesCapped(maxBytes) }
}.getOrNull()

private fun java.io.InputStream.readNBytesCapped(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
