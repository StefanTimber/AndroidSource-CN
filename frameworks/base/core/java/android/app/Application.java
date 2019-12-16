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

package android.app;

import android.annotation.CallSuper;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UnsupportedAppUsage;
import android.content.ComponentCallbacks;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.autofill.AutofillManager;

import java.util.ArrayList;

/**
 * 持有应用全局状态的基类。 您可以通过创建子类并使用其完全限定名作为 
 * AndroidManifest.xml 中 <code>&lt;application&gt;</code> 标签内，<code>"android:name"</code>
 * 属性的值，来使用自己实现的 application。
 * 当您的应用进程创建时，Application 类或您创建的子类将在任何其他类之前实例化。
 *
 * <p class="note"><strong>注意：</strong>
 * 通常不需要创建子类 Application。大多数情况下，静态单例可以以更模块化的方式提供相同的功能。
 * 如果您的单例需要全局 context（如注册广播接收器），在调用单例的<code>getInstance()</code>
 * 方法时使用 {@link android.content.Context#getApplicationContext() Context.getApplicationContext()}
 * 作为 {@link android.content.Context} 参数。
 * </p>
 */
public class Application extends ContextWrapper implements ComponentCallbacks2 {
    private static final String TAG = "Application";
    @UnsupportedAppUsage
    private ArrayList<ComponentCallbacks> mComponentCallbacks =
            new ArrayList<ComponentCallbacks>();
    @UnsupportedAppUsage
    private ArrayList<ActivityLifecycleCallbacks> mActivityLifecycleCallbacks =
            new ArrayList<ActivityLifecycleCallbacks>();
    @UnsupportedAppUsage
    private ArrayList<OnProvideAssistDataListener> mAssistCallbacks = null;

    /** @hide */
    @UnsupportedAppUsage
    public LoadedApk mLoadedApk;

    public interface ActivityLifecycleCallbacks {

        /**
         * 作为 Activity 创建的第一步调用。总是先于 {@link Activity#onCreate}。
         */
        default void onActivityPreCreated(@NonNull Activity activity,
                @Nullable Bundle savedInstanceState) {
        }

        /**
         * 当 Activity 调用 {@link Activity#onCreate super.onCreate()} 时调用。
         */
        void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState);

        /**
         * 作为 Activity 创建的最后一步调用。总是在 {@link Activity#onCreate} 后调用。
         */
        default void onActivityPostCreated(@NonNull Activity activity,
                @Nullable Bundle savedInstanceState) {
        }

        /**
         * 作为 Activity 启动的第一步调用。总是先于 {@link Activity#onStart}。
         */
        default void onActivityPreStarted(@NonNull Activity activity) {
        }

        /**
         * 当 Activity 调用 {@link Activity#onStart super.onStart()} 时调用。
         */
        void onActivityStarted(@NonNull Activity activity);

        /**
         * 作为 Activity 启动的最后一步调用。总是在 {@link Activity#onStart} 后调用。
         */
        default void onActivityPostStarted(@NonNull Activity activity) {
        }

        /**
         * 作为 Activity 恢复的第一步调用。总是先于 {@link Activity#onResume}。
         */
        default void onActivityPreResumed(@NonNull Activity activity) {
        }

        /**
         * 当 Activity 调用 {@link Activity#onResume super.onResume()} 时调用。
         */
        void onActivityResumed(@NonNull Activity activity);

        /**
         * 作为 Activity 恢复的最后一步调用。总是在
         * {@link Activity#onResume} 和 {@link Activity#onPostResume} 后调用。
         */
        default void onActivityPostResumed(@NonNull Activity activity) {
        }

        /**
         * 作为 Activity 暂停的第一步调用。总是先于 {@link Activity#onPause}。
         */
        default void onActivityPrePaused(@NonNull Activity activity) {
        }

        /**
         * 但 Activity 调用 {@link Activity#onPause super.onPause()} 时调用。
         */
        void onActivityPaused(@NonNull Activity activity);

        /**
         * 作为 Activity 暂停的最后一步调用。总是在 {@link Activity#onPause} 之后调用。
         */
        default void onActivityPostPaused(@NonNull Activity activity) {
        }

        /**
         * 作为 Activity 停止的第一步调用。总是先于 {@link Activity#onStop}。
         */
        default void onActivityPreStopped(@NonNull Activity activity) {
        }

        /**
         * 当 Activity 调用 {@link Activity#onStop super.onStop()} 时调用。
         */
        void onActivityStopped(@NonNull Activity activity);

        /**
         * 作为 Activity 停止的最后一步调用。总是在 {@link Activity#onStop} 之后调用。
         */
        default void onActivityPostStopped(@NonNull Activity activity) {
        }

        /**
         *作为 Activity 保存实例状态的第一步调用。总是先于 {@link Activity#onSaveInstanceState}。
         */
        default void onActivityPreSaveInstanceState(@NonNull Activity activity,
                @NonNull Bundle outState) {
        }

        /**
         * 当 Activity 调用
         * {@link Activity#onSaveInstanceState super.onSaveInstanceState()} 时调用。
         */
        void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState);

        /**
         * 作为 Activity 保存其实例状态的最后一步调用。
         * 总是在 {@link Activity#onSaveInstanceState} 之后调用。
         */
        default void onActivityPostSaveInstanceState(@NonNull Activity activity,
                @NonNull Bundle outState) {
        }

        /**
         * 作为 Activity 销毁的第一步调用。总是先于 {@link Activity#onDestroy}。
         */
        default void onActivityPreDestroyed(@NonNull Activity activity) {
        }

        /**
         * 当 Activity 调用 {@link Activity#onDestroy super.onDestroy()} 时调用。
         */
        void onActivityDestroyed(@NonNull Activity activity);

        /**
         * 作为 Activity 销毁的最后一步调用。总是在 {@link Activity#onDestroy} 之后调用。
         */
        default void onActivityPostDestroyed(@NonNull Activity activity) {
        }
    }

    /**
     * Callback interface for use with {@link Application#registerOnProvideAssistDataListener}
     * and {@link Application#unregisterOnProvideAssistDataListener}.
     */
    public interface OnProvideAssistDataListener {
        /**
         * This is called when the user is requesting an assist, to build a full
         * {@link Intent#ACTION_ASSIST} Intent with all of the context of the current
         * application.  You can override this method to place into the bundle anything
         * you would like to appear in the {@link Intent#EXTRA_ASSIST_CONTEXT} part
         * of the assist Intent.
         */
        public void onProvideAssistData(Activity activity, Bundle data);
    }

    public Application() {
        super(null);
    }

    /**
     * Called when the application is starting, before any activity, service,
     * or receiver objects (excluding content providers) have been created.
     *
     * <p>Implementations should be as quick as possible (for example using
     * lazy initialization of state) since the time spent in this function
     * directly impacts the performance of starting the first activity,
     * service, or receiver in a process.</p>
     *
     * <p>If you override this method, be sure to call {@code super.onCreate()}.</p>
     *
     * <p class="note">Be aware that direct boot may also affect callback order on
     * Android {@link android.os.Build.VERSION_CODES#N} and later devices.
     * Until the user unlocks the device, only direct boot aware components are
     * allowed to run. You should consider that all direct boot unaware
     * components, including such {@link android.content.ContentProvider}, are
     * disabled until user unlock happens, especially when component callback
     * order matters.</p>
     */
    @CallSuper
    public void onCreate() {
    }

    /**
     * This method is for use in emulated process environments.  It will
     * never be called on a production Android device, where processes are
     * removed by simply killing them; no user code (including this callback)
     * is executed when doing so.
     */
    @CallSuper
    public void onTerminate() {
    }

    @CallSuper
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Object[] callbacks = collectComponentCallbacks();
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                ((ComponentCallbacks)callbacks[i]).onConfigurationChanged(newConfig);
            }
        }
    }

    @CallSuper
    public void onLowMemory() {
        Object[] callbacks = collectComponentCallbacks();
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                ((ComponentCallbacks)callbacks[i]).onLowMemory();
            }
        }
    }

    @CallSuper
    public void onTrimMemory(int level) {
        Object[] callbacks = collectComponentCallbacks();
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                Object c = callbacks[i];
                if (c instanceof ComponentCallbacks2) {
                    ((ComponentCallbacks2)c).onTrimMemory(level);
                }
            }
        }
    }

    public void registerComponentCallbacks(ComponentCallbacks callback) {
        synchronized (mComponentCallbacks) {
            mComponentCallbacks.add(callback);
        }
    }

    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        synchronized (mComponentCallbacks) {
            mComponentCallbacks.remove(callback);
        }
    }

    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        synchronized (mActivityLifecycleCallbacks) {
            mActivityLifecycleCallbacks.add(callback);
        }
    }

    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        synchronized (mActivityLifecycleCallbacks) {
            mActivityLifecycleCallbacks.remove(callback);
        }
    }

    public void registerOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
        synchronized (this) {
            if (mAssistCallbacks == null) {
                mAssistCallbacks = new ArrayList<OnProvideAssistDataListener>();
            }
            mAssistCallbacks.add(callback);
        }
    }

    public void unregisterOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
        synchronized (this) {
            if (mAssistCallbacks != null) {
                mAssistCallbacks.remove(callback);
            }
        }
    }

    /**
     * Returns the name of the current process. A package's default process name
     * is the same as its package name. Non-default processes will look like
     * "$PACKAGE_NAME:$NAME", where $NAME corresponds to an android:process
     * attribute within AndroidManifest.xml.
     */
    public static String getProcessName() {
        return ActivityThread.currentProcessName();
    }

    // ------------------ Internal API ------------------

    /**
     * @hide
     */
    @UnsupportedAppUsage
    /* package */ final void attach(Context context) {
        attachBaseContext(context);
        mLoadedApk = ContextImpl.getImpl(context).mPackageInfo;
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPreCreated(@NonNull Activity activity,
            @Nullable Bundle savedInstanceState) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPreCreated(activity,
                        savedInstanceState);
            }
        }
    }

    @UnsupportedAppUsage
    /* package */ void dispatchActivityCreated(@NonNull Activity activity,
            @Nullable Bundle savedInstanceState) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                ((ActivityLifecycleCallbacks)callbacks[i]).onActivityCreated(activity,
                        savedInstanceState);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPostCreated(@NonNull Activity activity,
            @Nullable Bundle savedInstanceState) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPostCreated(activity,
                        savedInstanceState);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPreStarted(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPreStarted(activity);
            }
        }
    }

    @UnsupportedAppUsage
    /* package */ void dispatchActivityStarted(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                ((ActivityLifecycleCallbacks)callbacks[i]).onActivityStarted(activity);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPostStarted(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPostStarted(activity);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPreResumed(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPreResumed(activity);
            }
        }
    }

    @UnsupportedAppUsage
    /* package */ void dispatchActivityResumed(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                ((ActivityLifecycleCallbacks)callbacks[i]).onActivityResumed(activity);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPostResumed(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPostResumed(activity);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPrePaused(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPrePaused(activity);
            }
        }
    }

    @UnsupportedAppUsage
    /* package */ void dispatchActivityPaused(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                ((ActivityLifecycleCallbacks)callbacks[i]).onActivityPaused(activity);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPostPaused(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPostPaused(activity);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPreStopped(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPreStopped(activity);
            }
        }
    }

    @UnsupportedAppUsage
    /* package */ void dispatchActivityStopped(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                ((ActivityLifecycleCallbacks)callbacks[i]).onActivityStopped(activity);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPostStopped(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPostStopped(activity);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPreSaveInstanceState(@NonNull Activity activity,
            @NonNull Bundle outState) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPreSaveInstanceState(
                        activity, outState);
            }
        }
    }

    @UnsupportedAppUsage
    /* package */ void dispatchActivitySaveInstanceState(@NonNull Activity activity,
            @NonNull Bundle outState) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                ((ActivityLifecycleCallbacks)callbacks[i]).onActivitySaveInstanceState(activity,
                        outState);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPostSaveInstanceState(@NonNull Activity activity,
            @NonNull Bundle outState) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPostSaveInstanceState(
                        activity, outState);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPreDestroyed(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPreDestroyed(activity);
            }
        }
    }

    @UnsupportedAppUsage
    /* package */ void dispatchActivityDestroyed(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                ((ActivityLifecycleCallbacks)callbacks[i]).onActivityDestroyed(activity);
            }
        }
    }

    @UnsupportedAppUsage
        /* package */ void dispatchActivityPostDestroyed(@NonNull Activity activity) {
        Object[] callbacks = collectActivityLifecycleCallbacks();
        if (callbacks != null) {
            for (int i = 0; i < callbacks.length; i++) {
                ((ActivityLifecycleCallbacks) callbacks[i]).onActivityPostDestroyed(activity);
            }
        }
    }

    private Object[] collectComponentCallbacks() {
        Object[] callbacks = null;
        synchronized (mComponentCallbacks) {
            if (mComponentCallbacks.size() > 0) {
                callbacks = mComponentCallbacks.toArray();
            }
        }
        return callbacks;
    }

    @UnsupportedAppUsage
    private Object[] collectActivityLifecycleCallbacks() {
        Object[] callbacks = null;
        synchronized (mActivityLifecycleCallbacks) {
            if (mActivityLifecycleCallbacks.size() > 0) {
                callbacks = mActivityLifecycleCallbacks.toArray();
            }
        }
        return callbacks;
    }

    /* package */ void dispatchOnProvideAssistData(Activity activity, Bundle data) {
        Object[] callbacks;
        synchronized (this) {
            if (mAssistCallbacks == null) {
                return;
            }
            callbacks = mAssistCallbacks.toArray();
        }
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                ((OnProvideAssistDataListener)callbacks[i]).onProvideAssistData(activity, data);
            }
        }
    }

    /** @hide */
    @Override
    public AutofillManager.AutofillClient getAutofillClient() {
        final AutofillManager.AutofillClient client = super.getAutofillClient();
        if (client != null) {
            return client;
        }
        if (android.view.autofill.Helper.sVerbose) {
            Log.v(TAG, "getAutofillClient(): null on super, trying to find activity thread");
        }
        // Okay, ppl use the application context when they should not. This breaks
        // autofill among other things. We pick the focused activity since autofill
        // interacts only with the currently focused activity and we need the fill
        // client only if a call comes from the focused activity. Sigh...
        final ActivityThread activityThread = ActivityThread.currentActivityThread();
        if (activityThread == null) {
            return null;
        }
        final int activityCount = activityThread.mActivities.size();
        for (int i = 0; i < activityCount; i++) {
            final ActivityThread.ActivityClientRecord record =
                    activityThread.mActivities.valueAt(i);
            if (record == null) {
                continue;
            }
            final Activity activity = record.activity;
            if (activity == null) {
                continue;
            }
            if (activity.getWindow().getDecorView().hasFocus()) {
                if (android.view.autofill.Helper.sVerbose) {
                    Log.v(TAG, "getAutofillClient(): found activity for " + this + ": " + activity);
                }
                return activity;
            }
        }
        if (android.view.autofill.Helper.sVerbose) {
            Log.v(TAG, "getAutofillClient(): none of the " + activityCount + " activities on "
                    + this + " have focus");
        }
        return null;
    }
}