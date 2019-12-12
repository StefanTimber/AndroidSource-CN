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

package android.content;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 扩展了 {@link ComponentCallbacks} 的接口，新的回调用于更细粒度的内存管理。
 * 此接口可用于所有应用程序组件，包括
 * ({@link android.app.Activity}, {@link android.app.Service},
 * {@link ContentProvider}, 和 {@link android.app.Application})。
 *
 * 您应实现 {@link #onTrimMemory} 来基于当前系统限制增量的释放内存。
 * 使用此回调来释放资源能有助于提供一个响应性更强的系统整体，
 * 同时通过使系统让您的应用进程存活的更久，对提高应用的用户体验有益。
 * 也就是说，如果您<em>不</em>根据此回调定义的内存级别来回收资源，
 * 系统更有可能停止您的进程，而它缓存在最近最少使用（LRU）列表中，
 * 因此当用户回到应用时，需要重启并还原所有状态。
 *
 * {@link #onTrimMemory}提供的值不代表内存限制的单一线性极数，
 * 但提供了关于内存可用性的不同类型的提示：
 * 
 * 当您的应用在运行中：
 *  
 * {@link #TRIM_MEMORY_RUNNING_MODERATE} 设备开始内存不足。您的应用在运行且不可杀死。
 * {@link #TRIM_MEMORY_RUNNING_LOW} 设备内存明显不足。您的应用在运行且不可杀死，
 * 但请释放不用的资源以提高系统性能（这将直接影响您应用的性能表现）。
 * {@link #TRIM_MEMORY_RUNNING_CRITICAL} 设备内存严重不足。您的应用还不被认为是
 * 可杀死的进程，但若不释放资源，系统将开始中止后台进程，因此您应当释放非必要资源
 * 以防止性能下降。
 * 
 *
 * 当您的应用可见性改变时：
 * 
 * {@link #TRIM_MEMORY_UI_HIDDEN} 您的应用 UI 不再可见，此时是释放仅被 UI 使用的
 * 大资源的好时机。
 *  
 *
 * 当应用程序进程位于后台 LRU 列表中时：
 *
 * {@link #TRIM_MEMORY_BACKGROUND} 系统内存不足，而您的进程在靠近 LRU 列表头部位置。
 * 尽管您的应用进程并没有很高的可能性被杀死，但系统可能已经开始杀死 LRU 列表中的进程，
 * 所以您应当释放那些易于恢复的资源，这样应用进程会保留在列表中并在用户回到应用时快速恢复。
 * {@link #TRIM_MEMORY_MODERATE} 系统内存不足，而您的进程在 LRU 列表中间位置。如果系统内存
 * 持续吃紧，您的进程有可能被杀死。
 * {@link #TRIM_MEMORY_COMPLETE} 系统内存不足，如果不立即释放内存您的进程将被优先杀死。
 * 您应当释放所有不影响恢复应用状态的资源。
 * 为支持 API 14 以下设备，您可以使用 {@link #onLowMemory} 作为兼容方法，此方法基本和
 * {@link ComponentCallbacks2#TRIM_MEMORY_COMPLETE} 级别相同。
 * 
 * 
 * <p class="note"><strong>注意</strong> When the system begins
 * 当系统开始杀死 LRU 列表中的进程，虽然它主要是自底向上工作的，但它会考虑到内存消耗更大的进程，
 * 如果杀死他们可以得到更多的内存释放。所以当您在 LRU 列表中使用的内存越少，就越有可能被保留
 * 在列表中，并能够快速恢复。</p>
 * 
 * 
 * 更多关于进程生命周期不同等级的信息（如置于后台 LRU 列表意味着什么）
 * 请参考文档：
 *  <ahref="{@docRoot}guide/components/processes-and-threads.html#Lifecycle">进程和线程</a>
 */
public interface ComponentCallbacks2 extends ComponentCallbacks {

    /** @hide */
    @IntDef(prefix = { "TRIM_MEMORY_" }, value = {
            TRIM_MEMORY_COMPLETE,
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_BACKGROUND,
            TRIM_MEMORY_UI_HIDDEN,
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_MODERATE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TrimMemoryLevel {}

    /**
     * {@link #onTrimMemory(int)} 中的级别： 进程在后台 LRU 列表尾部附近，
     * 如果没能尽快获取到更多内存的话将被杀死。
     */
    static final int TRIM_MEMORY_COMPLETE = 80;
    
    /**
     * {@link #onTrimMemory(int)} 中的级别： 进程位于后台 LRU 列表中部；
     * 释放内存可以帮助系统让列表中后面的进程保持运行，已获得更好的性能。
     */
    static final int TRIM_MEMORY_MODERATE = 60;
    
    /**
     * {@link #onTrimMemory(int)} 中的级别： 进程已被列入 LRU 列表。
     *这是清理资源的好时机，当用户返回程序时可以快速高效的重建这些资源。
     */
    static final int TRIM_MEMORY_BACKGROUND = 40;
    
    /**
     * {@link #onTrimMemory(int)} 中的级别：进程之前展示了用户界面，现在已
     * 不再展示了。此时应释放 UI 占用的较大资源，以便更好地管理内存。
     */
    static final int TRIM_MEMORY_UI_HIDDEN = 20;

    /**
     * {@link #onTrimMemory(int)} 中的级别： 进程不是可销毁的后台进程，
     * 但设备内存严重不足，即将无法保持任何后台进程运行。您的进程应当尽可能
     * 释放非关键资源，以供别处使用。之后将发生的是 {@link #onLowMemory()}
     * 的调用，告知无法在后台保留任何进程，这样的情况会显著的影响用户。
     */
    static final int TRIM_MEMORY_RUNNING_CRITICAL = 15;

    /**
     * {@link #onTrimMemory(int)} 中的级别：进程不是可销毁的后台进程，
     * 但设备内存不足。您的进程应释放不需要的资源，以供别处使用内存。
     */
    static final int TRIM_MEMORY_RUNNING_LOW = 10;

    /**
     * {@link #onTrimMemory(int)} 中的级别：进程不是可销毁的后台进程，
     * 但设备内存出现一定的不足。您的进程可能要释放一些不必要的资源，
     * 以供别处使用。
     */
    static final int TRIM_MEMORY_RUNNING_MODERATE = 5;

    /**
     * 当操作系统确定进程应该释放不需要的内存时调用。例如，当进程进入后台，
     * 并且没有足够的内存来维持所需的后台进程运行时，就会调用此方法。
     * 您不应用级别的确切的值来比较，因为可能会在其中加入新的值————您通常
     * 想要比较值是否大于您感兴趣的级别。
     *
     * 您可以使用 {@link android.app.ActivityManager#getMyMemoryState
     * ActivityManager.getMyMemoryState(RunningAppProcessInfo)} 来获取任何
	 * 时间点的当前进程级别。
     *
     * @param level 内存整理的级别，提示应用可能需要释放的内存量。
     */
    void onTrimMemory(@TrimMemoryLevel int level);
}