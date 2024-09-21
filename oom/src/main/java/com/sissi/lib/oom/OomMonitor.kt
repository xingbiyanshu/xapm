package com.sissi.lib.oom

import android.os.Handler
import android.os.HandlerThread
import com.sissi.lib.proc.MemInfo
import kotlin.math.min

object OomMonitor {

    private lateinit var config:Config

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
        val JAVA_HEAP_FORCE_OOM_RATIO_THRESHOLD = 0.95
        val JAVA_HEAP_REACH_THRESHOLD_COUNT_LIMIT = 5
        val JAVA_HEAP_RATIO_GAP = 0.05
        var javaHeapContinuousOverThresholdCount=0
        var lastJavaHeapRatio=0.0

        val SYSTEM_MEMORY_EXHAUSTED_THRESHOLD = 0.95

        override fun run() {
            var delay= MONITOR_INTERVAL
            val memInfo=MemInfo.get()
            val heapUsedRatio = 1-memInfo.procJavaHeapAvailableRatio
            if (1-memInfo.sysAvailableRatio > min(SYSTEM_MEMORY_EXHAUSTED_THRESHOLD, config.systemMemoryExhaustedThreshold)){
                reportOom(OomType.SystemMemoryExhausted, memInfo)
            }else if (heapUsedRatio > JAVA_HEAP_FORCE_OOM_RATIO_THRESHOLD){
                reportOom(OomType.JavaHeapOverFlow, memInfo)
            }else if (heapUsedRatio > config.javaHeapOverflowThreshold
                && heapUsedRatio >= lastJavaHeapRatio - JAVA_HEAP_RATIO_GAP){
                javaHeapContinuousOverThresholdCount++
                lastJavaHeapRatio = heapUsedRatio
                if (javaHeapContinuousOverThresholdCount == JAVA_HEAP_REACH_THRESHOLD_COUNT_LIMIT){
                    reportOom(OomType.JavaHeapOverFlow, memInfo)
                }else{
                    delay = 2
                }
            }else{
                reset()
            }
            
            handler.postDelayed(this, delay*1000)
        }
        
        fun reportOom(type:OomType, info:Any){
            listeners.forEach { 
                it.onOom(type, info)
            }
            reset()
        }
        
        fun reset(){
            javaHeapContinuousOverThresholdCount = 0
            lastJavaHeapRatio=0.0
        }
        
    }


    private val threadsInfoMonitor = object : Runnable {
        override fun run() {
            TODO("Not yet implemented")
        }

    }


    private val fdsInfoMonitor = object : Runnable {
        override fun run() {
            TODO("Not yet implemented")
        }

    }


    fun init(/*app:Application, */config: Config?=null){
        if (OomMonitor::config.isInitialized){
            return
        }
        this.config=config?:Config()
    }


    fun start(){
        handler.removeCallbacksAndMessages(null)
        handler.post(memInfoMonitor)
        handler.post(threadsInfoMonitor)
        handler.post(fdsInfoMonitor)
    }


    fun stop(){
        handler.removeCallbacksAndMessages(null)
    }


    fun addListener(listener: Listener){
        listeners.add(listener)
    }


    fun delListener(listener: Listener){
        listeners.remove(listener)
    }


    fun delAllListeners(){
        listeners.clear()
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
         * 进程在极短时间内耗用的堆内存触及该阈值则认为出现了严重的内存抖动异常，Monitor报oom
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
        /** 是否严格模式。
         * 严格模式下触即上述阈值立即上报oom，非严格模式下monitor会优化判断逻辑，如连续多次触及阈值才判定为即将OOM。*/
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
        fun onOom(type:OomType, info:Any)
    }

}