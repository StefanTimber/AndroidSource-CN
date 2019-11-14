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
import android.util.Log;
import android.util.Printer;

import java.lang.reflect.Modifier;

/**
 * Handler 允许你发送和处理 与一个线程的 {@link MessageQueue} 关联的
 * {@link Message} 和 Runnable 类。  Each Handler
 * 每个 Handler 实例都与单个线程及其消息队列关联。
 * 当你创建一个新的 Handler 时，它将与创建它的线程/消息队列绑定————
 * 从那一刻起，它会把消息和 runnable 发送给消息队列并在他们被取出时执行。
 * 
 * Handler的两个主要用途： (1) 将消息和 runnables 安排在将来某个时间执行；
 * (2) 将操作切换到不同线程。
 * 
 * 我们通过
 * {@link #post}, {@link #postAtTime(Runnable, long)},
 * {@link #postDelayed}, {@link #sendEmptyMessage},
 * {@link #sendMessage}, {@link #sendMessageAtTime}, 和
 * {@link #sendMessageDelayed} 方法调度消息。  The  versions allow
 * <em>post</em> 版本的方法允许你发送消息队列调用的 Runnable 类。
 * <em>sendMessage</em> 版本的方法允许你发送包含一些数据的 {@link Message} 类，
 * 它将被 Handler的 {@link #handleMessage} 方法处理（需要实现子类）。

 * 
 * 但发送给一个 Handler 时，你可以让其在消息队列准备好时立刻被处理，
 * 或指定一个延迟或者是特定的处理时间。
 * 后两种方法允许您实现超时、计时和其他基于计时的行为。
 * 
 * 当应用程序的进程创建时，其主线程专用于运行消息队列，
 * 该队列负责管理顶级应用程序对象（活动、广播接收器等）及其创建的任何窗口。
 * 你可以创建自己的线程，并通过 Handler 与主线程通信。
 * 这是通过在子线程调用上述的  <em>post</em> 或 <em>sendMessage</em> 系列方法实现的，
 * 给定的 Runnalbe 或消息将会列入 Handler 的消息队列，并适时的被处理。
 */
public class Handler {
    /*
     * 此标志位设为 true 将会检测继承此 Handler 类的非静态匿名、本地或内部类。
     * 这样的类存在内存泄漏风险。
     */
    private static final boolean FIND_POTENTIAL_LEAKS = false;
    private static final String TAG = "Handler";
    private static Handler MAIN_THREAD_HANDLER = null;

    /**
     * 实例化 Handler 时可以使用此接口，而无需实现自己的 Handler 子类。
     */
    public interface Callback {
        /**
         * @param msg 一个 {@link android.os.Message Message} 对象
         * @return True 如果不需要进一步处理
         */
        public boolean handleMessage(Message msg);
    }
    
    /**
     * 子类必须实现它才能接收消息。
     */
    public void handleMessage(Message msg) {
    }
    
    /**
     * 在这里处理系统消息。
     */
    public void dispatchMessage(Message msg) {
        if (msg.callback != null) {
            handleCallback(msg);
        } else {
            if (mCallback != null) {
                if (mCallback.handleMessage(msg)) {
                    return;
                }
            }
            handleMessage(msg);
        }
    }

    /**
     * 默认构造方法，将 handler 与当前线程的 {@link Looper} 关联
     *
     * 如果此线程没有 looper, 那么 handler 将无法接收消息，抛出异常。
     */
    public Handler() {
        this(null, false);
    }

    /**
     * 构造方法，将 handler 与当前线程的 {@link Looper} 关联，
     * 并接受一个回调接口用于处理消息。
     *
     * 如果此线程没有 looper, 那么 handler 将无法接收消息，抛出异常。
     *
     * @param callback 用于处理消息的回调接口，或 null。
     */
    public Handler(Callback callback) {
        this(callback, false);
    }

    /**
     * 使用指定的 {@link Looper} 而非默认的。
     *
     * @param looper 指定的 looper，不能为 null。
     */
    public Handler(Looper looper) {
        this(looper, null, false);
    }

    /**
     * 使用指定的 {@link Looper} 而非默认的， 
     * 并接受一个回调接口用于处理消息。
     *
     * @param looper 指定的 looper，不能为 null。
     * @param callback 用于处理消息的回调接口，或 null。
     */
    public Handler(Looper looper, Callback callback) {
        this(looper, callback, false);
    }

    /**
     * 使用当前线程的 {@link Looper} 并设置 handler 是否应当异步。
     *
     * Handlers 默认是同步的，除非使用此构造方法使它严格异步。
     *
     * 异步消息表示不需要对同步消息进行全局排序的中断或事件。
     * 异步消息不受 {@link MessageQueue#enqueueSyncBarrier(long)} 引入的同步屏障的约束。
     *
     * @param async 如果为 true，handler 会对每个发送给它的
     * {@link Message} 或 {@link Runnable} 调用 {@link Message#setAsynchronous(boolean)}。
     *
     * @hide
     */
    public Handler(boolean async) {
        this(null, async);
    }

    /**
     * 使用当前线程的 {@link Looper} ，指定回调接口，
     * 并设定 handler 是否是异步的。
     *
     * Handlers 默认是同步的，除非使用此构造方法使它严格异步。
     *
     * 异步消息表示不需要对同步消息进行全局排序的中断或事件。
     * 异步消息不受 {@link MessageQueue#enqueueSyncBarrier(long)} 引入的同步屏障的约束。
     *
     * @param callback 处理消息的回调接口，或 null。
     * @param async 如果为 true，handler 会对每个发送给它的
     * {@link Message} 或 {@link Runnable} 调用 {@link Message#setAsynchronous(boolean)}。
     *
     * @hide
     */
    public Handler(Callback callback, boolean async) {
        if (FIND_POTENTIAL_LEAKS) {
            final Class<? extends Handler> klass = getClass();
            if ((klass.isAnonymousClass() || klass.isMemberClass() || klass.isLocalClass()) &&
                    (klass.getModifiers() & Modifier.STATIC) == 0) {
                Log.w(TAG, "The following Handler class should be static or leaks might occur: " +
                    klass.getCanonicalName());
            }
        }

        mLooper = Looper.myLooper();
        if (mLooper == null) {
            throw new RuntimeException(
                "Can't create handler inside thread " + Thread.currentThread()
                        + " that has not called Looper.prepare()");
        }
        mQueue = mLooper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }

    /**
     * 使用指定的 {@link Looper} 而非默认的， 并接受处理消息的回调接口，
     * 同时还设置 handler 是否是异步的。
     *
     * Handlers 默认是同步的，除非使用此构造方法使它严格异步。
     *
     * 异步消息表示不需要对同步消息进行全局排序的中断或事件。
     * 异步消息不受显示 vsync等条件引入的同步屏障的约束。
     *
     * @param looper 指定的 looper, 不能为 null.
     * @param callback 处理消息的回调接口，或 null.
     * @param async 如果为 true，handler 会对每个发送给它的
     * {@link Message} 或 {@link Runnable} 调用 {@link Message#setAsynchronous(boolean)}。
     *
     * @hide
     */
    public Handler(Looper looper, Callback callback, boolean async) {
        mLooper = looper;
        mQueue = looper.mQueue;
        mCallback = callback;
        mAsynchronous = async;
    }

    /**
     * 创建一个新的 Handler，其发布的消息和 runnables 不受同步屏障（如显示 vsync）的限制。
     *
     * <p>发送到异步 handler 的消息可以保证相互排序，但不一定与来自其他 handler 的消息排序。</p>
     *
     * @see #createAsync(Looper, Callback) 来创建一个使用自定义消息处理的异步 Handler。
     *
     * @param looper 新的 Handler 应当绑定的 Looper
     * @return 一个新的异步 Handler 实例
     */
    @NonNull
    public static Handler createAsync(@NonNull Looper looper) {
        if (looper == null) throw new NullPointerException("looper must not be null");
        return new Handler(looper, null, true);
    }

    /**
     * 创建一个新的 Handler，其发布的消息和 runnables 不受同步屏障（如显示 vsync）的限制。
     *
     * <p>发送到异步 handler 的消息可以保证相互排序，但不一定与来自其他 handler 的消息排序。</p>
     *
     * @see #createAsync(Looper) 来创建一个没有自定义消息处理的异步 Handler。
     *
     * @param looper 新的 Handler 应当绑定的 Looper
     * @return 一个新的异步 Handler 实例
     */
    @NonNull
    public static Handler createAsync(@NonNull Looper looper, @NonNull Callback callback) {
        if (looper == null) throw new NullPointerException("looper must not be null");
        if (callback == null) throw new NullPointerException("callback must not be null");
        return new Handler(looper, callback, true);
    }

    /** @hide */
    @NonNull
    public static Handler getMain() {
        if (MAIN_THREAD_HANDLER == null) {
            MAIN_THREAD_HANDLER = new Handler(Looper.getMainLooper());
        }
        return MAIN_THREAD_HANDLER;
    }

    /** @hide */
    @NonNull
    public static Handler mainIfNull(@Nullable Handler handler) {
        return handler == null ? getMain() : handler;
    }

    /** {@hide} */
    public String getTraceName(Message message) {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append(": ");
        if (message.callback != null) {
            sb.append(message.callback.getClass().getName());
        } else {
            sb.append("#").append(message.what);
        }
        return sb.toString();
    }

    /**
     * 返回表示指定消息名称的字符串。
     * 默认实现将返回消息回调的类名（如果有的话），或者返回消息“what”字段的十六进制表示形式。
     *  
     * @param message The message whose name is being queried 
     */
    public String getMessageName(Message message) {
        if (message.callback != null) {
            return message.callback.getClass().getName();
        }
        return "0x" + Integer.toHexString(message.what);
    }

    /**
     * 从全局消息池返回一个新的 {@link android.os.Message Message}。相比创建和分配实例更高效。
     * 取得的消息将它的 handler 设置为此实例 (Message.target == this).
     *  如果您不需要这样的特性，只需调用 Message.obtain() 即可。
     */
    public final Message obtainMessage()
    {
        return Message.obtain(this);
    }

    /**
     * 和 {@link #obtainMessage()} 相同，同时还设置了返回消息的 what 成员。
     * 
     * @param what 分配给 Message.what 字段的值。
     * @return 来自全局消息池的一个 Message。
     */
    public final Message obtainMessage(int what)
    {
        return Message.obtain(this, what);
    }
    
    /**
     * 
     * 和 {@link #obtainMessage()}相同，此外还设置了消息的 what 和 obj 成员。 
     * 
     * @param what 分配给返回的 Message.what 字段的值。
     * @param obj 分配给返回的 Message.obj 字段的值。
     * @return 来自全局消息池的一个 Message。
     */
    public final Message obtainMessage(int what, Object obj)
    {
        return Message.obtain(this, what, obj);
    }

    /**
     * 
     * 和 {@link #obtainMessage()}相同，此外还设置了消息的 what,arg1 和 arg2 成员。
     * @param what 分配给返回的 Message.what 字段的值。
     * @param arg1 分配给返回的 Message.arg1 字段的值。
     * @param arg2 分配给返回的 Message.arg2 字段的值。
     * @return 来自全局消息池的一个 Message。
     */
    public final Message obtainMessage(int what, int arg1, int arg2)
    {
        return Message.obtain(this, what, arg1, arg2);
    }
    
    /**
     * 
     * 和 {@link #obtainMessage()}相同，此外还设置了消息的 what,obj,arg1 和 arg2 的值。
     * @param what 分配给返回的 Message.what 字段的值。
     * @param arg1 分配给返回的 Message.arg1 字段的值。
     * @param arg2 分配给返回的 Message.arg2 字段的值。
     * @param obj 分配给返回的 Message.obj 字段的值。
     * @return 来自全局消息池的一个 Message。
     */
    public final Message obtainMessage(int what, int arg1, int arg2, Object obj)
    {
        return Message.obtain(this, what, arg1, arg2, obj);
    }

    /**
     * 使 Runnable r 添加到消息队列中。
     * 此 runnable 将运行在此 handler 附属的线程上。
     *  
     * @param r 将被执行的 Runnable。
     * 
     * @return 如果Runnable成功放入消息队列，则返回true。 
     *         在失败时返回false，通常是因为处理消息队列的 looper 正在退出。
     */
    public final boolean post(Runnable r)
    {
       return  sendMessageDelayed(getPostMessage(r), 0);
    }
    
    /**
     * 使 Runnable r 添加到消息队列中，并在<var>uptimeMillis</var>提供的特定时间运行。
     * <b>时基为 {@link android.os.SystemClock#uptimeMillis}.</b>
     * 在深度睡眠中花费的时间会增加执行的额外延迟。
     * 此 runnable 将运行在此 handler 附属的线程上。
     *
     * @param r 将被执行的 Runnable。
     * @param uptimeMillis 回调运行的绝对时间，
     *         使用 {@link android.os.SystemClock#uptimeMillis} 时基。
     *  
     * @return 如果 Runnable 成功加入了消息队列返回 true。
     *         失败时返回 false，通常是因为处理消息队列的 looper 正在退出。
     *         注意返回 true 并不意味着 Runnalbe 一定会执行————如果 looper 在
     *         消息传递的时间前退出，此消息将会被丢弃。
     */
    public final boolean postAtTime(Runnable r, long uptimeMillis)
    {
        return sendMessageAtTime(getPostMessage(r), uptimeMillis);
    }
    
    /**
     * 使 Runnable r 添加到消息队列中，并在<var>uptimeMillis</var>提供的特定时间运行。
     * <b>时基为 {@link android.os.SystemClock#uptimeMillis}.</b>
     * 在深度睡眠中花费的时间会增加执行的额外延迟。
     * 此 runnable 将运行在此 handler 附属的线程上。
     *
     * @param r 将被执行的 Runnable。
     * @param token 可用于通过 {@link #removeCallbacksAndMessages} 取消 {@code r} 的实例。
     * @param uptimeMillis 回调运行的绝对时间，
     *         使用 {@link android.os.SystemClock#uptimeMillis} 时基。
     * 
     * @return 如果 Runnable 成功加入了消息队列返回 true。
     *         失败时返回 false，通常是因为处理消息队列的 looper 正在退出。
     *         注意返回 true 并不意味着 Runnalbe 一定会执行————如果 looper 在
     *         消息传递的时间前退出，此消息将会被丢弃。
     *         
     * @see android.os.SystemClock#uptimeMillis
     */
    public final boolean postAtTime(Runnable r, Object token, long uptimeMillis)
    {
        return sendMessageAtTime(getPostMessage(r, token), uptimeMillis);
    }
    
    /**
     * 使 Runnable r 添加到消息队列中，并在指定的延迟时间后运行。 
     * 此 runnable 将运行在此 handler 附属的线程上。
     * <b>时基为 {@link android.os.SystemClock#uptimeMillis}.</b>
     * 在深度睡眠中花费的时间会增加执行的额外延迟。
     *  
     * @param r 将被执行的 Runnable。
     * @param delayMillis 执行 Runnable 之前的延迟（以毫秒为单位）。
     *        
     * @return 如果 Runnable 成功加入了消息队列返回 true。
     *         失败时返回 false，通常是因为处理消息队列的 looper 正在退出。
     *         注意返回 true 并不意味着 Runnalbe 一定会执行————如果 looper 在
     *         消息传递的时间前退出，此消息将会被丢弃。
     */
    public final boolean postDelayed(Runnable r, long delayMillis)
    {
        return sendMessageDelayed(getPostMessage(r), delayMillis);
    }
    
    /**
     * 使 Runnable r 添加到消息队列中，并在指定的延迟时间后运行。 
     * 此 runnable 将运行在此 handler 附属的线程上。
     * <b>时基为 {@link android.os.SystemClock#uptimeMillis}.</b>
     * 在深度睡眠中花费的时间会增加执行的额外延迟。
     *
     * @param r 将被执行的 Runnable。
     * @param token 可用于通过 {@link #removeCallbacksAndMessages} 取消 {@code r} 的实例。
     * @param delayMillis 执行 Runnable 之前的延迟（以毫秒为单位）。
     *
     * @return 如果 Runnable 成功加入了消息队列返回 true。
     *         失败时返回 false，通常是因为处理消息队列的 looper 正在退出。
     *         注意返回 true 并不意味着 Runnalbe 一定会执行————如果 looper 在
     *         消息传递的时间前退出，此消息将会被丢弃。
     */
    public final boolean postDelayed(Runnable r, Object token, long delayMillis)
    {
        return sendMessageDelayed(getPostMessage(r, token), delayMillis);
    }

    /**
     * 向实现 Runnable 的对象发送消息。
	 * 使 Runnable r 在消息队列的下一次循环中执行。
     * 此 runnable 将运行在此 handler 附属的线程上。
     * <b>此方法只在非常特殊的情况下使用————它很容易
     * starve消息队列、导致排序问题或产生其他意外的副作用。</b>
     *  
     * @param r 将被执行的 Runnable。
     * 
     * @return 如果 Runnable 成功加入了消息队列返回 true。
     *         失败时返回 false，通常是因为处理消息队列的 looper 正在退出。
     */
    public final boolean postAtFrontOfQueue(Runnable r)
    {
        return sendMessageAtFrontOfQueue(getPostMessage(r));
    }

    /**
     * 同步运行指定的任务。
     * <p>
     * 如果当前线程与 handler 线程相同，runnable将立即运行，无需加入队列。
     * 否则将 runnable 发送给 handler，并在返回之前等待它完成。
     * </p><p>
     * 此方法很危险！ 不当使用会导致死锁。
     * 不要在持有任何锁的情况下调用此方法，也不要以可重入的方式使用它。
     * </p><p>
     * 在后台线程必须同步等待在 handler 线程上运行的任务完成的情况下，此方法有时很有用。
     * 然而，这个问题往往是不良设计的表现。在采用这种方法之前，考虑改进设计（如果可能的话）。
     * </p><p>
     * 你也许要使用此方法的一个例子：
     * 当您刚刚创建了一个 handler 线程，并且在继续执行之前需要对其执行一些初始化步骤时。
     * </p><p>
     * 如果发生超时，则此方法返回<code>false</code>，但 runnable 将保留在 handler 上，
     * 并且可能已在运行中或稍后完成。
     * </p><p>
     * 使用此方法时，要退出 looper 请确保使用 {@link Looper#quitSafely}。
     * 否则 {@link #runWithScissors} 可能永久挂起。
     * (TODO: 我们应该通过让 MessageQueue 可感知阻塞 runnable 来解决这个问题。)
     * </p>
     *
     * @param r 将同步运行的 Runnable。
     * @param timeout 以毫秒为单位的超时，或0代表无限期等待。
     *
     * @return 如果 Runnable 成功执行返回 true。
     *         失败时返回 false， 通常是因为处理消息队列的 looper 正在退出。
     *
     * @hide 这个方法容易被滥用，可能不应该出现在API中。
     * 如果我们把它作为API的一部分，我们可能需要将它重命名为不那么有趣的名字，
     * 比如 runUnsafe().
     */
    public final boolean runWithScissors(final Runnable r, long timeout) {
        if (r == null) {
            throw new IllegalArgumentException("runnable must not be null");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }

        if (Looper.myLooper() == mLooper) {
            r.run();
            return true;
        }

        BlockingRunnable br = new BlockingRunnable(r);
        return br.postAndWait(this, timeout);
    }

    /**
     * 删除消息队列中任何等待的 Runnable r。
     */
    public final void removeCallbacks(Runnable r)
    {
        mQueue.removeMessages(this, r, null);
    }

    /**
     * 使用 token 删除消息队列中任何等待的 Runnable r。
     * 如果 token 为 null，所有 callback 将会被移除。
     */
    public final void removeCallbacks(Runnable r, Object token)
    {
        mQueue.removeMessages(this, r, token);
    }

    /**
     * 将消息推送到消息队列的末尾，位于当前时间之前的所有挂起消息的后面。
     * 它将在 handler 所在线程的 {@link #handleMessage} 中接收到。
     *  
     * @return 如果消息成功放入队列返回 true。
     *         失败时返回 false， 通常是因为处理消息队列的 looper 正在退出。
     */
    public final boolean sendMessage(Message msg)
    {
        return sendMessageDelayed(msg, 0);
    }

    /**
     * 发送一个只包含 what 值的消息。
     *  
     * @return 如果消息成功放入队列返回 true。
     *         失败时返回 false， 通常是因为处理消息队列的 looper 正在退出。
     */
    public final boolean sendEmptyMessage(int what)
    {
        return sendEmptyMessageDelayed(what, 0);
    }

    /**
     * 发送一个只包含 what 值的消息，将在特定时间延迟后发送。
     * @see #sendMessageDelayed(android.os.Message, long) 
     * 
     * @return 如果消息成功放入队列返回 true。
     *         失败时返回 false， 通常是因为处理消息队列的 looper 正在退出。
     */
    public final boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageDelayed(msg, delayMillis);
    }

    /**
     * 发送一个只包含 what 值的消息，将在特定时间发送。
     * @see #sendMessageAtTime(android.os.Message, long)
     *  
     * @return 如果消息成功放入队列返回 true。
     *         失败时返回 false， 通常是因为处理消息队列的 looper 正在退出。
     */

    public final boolean sendEmptyMessageAtTime(int what, long uptimeMillis) {
        Message msg = Message.obtain();
        msg.what = what;
        return sendMessageAtTime(msg, uptimeMillis);
    }

    /**
     * 将消息加入消息队列，位于所有(当前时间 + delayMillis)前的消息之后。
     * 将在 handler 线程的 {@link #handleMessage} 中收到。
     *  
     * @return 如果消息成功放入队列返回 true。
     *         失败时返回 false， 通常是因为处理消息队列的 looper 正在退出。
     *         注意返回 true 并不意味着消息一定会被处理————如果 looper 在
     *         消息传递的时间前退出，此消息将会被丢弃。
     */
    public final boolean sendMessageDelayed(Message msg, long delayMillis)
    {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        return sendMessageAtTime(msg, SystemClock.uptimeMillis() + delayMillis);
    }

    /**
     * 将消息放入消息队列，位于所有绝对时间（毫秒单位）<var>uptimeMillis</var>
     * 前的消息后面。
     * <b>时基为 {@link android.os.SystemClock#uptimeMillis}.</b>
     * 在深度睡眠中花费的时间会增加执行的额外延迟。
     * 将在 handler 线程的 {@link #handleMessage} 中收到。
     * 
     * @param uptimeMillis 消息发送的绝对时间，
     *         使用 {@link android.os.SystemClock#uptimeMillis} 时基。
     *         
     * @return 如果消息成功放入队列返回 true。
     *         失败时返回 false， 通常是因为处理消息队列的 looper 正在退出。
     *         注意返回 true 并不意味着消息一定会被处理————如果 looper 在
     *         消息传递的时间前退出，此消息将会被丢弃。
     */
    public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
        MessageQueue queue = mQueue;
        if (queue == null) {
            RuntimeException e = new RuntimeException(
                    this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
            return false;
        }
        return enqueueMessage(queue, msg, uptimeMillis);
    }

    /**
     * 将消息插入消息队列头部, 将会在下一次消息循环处理。
     * 你会在 handler 所在线程的 {@link #handleMessage} 中接收到。
     * <b>此方法只在非常特殊的情况下使用————它很容易
     * starve消息队列、导致排序问题或产生其他意外的副作用。</b>
     *  
     * @return 如果消息成功放入队列返回 true。
     *         失败时返回 false， 通常是因为处理消息队列的 looper 正在退出。
     */
    public final boolean sendMessageAtFrontOfQueue(Message msg) {
        MessageQueue queue = mQueue;
        if (queue == null) {
            RuntimeException e = new RuntimeException(
                this + " sendMessageAtTime() called with no mQueue");
            Log.w("Looper", e.getMessage(), e);
            return false;
        }
        return enqueueMessage(queue, msg, 0);
    }

    /**
     * 如果在 handler 对应的同一线程上调用，将同步执行该消息，
     * 否则 {@link #sendMessage} 将其推送给消息队列。
     *
     * @return 如果消息成功运行或成功放入队列返回 true。
     *         失败时返回 false， 通常是因为处理消息队列的 looper 正在退出。
     * @hide
     */
    public final boolean executeOrSendMessage(Message msg) {
        if (mLooper == Looper.myLooper()) {
            dispatchMessage(msg);
            return true;
        }
        return sendMessage(msg);
    }

    private boolean enqueueMessage(MessageQueue queue, Message msg, long uptimeMillis) {
        msg.target = this;
        if (mAsynchronous) {
            msg.setAsynchronous(true);
        }
        return queue.enqueueMessage(msg, uptimeMillis);
    }

    /**
     * 移除消息队列中任何对应 'what' 字段的消息。
     */
    public final void removeMessages(int what) {
        mQueue.removeMessages(this, what, null);
    }

    /**
     * 移除消息队列中任何对应的 'what' 和 'object' 字段的消息。
     * 如果 <var>object</var> 为 null，所有消息将被移除。
     */
    public final void removeMessages(int what, Object object) {
        mQueue.removeMessages(this, what, object);
    }

    /**
     * 移除消息队列中所有 <var>obj</var> 为 <var>token</var> 的回调和消息。
     * 如果 <var>object</var> 为 null，所有消息将被移除。
     */
    public final void removeCallbacksAndMessages(Object token) {
        mQueue.removeCallbacksAndMessages(this, token);
    }

    /**
     * 检查消息队列中是否有任何对应 'what' 的消息挂起。
     */
    public final boolean hasMessages(int what) {
        return mQueue.hasMessages(this, what, null);
    }

    /**
     * 返回此 handler 上当前是否计划了任何消息或回调。
     * @hide
     */
    public final boolean hasMessagesOrCallbacks() {
        return mQueue.hasMessages(this);
    }

    /**
     * 检查消息队列中是否有任何对应的 'what' 和 'object' 的消息。
     */
    public final boolean hasMessages(int what, Object object) {
        return mQueue.hasMessages(this, what, object);
    }

    /**
     * 检查消息队列中是否有任何 callback r 的消息。
     * 
     * @hide
     */
    public final boolean hasCallbacks(Runnable r) {
        return mQueue.hasMessages(this, r, null);
    }

    // 如果我们可以去掉这个方法，handler 就无须记住它的循环
    // 我们可以导出一个 getMessageQueue() 方法... 
    public final Looper getLooper() {
        return mLooper;
    }

    public final void dump(Printer pw, String prefix) {
        pw.println(prefix + this + " @ " + SystemClock.uptimeMillis());
        if (mLooper == null) {
            pw.println(prefix + "looper uninitialized");
        } else {
            mLooper.dump(pw, prefix + "  ");
        }
    }

    /**
     * @hide
     */
    public final void dumpMine(Printer pw, String prefix) {
        pw.println(prefix + this + " @ " + SystemClock.uptimeMillis());
        if (mLooper == null) {
            pw.println(prefix + "looper uninitialized");
        } else {
            mLooper.dump(pw, prefix + "  ", this);
        }
    }

    @Override
    public String toString() {
        return "Handler (" + getClass().getName() + ") {"
        + Integer.toHexString(System.identityHashCode(this))
        + "}";
    }

    final IMessenger getIMessenger() {
        synchronized (mQueue) {
            if (mMessenger != null) {
                return mMessenger;
            }
            mMessenger = new MessengerImpl();
            return mMessenger;
        }
    }

    private final class MessengerImpl extends IMessenger.Stub {
        public void send(Message msg) {
            msg.sendingUid = Binder.getCallingUid();
            Handler.this.sendMessage(msg);
        }
    }

    private static Message getPostMessage(Runnable r) {
        Message m = Message.obtain();
        m.callback = r;
        return m;
    }

    private static Message getPostMessage(Runnable r, Object token) {
        Message m = Message.obtain();
        m.obj = token;
        m.callback = r;
        return m;
    }

    private static void handleCallback(Message message) {
        message.callback.run();
    }

    final Looper mLooper;
    final MessageQueue mQueue;
    final Callback mCallback;
    final boolean mAsynchronous;
    IMessenger mMessenger;

    private static final class BlockingRunnable implements Runnable {
        private final Runnable mTask;
        private boolean mDone;

        public BlockingRunnable(Runnable task) {
            mTask = task;
        }

        @Override
        public void run() {
            try {
                mTask.run();
            } finally {
                synchronized (this) {
                    mDone = true;
                    notifyAll();
                }
            }
        }

        public boolean postAndWait(Handler handler, long timeout) {
            if (!handler.post(this)) {
                return false;
            }

            synchronized (this) {
                if (timeout > 0) {
                    final long expirationTime = SystemClock.uptimeMillis() + timeout;
                    while (!mDone) {
                        long delay = expirationTime - SystemClock.uptimeMillis();
                        if (delay <= 0) {
                            return false; // timeout
                        }
                        try {
                            wait(delay);
                        } catch (InterruptedException ex) {
                        }
                    }
                } else {
                    while (!mDone) {
                        try {
                            wait();
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
            return true;
        }
    }
}