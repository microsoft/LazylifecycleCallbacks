package com.microsoft.lazylifecycledemo

import android.os.Bundle
import android.view.View

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
    }

    override val watchedView: View
        get() = this.window.decorView

    override fun onResume() {
        super.onResume()
    }

    override fun onLazyCreate() {
        super.onLazyCreate()
        someHeavyInit()
    }

    override fun onLazyResume() {
        super.onLazyResume()
        someHeavyInit()
    }

    override fun onLazyStart() {
        super.onLazyStart()
        someHeavyInit()
    }

    private fun someHeavyInit() {
        Thread.sleep(400)
    }

    override fun supportsLazyLifecycleCallbacks() = true
}