package com.microsoft.lazylifecyclecallbacks.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Barrier creates a condition based barrier, which prevents a piece of code from being executed
 * till a condition represented by {@link Condition} becomes true. It has an optional SLA
 * that executes the code, if that SLA(in millis) expires only if execution did not take place due
 * to the condition getting satisfied. It works in "which ever happens first" basis.
 * If the condition gets satisfied first, then SLA will be ignored. And if SLA expires and then the condition
 * gets satisfied after that then only SLA expiration execution will take place.
 * Once a barrier is broken and {@link BarrierCode} is executed, it will not execute again for the
 * same barrier instance. Barrier is a one time use class.
 */
public final class Barrier {
    public static final String EXECUTED_DUE_TO_SLA = "sla";
    public static final String EXECUTED_DUE_TO_CONDITION = "condition";

    private static final String TAG = "Barrier";

    // This is only there for tests, as there has to be a way to know the type of execution happened
    // Whether it was due to SLA breach or condition getting satisfied.
    @VisibleForTesting
    AtomicReference<String> mExecutionMechanism = new AtomicReference<>(null);

    private final WeakReference<Condition> mCondition;
    private final AtomicBoolean mHasExecuted;
    private final AtomicBoolean mSlaStarted = new AtomicBoolean(false);

    private long mSLA = -1;
    private BarrierCode mCode;

    private Barrier(WeakReference<Condition> condition) {
        this.mCondition = condition;
        mHasExecuted = new AtomicBoolean(false);
    }

    /**
     * Creates a Barrier object with a given condition.
     *
     * @param condition condition used to break the barrier.
     * @return Barrier object
     */
    public static Barrier with(@NonNull Condition condition) {
        WeakReference<Condition> temp = new WeakReference<>(condition);
        return new Barrier(temp);
    }

    public boolean hasBarrierFinishedExecution() {
        return mHasExecuted.get();
    }
    /**
     * Takes the code that needs to be executed when the barrier breaks due to condition getting
     * satisfied or SLA expiration in "which ever happens first" fashion.
     *
     * @param code Barrier code
     * @return Barrier object with barrier code defined
     */
    public Barrier runOnBreak(@NonNull BarrierCode code) {
        this.mCode = code;
        return this;
    }

    /**
     * Defines the optional SLA for the execution. If the condition does not get satisfied till the SLA
     * reaches, the defined barrier code will get executed anyways. It takes a parameter,
     * shouldPostOnMainThread that dictates on which thread code gets executed. If this method is called
     * multiple times on a barrier object before calling {@link Barrier#startTimer()} only the last call
     * is honoured. Calling this after {@link Barrier#startTimer()} has no effect.
     * Note: It is important to call {@link Barrier#startTimer()} after calling this method that
     * triggers the operation of posting the BarrierCode on the required thread. Not calling startSLA()
     * will ignore SLA parameter and nothing will happen in relation to SLA.
     *
     * @param overrideDeadlineMillis SLA in milli seconds.
     * @return Barrier object after capturing SLA.
     */
    public Barrier withOverrideDeadline(long overrideDeadlineMillis) {
        // When SLA is not defined.
        if (overrideDeadlineMillis <= 0) {
            throw new IllegalArgumentException("SLA should not be 0 or less than 0");
        }
        this.mSLA = overrideDeadlineMillis;
        return this;
    }

    /**
     * This is point from where the SLA counting starts. This call is important if the SLA needs to work.
     * This can be called from a different place where the barrier is created. Calling this method multiple times
     * has no effect. Only the first call is honoured.
     *
     * @return Barrier
     */
    public Barrier startTimer() {
        if (mCode == null) {
            throw new IllegalStateException("BarrierCode not defined in the barrier.");
        }

        if (mSLA == -1) {
            throw new IllegalStateException("SLA is not defined and startSLA() called, use withSLA() first.");
        }

        boolean willStartSLAFromHere = mSlaStarted.compareAndSet(false, true);

        if (willStartSLAFromHere) {
            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.postDelayed(this::tryExecute, mSLA);
        }
        return this;
    }

    private void tryExecute() {
        boolean willExecute = mHasExecuted.compareAndSet(false, true);
        if (willExecute) {
            mExecutionMechanism.compareAndSet(null, EXECUTED_DUE_TO_SLA);
            Log.d(TAG, "Barrier condition did not become true, started executing due to SLA");
            mCode.invoke();
        } else {
            Log.d(TAG, "Barrier code already executed due to the condition becoming true. SLA will be ignored.");
        }
    }

    /**
     * Barriers can only be broken if we strike/flick them enough no of times. This needs to installed in
     * the execution path where the condition needs to be evaluated.
     * Once a barrier is broken and {@link BarrierCode} is executed, it will never execute again
     * for the same barrier instance.
     */
    public void strike() {
        if (mCode == null) {
            throw new IllegalStateException("Barrier cannot be created without a barrier code, "
                    + "Try using runOnBreak() to pass a code for the barrier.");
        }

        if (mCondition.get() != null && !mHasExecuted.get() && mCondition.get().evaluate()) {
            boolean willExecute = mHasExecuted.compareAndSet(false, true);
            if (willExecute) {
                mExecutionMechanism.compareAndSet(null, EXECUTED_DUE_TO_CONDITION);
                mCode.invoke();
                Log.d(TAG, "Barrier code started executing due to the condition getting satisfied.");
            } else {
                Log.d(TAG, "Barrier code already executed due to an smaller SLA");
            }
        }
    }

    /**
     * Usually the code instance is retained till the barrier instance is in the memory.
     * Use clear if the barrier instance has a wider scope and we want to clear the code.
     * After calling this method, all invocations of strike will throw IllegalStateException
     */
    public void clear() {
        mCode = null;
    }

    /**
     * Piece of code that needs to be executed once the barrier is broken due to the
     * {@link Condition} getting satisfied or SLA time is expired!
     */
    public interface BarrierCode {
        void invoke();
    }

    /**
     * Represents the condition that should break the Barrier.
     */
    public interface Condition {
        /**
         * Implementors should override this method to implement their barrier condition.
         * {@link Barrier} internally calls evaluate() every time {@link Barrier#strike()} is
         * called. Once the condition becomes true, if executes the codes represented by
         * {@link BarrierCode}
         *
         * @return true, if the condition is satisfied, false otherwise.
         */
        boolean evaluate();
    }
}
