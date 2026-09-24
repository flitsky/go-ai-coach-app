package com.worksoc.goaicoach.application.concurrency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun launchUiEffect(
    scope: CoroutineScope,
    block: suspend CoroutineScope.() -> Unit,
): Job =
    scope.launch(block = block)
