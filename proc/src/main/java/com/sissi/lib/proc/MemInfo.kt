package com.sissi.lib.proc

import android.os.Debug
import java.io.File

class MemInfo private constructor(){

    private var _sysTotal=0
    /**
     * 系统总内存。单位MB
     */
    val sysTotal:Int
        get() {
            return _sysTotal
        }

    private var _sysFree=0
    /**
     * 系统空闲内存。单位MB
     */
    val sysFree:Int
        get() {
            return _sysFree
        }

    private var _sysAvailable=0
    /**
     * 系统可用内存。单位MB
     * 可用内存大于空闲内存，有些内存虽然被使用但可在需要时释放。
     */
    val sysAvailable:Int
        get() {
            return _sysAvailable
        }

    private var _sysCmaTotal=0
    /**
     * 系统CMA(Contiguous Memory Allocator)总内存。单位MB
     */
    val sysCmaTotal:Int
        get() {
            return _sysCmaTotal
        }

    private var _sysCmaFree=0
    /**
     * 系统CMA(Contiguous Memory Allocator)空闲内存。单位MB
     */
    val sysCmaFree:Int
        get() {
            return _sysCmaFree
        }

    private var _sysAvailableRatio=0.0
    /**
     * 系统可用内存比例。
     */
    val sysAvailableRatio:Double
        get() {
            return _sysAvailableRatio
        }


//    private var _procJavaHeapOomThreshold=0
//    /**
//     * 进程java堆内存上限。单位MB
//     */
//    val procJavaHeapOomThreshold:Int
//        get() {
//            return _procJavaHeapOomThreshold
//        }
//
//    private var _procLargeJavaHeapOomThreshold=0
//    /**
//     * 当设置了“LargeJavaHeap=true”时进程java堆内存上限。单位MB
//     */
//    val procLargeJavaHeapOomThreshold:Int
//        get() {
//            return _procLargeJavaHeapOomThreshold
//        }

    private var _procJavaHeap=0.0
    /**
     * 进程java堆内存使用。单位MB
     * 超过[procJavaHeapOomThreshold]或[procLargeJavaHeapOomThreshold]（当“LargeJavaHeap=true”时）
     * 会导致OOM
     */
    val procJavaHeap:Double
        get() {
            return _procJavaHeap
        }

    private var _procNativeHeap=0.0
    /**
     * 进程native堆内存使用。单位MB
     */
    val procNativeHeap:Double
        get() {
            return _procNativeHeap
        }

    private var _procCode=0.0
    /**
     * 进程code占用内存。单位MB
     */
    val procCode:Double
        get() {
            return _procCode
        }

    private var _procStack=0.0
    /**
     * 进程栈内存占用。单位MB
     */
    val procStack:Double
        get() {
            return _procStack
        }

    private var _procGraphics=0.0
    /**
     * 进程Graphics占用内存。单位MB
     */
    val procGraphics:Double
        get() {
            return _procGraphics
        }

    private var _procTotalPss=0.0
    /**
     * 进程总占用内存。单位MB
     */
    val procTotalPss:Double
        get() {
            return _procTotalPss
        }



    companion object{

        private val K=1024

        fun get() : MemInfo{
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
            mi._sysAvailableRatio = (mi._sysAvailable.toDouble()/mi._sysTotal).clip()

            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            for((key, value) in memInfo.memoryStats){
                val refinedVal = (value.toDouble()/K).clip()
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

            return mi
        }
    }

    override fun toString(): String {
        return "MemInfo(MB)(" +"\n"+
                "sysTotal=$sysTotal, " +"\n"+
                "sysFree=$sysFree, " +"\n"+
                "sysAvailable=$sysAvailable, " +"\n"+
                "sysCmaTotal=$sysCmaTotal, " +"\n"+
                "sysCmaFree=$sysCmaFree, " +"\n"+
                "sysAvailableRatio=$sysAvailableRatio, " +"\n"+
                "procJavaHeap=$procJavaHeap, " +"\n"+
                "procNativeHeap=$procNativeHeap, " +"\n"+
                "procCode=$procCode, " +"\n"+
                "procStack=$procStack, " +"\n"+
                "procGraphics=$procGraphics, " +"\n"+
                "procTotalPss=$procTotalPss" +"\n"+
                ")"
    }

}

fun Double.clip() = String.format("%.2f", this).toDouble()