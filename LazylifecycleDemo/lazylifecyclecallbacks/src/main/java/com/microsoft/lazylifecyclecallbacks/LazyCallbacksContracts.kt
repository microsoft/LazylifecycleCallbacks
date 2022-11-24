/*
 *  Copyright © Microsoft Corporation. All rights reserved.
 */

package com.microsoft.lazylifecyclecallbacks

import android.view.View

interface LazyLifecycleCallbacks {
    /**
     * Lazy version of activity onCreate() callback. Should be used for one time initialisations that could be done after the
     * screen has finished rendering. Should not be used for complementary calls that set/reset their state in
     * onCreate/onDestroy
     */
    fun onLazyCreate()

    /**
     * Lazy version of activity onStart() callback. Should be used for initialisations and code that are non essential and can be
     * deferred till the activity has finished rendering. Just like onCreate() this should not be used for complementary calls
     * like registering a broadcast receiver in onStart() and unregistering in onStop(). For these kinds of use cases use
     * activity lifecycle methods. This can be used for cases, like updating a bitmap every time apps come to foreground from background,
     * making some n/w, db calls to refresh the data, some initialisations of classes etc.
     */
    fun onLazyStart()

    /**
     * See onLazyStart() documentation.
     */
    fun onLazyResume()

    /**
     * Returns if the current activity supports lazy lifecycle callbacks.
     * If this method returns false, then even if the activity overrides the lazy callbacks
     * it does not have any impact.
     * The individual activities have to onboard this by returning true.
     */
    fun supportsLazyLifecycleCallbacks(): Boolean

    /**
     * The view that will be observed by the [ViewBasedLazyLifecycleManager] for draw events.
     * Kiln used this same method. So, if a component uses Kiln then lazy lifecycle manager picks up
     * the same view as it is more relevant. For other screen, decor view is used.
     */
    val watchedView: View?
}

/**
 * Interface that defines the contracts for lazy fragment callbacks.
 */
interface LazyFragmentLifecycleCallbacks {

    /**
     * Lazy version of onViewCreated, that will be called with the view of the fragment.
     * In most of the cases, fragment's lifeCycleOwner and fragment's view lifecycle owner will have the same scope.
     * But in case of retained fragments, fragment's scope could be larger than it's views.
     * That is the reason, why retained fragments are not supported by lazy lifecycle callbacks.
     */
    fun onViewCreatedLazy(view: View)
}
