package com.worksoc.goaicoach.application.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val engineIoDispatcher: CoroutineDispatcher = Dispatchers.IO
