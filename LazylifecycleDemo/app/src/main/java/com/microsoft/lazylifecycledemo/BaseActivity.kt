package com.microsoft.lazylifecycledemo

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.microsoft.lazylifecyclecallbacks.LazyLifecycleCallbacks
import com.microsoft.lazylifecyclecallbacks.LazyLifecycleManager
import com.microsoft.lazylifecyclecallbacks.ViewBasedLazyLifecycleManager

abstract class BaseActivity : AppCompatActivity(), LazyLifecycleCallbacks {

    protected lateinit var lazyLifecycleManager: LazyLifecycleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lazyLifecycleManager = ViewBasedLazyLifecycleManager(this)
    }

    override fun onResume() {
        super.onResume()
        lazyLifecycleManager.activate()
    }

    override fun onPause() {
        super.onPause()
        lazyLifecycleManager.deactivate()
    }

    override fun onLazyCreate() {}

    override fun onLazyStart() {}

    override fun onLazyResume() {}

    override fun supportsLazyLifecycleCallbacks(): Boolean = false
}