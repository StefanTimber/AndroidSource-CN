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

import android.os.MessageProto;
import android.util.TimeUtils;
import android.util.proto.ProtoOutputStream;

/**
 *
 * 定义了一个消息类，包含描述信息和可发送给 {@link Handler} 的任意数据对象。
 * 此对象包含2个额外的 int 字段和一个额外的 object 字段，在很多情况下允许您不进行分配。
 *
 * 注意：尽管 Message 的构造方法是 public 的，但获取实例的最佳方法是
 * 调用 {@link #obtain Message.obtain()} 或 {@link Handler#obtainMessage Handler.obtainMessage()} 
 * 方法之一，这样将从回收对象池中取得它们。
 */
public final class Message implements Parcelable {
    /**
     * 用户定义的消息识别码，以便接收方识别消息的内容。
     * 每个 {@link Handler} 都有自己的识别码命名空间，因此无需担心与其他 handler 冲突。
     */
    public int what;

    /**
     * 如果您只需要保存几个整数值，相比使用 {@link #setData(Bundle) setData()}，
     * arg1 和 arg2 是 更低开销的替代方式。
     */
    public int arg1;

    /**
     * 如果您只需要保存几个整数值，相比使用 {@link #setData(Bundle) setData()}，
     * arg1 和 arg2 是 更低开销的替代品。
     */
    public int arg2;

    /**
     * 要发送给接收方的任意对象。
     * 当使用 {@link Messenger} 跨进程发送消息时，如果它包含了序列化的框架类
     * （非应用程序实现的类），它只能为非空的。对于其他数据传输请使用
     * {@link #setData}。
     *
     * 注意在 {@link android.os.Build.VERSION_CODES#FROYO} 版本前不支持
     * 此处的 Parcelable 对象。
     */
    public Object obj;

    /**
     * 可发送针对此消息的可选 Messenger。
     * 具体如何使用它取决于发送方和接收方。
     */
    public Messenger replyTo;

    /**
     * 表示发送消息的 uid 的可选字段。
     * 这只对由 {@link Messenger} 发送的消息有效；否则将为-1。
     */
    public int sendingUid = -1;

    /** 标记消息在使用中。
     * 此标志在消息入队时设置，并在传递时和回收后保持。
     * 只有在创建或获取新消息时才清除标志，因为这是唯一允许程序修改消息内容的时候。
     * 
     * 尝试将已在使用的消息入队或回收是错误的。
     */
    /*package*/ static final int FLAG_IN_USE = 1 << 0;

    /** 如果设置，消息将为异步的 */
    /*package*/ static final int FLAG_ASYNCHRONOUS = 1 << 1;

    /** 在 copyFrom 方法中要清除的标志 */
    /*package*/ static final int FLAGS_TO_CLEAR_ON_COPY_FROM = FLAG_IN_USE;

    /*package*/ int flags;

    /*package*/ long when;

    /*package*/ Bundle data;

    /*package*/ Handler target;

    /*package*/ Runnable callback;

    // 有时我们存储这些东西的链表
    /*package*/ Message next;


    /** @hide */
    public static final Object sPoolSync = new Object();
    private static Message sPool;
    private static int sPoolSize = 0;

    private static final int MAX_POOL_SIZE = 50;

    private static boolean gCheckRecycle = true;

    /**
     * 从全局池返回新的消息实例。
     * 允许我们在许多情况下避免分配新对象。
     */
    public static Message obtain() {
        synchronized (sPoolSync) {
            if (sPool != null) {
                Message m = sPool;
                sPool = m.next;
                m.next = null;
                m.flags = 0; // clear in-use flag
                sPoolSize--;
                return m;
            }
        }
        return new Message();
    }

    /**
     * 与 {@link #obtain()} 相同，但是复制现有消息的值（包括其目标）到新的消息中。
     * @param orig 要复制的原消息。
     * @return 来自全局池的一个消息对象。
     */
    public static Message obtain(Message orig) {
        Message m = obtain();
        m.what = orig.what;
        m.arg1 = orig.arg1;
        m.arg2 = orig.arg2;
        m.obj = orig.obj;
        m.replyTo = orig.replyTo;
        m.sendingUid = orig.sendingUid;
        if (orig.data != null) {
            m.data = new Bundle(orig.data);
        }
        m.target = orig.target;
        m.callback = orig.callback;

        return m;
    }

    /**
     * 与 {@link #obtain()} 相同，但在返回时设置消息的 <em>target</em> 成员。
     * @param h  分配给返回消息对象的 <em>target</em> 成员的 Handler。
     * @return 来自全局池的一个消息对象。
     */
    public static Message obtain(Handler h) {
        Message m = obtain();
        m.target = h;

        return m;
    }

    /**
     * 与 {@link #obtain(Handler)} 相同，但是在返回的消息上分配一个回调 Runnable。
     * @param h  分配给返回消息对象的 <em>target</em> 成员的 Handler。
     * @param callback 处理消息时将执行的 Runnable。
     * @return 来自全局池的一个消息对象。
     */
    public static Message obtain(Handler h, Runnable callback) {
        Message m = obtain();
        m.target = h;
        m.callback = callback;

        return m;
    }

    /**
     * 与 {@link #obtain()} 相同，但设置了消息的 <em>target</em> 和
     * <em>what</em> 成员。
     * @param h  分配给 <em>target</em> 成员的值。
     * @param what  分配给 <em>what</em> 成员的值。
     * @return 来自全局池的一个消息对象。
     */
    public static Message obtain(Handler h, int what) {
        Message m = obtain();
        m.target = h;
        m.what = what;

        return m;
    }

    /**
     * 与 {@link #obtain()} 相同，当设置了 <em>target</em>, <em>what</em>, 和 <em>obj</em>
     * 成员。
     * @param h  设置给 <em>target</em> 的值。
     * @param what  设置给 <em>what</em> 的值。
     * @param obj  设置给 <em>object</em> 的值。
     * @return  来自全局池的一个消息对象。
     */
    public static Message obtain(Handler h, int what, Object obj) {
        Message m = obtain();
        m.target = h;
        m.what = what;
        m.obj = obj;

        return m;
    }

    /**
     * 与 {@link #obtain()} 相同，但设置 <em>target</em>, <em>what</em>,
     * <em>arg1</em>, 和 <em>arg2</em> 成员。
     *
     * @param h  设置给 <em>target</em> 的值。
     * @param what  设置给 <em>what</em> 的值。
     * @param arg1  设置给 <em>arg1</em> 的值。
     * @param arg2  设置给 <em>arg2</em> 的值。
     * @return  来自全局池的一个消息对象。
     */
    public static Message obtain(Handler h, int what, int arg1, int arg2) {
        Message m = obtain();
        m.target = h;
        m.what = what;
        m.arg1 = arg1;
        m.arg2 = arg2;

        return m;
    }

    /**
     * 与 {@link #obtain()} 相同，但设置 <em>target</em>, <em>what</em>,
     * <em>arg1</em>, <em>arg2</em>, 和 <em>obj</em> 成员。
     *
     * @param h  设置给 <em>target</em> 的值。
     * @param what  设置给 <em>what</em> 的值。
     * @param arg1  设置给 <em>arg1</em> 的值。
     * @param arg2  设置给 <em>arg2</em> 的值。
     * @param obj  设置给 <em>obj</em> 的值。
     * @return  来自全局池的一个消息对象。
     */
    public static Message obtain(Handler h, int what,
            int arg1, int arg2, Object obj) {
        Message m = obtain();
        m.target = h;
        m.what = what;
        m.arg1 = arg1;
        m.arg2 = arg2;
        m.obj = obj;

        return m;
    }

    /** @hide */
    public static void updateCheckRecycle(int targetSdkVersion) {
        if (targetSdkVersion < Build.VERSION_CODES.LOLLIPOP) {
            gCheckRecycle = false;
        }
    }

    /**
     * 将消息实例回收到全局池。
     * 
     * 调用此函数后，*禁止*再操作消息，因为它已被有效释放。
     * 回收当前已入队或正在传递给 Handler 的消息是错误的。
     * 
     */
    public void recycle() {
        if (isInUse()) {
            if (gCheckRecycle) {
                throw new IllegalStateException("This message cannot be recycled because it "
                        + "is still in use.");
            }
            return;
        }
        recycleUnchecked();
    }

    /**
     * 回收可能正在使用的消息。
     * 在处理排队的消息时由消息队列和 Looper 在内部使用。
     */
    void recycleUnchecked() {
        // 将消息标记为在使用中，而它仍保留在回收的对象池中。
        // 清除所有其他细节。
        flags = FLAG_IN_USE;
        what = 0;
        arg1 = 0;
        arg2 = 0;
        obj = null;
        replyTo = null;
        sendingUid = -1;
        when = 0;
        target = null;
        callback = null;
        data = null;

        synchronized (sPoolSync) {
            if (sPoolSize < MAX_POOL_SIZE) {
                next = sPool;
                sPool = this;
                sPoolSize++;
            }
        }
    }

    /**
     * 使此消息与消息o相同。 执行数据字段的浅拷贝。
     * 不复制链表字段，也不复制原始消息的时间戳或 target/callback。
     */
    public void copyFrom(Message o) {
        this.flags = o.flags & ~FLAGS_TO_CLEAR_ON_COPY_FROM;
        this.what = o.what;
        this.arg1 = o.arg1;
        this.arg2 = o.arg2;
        this.obj = o.obj;
        this.replyTo = o.replyTo;
        this.sendingUid = o.sendingUid;

        if (o.data != null) {
            this.data = (Bundle) o.data.clone();
        } else {
            this.data = null;
        }
    }

    /**
     * 返回此消息的目标传递时间（毫秒）。
     */
    public long getWhen() {
        return when;
    }

    public void setTarget(Handler target) {
        this.target = target;
    }

    /**
     * 获取将接收此消息的 {@link android.os.Handler Handler} 实现类。
     * 此类必须实现了
     * {@link android.os.Handler#handleMessage(android.os.Message)
     * Handler.handleMessage()}。
     * 每个 Handler 都有自己的消息代码命名空间，因此无需担心与其他 handler 冲突。
     */
    public Handler getTarget() {
        return target;
    }

    /**
     * 获取消息处理时将会执行的 callback 对象。
     * 此对象必须实现了 Runnable 接口。
     * 它被接收此消息的 <em>target</em> {@link Handler} 派发。
     * 如果未设置，消息将会发送给接收 Handler 的
     * {@link Handler#handleMessage(Message Handler.handleMessage())}.
     */
    public Runnable getCallback() {
        return callback;
    }

    /** @hide */
    public Message setCallback(Runnable r) {
        callback = r;
        return this;
    }

    /**
     * 获取与此事件关联的数据包，必要时懒创建它。
     * 通过 {@link #setData(Bundle)} 设置此值。
     * 注意，当通过 {@link Messenger} 跨进程传输数据时，
     * 您需要通过 {@link Bundle#setClassLoader(ClassLoader)
     * Bundle.setClassLoader()} 为 Bundle 设置 ClassLoader，
     * 以便于在您获取这些对象时实例化它们。
     * @see #peekData()
     * @see #setData(Bundle)
     */
    public Bundle getData() {
        if (data == null) {
            data = new Bundle();
        }

        return data;
    }

    /**
     * 类似 getData()，但不会懒创建 Bundle。
     * 如果 Bundle 不存在将返回 null。
     * 查看 {@link #getData} 获取更多相关信息。
     * @see #getData()
     * @see #setData(Bundle)
     */
    public Bundle peekData() {
        return data;
    }

    /**
     * 设置包含任意数据值的 Bundle。 
     * 如果可以，使用 arg1 和 arg2 成员作为低开销的方式来传递简单的整型数值。
     * @see #getData()
     * @see #peekData()
     */
    public void setData(Bundle data) {
        this.data = data;
    }

    /**
     * {@link #what} 的可链式调用 setter 方法。
     *
     * @hide
     */
    public Message setWhat(int what) {
        this.what = what;
        return this;
    }

    /**
     * 发送此消息给 {@link #getTarget} 指定的 Handler。
     * 如果未设置此字段，则抛出空指针异常。
     */
    public void sendToTarget() {
        target.sendMessage(this);
    }

    /**
     * 如果消息是异步的返回 true，意味着它不受
     * {@link Looper} 同步屏障的限制。
     *
     * @return True 如果消息是异步的
     *
     * @see #setAsynchronous(boolean)
     */
    public boolean isAsynchronous() {
        return (flags & FLAG_ASYNCHRONOUS) != 0;
    }

    /**
     * 设置消息是否是异步的，意味着它不受
     * {@link Looper} 同步屏障的限制。
     * 
     * 某些操作（如视图刷新）可能会在 {@link Looper} 的消息队列中引入同步屏障，
     * 以防止在满足某些条件之前传递后续消息。
     * 在视图刷新时，调用 {@link android.view.View#invalidate} 后发送的消息会
     * 通过同步屏障挂起，直到下一帧准备好绘制。
     * 同步屏障确保在恢复前完全处理了刷新请求。
     * 
     * 异步消息不受同步障碍的影响。它们通常表示中断、输入事件和其他信号，
     * 这些信号必须独立处理，即使需要暂停其他工作。
     * 
     * 请注意，异步消息可能与同步消息的传递顺序不一致，尽管它们自己之间总是按顺序传递的。
     * 如果这些消息的相对顺序很重要，那么它们可能首先就不应该是异步的。小心使用。
     *
     * @param async 如果消息是异步的则为 True。
     *
     * @see #isAsynchronous()
     */
    public void setAsynchronous(boolean async) {
        if (async) {
            flags |= FLAG_ASYNCHRONOUS;
        } else {
            flags &= ~FLAG_ASYNCHRONOUS;
        }
    }

    /*package*/ boolean isInUse() {
        return ((flags & FLAG_IN_USE) == FLAG_IN_USE);
    }

    /*package*/ void markInUse() {
        flags |= FLAG_IN_USE;
    }

    /** 构造方法（但获取一个消息对象的更好方式是调用 {@link #obtain() Message.obtain()}）。
    */
    public Message() {
    }

    @Override
    public String toString() {
        return toString(SystemClock.uptimeMillis());
    }

    String toString(long now) {
        StringBuilder b = new StringBuilder();
        b.append("{ when=");
        TimeUtils.formatDuration(when - now, b);

        if (target != null) {
            if (callback != null) {
                b.append(" callback=");
                b.append(callback.getClass().getName());
            } else {
                b.append(" what=");
                b.append(what);
            }

            if (arg1 != 0) {
                b.append(" arg1=");
                b.append(arg1);
            }

            if (arg2 != 0) {
                b.append(" arg2=");
                b.append(arg2);
            }

            if (obj != null) {
                b.append(" obj=");
                b.append(obj);
            }

            b.append(" target=");
            b.append(target.getClass().getName());
        } else {
            b.append(" barrier=");
            b.append(arg1);
        }

        b.append(" }");
        return b.toString();
    }

    void writeToProto(ProtoOutputStream proto, long fieldId) {
        final long messageToken = proto.start(fieldId);
        proto.write(MessageProto.WHEN, when);

        if (target != null) {
            if (callback != null) {
                proto.write(MessageProto.CALLBACK, callback.getClass().getName());
            } else {
                proto.write(MessageProto.WHAT, what);
            }

            if (arg1 != 0) {
                proto.write(MessageProto.ARG1, arg1);
            }

            if (arg2 != 0) {
                proto.write(MessageProto.ARG2, arg2);
            }

            if (obj != null) {
                proto.write(MessageProto.OBJ, obj.toString());
            }

            proto.write(MessageProto.TARGET, target.getClass().getName());
        } else {
            proto.write(MessageProto.BARRIER, arg1);
        }

        proto.end(messageToken);
    }

    public static final Parcelable.Creator<Message> CREATOR
            = new Parcelable.Creator<Message>() {
        public Message createFromParcel(Parcel source) {
            Message msg = Message.obtain();
            msg.readFromParcel(source);
            return msg;
        }

        public Message[] newArray(int size) {
            return new Message[size];
        }
    };

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        if (callback != null) {
            throw new RuntimeException(
                "Can't marshal callbacks across processes.");
        }
        dest.writeInt(what);
        dest.writeInt(arg1);
        dest.writeInt(arg2);
        if (obj != null) {
            try {
                Parcelable p = (Parcelable)obj;
                dest.writeInt(1);
                dest.writeParcelable(p, flags);
            } catch (ClassCastException e) {
                throw new RuntimeException(
                    "Can't marshal non-Parcelable objects across processes.");
            }
        } else {
            dest.writeInt(0);
        }
        dest.writeLong(when);
        dest.writeBundle(data);
        Messenger.writeMessengerOrNullToParcel(replyTo, dest);
        dest.writeInt(sendingUid);
    }

    private void readFromParcel(Parcel source) {
        what = source.readInt();
        arg1 = source.readInt();
        arg2 = source.readInt();
        if (source.readInt() != 0) {
            obj = source.readParcelable(getClass().getClassLoader());
        }
        when = source.readLong();
        data = source.readBundle();
        replyTo = Messenger.readMessengerOrNullFromParcel(source);
        sendingUid = source.readInt();
    }
}