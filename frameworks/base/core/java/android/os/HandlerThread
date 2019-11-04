/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import android.annotation.NonNull;
import android.annotation.Nullable;

/**
 * 用于启动一个包含looper的新线程的便利类。 它的looper可以用来创建handler类。
 * 注意仍需调用start()方法。
 */
public class HandlerThread extends Thread {
    int mPriority;
    int mTid = -1;
    Looper mLooper;
    private @Nullable Handler mHandler;

    public HandlerThread(String name) {
        super(name);
        mPriority = Process.THREAD_PRIORITY_DEFAULT;
    }
    
    /**
     * 构造 HandlerThread.
     * @param name
     * @param priority 线程运行的优先级。提供的值必须来自
     * {@link android.os.Process} 而不是 java.lang.Thread.
     */
    public HandlerThread(String name, int priority) {
        super(name);
        mPriority = priority;
    }
    
    /**
     * 如果需要在Looper循环之前执行某些设置，则可以显式重写的回调方法。
     */
    protected void onLooperPrepared() {
    }

    @Override
    public void run() {
        mTid = Process.myTid();
        Looper.prepare();
        synchronized (this) {
            mLooper = Looper.myLooper();
            notifyAll();
        }
        Process.setThreadPriority(mPriority);
        onLooperPrepared();
        Looper.loop();
        mTid = -1;
    }
    
    /**
     * 此方法返回与此线程关联的Looper。如果此线程未启动或由于任何原因 isalive()返回false，
	 * 则此方法将返回null。如果此线程已启动，则此方法将阻塞，直到Looper初始化完成。  
     * @return The looper.
     */
    public Looper getLooper() {
        if (!isAlive()) {
            return null;
        }
        
        // 如果线程已启动，等待looper创建
        synchronized (this) {
            while (isAlive() && mLooper == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
        }
        return mLooper;
    }

    /**
     * @return 与此线程关联的 {@link Handler} 
     * @hide
     */
    @NonNull
    public Handler getThreadHandler() {
        if (mHandler == null) {
            mHandler = new Handler(getLooper());
        }
        return mHandler;
    }

    /**
     * 停止handler线程的循环。
     * <p>
     * 会终止handler线程的循环，不再处理消息队列中的任何消息。
     * </p><p>
     * 在 Looper 被要求停止后，任何向队列发送消息的尝试将会失败。 
     * 例如 {@link Handler#sendMessage(Message)} 方法将返回 false。
     * </p><p class="note">
     * 使用此方法可能不安全，因为某些消息可能会在 looper 终止前未被送达。
     * 考虑使用 {@link #quitSafely} 来确保所有未了结的工作都有序的完成。
     * </p>
     *
     * @return True 如果looper被要求停止
	 *         False 如果线程还未启动
     *
     * @see #quitSafely
     */
    public boolean quit() {
        Looper looper = getLooper();
        if (looper != null) {
            looper.quit();
            return true;
        }
        return false;
    }

    /**
     * 安全的停止handler线程的循环。
     * <p>
     * 使handler线程的循环在处理完消息队列中的剩余消息后后立即终止。
     * 挂起的延迟发送的消息将不会被传递。
     * </p><p>
     * 在 Looper 被要求停止后，任何向队列发送消息的尝试将会失败。 
     * 例如 {@link Handler#sendMessage(Message)} 方法将返回 false。
     * </p><p>
     * 如果线程还未启动或已终止 (也就是 {@link #getLooper} 返回 null)，
     * 将返回 false。
     * 否则会要求循环停止并返回true。
     * </p>
     *
     * @return True 如果looper被要求停止
	 *         False 如果线程还未启动.
     */
    public boolean quitSafely() {
        Looper looper = getLooper();
        if (looper != null) {
            looper.quitSafely();
            return true;
        }
        return false;
    }

    /**
     * 返回此线程的标识符。见 Process.myTid().
     */
    public int getThreadId() {
        return mTid;
    }
}