package com.sissi.apm.oom

import android.os.Handler
import android.os.HandlerThread
import com.sissi.apm.log.DefaultLogger
import com.sissi.apm.log.Logger
import com.sissi.apm.proc.FdInfo
import com.sissi.apm.proc.MemInfo
import com.sissi.apm.proc.ThreadInfo
import kotlin.math.min

object OomMonitor {

    private lateinit var config: Config

    private lateinit var logger:Logger

    private val listeners by lazy {
        mutableListOf<Listener>()
    }

    private val handlerThread by lazy {
        HandlerThread("OomMonitor").apply {
            start()
        }
    }

    private val handler by lazy {
        Handler(handlerThread.looper)
    }

    /**
     * 监控的间隔。单位：秒
     */
    private const val MONITOR_INTERVAL=10L
    
    private val memInfoMonitor = object : Runnable {
        // java堆使用比例（已用/最大）硬性限制。超过该限制直接上报oom，不论用户设置的比例是多少。
        val JAVA_HEAP_FORCE_OOM_RATIO_THRESHOLD = 0.95
        // java堆使用比例触及用户设置的限制，允许的次数，达到该次数才报oom
        val JAVA_HEAP_OVER_THRESHOLD_COUNT_LIMIT = 3
        /** 当java堆使用比例触及用户设置的限制后，我们开始密切关注后续java堆变化情况，若持续超限，且回落的幅度小于该值
          则超限计数[javaHeapContinuousOverThresholdCount]加一，超限次数达到[JAVA_HEAP_OVER_THRESHOLD_COUNT_LIMIT]则报oom*/
        val JAVA_HEAP_RATIO_FALL_BACK_GAP = 0.05  // 192*0.05=9.6, 384*0.05=19.2, 512*0.05=25.6
        var javaHeapContinuousOverThresholdCount=0
        var lastJavaHeapRatio=0.0

        val SYSTEM_MEMORY_EXHAUSTED_THRESHOLD = 0.95

        override fun run() {
            val lastMemInfo = MemInfo.lastRecord
            val memInfo= MemInfo.get()
            val heapUsedRatio = 1-memInfo.procJavaHeapAvailableRatio
            var oomType:OomType?=null
            var oomConfirming = false
            if (1-memInfo.sysAvailableRatio > min(SYSTEM_MEMORY_EXHAUSTED_THRESHOLD, config.systemMemoryExhaustedThreshold)){
                oomType = OomType.SystemMemoryExhausted
            }else if (heapUsedRatio > JAVA_HEAP_FORCE_OOM_RATIO_THRESHOLD){
                oomType = OomType.JavaHeapOverFlow
            }else if (lastMemInfo!=null && memInfo.procJavaHeap-lastMemInfo.procJavaHeap > config.javaHeapSuddenlySwellThreshold){
                oomType = OomType.JavaHeapSuddenlySwell
            }else if (heapUsedRatio > config.javaHeapOverflowThreshold){
                if (config.strictMode){
                    oomType = OomType.JavaHeapOverFlow
                }else if (heapUsedRatio >= lastJavaHeapRatio - JAVA_HEAP_RATIO_FALL_BACK_GAP){
                    javaHeapContinuousOverThresholdCount++
                    if (javaHeapContinuousOverThresholdCount == JAVA_HEAP_OVER_THRESHOLD_COUNT_LIMIT){
                        oomType = OomType.JavaHeapOverFlow
                    }else{
                        // 为了尽量准确判断，我们要进一步确认
                        oomConfirming = true
                    }
                }
            }

            if (oomType!=null){
                reportOom(oomType, memInfo)
            }

            if (!oomConfirming){
                javaHeapContinuousOverThresholdCount = 0
            }

            lastJavaHeapRatio = heapUsedRatio

            handler.postDelayed(this, if (oomConfirming) 2000L else MONITOR_INTERVAL)
        }

    }


    private val threadsInfoMonitor = object : Runnable {
        val OVER_THRESHOLD_COUNT_LIMIT = 3
        val FALL_BACK_GAP = 50
        var continuousOverThresholdCount=0
        var lastCount=0

        override fun run() {
            val count = ThreadInfo.getCount()
            var oomType:OomType?=null
            var oomConfirming = false
            if (count > config.threadThreshold){
                if (config.strictMode){
                    oomType = OomType.ThreadOverFlow
                }else if (count >= lastCount - FALL_BACK_GAP) {
                    continuousOverThresholdCount++
                    if (continuousOverThresholdCount == OVER_THRESHOLD_COUNT_LIMIT) {
                        oomType = OomType.ThreadOverFlow
                    } else {
                        oomConfirming = true
                    }
                }
            }

            if (oomType!=null){
                reportOom(oomType, ThreadInfo.getAll())
            }

            if (!oomConfirming){
                continuousOverThresholdCount = 0
            }

            lastCount = count

            handler.postDelayed(this, if (oomConfirming) 2000L else MONITOR_INTERVAL)
        }

    }


    private val fdsInfoMonitor = object : Runnable {
        val OVER_THRESHOLD_COUNT_LIMIT = 3
        val FALL_BACK_GAP = 50
        var continuousOverThresholdCount=0
        var lastCount=0

        override fun run() {
            val count = FdInfo.getCount()
            var oomType:OomType?=null
            var oomConfirming = false
            if (count > config.fdThreshold){
                if (config.strictMode){
                    oomType = OomType.FdExhausted
                }else if (count >= lastCount - FALL_BACK_GAP){
                    continuousOverThresholdCount++
                    if (continuousOverThresholdCount==OVER_THRESHOLD_COUNT_LIMIT){
                        oomType = OomType.FdExhausted
                    }else{
                        oomConfirming=true
                    }
                }
            }

            if (oomType!=null){
                reportOom(oomType, FdInfo.getAll())
            }

            if (!oomConfirming){
                continuousOverThresholdCount = 0
            }

            lastCount = count

            handler.postDelayed(this, if (oomConfirming) 2000L else MONITOR_INTERVAL)
        }
    }


    fun init(/*app:Application, */config: Config=Config(), logger:Logger=DefaultLogger()){
        logger.i("OomMonitor init!")
        if (OomMonitor::config.isInitialized){
            logger.e("OomMonitor inited already!")
            return
        }
        this.config =config
        this.logger = logger
    }


    fun start(){
        logger.i("OomMonitor start!")
        handler.removeCallbacksAndMessages(null)
        handler.post(memInfoMonitor)
        handler.post(threadsInfoMonitor)
        handler.post(fdsInfoMonitor)
    }


    fun stop(){
        logger.i("OomMonitor stop!")
        handler.removeCallbacksAndMessages(null)
    }


    fun addListener(listener: Listener){
        logger.i("OomMonitor add listener: $listener")
        listeners.add(listener)
    }


    fun delListener(listener: Listener){
        logger.i("OomMonitor del listener: $listener")
        listeners.remove(listener)
    }


    fun delAllListeners(){
        logger.i("OomMonitor del all listeners!")
        listeners.clear()
    }


    fun reportOom(type: OomType, info:Any){
        listeners.forEach {
            it.onOom(type, info)
        }
    }


    /**
     * 监视器配置。
     * 系统的某些阈值可能远大于此处设置的阈值，如fd、线程，尤其是64位设备。
     * 然而此处设置这些阈值的意义在于它们是我们监控进程内存状态是否健康的手段。
     * 触及这些阈值说明极有可能进程内存状态严重异常。
     * */
    class Config(
        /**
         * java堆内存溢出阈值（已用占比）。
         * 触及该值则认为进程可用java堆内存即将耗尽，Monitor报oom
         * */
        val javaHeapOverflowThreshold:Double=0.9,
        /**
         * java堆内存急剧膨胀的阈值。单位：MB
         * 进程在短时间内耗用的堆内存触及该阈值则认为出现了严重的内存抖动异常，Monitor报oom
         */
        val javaHeapSuddenlySwellThreshold:Int=300,
        /**
         * 系统内存告急的阈值（已用占比）。
         * 触及该阈值则认为系统内存告急，Monitor报oom
         */
        val systemMemoryExhaustedThreshold:Double=0.9,
        /**
         * 进程可用文件描述符耗尽的阈值。
         * 触及该阈值则认为进程可用的文件描述符即将耗尽，Monitor报oom
         */
        val fdThreshold:Int=1000,
        /**
         * 进程可分配线程耗尽的阈值。
         * 触及该阈值则认为进程可分配的线程即将耗尽，Monitor报oom
         */
        val threadThreshold:Int=700,
        /** 是否严格模式。严格true，非严格false，默认false。
         * 严格模式下触及上述阈值立即上报oom，非严格模式下monitor会在参考上述阈值的前提下优化判断逻辑以求结果更准确。
         * 无特殊需求建议使用非严格模式。*/
        val strictMode:Boolean =false,
    )


    enum class OomType{
        JavaHeapOverFlow,
        JavaHeapSuddenlySwell,
        SystemMemoryExhausted,
        CmaFailed,
        FdExhausted,
        ThreadOverFlow,
    }


    interface Listener{
        fun onOom(type: OomType, info:Any)
    }

}