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
import android.os.LooperProto;
import android.util.Log;
import android.util.Printer;
import android.util.Slog;
import android.util.proto.ProtoOutputStream;

/**
  * 用于为线程运行消息循环的类。
  * 默认情况下，线程没有与之关联的消息循环；若要创建一个消息循环，
  * 请在要运行该循环的线程中调用 {@link#prepare}，
  * 然后调用 {@link#loop} 使其处理消息，直到循环停止。
  *
  * 与消息循环的大多数交互是通过 {@link Handler} 类进行的。
  *
  * 以下是实现一个 Looper 线程的典型示例，
  * 通过 {@link #prepare} 和 {@link #loop} 的分离来创建一个
  * 初始的 Handler 与 Looper 通信。
  *
  * <pre>
  *  class LooperThread extends Thread {
  *      public Handler mHandler;
  *
  *      public void run() {
  *          Looper.prepare();
  *
  *          mHandler = new Handler() {
  *              public void handleMessage(Message msg) {
  *                  // process incoming messages here
  *              }
  *          };
  *
  *          Looper.loop();
  *      }
  *  }</pre>
  */
public final class Looper {
    /*
     * API 实现说明：
     *
     * 此类包含基于消息队列的创建和管理消息循环所需的代码。
     * 影响队列状态的 API 应当在消息队列或 Handler 中定义，而不是 Looper 自己。
     * 例如，空闲 handler 和同步屏障是在队列中定义的，
     * 而准备线程、循环和退出是在 looper 上定义的。
     */

    private static final String TAG = "Looper";

    // sThreadLocal.get() 将返回 null，除非你已经调用了 prepare().
    static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<Looper>();
    private static Looper sMainLooper;  // guarded by Looper.class

    final MessageQueue mQueue;
    final Thread mThread;

    private Printer mLogging;
    private long mTraceTag;

    /**
     * 若设置了此项，则如果消息分派花费的时间超过此值，则 looper 将显示警告日志。
     */
    private long mSlowDispatchThresholdMs;

    /**
     * 若设置了此项，如果消息传递的时间（实际传达时间-发送时间）超过此值，
     * looper 将显示警告日志。
     */
    private long mSlowDeliveryThresholdMs;

    /** 将当前线程初始化为 looper。
      * 这使您有机会在实际开始循环之前创建 handler并引用此 looper。
      * 请确保在调用此方法后调用 {@link #loop()}，并调用 {@link #quit()} 来结束。
      */
    public static void prepare() {
        prepare(true);
    }

    private static void prepare(boolean quitAllowed) {
        if (sThreadLocal.get() != null) {
            throw new RuntimeException("Only one Looper may be created per thread");
        }
        sThreadLocal.set(new Looper(quitAllowed));
    }

    /**
     * 将当前线程初始化为 looper，并标记为程序的 main looper。
     * 你的程序的 main looper 是由 Android 环境创建的，所以你不应自己调用此方法。
     * 参考：{@link #prepare()}
     */
    public static void prepareMainLooper() {
        prepare(false);
        synchronized (Looper.class) {
            if (sMainLooper != null) {
                throw new IllegalStateException("The main Looper has already been prepared.");
            }
            sMainLooper = myLooper();
        }
    }

    /**
     * 返回程序的 main looper，它存活于应用程序的主线程。
     */
    public static Looper getMainLooper() {
        synchronized (Looper.class) {
            return sMainLooper;
        }
    }

    /**
     * 在此线程中运行消息队列。 确保调用 {@link #quit()} 结束循环。
     */
    public static void loop() {
        final Looper me = myLooper();
        if (me == null) {
            throw new RuntimeException("No Looper; Looper.prepare() wasn't called on this thread.");
        }
        final MessageQueue queue = me.mQueue;

        // 确保此线程的标识是本地进程的标识，
        // 并保持跟踪真实的身份标识。
        Binder.clearCallingIdentity();
        final long ident = Binder.clearCallingIdentity();

        // 允许使用系统属性覆盖阈值。如：
        // adb shell 'setprop log.looper.1000.main.slow 1 && stop && start'
        final int thresholdOverride =
                SystemProperties.getInt("log.looper."
                        + Process.myUid() + "."
                        + Thread.currentThread().getName()
                        + ".slow", 0);

        boolean slowDeliveryDetected = false;

        for (;;) {
            Message msg = queue.next(); // 可能阻塞
            if (msg == null) {
                // 没有消息表明消息队列正在退出。
                return;
            }

            // 必须在局部变量中，以防 UI 事件设置 logger
            final Printer logging = me.mLogging;
            if (logging != null) {
                logging.println(">>>>> Dispatching to " + msg.target + " " +
                        msg.callback + ": " + msg.what);
            }

            final long traceTag = me.mTraceTag;
            long slowDispatchThresholdMs = me.mSlowDispatchThresholdMs;
            long slowDeliveryThresholdMs = me.mSlowDeliveryThresholdMs;
            if (thresholdOverride > 0) {
                slowDispatchThresholdMs = thresholdOverride;
                slowDeliveryThresholdMs = thresholdOverride;
            }
            final boolean logSlowDelivery = (slowDeliveryThresholdMs > 0) && (msg.when > 0);
            final boolean logSlowDispatch = (slowDispatchThresholdMs > 0);

            final boolean needStartTime = logSlowDelivery || logSlowDispatch;
            final boolean needEndTime = logSlowDispatch;

            if (traceTag != 0 && Trace.isTagEnabled(traceTag)) {
                Trace.traceBegin(traceTag, msg.target.getTraceName(msg));
            }

            final long dispatchStart = needStartTime ? SystemClock.uptimeMillis() : 0;
            final long dispatchEnd;
            try {
                msg.target.dispatchMessage(msg);
                dispatchEnd = needEndTime ? SystemClock.uptimeMillis() : 0;
            } finally {
                if (traceTag != 0) {
                    Trace.traceEnd(traceTag);
                }
            }
            if (logSlowDelivery) {
                if (slowDeliveryDetected) {
                    if ((dispatchStart - msg.when) <= 10) {
                        Slog.w(TAG, "Drained");
                        slowDeliveryDetected = false;
                    }
                } else {
                    if (showSlowLog(slowDeliveryThresholdMs, msg.when, dispatchStart, "delivery",
                            msg)) {
                        // 一旦打印了传递过慢的日志，在队列耗尽前忽略
                        slowDeliveryDetected = true;
                    }
                }
            }
            if (logSlowDispatch) {
                showSlowLog(slowDispatchThresholdMs, dispatchStart, dispatchEnd, "dispatch", msg);
            }

            if (logging != null) {
                logging.println("<<<<< Finished to " + msg.target + " " + msg.callback);
            }

            // 确保在分派的过程中线程的标识没有损坏。
            final long newIdent = Binder.clearCallingIdentity();
            if (ident != newIdent) {
                Log.wtf(TAG, "Thread identity changed from 0x"
                        + Long.toHexString(ident) + " to 0x"
                        + Long.toHexString(newIdent) + " while dispatching to "
                        + msg.target.getClass().getName() + " "
                        + msg.callback + " what=" + msg.what);
            }

            msg.recycleUnchecked();
        }
    }

    private static boolean showSlowLog(long threshold, long measureStart, long measureEnd,
            String what, Message msg) {
        final long actualTime = measureEnd - measureStart;
        if (actualTime < threshold) {
            return false;
        }
        // 对于慢速传递，当前消息并不十分重要，但无论如何记录下来。
        Slog.w(TAG, "Slow " + what + " took " + actualTime + "ms "
                + Thread.currentThread().getName() + " h="
                + msg.target.getClass().getName() + " c=" + msg.callback + " m=" + msg.what);
        return true;
    }

    /**
     * 返回与当前线程关联的 Looper 对象。
     * 返回 null 如果调用线程未与 Looper 关联。
     */
    public static @Nullable Looper myLooper() {
        return sThreadLocal.get();
    }

    /**
     * 返回与当前线程关联的 {@link MessageQueue} 对象。
     * 必须从运行 Looper 的线程调用，否则将抛出 NullPointerException。
     */
    public static @NonNull MessageQueue myQueue() {
        return myLooper().mQueue;
    }

    private Looper(boolean quitAllowed) {
        mQueue = new MessageQueue(quitAllowed);
        mThread = Thread.currentThread();
    }

    /**
     * 如果当前线程是此 looper 的线程，返回 true。
     */
    public boolean isCurrentThread() {
        return Thread.currentThread() == mThread;
    }

    /**
     * 控制由此 Looper 处理的消息的日志记录。
     * 如果启用，日志信息将在每个消息分派的开始和结束处写入 <var>printer</var>，
     * 标识目标 Handler 和消息内容。
     *
     * @param printer 接收 log 信息的 Printer 对象，或传 null 禁用日志记录。
     */
    public void setMessageLogging(@Nullable Printer printer) {
        mLogging = printer;
    }

    /** {@hide} */
    public void setTraceTag(long traceTag) {
        mTraceTag = traceTag;
    }

    /**
     * 为慢速分派/传递日志设置阈值。
     * {@hide}
     */
    public void setSlowLogThresholdMs(long slowDispatchThresholdMs, long slowDeliveryThresholdMs) {
        mSlowDispatchThresholdMs = slowDispatchThresholdMs;
        mSlowDeliveryThresholdMs = slowDeliveryThresholdMs;
    }

    /**
     * 退出 looper。
     * 
     * 导致 {@link #loop} 方法终止而不再处理消息队列中的任何消息。 
     * 
     * 在请求 looper 退出后，向队列发送消息的任何尝试都将失败。
     * 例如，{@link Handler#sendMessage(Message)} 方法将返回 false。
     * 
	 * 注意：
     * 使用此方法可能不安全，因为在循环程序终止之前可能无法传递某些消息。
     * 考虑使用 {@link #quitSafely} 代替，以确保以有序的方式完成所有挂起的工作。
     *
     * @see #quitSafely
     */
    public void quit() {
        mQueue.quit(false);
    }

    /**
     * 安全退出 looper。
     * 
     * 使 {@link#loop} 方法在处理完消息队列中已应传递的所有剩余消息后立即终止。
     * 但是在循环终止之前，将不会传递具有未来到期时间的挂起延迟消息。
     * 
     * 在请求 looper 退出后，向队列发送消息的任何尝试都将失败。
     * 例如，{@link Handler#sendMessage(Message)} 方法将返回 false。
     */
    public void quitSafely() {
        mQueue.quit(true);
    }

    /**
     * 获取与此 Looper 关联的线程。
     *
     * @return looper 的线程。
     */
    public @NonNull Thread getThread() {
        return mThread;
    }

    /**
     * 获取此 looper 的消息队列。
     *
     * @return looper 的消息队列。
     */
    public @NonNull MessageQueue getQueue() {
        return mQueue;
    }

    /**
     * 转存 looper 的状态信息以便调试。
     *
     * @param pw 接收转储内容的 printer。
     * @param prefix 打印的每一行前添加的前缀。
     */
    public void dump(@NonNull Printer pw, @NonNull String prefix) {
        pw.println(prefix + toString());
        mQueue.dump(pw, prefix + "  ", null);
    }

    /**
     * 转存 looper 的状态信息以便调试。
     *
     * @param pw 接收转储内容的 printer。
     * @param prefix 打印的每一行前添加的前缀。
     * @param handler 仅转储此 Handler 的消息。
     * @hide
     */
    public void dump(@NonNull Printer pw, @NonNull String prefix, Handler handler) {
        pw.println(prefix + toString());
        mQueue.dump(pw, prefix + "  ", handler);
    }

    /** @hide */
    public void writeToProto(ProtoOutputStream proto, long fieldId) {
        final long looperToken = proto.start(fieldId);
        proto.write(LooperProto.THREAD_NAME, mThread.getName());
        proto.write(LooperProto.THREAD_ID, mThread.getId());
        mQueue.writeToProto(proto, LooperProto.QUEUE);
        proto.end(looperToken);
    }

    @Override
    public String toString() {
        return "Looper (" + mThread.getName() + ", tid " + mThread.getId()
                + ") {" + Integer.toHexString(System.identityHashCode(this)) + "}";
    }
}