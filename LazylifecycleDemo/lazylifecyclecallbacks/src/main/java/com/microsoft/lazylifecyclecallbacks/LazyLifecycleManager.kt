/*
 *  Copyright © Microsoft Corporation. All rights reserved.
 */

package com.microsoft.lazylifecyclecallbacks

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.microsoft.lazylifecyclecallbacks.utils.InMemoryOnce
import java.lang.ref.WeakReference

/**
 * Lazy lifecycle implementations could wait for draws, scenarios or some other events. Each of them
 * or their combinations can represent a trigger.
 * This is the general contract that needs to be adhered by all the managers.
 */
abstract class LazyLifecycleManager(owner: LifecycleOwner) {

    protected val lifecycleOwner: WeakReference<LifecycleOwner> = WeakReference(owner)
    private val executeLazyCreateOnce = InMemoryOnce()
    private val executeOnViewCreatedLazyOnce = InMemoryOnce()

    /**
     * When activate() is called, it installs a barrier at the point that guards the target code.
     * This method is meant for lazy initialisations or calls that could be deferred.
     * This is not meant for calls that are purely dependent on activity lifecycle transitions eg.
     * registering broadcast receivers in onStart() and unregistering them in onStop(). These type
     * of complimentary calls should NOT be added in lazy callbacks as we do not provide lazy versions of
     * onPause() and onStop() and it is not needed too. It is always good to depend on android in these cases.
     */
    @MainThread
    fun activate() {
        if (shouldContinue()) {
            lifecycleOwner.get()?.let {
                setupLifecycleTrigger(it)
            }
        }
    }

    /**
     * Implementation should add the actual code to fire the lazy lifecycle callbacks in this method.
     * Default implementation of what happens after trigger is provided in [triggerLazyLifecycleCallbacks]
     */
    abstract fun setupLifecycleTrigger(owner: LifecycleOwner)

    /**
     * If required, deactivates the lazy lifecycle callbacks. Can be used for cleanup of held objects too.
     * Default implementation is a noop.
     */
    open fun deactivate() {
        // Implement if needed in subclasses
    }

    protected fun triggerLazyLifecycleCallbacks() {
        this.lifecycleOwner.get()?.let { owner ->
            if (!owner.isVisible()) {
                return
            }
            // Execute onLazyOnCreate() only once per instance.
            with((owner as LazyLifecycleCallbacks)) {
                executeLazyCreateOnce.execute {
                    // We use post instead of direct execution. executing directly might cause main thread to freeze.
                    postOnCreate(owner)
                }
                // If the implementor of LazyLifecycle callbacks is also a implementor of LazyFragmentCallbacks then provide onViewCreatedLazy() too
                runsOnLazyFragment {
                    executeOnViewCreatedLazyOnce.execute {
                        postOnViewCreatedLazy(owner)
                    }
                }
                postOnStart(owner)
                postOnResume(owner)
            }
        }
    }

    private fun LazyLifecycleCallbacks.postOnViewCreatedLazy(owner: LifecycleOwner) {
        val inflatedView = (this as Fragment).view
        inflatedView?.let {
            uiHandler.post {
                if (owner.isVisible()) {
                    (owner as LazyFragmentLifecycleCallbacks).onViewCreatedLazy(inflatedView)
                } else executeOnViewCreatedLazyOnce.reset()
            }
        }
    }

    private fun LazyLifecycleCallbacks.postOnResume(owner: LifecycleOwner) = uiHandler.post {
        if (owner.isVisible()) {
            onLazyResume()
        }
    }

    private fun LazyLifecycleCallbacks.postOnStart(owner: LifecycleOwner) = uiHandler.post {
        if (owner.isVisible()) {
            onLazyStart()
        }
    }

    private fun LazyLifecycleCallbacks.postOnCreate(owner: LifecycleOwner) = uiHandler.post {
        if (owner.isVisible()) {
            onLazyCreate()
        } else executeLazyCreateOnce.reset()
    }

    private fun shouldContinue(): Boolean {
        lifecycleOwner.get()?.let { owner ->
            if (owner !is LazyLifecycleCallbacks || ownerNotSupported(owner)) {
                failExceptionally(owner)
            }

            assertFragmentIsNotRetained(owner)
            // Returns false, if the implementation does not support lazy lifecycle callbacks.
            if ((owner as LazyLifecycleCallbacks).supportsLazyLifecycleCallbacks().not() || owner.watchedView == null) {
                // Client has disabled the lazy lifecycle callbacks or the watched view is null.
                return false
            }
            // go go go!
            return true
        }
        // safely net for nulls, that won't happen!
        return false
    }

    private fun assertFragmentIsNotRetained(owner: LifecycleOwner) {
        if (owner is Fragment) {
            if (owner.retainInstance && (owner as LazyLifecycleCallbacks).supportsLazyLifecycleCallbacks()) {
                throw UnsupportedOperationException(
                    "LazyLifecycleCallbacks can not be used with retained fragments[${owner.javaClass.simpleName}]. " +
                        "It might lead to mismanaged state. "
                )
            }
        }
    }

    private fun failExceptionally(owner: LifecycleOwner) {
        if (owner !is LazyLifecycleCallbacks) {
            throw ClassCastException(
                "Not able to cast LifeCycleOwner to LazyLifecycleCallbacks. " +
                    "Please implement LazyLifecycleCallbacks interface before calling activate()"
            )
        } else {
            throw UnsupportedOperationException("We do not support any component other than android activity and fragments.")
        }
    }

    private fun ownerNotSupported(owner: LifecycleOwner) = (owner !is Activity && owner !is Fragment)

    protected fun LifecycleOwner.isVisible(): Boolean = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    companion object {
        const val TAG = "LazyLifecycleManager"
        private val uiHandler = Handler(Looper.getMainLooper())
    }

    private fun LazyLifecycleCallbacks.runsOnLazyFragment(block: () -> Unit) {
        if ((this is Fragment) && (this is LazyFragmentLifecycleCallbacks)) {
            block()
        }
    }
}