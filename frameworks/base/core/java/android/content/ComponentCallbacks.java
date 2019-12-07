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

import android.annotation.NonNull;
import android.content.res.Configuration;

/**
 * 对所有应用程序组件通用的回调 API 集合，包括
 * ({@link android.app.Activity}, {@link android.app.Service},
 * {@link ContentProvider}, 和 {@link android.app.Application})。
 *
 * <p class="note"><strong>注意：</strong> 
 * 您还应实现 {@linkComponentCallbacks2} 接口，它提供了 {@link
 * ComponentCallbacks2#onTrimMemory} 回调帮助你的应用更有效地管理内存使用。</p>
 */
public interface ComponentCallbacks {
    /**
     * 当组件运行时，设备配置发生改变，由系统调用。
     * 需要注意的是，不同于活动，其他的组件在配置改变时不会重启：
     * 它们必须处理改变的结果，例如重新获取资源。
     *
     * 此方法被调用时，您的 Resources 对象将被更新，
     * 以返回与新配置匹配的资源值。
     *
     * <p>更多信息请参考 <a href="{@docRoot}guide/topics/resources/runtime-changes.html"
     * >Handling Runtime Changes</a>.
     *
     * @param newConfig 新的设备配置。
     */
    void onConfigurationChanged(@NonNull Configuration newConfig);

    /**
     * 当整个系统内存不足时调用此函数，且主动运行的进程应该减少其内存使用。
     * 虽然没有定义调用该方法的确切时间点，但通常在所有后台进程都被终止时才发生。
     * 也就是说，是在即将终止 service 和前台UI进程之前，而这些进程是我们想
     * 避免杀死的。
     *
     * 您应当实现此方法来释放持有的缓存或其他非必须的资源。
     * 系统将在此方法返回后执行垃圾回收。
     * 更佳的是，您应当实现 {@link ComponentCallbacks2} 接口的
     * {@link ComponentCallbacks2#onTrimMemory} 来依据不同级别的内存需求增量释放资源。
     * 此 API 适用于 API 14 或更高，因此您应只用此 {@link #onLowMemory} 作为旧版本的
     * 兼容，该方法与 {@link ComponentCallbacks2#onTrimMemory} 中的
     * {@link ComponentCallbacks2#TRIM_MEMORY_COMPLETE} 级别相同。
     */
    void onLowMemory();
}