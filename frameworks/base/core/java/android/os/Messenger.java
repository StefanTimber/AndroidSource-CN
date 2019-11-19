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

/**
 * 对 Handler 的引用，其他人可以使用它向其发送消息。
 * 这允许跨进程实现基于消息的通信，通过在一个进程中创建一个 Messenger 
 * 指向 handler，并将该 Messenger 传递给另一个进程。
 *
 * 注意：底层实现只是对用来执行通信的 {@link Binder} 的简单包装。
 * 这意味着您应当这样处理它：这个类不影响进程生命周期管理（您必须使用
 * 一些更高级别的组件来告诉系统你的进程需要继续运行），如果你的进程因
 * 任何原因消失，连接将断开，等等。
 */
public final class Messenger implements Parcelable {
    private final IMessenger mTarget;

    /**
     * 创建一个新的 Messenger 指向给定的 Handler。
     * 任何通过此 Messenger 发送的 Message 对象将出现在 Handler 中，
     * 就好像直接调用了 {@link Handler#sendMessage(Message) Handler.sendMessage(Message)} 
     * 一样。
     * 
     * @param target 将会接收到消息的 Handler。
     */
    public Messenger(Handler target) {
        mTarget = target.getIMessenger();
    }
    
    /**
     * 向此 Messenger 的 Handler 发送消息。
     * 
     * @param message 要发送的消息。通常通过
     * {@link Message#obtain() Message.obtain()}获取。
     * 
     * @throws RemoteException 如果目标 Handler 不再存在，
     * 抛出 DeadObjectException。
     */
    public void send(Message message) throws RemoteException {
        mTarget.send(message);
    }
    
    /**
     * 获得此 Messenger 用来与关联的 Handler 通信的 IBinder。
     * 
     * @return Returns the IBinder backing this Messenger.
     */
    public IBinder getBinder() {
        return mTarget.asBinder();
    }
    
    /**
     * 两个 Messenger 对象上的比较运算符，若它们都指向同一个
     * Handler 会返回 true。
     */
    public boolean equals(Object otherObj) {
        if (otherObj == null) {
            return false;
        }
        try {
            return mTarget.asBinder().equals(((Messenger)otherObj)
                    .mTarget.asBinder());
        } catch (ClassCastException e) {
        }
        return false;
    }

    public int hashCode() {
        return mTarget.asBinder().hashCode();
    }
    
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeStrongBinder(mTarget.asBinder());
    }

    public static final Parcelable.Creator<Messenger> CREATOR
            = new Parcelable.Creator<Messenger>() {
        public Messenger createFromParcel(Parcel in) {
            IBinder target = in.readStrongBinder();
            return target != null ? new Messenger(target) : null;
        }

        public Messenger[] newArray(int size) {
            return new Messenger[size];
        }
    };

    /**
     * 用于向 Parcel 写入 Messenger 或空指针的便利函数。
     * 您必须将它与 {@link #readMessengerOrNullFromParcel} 一起使用，
     * 以便稍后读取。
     * 
     * @param messenger 要写入的 Messenger，或 null。
     * @param out 写入 Messenger 的地方。
     */
    public static void writeMessengerOrNullToParcel(Messenger messenger,
            Parcel out) {
        out.writeStrongBinder(messenger != null ? messenger.mTarget.asBinder()
                : null);
    }
    
    /**
     * 用于从 Parcel 读取 Messenger 或空指针的便利函数。
     * 必须之前使用 {@link #writeMessengerOrNullToParcel} 写入了 Messenger。
     * 
     * @param in 包含了写入的 Messenger 的 Parcel。
     * 
     * @return 返回从 Parcel 读取的 Messenger，或 null 如果写入了 null。
     */
    public static Messenger readMessengerOrNullFromParcel(Parcel in) {
        IBinder b = in.readStrongBinder();
        return b != null ? new Messenger(b) : null;
    }
    
    /**
     * 从原始 IBinder 创建一个 Messenger，它是之前调用 {@link #getBinder}
     * 获取的。
     * 
     * @param target 此 Messenger 应当与之通信的 IBinder。
     */
    public Messenger(IBinder target) {
        mTarget = IMessenger.Stub.asInterface(target);
    }
}