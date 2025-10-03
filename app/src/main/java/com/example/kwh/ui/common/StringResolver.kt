package com.example.kwh.ui.common

import androidx.annotation.StringRes

/**
 * Abstraction for resolving localized strings. Allows ViewModels to remain agnostic of
 * Android framework dependencies during unit tests.
 */
interface StringResolver {
    fun get(@StringRes id: Int, vararg formatArgs: Any): String
}
