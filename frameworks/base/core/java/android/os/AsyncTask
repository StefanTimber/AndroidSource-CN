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

import android.annotation.MainThread;
import android.annotation.Nullable;
import android.annotation.WorkerThread;
import java.util.ArrayDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>AsyncTask 实现了UI线程正确和简单的使用。此类允许你
 * 执行后台操作并在UI线程直接发布结果而不需要操作 Thread 和/或 Handler</p>
 *
 * <p>AsyncTask 被设计为一个围绕 {@link Thread} 和 {@link Handler} 的帮助类
 * 而不是一个线程通用框架。AsyncTasks 应当用于较短的操作（最多几秒）。
 * 如果你需要保持线程运行较长的时间，强烈推荐使用 <code>java.util.concurrent</code> 包
 * 提供的API例如 {@link Executor}, {@link ThreadPoolExecutor} and {@link FutureTask}.</p>
 *
 * <p>一个异步任务是指在后台线程上运行的计算并且其结果发布在UI线程。
 * 一个异步任务由3个泛型定义，分别是 <code>Params</code>, <code>Progress</code> 和 <code>Result</code>,
 * 和4个步骤， 分别为 <code>onPreExecute</code>, <code>doInBackground</code>,
 * <code>onProgressUpdate</code> and <code>onPostExecute</code>.</p>
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about using tasks and threads, read the
 * <a href="{@docRoot}guide/components/processes-and-threads.html">Processes and
 * Threads</a> developer guide.</p>
 * </div>
 *
 * <h2>使用</h2>
 * <p>AsyncTask 必须派生子类使用。 子类至少重写一个方法 ({@link #doInBackground}),
 *  通常还会重写 ({@link #onPostExecute}.)</p>
 *
 * <p>这是一个子类的示例：</p>
 * <pre class="prettyprint">
 * private class DownloadFilesTask extends AsyncTask&lt;URL, Integer, Long&gt; {
 *     protected Long doInBackground(URL... urls) {
 *         int count = urls.length;
 *         long totalSize = 0;
 *         for (int i = 0; i < count; i++) {
 *             totalSize += Downloader.downloadFile(urls[i]);
 *             publishProgress((int) ((i / (float) count) * 100));
 *             // Escape early if cancel() is called
 *             if (isCancelled()) break;
 *         }
 *         return totalSize;
 *     }
 *
 *     protected void onProgressUpdate(Integer... progress) {
 *         setProgressPercent(progress[0]);
 *     }
 *
 *     protected void onPostExecute(Long result) {
 *         showDialog("Downloaded " + result + " bytes");
 *     }
 * }
 * </pre>
 *
 * <p>一旦创建，任务的执行非常简单：</p>
 * <pre class="prettyprint">
 * new DownloadFilesTask().execute(url1, url2, url3);
 * </pre>
 *
 * <h2>AsyncTask的泛型类型</h2>
 * <p>异步任务使用的三种类型如下：</p>
 * <ol>
 *     <li><code>Params</code>, 执行时发送给任务的参数类型。</li>
 *     <li><code>Progress</code>, 后台计算期间发布的进度单位的类型。</li>
 *     <li><code>Result</code>, 后台计算结果的类型</li>
 * </ol>
 * <p>不是所有类型都总会被异步任务使用。要将类型标记为未使用，只需使用{@link Void}:</p>
 * <pre>
 * private class MyTask extends AsyncTask&lt;Void, Void, Void&gt; { ... }
 * </pre>
 *
 * <h2>四个步骤</h2>
 * <p>执行异步任务时，该任务将经历4个步骤：</p>
 * <ol>
 *     <li>{@link #onPreExecute()}, 在任务启动前在UI线程调用。
 *     这一步通常用于任务前的准备，比如在用户界面展示一个进度条。</li>
 *     <li>{@link #doInBackground}, 在 {@link #onPreExecute()} 执行完毕后在后台线程立即调用。
 *     此步骤用于执行耗时的后台计算。 异步任务的参数被传递给此方法。
 *     计算的结果必须由此步骤返回，并且会被传递给最后一步。 
 *     这一步骤也可以使用 {@link #publishProgress} 来更新进度。
 *     这些进度的值将在 {@link #onProgressUpdate} 步骤中在UI线程更新。</li>
 *     <li>{@link #onProgressUpdate}, 在调用 {@link #publishProgress} 后在UI线程调用。
 *     执行的时机没有定义。 此方法用于在后台任务执行的同时在界面上展示进度。
 *     例如可用于开启进度条或者在文本区域输出日志。</li>
 *     <li>{@link #onPostExecute}, 后台计算完毕后在UI线程调用。
 *     计算结果作为参数传递给此方法。</li>
 * </ol>
 * 
 * <h2>取消任务</h2>
 * <p>可以通过调用 {@link #cancel(boolean)} 来随时取消任务。 
 * 调用此方法会导致后续调用的 {@link #isCancelled()} 返回 True。
 * 调用了此方法后， {@link #onCancelled(Object)} 会取代 {@link #onPostExecute(Object)}
 * 在 {@link #doInBackground(Object[])} 返回后调用。
 * 为确保任务能够尽快取消，如果可能的话，你应当定期检查 {@link #isCancelled()} 的返回值。
 * （例如在Looper内）</p>
 *
 * <h2>线程规则</h2>
 * <p>这个类必须遵循一些线程规则才能正常工作：</p>
 * <ul>
 *     <li>AsyncTask 类必须在UI线程加载，class must be loaded on the UI thread. 
 *     从 {@link android.os.Build.VERSION_CODES#JELLY_BEAN} 开始这是自动完成的。</li>
 *     <li>任务实例必须在UI线程创建。</li>
 *     <li>{@link #execute} 方法必须在UI线程调用。</li>
 *     <li>请不要手动调用 {@link #onPreExecute()}, {@link #onPostExecute},
 *     {@link #doInBackground}, {@link #onProgressUpdate} 方法。</li>
 *     <li>任务只能被执行一次（尝试第二次执行会抛出异常）</li>
 * </ul>
 *
 * <h2>Memory observability</h2>
 * <p>AsyncTask 保证所有回调都是同步的，以下的操作在没有显示同步的情况下是安全的。</p>
 * <ul>
 *     <li>在构造方法或 {@link #onPreExecute} 中设置成员字段，并在 {@link #doInBackground} 中引用。
 *     <li>在 {@link #doInBackground} 中设置成员字段， 并在
 *     {@link #onProgressUpdate} 和 {@link #onPostExecute} 中引用。
 * </ul>
 *
 * <h2>执行顺序</h2>
 * <p>首次引入时，AsyncTasks 是在单个后台线程上串行执行的。
 * 从 {@link android.os.Build.VERSION_CODES#DONUT} 开始，改为了允许多任务并行执行的线程池。
 * 从 {@link android.os.Build.VERSION_CODES#HONEYCOMB}开始，任务又改为在单线程执行，
 * 为避免串行执行导致的常见应用程序错误。</p>
 * <p>如果你确实需要并行执行， 可以调用
 * {@link #executeOnExecutor(java.util.concurrent.Executor, Object[])} 传入
 * {@link #THREAD_POOL_EXECUTOR}.</p>
 */
public abstract class AsyncTask<Params, Progress, Result> {
    private static final String LOG_TAG = "AsyncTask";

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    // 核心线程池需要最少2个，至多4个线程
    // 比CPU数少1避免使CPU的后台工作饱和
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
        }
    };

    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(128);

    /**
     * 一个 {@link Executor} ，用于并行执行任务。
     */
    public static final Executor THREAD_POOL_EXECUTOR;

    static {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                sPoolWorkQueue, sThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }

    /**
     * 顺序执行的串行线程池，在进程中这是全局的。
     */
    public static final Executor SERIAL_EXECUTOR = new SerialExecutor();

    private static final int MESSAGE_POST_RESULT = 0x1;
    private static final int MESSAGE_POST_PROGRESS = 0x2;

    private static volatile Executor sDefaultExecutor = SERIAL_EXECUTOR;
    private static InternalHandler sHandler;

    private final WorkerRunnable<Params, Result> mWorker;
    private final FutureTask<Result> mFuture;

    private volatile Status mStatus = Status.PENDING;
    
    private final AtomicBoolean mCancelled = new AtomicBoolean();
    private final AtomicBoolean mTaskInvoked = new AtomicBoolean();

    private final Handler mHandler;

    private static class SerialExecutor implements Executor {
        final ArrayDeque<Runnable> mTasks = new ArrayDeque<Runnable>();
        Runnable mActive;

        public synchronized void execute(final Runnable r) {
            mTasks.offer(new Runnable() {
                public void run() {
                    try {
                        r.run();
                    } finally {
                        scheduleNext();
                    }
                }
            });
            if (mActive == null) {
                scheduleNext();
            }
        }

        protected synchronized void scheduleNext() {
            if ((mActive = mTasks.poll()) != null) {
                THREAD_POOL_EXECUTOR.execute(mActive);
            }
        }
    }

    /**
     * 表示任务当前状态，任务的生命周期中每种状态只会设置一次。
     */
    public enum Status {
        /**
         * 任务还未执行
         */
        PENDING,
        /**
         * 正在执行
         */
        RUNNING,
        /**
         *  {@link AsyncTask#onPostExecute} 已调用完成
         */
        FINISHED,
    }

    private static Handler getMainHandler() {
        synchronized (AsyncTask.class) {
            if (sHandler == null) {
                sHandler = new InternalHandler(Looper.getMainLooper());
            }
            return sHandler;
        }
    }

    private Handler getHandler() {
        return mHandler;
    }

    /** @hide */
    public static void setDefaultExecutor(Executor exec) {
        sDefaultExecutor = exec;
    }

    /**
     * 创建一个新的异步任务，此构造方法必须在UI线程调用。
     */
    public AsyncTask() {
        this((Looper) null);
    }

    /**
     * 创建一个新的异步任务，此构造方法必须在UI线程调用。
     *
     * @hide
     */
    public AsyncTask(@Nullable Handler handler) {
        this(handler != null ? handler.getLooper() : null);
    }

    /**
     * 创建一个新的异步任务，此构造方法必须在UI线程调用。
     *
     * @hide
     */
    public AsyncTask(@Nullable Looper callbackLooper) {
        mHandler = callbackLooper == null || callbackLooper == Looper.getMainLooper()
            ? getMainHandler()
            : new Handler(callbackLooper);

        mWorker = new WorkerRunnable<Params, Result>() {
            public Result call() throws Exception {
                mTaskInvoked.set(true);
                Result result = null;
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    //noinspection unchecked
                    result = doInBackground(mParams);
                    Binder.flushPendingCommands();
                } catch (Throwable tr) {
                    mCancelled.set(true);
                    throw tr;
                } finally {
                    postResult(result);
                }
                return result;
            }
        };

        mFuture = new FutureTask<Result>(mWorker) {
            @Override
            protected void done() {
                try {
                    postResultIfNotInvoked(get());
                } catch (InterruptedException e) {
                    android.util.Log.w(LOG_TAG, e);
                } catch (ExecutionException e) {
                    throw new RuntimeException("An error occurred while executing doInBackground()",
                            e.getCause());
                } catch (CancellationException e) {
                    postResultIfNotInvoked(null);
                }
            }
        };
    }

    private void postResultIfNotInvoked(Result result) {
        final boolean wasTaskInvoked = mTaskInvoked.get();
        if (!wasTaskInvoked) {
            postResult(result);
        }
    }

    private Result postResult(Result result) {
        @SuppressWarnings("unchecked")
        Message message = getHandler().obtainMessage(MESSAGE_POST_RESULT,
                new AsyncTaskResult<Result>(this, result));
        message.sendToTarget();
        return result;
    }

    /**
     * 返回任务的当前状态
     *
     * @return 当前状态
     */
    public final Status getStatus() {
        return mStatus;
    }

    /**
     * 重写此方法以在后台线程执行计算。 
     * 列出的参数是由任务调用方传递给 {@link #execute} 方法的。
     *
     * 此方法可以调用 {@link #publishProgress} 来在UI线程上更新进度。
     *
     * @param params 任务的参数
     *
     * @return 结果，由子类定义
     *
     * @see #onPreExecute()
     * @see #onPostExecute
     * @see #publishProgress
     */
    @WorkerThread
    protected abstract Result doInBackground(Params... params);

    /**
     * 在 {@link #doInBackground} 执行前，在UI线程运行
     *
     * @see #onPostExecute
     * @see #doInBackground
     */
    @MainThread
    protected void onPreExecute() {
    }

    /**
     * <p>在 {@link #doInBackground} 后在UI线程运行。 
     * result是由 {@link #doInBackground} 返回的。</p>
     * 
     * <p>如果任务被取消，此方法不会调用。</p>
     *
     * @param result {@link #doInBackground}计算得到的操作结果
     *
     * @see #onPreExecute
     * @see #doInBackground
     * @see #onCancelled(Object) 
     */
    @SuppressWarnings({"UnusedDeclaration"})
    @MainThread
    protected void onPostExecute(Result result) {
    }

    /**
     * 调用了 {@link #publishProgress} 后在UI线程调用。
     * values是传递给 {@link #publishProgress}的值。
     *
     * @param values 表示进度的值
     *
     * @see #publishProgress
     * @see #doInBackground
     */
    @SuppressWarnings({"UnusedDeclaration"})
    @MainThread
    protected void onProgressUpdate(Progress... values) {
    }

    /**
     * <p>在调用 {@link #cancel(boolean)} 并且
     * {@link #doInBackground(Object[])} 方法结束后在UI线程运行。</p>
     * 
     * <p>默认实现只是调用了 {@link #onCancelled()} 并忽略结果。
     * 如果您编写自己的实现，不要调用 <code>super.onCancelled(result)</code>.</p>
     *
     * @param result The result, if any, computed in
     *               {@link #doInBackground(Object[])}, can be null
     * 
     * @see #cancel(boolean)
     * @see #isCancelled()
     */
    @SuppressWarnings({"UnusedParameters"})
    @MainThread
    protected void onCancelled(Result result) {
        onCancelled();
    }    
    
    /**
     * <p>程序最好覆写 {@link #onCancelled(Object)}.
     * 此方法被 {@link #onCancelled(Object)} 的默认实现调用。</p>
     * 
     * <p>在调用 {@link #cancel(boolean)} 并且
     * {@link #doInBackground(Object[])} 方法结束后在UI线程运行。</p>
     *
     * @see #onCancelled(Object) 
     * @see #cancel(boolean)
     * @see #isCancelled()
     */
    @MainThread
    protected void onCancelled() {
    }

    /**
     * 返回Ture如果任务在正常完成前被取消了。 
     * 如果对任务调用了 {@link #cancel(boolean)} ，则应当在
     * {@link #doInBackground(Object[])} 中定期检查此方法返回的值，以尽快结束任务。
     *
     * @return <tt>true</tt> 如果任务在完成前被取消
     *
     * @see #cancel(boolean)
     */
    public final boolean isCancelled() {
        return mCancelled.get();
    }

    /**
     * <p>尝试取消任务的执行。
     * 如果任务已经完成、已经取消或者由于某些原因不能取消，尝试将会失败。
     * 如果成功，且任务在调用cancel时还未启动，此任务不再运行。
     * 如果任务已经开始，mayInterruptIfRunning参数将决定执行任务的线程
     * 是否应被中断以尝试停止该任务。</p>
     * 
     * <p>调用此方法将导致 {@link #onCancelled(Object)} 会在
     * {@link #doInBackground(Object[])} 返回后在UI线程上调用。
     * 调用此方法保证 {@link #onPostExecute(Object)} 不会被调用。
     * 调用此方法后，您应当在 {@link #doInBackground(Object[])} 定期检查
     * {@link #isCancelled()} 返回的值，以尽快结束任务。</p>
     *
     * @param mayInterruptIfRunning <tt>true</tt> 如果执行任务的线程应当被中断，
     *        否则执行中的任务允许完成。
     *
     * @return <tt>false</tt> 如果任务不能被取消，
     *         通常是因为它已经正常完成了；
     *         <tt>true</tt> 否则返回true
     *
     * @see #isCancelled()
     * @see #onCancelled(Object)
     */
    public final boolean cancel(boolean mayInterruptIfRunning) {
        mCancelled.set(true);
        return mFuture.cancel(mayInterruptIfRunning);
    }

    /**
     * 如果需要，等待计算完成，然后获取结果。
     *
     * @return 计算结果
     *
     * @throws CancellationException 如果计算被取消
     * @throws ExecutionException 如果计算抛出异常
     * @throws InterruptedException 如果当前线程等待时被中断
     */
    public final Result get() throws InterruptedException, ExecutionException {
        return mFuture.get();
    }

    /**
     * 如果需要，在给定的超时时间内等待计算完成，然后获取结果。
     *
     * @param timeout 取消操作前等待的事件
     * @param unit 超时时间的单位
     *
     * @return 计算结果
     *
     * @throws CancellationException 如果计算被取消
     * @throws ExecutionException 如果计算抛出异常
     * @throws InterruptedException 如果当前线程等待时被中断
     * @throws TimeoutException 如果等待超时
     */
    public final Result get(long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        return mFuture.get(timeout, unit);
    }

    /**
     * 使用指定的参数执行任务。任务返回自身（this），以便调用方可以保留对它的引用。
     * 
     * <p>注意： 此方法在单线程队列或线程池调度任务，取决于平台版本。
	 * 首次引入时，AsyncTasks 是在单个后台线程上串行执行的。
     * 从 {@link android.os.Build.VERSION_CODES#DONUT} 开始，改为了允许多任务并行执行的线程池。
     * 从 {@link android.os.Build.VERSION_CODES#HONEYCOMB}开始，任务又改为在单线程执行
     * 以避免并行执行导致的常见错误。
     * 如果您确实需要并行执行，可以使用此方法的 {@link #executeOnExecutor} 版本
     * 传入 {@link #THREAD_POOL_EXECUTOR}；但请查看其注释中有关使用的警告。
     *
     * <p>此方法必须在UI线程调用
     *
     * @param params 任务的参数
     *
     * @return AsyncTask实例
     *
     * @throws IllegalStateException 如果 {@link #getStatus()} 返回
     *         {@link AsyncTask.Status#RUNNING} 或 {@link AsyncTask.Status#FINISHED}.
     *
     * @see #executeOnExecutor(java.util.concurrent.Executor, Object[])
     * @see #execute(Runnable)
     */
    @MainThread
    public final AsyncTask<Params, Progress, Result> execute(Params... params) {
        return executeOnExecutor(sDefaultExecutor, params);
    }

    /**
     * 使用指定的参数执行任务。任务返回自身（this），以便调用方可以保留对它的引用。
     * 
     * <p>此方法通常与 {@link #THREAD_POOL_EXECUTOR} 一起使用来允许多个任务
     * 在AsyncTask管理的线程池中并行运行，
     * 您也可以使用自己的 {@link Executor} 来实现自定义行为。
     * 
     * <p><em>警告：</em>	 
     * 允许多个任务在线程池并行执行通常不是我们想要的，因为它们的操作顺序不确定。
     * 例如，如果这些任务用于修改任何公共的状态（如点击按钮写入文件），
     * 将无法保证修改的顺序。
     * 没有仔细的操作，在少数情况下新数据有可能被旧的数据覆盖， 
     * 导致数据丢失和稳定性问题。这样的修改最好串行执行；  
     * 您可以将此方法与  {@link #SERIAL_EXECUTOR} 一起使用来保证此类操作的串行执行，
	 * 而无需担心平台版本。
     *
     * <p>此方法必须在UI线程调用。
     *
     * @param exec 使用的executor。{@link #THREAD_POOL_EXECUTOR} 是一个方便的进程范围的线程池，
     *             可用于松散耦合的任务。
     * @param params 任务的参数
     *
     * @return AsyncTask实例
     *
     * @throws IllegalStateException 如果 {@link #getStatus()} 返回
     *         {@link AsyncTask.Status#RUNNING} 或 {@link AsyncTask.Status#FINISHED}.
     *
     * @see #execute(Object[])
     */
    @MainThread
    public final AsyncTask<Params, Progress, Result> executeOnExecutor(Executor exec,
            Params... params) {
        if (mStatus != Status.PENDING) {
            switch (mStatus) {
                case RUNNING:
                    throw new IllegalStateException("Cannot execute task:"
                            + " the task is already running.");
                case FINISHED:
                    throw new IllegalStateException("Cannot execute task:"
                            + " the task has already been executed "
                            + "(a task can be executed only once)");
            }
        }

        mStatus = Status.RUNNING;

        onPreExecute();

        mWorker.mParams = params;
        exec.execute(mFuture);

        return this;
    }

    /**
     * {@link #execute(Object...)} 的简便版本，用于简单的Runnable对象。
     * 查看 {@link #execute(Object[])} 获取执行顺序的更多信息。
     *
     * @see #execute(Object[])
     * @see #executeOnExecutor(java.util.concurrent.Executor, Object[])
     */
    @MainThread
    public static void execute(Runnable runnable) {
        sDefaultExecutor.execute(runnable);
    }

    /**
     * 此方法可从 {@link #doInBackground} 调用，以在后台运算的同时在UI线程更新进度。
     * 每次调用都会在UI线程触发 {@link #onProgressUpdate} 
     *
     * 如果任务已被取消， {@link #onProgressUpdate} 不会调用
     *
     * @param values 更新UI的进度值
     *
     * @see #onProgressUpdate
     * @see #doInBackground
     */
    @WorkerThread
    protected final void publishProgress(Progress... values) {
        if (!isCancelled()) {
            getHandler().obtainMessage(MESSAGE_POST_PROGRESS,
                    new AsyncTaskResult<Progress>(this, values)).sendToTarget();
        }
    }

    private void finish(Result result) {
        if (isCancelled()) {
            onCancelled(result);
        } else {
            onPostExecute(result);
        }
        mStatus = Status.FINISHED;
    }

    private static class InternalHandler extends Handler {
        public InternalHandler(Looper looper) {
            super(looper);
        }

        @SuppressWarnings({"unchecked", "RawUseOfParameterizedType"})
        @Override
        public void handleMessage(Message msg) {
            AsyncTaskResult<?> result = (AsyncTaskResult<?>) msg.obj;
            switch (msg.what) {
                case MESSAGE_POST_RESULT:
                    // There is only one result
                    result.mTask.finish(result.mData[0]);
                    break;
                case MESSAGE_POST_PROGRESS:
                    result.mTask.onProgressUpdate(result.mData);
                    break;
            }
        }
    }

    private static abstract class WorkerRunnable<Params, Result> implements Callable<Result> {
        Params[] mParams;
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    private static class AsyncTaskResult<Data> {
        final AsyncTask mTask;
        final Data[] mData;

        AsyncTaskResult(AsyncTask task, Data... data) {
            mTask = task;
            mData = data;
        }
    }
}