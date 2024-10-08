package com.sissi.apm.proc

import android.os.Debug
import java.io.File

class MemInfo private constructor(){

    private var _sysTotal=0
    /**
     * 系统总内存。单位MB
     */
    val sysTotal:Int
        get() =_sysTotal

    private var _sysFree=0
    /**
     * 系统空闲内存。单位MB
     */
    val sysFree:Int
        get() =_sysFree

    private var _sysAvailable=0
    /**
     * 系统可用内存。单位MB
     * 可用内存大于空闲内存，有些内存虽然被使用但可在需要时释放。
     */
    val sysAvailable:Int
        get() =_sysAvailable

    /**
     * 系统可用内存比例。
     */
    val sysAvailableRatio:Double
        get() =(_sysAvailable.toDouble()/_sysTotal).clip()

    private var _sysCmaTotal=0
    /**
     * 系统CMA(Contiguous Memory Allocator)总内存。单位MB
     */
    val sysCmaTotal:Int
        get() =_sysCmaTotal

    private var _sysCmaFree=0
    /**
     * 系统CMA(Contiguous Memory Allocator)空闲内存。单位MB
     */
    val sysCmaFree:Int
        get() =_sysCmaFree


    private var _procJavaHeapOomThreshold=0
    /**
     * 进程java堆内存上限，触及该上限进程将崩溃报OOM。单位MB
     */
    val procJavaHeapOomThreshold:Int
        get()= _procJavaHeapOomThreshold

    private var _procJavaHeap=0.0
    /**
     * 进程java堆内存已使用。单位MB
     */
    val procJavaHeap:Double
        get()=_procJavaHeap

    /**
     * 进程java堆内存可用比例。
     */
    val procJavaHeapAvailableRatio:Double
        get() =((_procJavaHeapOomThreshold-_procJavaHeap)/_procJavaHeapOomThreshold).clip()

    private var _procNativeHeap=0.0
    /**
     * 进程native堆内存使用。单位MB
     */
    val procNativeHeap:Double
        get() =_procNativeHeap

    private var _procCode=0.0
    /**
     * 进程code占用内存。单位MB
     */
    val procCode:Double
        get() =_procCode

    private var _procStack=0.0
    /**
     * 进程栈内存占用。单位MB
     */
    val procStack:Double
        get() =_procStack

    private var _procGraphics=0.0
    /**
     * 进程Graphics占用内存。单位MB
     */
    val procGraphics:Double
        get() =_procGraphics

    private var _procTotalPss=0.0
    /**
     * 进程总占用内存。单位MB
     */
    val procTotalPss:Double
        get() =_procTotalPss



    companion object{

        private val K=1024

        /**
         * 最近一次的内存信息
         * 注意：最近一次信息会伴随[get]调用更新，所以如果要比对最近一次信息和当前信息，请先获取最近一次的再获取当前的。
         * */
        var _lastRecord:MemInfo?=null

        val lastRecord:MemInfo?
            get()=_lastRecord

        /**
         * 获取当前内存信息
         */
        fun get() : MemInfo {
            val mi = MemInfo()

            run{
                val MEM_TOTAL_REGEX = "MemTotal:\\s*(\\d+)\\s*kB".toRegex()
                val MEM_FREE_REGEX = "MemFree:\\s*(\\d+)\\s*kB".toRegex()
                val MEM_AVA_REGEX = "MemAvailable:\\s*(\\d+)\\s*kB".toRegex()
                val CMA_TOTAL_REGEX = "CmaTotal:\\s*(\\d+)\\s*kB".toRegex()
                val CMA_FREE_REGEX = "CmaFree:\\s*(\\d+)\\s*kB".toRegex()

                fun Regex.matchValue(s: String) = matchEntire(s.trim())
                    ?.groupValues?.getOrNull(1)?.toInt() ?: 0

                File("/proc/meminfo").forEachLine { line ->
                    line.run {
                        when {
                            startsWith("MemTotal") -> mi._sysTotal = MEM_TOTAL_REGEX.matchValue(line) / K
                            startsWith("MemFree") -> mi._sysFree = MEM_FREE_REGEX.matchValue(line) / K
                            startsWith("MemAvailable") -> mi._sysAvailable = MEM_AVA_REGEX.matchValue(line) / K
                            startsWith("CmaTotal") -> mi._sysCmaTotal = CMA_TOTAL_REGEX.matchValue(line) / K
                            startsWith("CmaFree") -> mi._sysCmaFree = CMA_FREE_REGEX.matchValue(line) / K
                        }
                    }
                }
            }

            mi._procJavaHeapOomThreshold = (Runtime.getRuntime().maxMemory()/ K / K).toInt()

            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            for((key, value) in memInfo.memoryStats){
                val refinedVal = (value.toDouble()/ K).clip()
                key.run{
                    when{
                        endsWith(".java-heap") -> mi._procJavaHeap=refinedVal
                        endsWith(".native-heap") -> mi._procNativeHeap=refinedVal
                        endsWith(".code") -> mi._procCode=refinedVal
                        endsWith(".stack") -> mi._procStack=refinedVal
                        endsWith(".graphics") -> mi._procGraphics=refinedVal
                        endsWith(".total-pss") -> mi._procTotalPss=refinedVal
                    }
                }
            }

            _lastRecord = mi

            return mi
        }

        /**
         * 获取进程允许使用的java堆内存上限。单位：MB
         */
        fun getProcJavaHeapOomThreshold() = (Runtime.getRuntime().maxMemory()/ K / K).toInt()

    }

    override fun toString(): String {
        return "MemInfo(MB)(" +"\n"+
                "sysTotal=$sysTotal, " +"\n"+
                "sysFree=$sysFree, " +"\n"+
                "sysAvailable=$sysAvailable, " +"\n"+
                "sysAvailableRatio=$sysAvailableRatio, " +"\n"+
                "sysCmaTotal=$sysCmaTotal, " +"\n"+
                "sysCmaFree=$sysCmaFree, " +"\n"+
                "procJavaHeapOomThreshold=$procJavaHeapOomThreshold, " +"\n"+
                "procJavaHeap=$procJavaHeap, " +"\n"+
                "procJavaHeapAvailableRatio=$procJavaHeapAvailableRatio, " +"\n"+
                "procNativeHeap=$procNativeHeap, " +"\n"+
                "procCode=$procCode, " +"\n"+
                "procStack=$procStack, " +"\n"+
                "procGraphics=$procGraphics, " +"\n"+
                "procTotalPss=$procTotalPss" +"\n"+
                ")"
    }

}

private fun Double.clip() = String.format("%.2f", this).toDouble()