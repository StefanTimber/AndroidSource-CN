/*
 * Copyright (C) 2008 The Android Open Source Project
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

/**
 * 发起倒计时，直到未来某个时刻，并在途中定期通知。
 *
 * 以下是一个在文本区域显示30秒倒计时的示例：
 *
 * <pre class="prettyprint">
 * new CountDownTimer(30000, 1000) {
 *
 *     public void onTick(long millisUntilFinished) {
 *         mTextField.setText("seconds remaining: " + millisUntilFinished / 1000);
 *     }
 *
 *     public void onFinish() {
 *         mTextField.setText("done!");
 *     }
 *  }.start();
 * </pre>
 *
 * {@link #onTick(long)}的调用是对这个类同步的，所以在上一个回调完成前下一个都不会发生。
 * 只有当 {@link #onTick(long)} 的实现与倒计时间隔相比，需要大量的时间来执行，
 * 此机制才有意义。
 */
public abstract class CountDownTimer {

    /**
     * 计时器应当停止的 epoch 时间
     */
    private final long mMillisInFuture;

    /**
     * 用户接收回调的毫秒间隔
     */
    private final long mCountdownInterval;

    private long mStopTimeInFuture;
    
    /**
    * 布尔值，表示计时器是否被取消
    */
    private boolean mCancelled = false;

    /**
     * @param millisInFuture 从 {@link #start()} 开始直到倒计时结束
     *   调用 {@link #onFinish()} 的毫秒数。
     * @param countDownInterval 倒计时过程中接收 {@link #onTick(long)}
     *    回调的间隔。
     */
    public CountDownTimer(long millisInFuture, long countDownInterval) {
        mMillisInFuture = millisInFuture;
        mCountdownInterval = countDownInterval;
    }

    /**
     * 取消倒计时。
     */
    public synchronized final void cancel() {
        mCancelled = true;
        mHandler.removeMessages(MSG);
    }

    /**
     * 开始倒计时。
     */
    public synchronized final CountDownTimer start() {
        mCancelled = false;
        if (mMillisInFuture <= 0) {
            onFinish();
            return this;
        }
        mStopTimeInFuture = SystemClock.elapsedRealtime() + mMillisInFuture;
        mHandler.sendMessage(mHandler.obtainMessage(MSG));
        return this;
    }


    /**
     * 定期启动的回调。
     * @param millisUntilFinished 距结束的毫秒数。
     */
    public abstract void onTick(long millisUntilFinished);

    /**
     * 倒计时结束启动的回调。
     */
    public abstract void onFinish();


    private static final int MSG = 1;


    // 处理倒计时
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            synchronized (CountDownTimer.this) {
                if (mCancelled) {
                    return;
                }

                final long millisLeft = mStopTimeInFuture - SystemClock.elapsedRealtime();

                if (millisLeft <= 0) {
                    onFinish();
                } else {
                    long lastTickStart = SystemClock.elapsedRealtime();
                    onTick(millisLeft);

                    // 考虑到用户的 onTick 执行需要时间
                    long lastTickDuration = SystemClock.elapsedRealtime() - lastTickStart;
                    long delay;

                    if (millisLeft < mCountdownInterval) {
                        // 延迟到结束
                        delay = millisLeft - lastTickDuration;

                        // 特殊情况：onTick 花费的时间超过了间隔，立刻触发 onFinish
                        if (delay < 0) delay = 0;
                    } else {
                        delay = mCountdownInterval - lastTickDuration;

                        // 特殊情况：onTick 花费的时间超过了间隔，跳到下一个间隔
                        while (delay < 0) delay += mCountdownInterval;
                    }

                    sendMessageDelayed(obtainMessage(MSG), delay);
                }
            }
        }
    };
}