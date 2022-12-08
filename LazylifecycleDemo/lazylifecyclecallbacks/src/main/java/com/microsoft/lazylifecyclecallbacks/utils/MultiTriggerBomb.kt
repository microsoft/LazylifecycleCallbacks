/*
 *  Copyright © Microsoft Corporation. All rights reserved.
 */

package com.microsoft.lazylifecyclecallbacks.utils

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * [MultiTriggerBomb] is a construct that executes a piece of code represented as [charge], only if
 * 'k' triggers are down('k' conditions become true) or the timeout has happened(in case 'k' conditions do not become true), which ever happens first.
 * To plant a bomb, call [MultiTriggerBomb.plant] on the object. Before [MultiTriggerBomb.plant] is called, bomb is in inert state, pressing down the triggers
 * has no impact. The timeout counter starts the moment the [MultiTriggerBomb.plant] is called.
 * To press down on a trigger, call [MultiTriggerBomb.down], when the number of down() actions
 * equals number of triggers, the bomb is exploded, and the code is executed.
 * Code/Bomb is always executed in the thread tied to the handler that was provided in the constructor.
 *
 * Bomb with 0 triggers will immediately explode at the time of planting.
 *
 * |-----------------------------------------------|
 * |                                               |
 * | [s1] --- [s2] --- [s3] ---[s4] --- [s5] ---   |  ----> [CHARGE]
 * |                                               |        ^
 * |------------|----------------------------------|        |
 *              |                                           |
 *              |________________t seconds__________________|
 */
class MultiTriggerBomb(val triggers: Int, val timeout: Long = 0L, private val handler: Handler = Handler(Looper.getMainLooper()), private val charge: () -> Unit) {

    var hasExploded = false
        private set

    var isPlanted = false
        private set

    var cause: String = ""

    private var remainingTriggers = triggers
    private var runnable: Runnable? = null

    // For easier debugging of what happened.
    private var allDownEvents = mutableListOf<String>()

    init {
        // Run some validations
        if (timeout < 0) {
            throw IllegalArgumentException("Timeout cannot be a -ve number")
        }
        if (triggers < 0) {
            throw IllegalArgumentException("Switches cannot be a -ve number")
        }
    }

    /**
     * Activates the bomb. This is when the deadline timer starts.
     */
    fun plant() {
        synchronized(this) {
            // Early exit
            if (isPlanted || hasExploded) return

            if (!isPlanted && !hasExploded) {
                if (triggers == 0) {
                    // Explode immediately
                    handler.post {
                        charge.invoke()
                    }
                    hasExploded = true
                    this.cause = CAUSE_ZERO_TRIGGER
                    allDownEvents.add(this.cause)
                    Log.d(TAG, "Exploded while planting!")
                }

                if (timeout >= 0) {
                    runnable = Runnable {
                        if (!hasExploded) {
                            explodeLocked()
                            Log.d(TAG, "Exploded due to timeout, $remainingTriggers switches remaining.")
                            this.cause = CAUSE_TIMED_OUT
                            allDownEvents.add(this.cause)
                        }
                    }
                    // start the count down. If trigger count does not reach to 0 without timeout, explode!
                    runnable?.let { code ->
                        handler.postDelayed(code, timeout)
                    }
                }
                isPlanted = true
            }
        }
    }

    /**
     * Reduces the number of remaining switches by 1. If the down() triggers the explosion the code is executed on the thread whose handler is
     * supplied in the bomb constructor.
     * @param cause addition cause representing the cause of explosion.
     */
    @JvmOverloads
    fun down(cause: String = "") {
        synchronized(this) {
            Log.d(TAG, "trigger down event: $cause, remaining switch count: ${remainingTriggers - 1}")
            // early exit
            if (remainingTriggers <= 0) {
                return
            }

            if (isPlanted && !hasExploded && --remainingTriggers == 0) {
                Log.d(TAG, "All switches down, exploding!")
                handler.post { explodeLocked() }
                this.cause = cause
            }
            allDownEvents.add(cause)
        }
    }

    fun tryDiffuse() {
        // first line of defence, even if we miss this, we skip the execution of runnable further down the line.
        synchronized(this) {
            runnable?.let {
                handler.removeCallbacks(it)
            }
            hasExploded = true
            allDownEvents.add("diffused")
        }
    }

    private fun explodeLocked() {
        charge.invoke()
        hasExploded = true
    }

    // For debugging purposes.
    private fun dumpToLog() {
        val events = StringBuilder()
        for (event in allDownEvents) {
            events.append(event).append(",")
        }
        Log.d(TAG, events.substring(0, events.length - 1))
    }

    companion object {
        const val CAUSE_TIMED_OUT = "timeout happened"
        const val CAUSE_ZERO_TRIGGER = "0 trigger"
        private const val TAG = "MultiTriggerBomb"
    }
}
