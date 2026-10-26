package com.worksoc.goaicoach.ui.l10n

import androidx.compose.ui.text.style.LineBreak

internal actual fun quoteLineBreak(): LineBreak = LineBreak(
    strategy = LineBreak.Strategy.Simple,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase,
)
