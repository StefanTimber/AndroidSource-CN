/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一种保存有限数量对象的强引用的缓存。
 * 每次访问值时，都会将其移动到队列的头部。 当一个值被加入到已满的缓存中时，
 * 队列末尾的值将被移除，并可能被垃圾回收。
 *
 * 如果您缓存的值包含需要显式释放的资源，重写 {@link #entryRemoved}。
 *
 * 如果需要计算对应键的缓存未命中，请重写 {@link #create}。
 * 这简化了调用代码，允许它假设始终会返回值，即使缓存未命中。
 *
 * 默认情况下，缓存大小以 entry 的数量计算。
 * 重写 {@link #sizeOf} 来以不同单位度量缓存大小。
 * 例如，此缓存限制4MB的 bitmap：
 * <pre>   {@code
 *   int cacheSize = 4 * 1024 * 1024; // 4MiB
 *   LruCache<String, Bitmap> bitmapCache = new LruCache<String, Bitmap>(cacheSize) {
 *       protected int sizeOf(String key, Bitmap value) {
 *           return value.getByteCount();
 *       }
 *   }}</pre>
 *
 * 此类是线程安全的。 
 * 通过缓存上的同步来原子的进行多个缓存操作： <pre>   {@code
 *   synchronized (cache) {
 *     if (cache.get(key) == null) {
 *         cache.put(key, value);
 *     }
 *   }}</pre>
 *
 * 此类不允许键或值为 null。
 * {@link #get}, {@link #put} 或 {@link #remove} 返回 null 的含义是明确的：
 * 键不在缓存中。
 *
 * 此类出现在 Android 3.1 (Honeycomb MR1)；它作为
 * <a href="http://developer.android.com/sdk/compatibility-library.html">Android's
 * Support Package</a> 的一部分是可获取的，以支持早期系统版本。
 */
public class LruCache<K, V> {
    private final LinkedHashMap<K, V> map;

    /** 此缓存的大小（单位的数值）。 不一定是元素数量。 */
    private int size;
    private int maxSize;

    private int putCount;
    private int createCount;
    private int evictionCount;
    private int hitCount;
    private int missCount;

    /**
     * @param maxSize 对于未重写 {@link #sizeOf} 的缓存，是缓存中 entry 的
     *     最大数量。 对于其他缓存，这是缓存中 entry 大小的最大总和。
     */
    public LruCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }
        this.maxSize = maxSize;
        this.map = new LinkedHashMap<K, V>(0, 0.75f, true);
    }

    /**
     * 设置缓存大小。
     *
     * @param maxSize 新的最大容量。
     */
    public void resize(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }

        synchronized (this) {
            this.maxSize = maxSize;
        }
        trimToSize(maxSize);
    }

    /**
     * 如果缓存中存在，返回 {@code key} 的值，或由 {@code #create} 创建。
     * 如果有值返回，它将被移至队列头部。
     * 如果值没有被缓存且无法创建，返回 null。
     */
    public final V get(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        V mapValue;
        synchronized (this) {
            mapValue = map.get(key);
            if (mapValue != null) {
                hitCount++;
                return mapValue;
            }
            missCount++;
        }

        /*
         * 尝试创建一个值。这可能需要较长时间，当 create() 返回时
         * 表可能已改变。如果当 create() 运行的时候一个冲突的值加入了表中，
         * 我们保留那个值并释放创建的值。
         */

        V createdValue = create(key);
        if (createdValue == null) {
            return null;
        }

        synchronized (this) {
            createCount++;
            mapValue = map.put(key, createdValue);

            if (mapValue != null) {
                // 发生冲突，撤销之前的 put 操作
                map.put(key, mapValue);
            } else {
                size += safeSizeOf(key, createdValue);
            }
        }

        if (mapValue != null) {
            entryRemoved(false, key, createdValue, mapValue);
            return mapValue;
        } else {
            trimToSize(maxSize);
            return createdValue;
        }
    }

    /**
     * 以 {@code key} 缓存 {@code value}。值将移动到队列头部。
     *
     * @return {@code key} 对应的之前的值。
     */
    public final V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }

        V previous;
        synchronized (this) {
            putCount++;
            size += safeSizeOf(key, value);
            previous = map.put(key, value);
            if (previous != null) {
                size -= safeSizeOf(key, previous);
            }
        }

        if (previous != null) {
            entryRemoved(false, key, previous, value);
        }

        trimToSize(maxSize);
        return previous;
    }

    /**
     * 移除最老的项，直到剩余项的总大小 <= 要求的大小。
     *
     * @param maxSize 返回前缓存的最大值。可以是-1来移除甚至大小为0的元素。
     */
    public void trimToSize(int maxSize) {
        while (true) {
            K key;
            V value;
            synchronized (this) {
                if (size < 0 || (map.isEmpty() && size != 0)) {
                    throw new IllegalStateException(getClass().getName()
                            + ".sizeOf() is reporting inconsistent results!");
                }

                if (size <= maxSize) {
                    break;
                }

                Map.Entry<K, V> toEvict = map.eldest();
                if (toEvict == null) {
                    break;
                }

                key = toEvict.getKey();
                value = toEvict.getValue();
                map.remove(key);
                size -= safeSizeOf(key, value);
                evictionCount++;
            }

            entryRemoved(true, key, value, null);
        }
    }

    /**
     * 如果存在，删除 {@code key} 对应的项。
     *
     * @return the previous value mapped by {@code key}.
     */
    public final V remove(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        V previous;
        synchronized (this) {
            previous = map.remove(key);
            if (previous != null) {
                size -= safeSizeOf(key, previous);
            }
        }

        if (previous != null) {
            entryRemoved(false, key, previous, null);
        }

        return previous;
    }

    /**
     * 为已被逐出或移除的项调用。
     * 当一个值被移除以腾出空间，或被 {@link #remove} 移除，
     * 又或者被 {@link #put} 替换时，将调用此方法。
     * 默认实现为空。
     *
     * 此方法不同步：此方法执行时其他线程可以访问缓存。
     *
     * @param evicted true：如果为腾出空间移除条目；
     *                false：移除是由于调用了 {@link #put} 或 {@link #remove}。
     * @param newValue 如果存在的话，{@code key} 的新值。如果是非空的，
     *     移除操作是由于 {@link #put}。否则是由于驱逐或 {@link #remove}。
     */
    protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {}

    /**
     * 在缓存未命中后调用以计算对应 key 的值。
     * 返回计算值，如果无可计算的值则返回 null。默认实现返回 null。
     *
     * 此方法不同步：此方法执行时其他线程可以访问缓存。
     *
     * 如果此方法返回时缓存中存在对应 {@code key} 的值，
     * 创建的值将通过 {@link #entryRemoved} 释放并丢弃。
     * 当多个线程同时请求相同 key 的时候这种情况可能会发生（导致创建多个值），
     * 或当一个线程调用 {@link #put} 的同时另一线程正为同一个 key 创建值。
     */
    protected V create(K key) {
        return null;
    }

    private int safeSizeOf(K key, V value) {
        int result = sizeOf(key, value);
        if (result < 0) {
            throw new IllegalStateException("Negative size: " + key + "=" + value);
        }
        return result;
    }

    /**
     * 以用户定义的单位返回 {@code key} 和 {@code value} 项的大小。
     * 默认实现返回1，大小即为条目数量，最大大小就是条目的最大数量。
     *
     * 条目在缓存中时，其大小不能更改。
     */
    protected int sizeOf(K key, V value) {
        return 1;
    }

    /**
     * 清空缓存，对每个移除的条目调用 {@link #entryRemoved}。
     */
    public final void evictAll() {
        trimToSize(-1); // -1 将移除大小为0的元素
    }

    /**
     * 对于未重写 {@link #sizeOf} 的缓存，返回条目数。
     * 对于其他缓存，返回条目大小之和。
     */
    public synchronized final int size() {
        return size;
    }

    /**
     * 对于未重写 {@link #sizeOf} 的缓存，返回最大条目数。
     * 对于其他缓存，返回最大条目大小之和。
     */
    public synchronized final int maxSize() {
        return maxSize;
    }

    /**
     * 返回 {@link #get} 返回了缓存中已存在的值的次数。
     */
    public synchronized final int hitCount() {
        return hitCount;
    }

    /**
     * 返回 {@link #get} 返回 null 或需要创建新值的次数。
     */
    public synchronized final int missCount() {
        return missCount;
    }

    /**
     * 返回 {@link #create(Object)} 返回了值的次数。
     */
    public synchronized final int createCount() {
        return createCount;
    }

    /**
     * 返回 {@link #put} 调用的次数。
     */
    public synchronized final int putCount() {
        return putCount;
    }

    /**
     * 返回已被逐出的值的数量。
     */
    public synchronized final int evictionCount() {
        return evictionCount;
    }

    /**
     * 返回缓存当前内容的副本，按从最近最少访问到最近访问的顺序排列。
     */
    public synchronized final Map<K, V> snapshot() {
        return new LinkedHashMap<K, V>(map);
    }

    @Override public synchronized final String toString() {
        int accesses = hitCount + missCount;
        int hitPercent = accesses != 0 ? (100 * hitCount / accesses) : 0;
        return String.format("LruCache[maxSize=%d,hits=%d,misses=%d,hitRate=%d%%]",
                maxSize, hitCount, missCount, hitPercent);
    }
}