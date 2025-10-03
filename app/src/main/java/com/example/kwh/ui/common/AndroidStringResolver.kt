package com.example.kwh.ui.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Production implementation that delegates to [Context.getString].
 */
class AndroidStringResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : StringResolver {

    override fun get(id: Int, vararg formatArgs: Any): String {
        return if (formatArgs.isEmpty()) {
            context.getString(id)
        } else {
            context.getString(id, *formatArgs)
        }
    }
}
