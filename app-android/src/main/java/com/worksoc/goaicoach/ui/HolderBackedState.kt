package com.worksoc.goaicoach.ui

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Bridges a Compose `var` to an off-Compose owner.
 *
 * Reads come from [get] (the current Compose mirror); writes go through [set]
 * (which updates the platform-independent [GameSessionStateHolder] and re-syncs
 * the mirror synchronously, preserving read-your-writes semantics within a
 * single callback). This lets the session state move out of the composable
 * without rewriting every call site at once.
 */
internal class HolderBackedState<T>(
    private val get: () -> T,
    private val set: (T) -> Unit,
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = get()
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)
}
