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

package android.app;

import android.annotation.WorkerThread;
import android.annotation.Nullable;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

/**
 * IntentService 是按需处理异步请求(以Intent表示)的 {@link Service}s 的基类。
 * 客户端通过调用 {@link android.content.Context#startService(Intent)} 发送请求；
 * 服务将会根据需要启动，使用工作线程按顺序处理每个Intent，并在完成后关闭自己。
 *
 * 这种“任务队列处理器”模式常用于减轻应用主线程的任务。
 * IntentService 类的存在是为了简化这种模式并处理好代码结构。
 * 要使用它，继承 IntentService 并实现 {@link #onHandleIntent(Intent)} 方法。
 * IntentService 会接收Intent，启动一个工作线程，并适时的关闭服务。

 *
 * 所有的请求都在单独的工作线程上处理————他们也许需要尽可能长的时间
 * （不会阻塞主线程）, 但一次只处理一个请求。
 *
 * 注意：
 * IntentService 受 Android 8.0 (API level 26) 增加的后台运行限制支配
 * <a href="/preview/features/background.html">
 * 大多数情况下，更好的选择是使用 {@link android.support.v4.app.JobIntentService}，
 * 它在Android 8.0或更高版本上使用jobs替代服务。

 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>有关如何创建服务的更详细的讨论，请查阅开发者指南
 * <a href="{@docRoot}guide/components/services.html">Services</a></p>
 * </div>
 *
 * @see android.support.v4.app.JobIntentService
 * @see android.os.AsyncTask
 */
public abstract class IntentService extends Service {
    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;
    private String mName;
    private boolean mRedelivery;

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            onHandleIntent((Intent)msg.obj);
            stopSelf(msg.arg1);
        }
    }

    /**
     * 创建一个IntentService，由子类的构造方法调用。
     *
     * @param name 用于命名工作线程，仅对调试重要。
     */
    public IntentService(String name) {
        super();
        mName = name;
    }

    /**
     * 设置Intent是否重新传递。通常由构造方法调用。
     * Usually called from the constructor with your preferred semantics.
     *
     * <p>如果enabled为true,
     * {@link #onStartCommand(Intent, int, int)} 将返回
     * {@link Service#START_REDELIVER_INTENT}, 所以如果进程在
     * {@link #onHandleIntent(Intent)} 返回前被杀死，进程将会重新启动并重新传递intent。
     * 如果发送了多个Intent，只有最近的会被重新传递。
     *
     * <p>如果enabled为false（默认）,
     * {@link #onStartCommand(Intent, int, int)} 将返回
     * {@link Service#START_NOT_STICKY}，如果进程死亡，Intent也将随之消亡。
     */
    public void setIntentRedelivery(boolean enabled) {
        mRedelivery = enabled;
    }

    @Override
    public void onCreate() {
        // TODO: todo：最好有一个选项，在处理过程中保持部分wakelock，
        // 并有一个静态 startservice（context，intent）方法来启动服务并移除wakelock。

        super.onCreate();
        HandlerThread thread = new HandlerThread("IntentService[" + mName + "]");
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public void onStart(@Nullable Intent intent, int startId) {
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
    }

    /**
     * 在您的IntentService中不应当重写此方法，而应该
     * 重写 {@link #onHandleIntent} 方法，当接收到一个启动请求时系统会调用它。
     * @see android.app.Service#onStartCommand
     */
    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return mRedelivery ? START_REDELIVER_INTENT : START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mServiceLooper.quit();
    }

    /**
     * 除非为服务提供绑定，否则不需要实现此方法，因为默认实现返回null。
     * @see android.app.Service#onBind
     */
    @Override
    @Nullable
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 此方法在有要处理的请求的工作线程上调用。
     * 一次只处理一个Intent，但处理发生在独立于其他程序逻辑的工作线程上。
     * 所以如果这段代码需要很长时间，它将阻塞发给同一个IntentService的其他请求，
     * 但不会再有其他影响。
     * 当所有请求都处理完毕，IntentService会关闭自己，因此您不应调用 {@link #stopSelf}.
     *
     * @param intent 传递给 {@link android.content.Context#startService(Intent)} 的值。
     *               如果服务是在其进程结束后重新启动，则此值可能为空
     *               详情查阅 {@link android.app.Service#onStartCommand}
     */
    @WorkerThread
    protected abstract void onHandleIntent(@Nullable Intent intent);
}