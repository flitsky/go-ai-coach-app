package com.worksoc.goaicoach.engine.android

import java.io.BufferedReader
import java.io.BufferedWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun Throwable.isEnginePipeBroken(): Boolean = this is java.io.IOException

internal actual val enginePipeIoDispatcher: CoroutineDispatcher = Dispatchers.IO

internal actual fun engineDiagnosticLine(line: String) {
    System.err.println(line)
}

/**
 * 프로세스 stdin을 [EngineLineWriter]로 — 줄 하나를 한 번의 `write`로 쓰고 비운다. `write` 한 번은 writer의 락을 통째로
 * 쥐므로, 배수가 따로 보내는 `terminate`와 다음 명령의 줄이 줄 중간에서 섞이지 않는다(refactor backlog #17).
 * 줄바꿈은 `\n`이다 — 예전 GTP 쪽의 `newLine()`(줄 구분자)과 안드로이드 · macOS에서 같은 바이트다.
 */
internal fun BufferedWriter.asEngineLineWriter(): EngineLineWriter =
    object : EngineLineWriter {
        override fun writeLine(line: String) {
            write(line + "\n")
            flush()
        }
    }

/** 프로세스 stdout을 [EngineLineReader]로 — `BufferedReader.readLine()` 그대로(줄바꿈 빼고, 끝이면 `null`). */
internal fun BufferedReader.asEngineLineReader(): EngineLineReader =
    object : EngineLineReader {
        override fun readLine(): String? = this@asEngineLineReader.readLine()
    }
