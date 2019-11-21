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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.MessageQueueProto;
import android.util.Log;
import android.util.Printer;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import java.io.FileDescriptor;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * 持有要由 {@link Looper} 发送的消息列表的下层类。 
 * 消息不是直接添加到消息队列中的，而是通过与 Looper 相关联的
 * {@link Handler} 对象。
 *
 * 你可以使用 {@link Looper#myQueue() Looper.myQueue()} 来获取
 * 当前线程的消息队列。
 */
public final class MessageQueue {
    private static final String TAG = "MessageQueue";
    private static final boolean DEBUG = false;

    // 如果消息队列可以退出，为 True。
    private final boolean mQuitAllowed;

    @SuppressWarnings("unused")
    private long mPtr; // 由 native 代码使用

    Message mMessages;
    private final ArrayList<IdleHandler> mIdleHandlers = new ArrayList<IdleHandler>();
    private SparseArray<FileDescriptorRecord> mFileDescriptorRecords;
    private IdleHandler[] mPendingIdleHandlers;
    private boolean mQuitting;

    // 表示 next() 以非零超时阻塞，等待 pollOnce()。
    private boolean mBlocked;

    // 下一个屏障 token.
    // 屏障是以 target 为空，arg1 字段携带 token 的消息表示。
    private int mNextBarrierToken;

    private native static long nativeInit();
    private native static void nativeDestroy(long ptr);
    private native void nativePollOnce(long ptr, int timeoutMillis); /*非静态回调*/
    private native static void nativeWake(long ptr);
    private native static boolean nativeIsPolling(long ptr);
    private native static void nativeSetFileDescriptorEvents(long ptr, int fd, int events);

    MessageQueue(boolean quitAllowed) {
        mQuitAllowed = quitAllowed;
        mPtr = nativeInit();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            dispose();
        } finally {
            super.finalize();
        }
    }

    // 销毁底层消息队列。
    // 只能在 looper 线程或 finalizer 上调用。
    private void dispose() {
        if (mPtr != 0) {
            nativeDestroy(mPtr);
            mPtr = 0;
        }
    }

    /**
     * 如果 looper 没有需要处理的挂起消息，返回 true。
     *
     * 从任何线程调用此方法都是安全的。
     *
     * @return True 如果 looper 空闲。
     */
    public boolean isIdle() {
        synchronized (this) {
            final long now = SystemClock.uptimeMillis();
            return mMessages == null || now < mMessages.when;
        }
    }

    /**
     * 向消息队列添加一个新的 {@link IdleHandler}。 
     * 可以通过 {@link IdleHandler#queueIdle IdleHandler.queueIdle()}
     * 返回 false 使其在调用后自动移除，或使用 {@link #removeIdleHandler}
     * 直接移除。
     *
     * 从任何线程调用此方法都是安全的。
     *
     * @param handler 要添加的 IdleHandler。
     */
    public void addIdleHandler(@NonNull IdleHandler handler) {
        if (handler == null) {
            throw new NullPointerException("Can't add a null IdleHandler");
        }
        synchronized (this) {
            mIdleHandlers.add(handler);
        }
    }

    /**
     * 从队列移除之前使用 {@link #addIdleHandler} 添加的 {@link IdleHandler}。
     * 如果给定的对象不在空闲列表中，不执行任何操作。
     *
     * 从任何线程调用此方法都是安全的。
     *
     * @param handler 要移除的 IdleHandler。
     */
    public void removeIdleHandler(@NonNull IdleHandler handler) {
        synchronized (this) {
            mIdleHandlers.remove(handler);
        }
    }

    /**
     * 返回此 looper 的线程当前是否正在轮询要做的更多工作。
     * 这是一个很好的信号，表明循环仍然是活动的，而不是在处理回调时被卡住。
     * 注意，这个方法本质上是racy的，因为循环的状态可以在得到结果之前改变。
     *
     * 从任何线程调用此方法都是安全的。
     *
     * @return True 如果 looper 当前正在轮询事件。
     * @hide
     */
    public boolean isPolling() {
        synchronized (this) {
            return isPollingLocked();
        }
    }

    private boolean isPollingLocked() {
        // 如果循环正在退出，则它不可能处于空闲状态。
        // 当 mQuitting 为 false，我们可以认为 mPtr != 0。
        return !mQuitting && nativeIsPolling(mPtr);
    }

    /**
     * 添加文件描述符监听器，以在文件描述符相关的事件出现时收到通知。
     * 
     * 如果文件描述符已经注册，则将替换掉任何之前与其关联的事件和侦听器。
     * 不能为每个文件描述符设置多个侦听器。
     * 
     * 当不再使用文件描述符时，务必注销监听器。
     * 
     *
     * @param fd 要为其注册监听器的文件描述符。
     * @param events 要接收的事件集合：
     * {@link OnFileDescriptorEventListener#EVENT_INPUT},
     * {@link OnFileDescriptorEventListener#EVENT_OUTPUT}, 和
     * {@link OnFileDescriptorEventListener#EVENT_ERROR} 事件掩码的组合。
     * 如果请求的事件集为零，则监听器将被注销。
     * @param listener 发生文件描述符事件时要调用的监听器。
     *
     * @see OnFileDescriptorEventListener
     * @see #removeOnFileDescriptorEventListener
     */
    public void addOnFileDescriptorEventListener(@NonNull FileDescriptor fd,
            @OnFileDescriptorEventListener.Events int events,
            @NonNull OnFileDescriptorEventListener listener) {
        if (fd == null) {
            throw new IllegalArgumentException("fd must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        synchronized (this) {
            updateOnFileDescriptorEventListenerLocked(fd, events, listener);
        }
    }

    /**
     * 移除文件描述符监听器。
     * 
     * 如果没有为指定的文件描述符注册监听器，则此方法不会执行任何操作。
     * 
     *
     * @param fd 将注销其监听器的文件描述符。
     *
     * @see OnFileDescriptorEventListener
     * @see #addOnFileDescriptorEventListener
     */
    public void removeOnFileDescriptorEventListener(@NonNull FileDescriptor fd) {
        if (fd == null) {
            throw new IllegalArgumentException("fd must not be null");
        }

        synchronized (this) {
            updateOnFileDescriptorEventListenerLocked(fd, 0, null);
        }
    }

    private void updateOnFileDescriptorEventListenerLocked(FileDescriptor fd, int events,
            OnFileDescriptorEventListener listener) {
        final int fdNum = fd.getInt$();

        int index = -1;
        FileDescriptorRecord record = null;
        if (mFileDescriptorRecords != null) {
            index = mFileDescriptorRecords.indexOfKey(fdNum);
            if (index >= 0) {
                record = mFileDescriptorRecords.valueAt(index);
                if (record != null && record.mEvents == events) {
                    return;
                }
            }
        }

        if (events != 0) {
            events |= OnFileDescriptorEventListener.EVENT_ERROR;
            if (record == null) {
                if (mFileDescriptorRecords == null) {
                    mFileDescriptorRecords = new SparseArray<FileDescriptorRecord>();
                }
                record = new FileDescriptorRecord(fd, events, listener);
                mFileDescriptorRecords.put(fdNum, record);
            } else {
                record.mListener = listener;
                record.mEvents = events;
                record.mSeq += 1;
            }
            nativeSetFileDescriptorEvents(mPtr, fdNum, events);
        } else if (record != null) {
            record.mEvents = 0;
            mFileDescriptorRecords.removeAt(index);
            nativeSetFileDescriptorEvents(mPtr, fdNum, 0);
        }
    }

    // 从 native 代码调用。
    private int dispatchEvents(int fd, int events) {
        // 获取文件描述符记录和任何可能更改的状态。
        final FileDescriptorRecord record;
        final int oldWatchedEvents;
        final OnFileDescriptorEventListener listener;
        final int seq;
        synchronized (this) {
            record = mFileDescriptorRecords.get(fd);
            if (record == null) {
                return 0; // 有误，未注册监听器
            }

            oldWatchedEvents = record.mEvents;
            events &= oldWatchedEvents; // 基于当前监视集筛选事件
            if (events == 0) {
                return oldWatchedEvents; // 有误，监听的事件改变了
            }

            listener = record.mListener;
            seq = record.mSeq;
        }

        // 在锁外调用监听器。
        int newWatchedEvents = listener.onFileDescriptorEvents(
                record.mDescriptor, events);
        if (newWatchedEvents != 0) {
            newWatchedEvents |= OnFileDescriptorEventListener.EVENT_ERROR;
        }

        // 如果监听器更改了要监视的事件集，并且监听器此后未被更新，
        // 则更新文件描述符记录。
        if (newWatchedEvents != oldWatchedEvents) {
            synchronized (this) {
                int index = mFileDescriptorRecords.indexOfKey(fd);
                if (index >= 0 && mFileDescriptorRecords.valueAt(index) == record
                        && record.mSeq == seq) {
                    record.mEvents = newWatchedEvents;
                    if (newWatchedEvents == 0) {
                        mFileDescriptorRecords.removeAt(index);
                    }
                }
            }
        }

        // 返回要监听以供 native 代码处理的新的事件集。
        return newWatchedEvents;
    }

    Message next() {
        // 如果消息循环已经退出并被释放，则在此处返回。
        // 如果应用程序尝试在 Looper 退出后重新启动它（这是不支持的），
        // 有可能发生这样的情况。
        final long ptr = mPtr;
        if (ptr == 0) {
            return null;
        }

        int pendingIdleHandlerCount = -1; // 仅在首次迭代时为-1
        int nextPollTimeoutMillis = 0;
        for (;;) {
            if (nextPollTimeoutMillis != 0) {
                Binder.flushPendingCommands();
            }

            nativePollOnce(ptr, nextPollTimeoutMillis);

            synchronized (this) {
                // 尝试或去下一个消息。若有则返回它。
                final long now = SystemClock.uptimeMillis();
                Message prevMsg = null;
                Message msg = mMessages;
                if (msg != null && msg.target == null) {
                    // 被屏障阻拦。在队列中查找下一个异步消息。
                    do {
                        prevMsg = msg;
                        msg = msg.next;
                    } while (msg != null && !msg.isAsynchronous());
                }
                if (msg != null) {
                    if (now < msg.when) {
                        // 下条消息还未就绪。设置一个超时在它就绪时唤醒。
                        nextPollTimeoutMillis = (int) Math.min(msg.when - now, Integer.MAX_VALUE);
                    } else {
                        // 得到一条消息。
                        mBlocked = false;
                        if (prevMsg != null) {
                            prevMsg.next = msg.next;
                        } else {
                            mMessages = msg.next;
                        }
                        msg.next = null;
                        if (DEBUG) Log.v(TAG, "Returning message: " + msg);
                        msg.markInUse();
                        return msg;
                    }
                } else {
                    // 没有更多消息了。
                    nextPollTimeoutMillis = -1;
                }

                // 由于所有挂起的消息都已处理，处理退出消息。
                if (mQuitting) {
                    dispose();
                    return null;
                }

                // 如果是第一次空闲下来，拿到要运行的 idlers 的数量。
                // Idle handles 只会在队列为空，或队列中的第一条消息（可能是一个屏障）将在
                // 未来处理时，才会运行。
                if (pendingIdleHandlerCount < 0
                        && (mMessages == null || now < mMessages.when)) {
                    pendingIdleHandlerCount = mIdleHandlers.size();
                }
                if (pendingIdleHandlerCount <= 0) {
                    // 没有要运行的 idle handlers。循环等待更多。
                    mBlocked = true;
                    continue;
                }

                if (mPendingIdleHandlers == null) {
                    mPendingIdleHandlers = new IdleHandler[Math.max(pendingIdleHandlerCount, 4)];
                }
                mPendingIdleHandlers = mIdleHandlers.toArray(mPendingIdleHandlers);
            }

            // 运行这些 idle handlers.
            // 我们只会在第一次迭代时到达这个代码块。
            for (int i = 0; i < pendingIdleHandlerCount; i++) {
                final IdleHandler idler = mPendingIdleHandlers[i];
                mPendingIdleHandlers[i] = null; // 释放对 handler 的引用

                boolean keep = false;
                try {
                    keep = idler.queueIdle();
                } catch (Throwable t) {
                    Log.wtf(TAG, "IdleHandler threw exception", t);
                }

                if (!keep) {
                    synchronized (this) {
                        mIdleHandlers.remove(idler);
                    }
                }
            }

            // 重置 idle handler 的计数为0，这样我们不会再运行它们。
            pendingIdleHandlerCount = 0;

            // 当调用 idle handler 时， 新的消息可能已经传来
            // 因此不必等待而回去查找挂起的信息。
            nextPollTimeoutMillis = 0;
        }
    }

    void quit(boolean safe) {
        if (!mQuitAllowed) {
            throw new IllegalStateException("Main thread not allowed to quit.");
        }

        synchronized (this) {
            if (mQuitting) {
                return;
            }
            mQuitting = true;

            if (safe) {
                removeAllFutureMessagesLocked();
            } else {
                removeAllMessagesLocked();
            }

            // 我们可以认为 mPtr != 0 因为 mQuitting 之前为 false。
            nativeWake(mPtr);
        }
    }

    /**
     * 向 Looper 的消息队列发送一个同步屏障。
     *
     * 消息处理照常进行，直到消息队列遇到已发布的同步屏障。
     * 遇到屏障时，队列中稍后的同步消息将暂停（阻止执行），
     * 直到通过调用 {@link #removeSyncBarrier} 并指定标识同步屏障的 token 来释放屏障。
     *
     * 此方法用于立即推迟所有随后发布的同步消息的执行，直到满足释放屏障的条件。
     * synchronous messages until a condition is met that releases the barrier.
     * 异步消息（参考 {@link Message#isAsynchronous}）不受屏障影响，继续照常处理。
     *
     * 此调用必须始终与使用相同 token的 {@link #removeSyncBarrier} 匹配，
     * 以确保消息队列恢复正常操作。
     * 否则应用程序可能会挂起！
     *
     * @return 一个能唯一标识屏障的 token。必须将此 token 传递给
     * {@link #removeSyncBarrier} 来释放屏障。
     *
     * @hide
     */
    public int postSyncBarrier() {
        return postSyncBarrier(SystemClock.uptimeMillis());
    }

    private int postSyncBarrier(long when) {
        // 将一个新的同步屏障 token 入队。
        // 我们不需要唤醒队列，因为屏障的目的是阻拦它。
        synchronized (this) {
            final int token = mNextBarrierToken++;
            final Message msg = Message.obtain();
            msg.markInUse();
            msg.when = when;
            msg.arg1 = token;

            Message prev = null;
            Message p = mMessages;
            if (when != 0) {
                while (p != null && p.when <= when) {
                    prev = p;
                    p = p.next;
                }
            }
            if (prev != null) { // invariant: p == prev.next
                msg.next = p;
                prev.next = msg;
            } else {
                msg.next = p;
                mMessages = msg;
            }
            return token;
        }
    }

    /**
     * 移除同步屏障。
     *
     * @param token {@link #postSyncBarrier} 返回的同步屏障 token。
     *
     * @throws IllegalStateException 如果未找到屏障。
     *
     * @hide
     */
    public void removeSyncBarrier(int token) {
        // 从队列移除一个同步屏障 token。
        // 如果队列不再被屏障阻塞，唤醒它。
        synchronized (this) {
            Message prev = null;
            Message p = mMessages;
            while (p != null && (p.target != null || p.arg1 != token)) {
                prev = p;
                p = p.next;
            }
            if (p == null) {
                throw new IllegalStateException("The specified message queue synchronization "
                        + " barrier token has not been posted or has already been removed.");
            }
            final boolean needWake;
            if (prev != null) {
                prev.next = p.next;
                needWake = false;
            } else {
                mMessages = p.next;
                needWake = mMessages == null || mMessages.target != null;
            }
            p.recycleUnchecked();

            // 如果循环正在退出，则它已经处于唤醒状态。
            // 当 mQuitting 为 false，我们可以认为 mPtr != 0 。
            if (needWake && !mQuitting) {
                nativeWake(mPtr);
            }
        }
    }

    boolean enqueueMessage(Message msg, long when) {
        if (msg.target == null) {
            throw new IllegalArgumentException("Message must have a target.");
        }
        if (msg.isInUse()) {
            throw new IllegalStateException(msg + " This message is already in use.");
        }

        synchronized (this) {
            if (mQuitting) {
                IllegalStateException e = new IllegalStateException(
                        msg.target + " sending message to a Handler on a dead thread");
                Log.w(TAG, e.getMessage(), e);
                msg.recycle();
                return false;
            }

            msg.markInUse();
            msg.when = when;
            Message p = mMessages;
            boolean needWake;
            if (p == null || when == 0 || when < p.when) {
                // 新的链表头，如果阻塞唤醒队列。
                msg.next = p;
                mMessages = msg;
                needWake = mBlocked;
            } else {
                // 插入到队列中。通常我们无需唤醒队列
                // 除非队列头部有个屏障，并且消息是队列中最早的异步消息。
                needWake = mBlocked && p.target == null && msg.isAsynchronous();
                Message prev;
                for (;;) {
                    prev = p;
                    p = p.next;
                    if (p == null || when < p.when) {
                        break;
                    }
                    if (needWake && p.isAsynchronous()) {
                        needWake = false;
                    }
                }
                msg.next = p; // invariant: p == prev.next
                prev.next = msg;
            }

            // 我们可以认为 mPtr != 0 因为 mQuitting 为 false。
            if (needWake) {
                nativeWake(mPtr);
            }
        }
        return true;
    }

    boolean hasMessages(Handler h, int what, Object object) {
        if (h == null) {
            return false;
        }

        synchronized (this) {
            Message p = mMessages;
            while (p != null) {
                if (p.target == h && p.what == what && (object == null || p.obj == object)) {
                    return true;
                }
                p = p.next;
            }
            return false;
        }
    }

    boolean hasMessages(Handler h, Runnable r, Object object) {
        if (h == null) {
            return false;
        }

        synchronized (this) {
            Message p = mMessages;
            while (p != null) {
                if (p.target == h && p.callback == r && (object == null || p.obj == object)) {
                    return true;
                }
                p = p.next;
            }
            return false;
        }
    }

    boolean hasMessages(Handler h) {
        if (h == null) {
            return false;
        }

        synchronized (this) {
            Message p = mMessages;
            while (p != null) {
                if (p.target == h) {
                    return true;
                }
                p = p.next;
            }
            return false;
        }
    }

    void removeMessages(Handler h, int what, Object object) {
        if (h == null) {
            return;
        }

        synchronized (this) {
            Message p = mMessages;

            // 移除所有在前面的消息
            while (p != null && p.target == h && p.what == what
                   && (object == null || p.obj == object)) {
                Message n = p.next;
                mMessages = n;
                p.recycleUnchecked();
                p = n;
            }

            // 移除所有后面的消息
            while (p != null) {
                Message n = p.next;
                if (n != null) {
                    if (n.target == h && n.what == what
                        && (object == null || n.obj == object)) {
                        Message nn = n.next;
                        n.recycleUnchecked();
                        p.next = nn;
                        continue;
                    }
                }
                p = n;
            }
        }
    }

    void removeMessages(Handler h, Runnable r, Object object) {
        if (h == null || r == null) {
            return;
        }

        synchronized (this) {
            Message p = mMessages;

            // 移除所有在前面的消息
            while (p != null && p.target == h && p.callback == r
                   && (object == null || p.obj == object)) {
                Message n = p.next;
                mMessages = n;
                p.recycleUnchecked();
                p = n;
            }

            // 移除所有后面的消息
            while (p != null) {
                Message n = p.next;
                if (n != null) {
                    if (n.target == h && n.callback == r
                        && (object == null || n.obj == object)) {
                        Message nn = n.next;
                        n.recycleUnchecked();
                        p.next = nn;
                        continue;
                    }
                }
                p = n;
            }
        }
    }

    void removeCallbacksAndMessages(Handler h, Object object) {
        if (h == null) {
            return;
        }

        synchronized (this) {
            Message p = mMessages;

            // 移除所有在前面的消息
            while (p != null && p.target == h
                    && (object == null || p.obj == object)) {
                Message n = p.next;
                mMessages = n;
                p.recycleUnchecked();
                p = n;
            }

            // 移除所有后面的消息
            while (p != null) {
                Message n = p.next;
                if (n != null) {
                    if (n.target == h && (object == null || n.obj == object)) {
                        Message nn = n.next;
                        n.recycleUnchecked();
                        p.next = nn;
                        continue;
                    }
                }
                p = n;
            }
        }
    }

    private void removeAllMessagesLocked() {
        Message p = mMessages;
        while (p != null) {
            Message n = p.next;
            p.recycleUnchecked();
            p = n;
        }
        mMessages = null;
    }

    private void removeAllFutureMessagesLocked() {
        final long now = SystemClock.uptimeMillis();
        Message p = mMessages;
        if (p != null) {
            if (p.when > now) {
                removeAllMessagesLocked();
            } else {
                Message n;
                for (;;) {
                    n = p.next;
                    if (n == null) {
                        return;
                    }
                    if (n.when > now) {
                        break;
                    }
                    p = n;
                }
                p.next = null;
                do {
                    p = n;
                    n = p.next;
                    p.recycleUnchecked();
                } while (n != null);
            }
        }
    }

    void dump(Printer pw, String prefix, Handler h) {
        synchronized (this) {
            long now = SystemClock.uptimeMillis();
            int n = 0;
            for (Message msg = mMessages; msg != null; msg = msg.next) {
                if (h == null || h == msg.target) {
                    pw.println(prefix + "Message " + n + ": " + msg.toString(now));
                }
                n++;
            }
            pw.println(prefix + "(Total messages: " + n + ", polling=" + isPollingLocked()
                    + ", quitting=" + mQuitting + ")");
        }
    }

    void writeToProto(ProtoOutputStream proto, long fieldId) {
        final long messageQueueToken = proto.start(fieldId);
        synchronized (this) {
            for (Message msg = mMessages; msg != null; msg = msg.next) {
                msg.writeToProto(proto, MessageQueueProto.MESSAGES);
            }
            proto.write(MessageQueueProto.IS_POLLING_LOCKED, isPollingLocked());
            proto.write(MessageQueueProto.IS_QUITTING, mQuitting);
        }
        proto.end(messageQueueToken);
    }

    /**
     * 当线程将阻塞等待更多消息时触发的回调接口。
     */
    public static interface IdleHandler {
        /**
         * 当消息队列中消息用完并等待更多消息时会调用。
         * 返回 true 以保持 idle handler 处于活跃状态，false 将其移除。
         * 有可能会在队列中仍有挂起的消息时调用，但这些消息都计划在
         * 当前时间之后发送。
         */
        boolean queueIdle();
    }

    /**
     * 当发生与文件描述符相关的事件时调用的监听器。
     */
    public interface OnFileDescriptorEventListener {
        /**
         * 文件描述符事件：表示文件描述符已准备好进行输入操作，例如读取。
         * 
         * 监听器应该从文件描述符中读取所有可用的数据，然后返回 <code>true</code> 
         * 以保持监听器处于活动状态，或 <code>false</code>以移除监听器。
         * 
         * 对于套接字，可能会生成此事件以表示监听器应接受至少一个传入连接。
         * 
         * 只有在添加监听器时指定了 {@link #EVENT_INPUT} 事件掩码，
         * 此事件才会产生。
         */
        public static final int EVENT_INPUT = 1 << 0;

        /**
         * 文件描述符事件：表示文件描述符已准备好进行输出操作，例如写入。
         * 
         * 监听器应该根据需要写尽可能多的数据。
         * 如果它不能同时写入所有内容，那么它应该返回 <code>true</code>
         * 以保持侦听器处于活动状态。反之，它应当返回 <code>false</code>
         * 来移除监听器，以后需要写别的东西时再重新注册。
         * 
         * 只有在添加监听器时指定了 {@link #EVENT_OUTPUT} 事件掩码，
         * 此事件才会产生。
         */
        public static final int EVENT_OUTPUT = 1 << 1;

        /**
         * 文件描述符事件：表示文件描述符遇到致命错误。
         * 
         * 文件描述符错误可能有很多原因。  One common error
         * 一个常见错误是 socket 或管道的远程端关闭了它那端的连接。
         * 
         * 此事件可能随时发生，无论是否在添加监听器时指定了
         * {@link #EVENT_ERROR} 事件掩码。
         * 
         */
        public static final int EVENT_ERROR = 1 << 2;

        /** @hide */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(flag = true, prefix = { "EVENT_" }, value = {
                EVENT_INPUT,
                EVENT_OUTPUT,
                EVENT_ERROR
        })
        public @interface Events {}

        /**
         * 当文件描述符接收事件时调用。
         *
         * @param fd 文件描述符
         * @param events 出现的事件集合：
         * {@link #EVENT_INPUT}, {@link #EVENT_OUTPUT}, 和 {@link #EVENT_ERROR} 事件掩码的组合。
         * @return 要监视的新事件集，或0取消注册监听器。
         *
         * @see #EVENT_INPUT
         * @see #EVENT_OUTPUT
         * @see #EVENT_ERROR
         */
        @Events int onFileDescriptorEvents(@NonNull FileDescriptor fd, @Events int events);
    }

    private static final class FileDescriptorRecord {
        public final FileDescriptor mDescriptor;
        public int mEvents;
        public OnFileDescriptorEventListener mListener;
        public int mSeq;

        public FileDescriptorRecord(FileDescriptor descriptor,
                int events, OnFileDescriptorEventListener listener) {
            mDescriptor = descriptor;
            mEvents = events;
            mListener = listener;
        }
    }
}