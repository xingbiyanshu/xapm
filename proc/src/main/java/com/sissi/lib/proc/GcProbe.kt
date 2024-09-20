package com.sissi.lib.proc

import java.lang.ref.SoftReference
import java.lang.ref.WeakReference

/**
 * GC事件监视器
 */
object GcProbe {

    private var probe: WeakReference<Probe>? = null

    private var heavyGcProbe: SoftReference<HeavyGcProbe>? = null

    private val gcListeners by lazy {
        ArrayList<() -> Unit>()
    }
    private val heavyGcListeners by lazy {
        ArrayList<() -> Unit>()
    }
    private val lock = Any()

    private var lastGcTimeStamp = System.currentTimeMillis()

    private class Probe {
        @Throws(Throwable::class)
        protected fun finalize() {
            synchronized(lock) {
                try {
                    val curGcTimeStamp = System.currentTimeMillis()
                    for (l in gcListeners) {
                        l.invoke()
                    }
                    lastGcTimeStamp = curGcTimeStamp
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                probe = WeakReference(Probe())
            }
        }
    }

    private class HeavyGcProbe {
        @Throws(Throwable::class)
        protected fun finalize() {
            synchronized(lock) {
                try {
                    for (l in heavyGcListeners) {
                        l.invoke()
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                heavyGcProbe = SoftReference(HeavyGcProbe())
            }
        }
    }


    /**
     * 添加GC事件监听器。
     * 当发生一次GC就回调一次。
     */
    @JvmStatic
    fun addGcListener(listener: () -> Unit) {
        synchronized(lock) {
            gcListeners.add(listener)
            if (probe ==null){
                probe = WeakReference(Probe())
            }
        }
    }

    @JvmStatic
    fun removeGcListener(listener: () -> Unit) {
        synchronized(lock) {
            gcListeners.remove(listener)
            if (gcListeners.isEmpty()){
                probe = null
            }
        }
    }

    @JvmStatic
    fun removeAllGcListeners() {
        synchronized(lock) {
            gcListeners.clear()
            probe = null
        }
    }

    /**
     * 添加“繁重”GC事件监听器。
     * 当发生频繁的GC时回调，此时往往异常着程序运行异常，如即将内存溢出。
     */
    @JvmStatic
    fun addHeavyGcListener(listener: () -> Unit) {
        synchronized(lock) {
            heavyGcListeners.add(listener)
            if (heavyGcProbe ==null){
                heavyGcProbe = SoftReference(HeavyGcProbe())
            }
        }
    }

    @JvmStatic
    fun removeHeavyGcListener(listener: () -> Unit) {
        synchronized(lock) {
            heavyGcListeners.remove(listener)
            if (heavyGcListeners.isEmpty()){
                heavyGcProbe = null
            }
        }
    }

    @JvmStatic
    fun removeAllHeavyGcListeners() {
        synchronized(lock) {
            heavyGcListeners.clear()
            heavyGcProbe = null
        }
    }

}
