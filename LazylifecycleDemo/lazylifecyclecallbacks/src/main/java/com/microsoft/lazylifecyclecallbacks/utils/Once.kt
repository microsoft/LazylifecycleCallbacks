package com.microsoft.lazylifecyclecallbacks.utils

/*
 * Copyright © Microsoft Corporation. All rights reserved.
 */

import android.app.Application
import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [Once] is a construct that is used to make sure that some piece of code is executed only once. But, that also has multiple
 * definitions. Whether it should get executed once per activity, once per fragment, once per applications or once per installation.
 * All, but the last once can be implemented using in-memory implementations by changing the scope of the variable.
 * Example: If we create [Once] in fragment scope it will be created with each instance of fragment. If we do it on application level,
 * [Once] will be behave like app level singleton.
 */
sealed interface Once {
    /** Executes a block of code represented by [block] once till the [Once] scope is destroyed or [reset] is called. */
    fun execute(block: () -> Unit)

    /** Returns true if the block has already executed once, false otherwise. */
    fun isExecuted(): Boolean

    /** Resets the state such that the [Once] object can be used(can execute the block) again. */
    fun reset()
}

/**
 * Construct that helps to restrict the code execution frequency to 1. It will not allow the snippet passed through the close to be executed more than once.
 * The first client to execute run() will execute this and other calls to run() will not be respected till reset is called.
 * The Scope of the [InMemoryOnce] is tied to its instance. In other words, a new [Once] object will be able to execute the code block again.
 */

class InMemoryOnce : Once {
    private val hasExecuted = AtomicBoolean(false)

    override fun execute(block: () -> Unit) {
        if (hasExecuted.compareAndSet(false, true)) {
            block()
        }
    }

    override fun isExecuted() = hasExecuted.get()
    override fun reset() = hasExecuted.set(false)
}

/**
 * Persisted implementation of [Once] interface, that makes sure that the code is executed once in the lifetime of the installation, or until [reset] is called.
 * It uses android shared preferences to persist its memory. It has to do the preference read and locking one time per application process.
 * Developers should be mindful about the cost while using it.
 * It's constructor requires 2 arguments, [key] and reference to the application instance. [key] refers to the actual key used in saving the preference entry.
 */
class PersistedOnce(val key: String, application: Application) : Once {
    private val preferences = application.getSharedPreferences("PersistedOnceValues", Context.MODE_PRIVATE)
    private val executed by lazy(LazyThreadSafetyMode.NONE) {
        AtomicBoolean(preferences.getBoolean(key, false))
    }

    @Synchronized
    override fun execute(block: () -> Unit) {
        if (executed.compareAndSet(false, true)) {
            block()
            preferences.edit().putBoolean(key, true).apply()
        }
    }

    override fun isExecuted(): Boolean = executed.get()

    @Synchronized
    override fun reset() {
        executed.set(false)
        preferences.edit().putBoolean(key, false).apply()
    }
}
