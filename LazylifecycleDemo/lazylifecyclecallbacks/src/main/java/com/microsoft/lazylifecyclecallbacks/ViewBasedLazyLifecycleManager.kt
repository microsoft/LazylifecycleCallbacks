/*
 *  Copyright © Microsoft Corporation. All rights reserved.
 */

package com.microsoft.lazylifecyclecallbacks

import android.util.Log
import android.view.ViewTreeObserver
import androidx.lifecycle.Lifecycle.State.*
import androidx.lifecycle.LifecycleOwner
import com.microsoft.lazylifecyclecallbacks.State.*
import com.microsoft.lazylifecyclecallbacks.utils.Barrier

/**
 * ViewBasedLazyLifecycleManager is small construct implementing [LazyLifecycleManager] to ensure that we initialise not so necessary
 * things required in onCreate()/onStart()/onResume() lazily. By default, it watches on the number of draws
 * on a [LazyLifecycleCallbacks.watchedView] defined for a screen, and has an SLA of [timeout] seconds.
 *
 * While one execution is in flight, this construct prevents the subsequent calls to lazy methods.
 * Implementor should implement [LazyLifecycleCallbacks] and override its methods.
 * And [ViewBasedLazyLifecycleManager.activate] should be called in onResume().
 */

/////////////////////////////////////////////////////////////////////
// NOTE: Should always be a activity/fragment scoped singleton. /////
////////////////////////////////////////////////////////////////////

class ViewBasedLazyLifecycleManager @JvmOverloads constructor(
    owner: LifecycleOwner,
    private val drawsToWait: Int = 2,
    private val timeout: Long = 3000L
) : LazyLifecycleManager(owner) {
    private var state = Rest // Init with resting state

    override fun setupLifecycleTrigger(owner: LifecycleOwner) {
        if (state == Active) {
            Log.d(TAG, "Execution is in flight already bailing out!")
            return
        }

        // Return if watched view is null.
        val localWatchedView = (owner as LazyLifecycleCallbacks).watchedView
        if (localWatchedView == null) {
            // reset the state and exit.
            state = state.reset()
            return
        }

        // Define the pre-draw listener that executes the callbacks after n draws.
        val onPreDrawListener: ViewTreeObserver.OnPreDrawListener =
            object : ViewTreeObserver.OnPreDrawListener {
                val lazyBarrier = Barrier.with(getCondition(owner, drawsToWait))
                    .withOverrideDeadline(timeout)
                    .runOnBreak {
                        dispatchLazyLifecycleCallbacks()
                        // Move to the next state.
                        state = state.next()
                    }.startSLA()

                override fun onPreDraw(): Boolean {
                    lazyBarrier.strike()
                    return true
                }

                private fun dispatchLazyLifecycleCallbacks() {
                    localWatchedView.viewTreeObserver.removeOnPreDrawListener(this)
                    triggerLazyLifecycleCallbacks()
                }
            }
        localWatchedView.viewTreeObserver?.addOnPreDrawListener(onPreDrawListener)

        // Increment to the state [Active] after everything is setup..
        state = state.next()
    }

    private fun getCondition(owner: LifecycleOwner, drawsToWait: Int): Barrier.Condition {
        return object : Barrier.Condition {
            var drawCount = 1
            val finalDrawsToWait = drawsToWait
            override fun evaluate(): Boolean {
                if (owner.lifecycle.currentState.isAtLeast(RESUMED)) {
                    if (drawCount >= finalDrawsToWait) {
                        return true
                    }
                    drawCount++
                    return false
                } else return false
            }
        }
    }

    companion object {
        const val TAG = "ViewBasedLazyCallbacks"
    }
}

/**
 * Represents the state of the view based lifecycle callback trigger.
 */
private enum class State {
    Rest {
        override fun next(): State = Active.also { it.logState() }
        override fun reset(): State = Rest.also { it.logState() }
    },

    Active {
        override fun next(): State = Rest.also { it.logState() }
        override fun reset(): State = Rest.also { it.logState() }
    };

    fun logState() = Log.d(ViewBasedLazyLifecycleManager.TAG, "Moving LazyLifecycle state to $name")

    abstract fun next(): State
    abstract fun reset(): State
}
