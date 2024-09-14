package com.sissi.lib.procres

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.system.Os
import androidx.annotation.MainThread
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

object ProcessResourceState {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    private lateinit var context:Application

    private lateinit var sb:StringBuilder

    private var spaceHolder:StringBuilder? = null

    private var mainThreadNativeId=-1


    /**
     * 初始化。
     * 使用其它接口前需先调用该接口；
     * 该接口需在主线程调用
     */
    @JvmStatic
    @MainThread
    fun init(app:Application){
        if (ProcessResourceState::context.isInitialized){
            return
        }
        context = app
        mainThreadNativeId = Process.myTid()
        sb = StringBuilder(100*1024) // 预定义足够大的sb以防oom时模块无法工作
        spaceHolder = StringBuilder(100*1024) // 预定义一个大对象用于占位以防oom时模块无法工作
    }

    /**
     * 获取进程当前资源使用情况。
     * 结果是其它几个接口结果的汇总。
     * @param detailMode 若为true则某些具有详细信息的章节将打印详细信息，否则打印精简信息。
     * @return 格式化过的字符串形式的进程当前资源使用情况
     */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun getState(context: Context, detailMode: Boolean = false):String{
        val curTs = dateFormat.format(Date(System.currentTimeMillis()))
        sb.setLength(0)
        sb.append("/-------------------------$curTs Process(${Process.myPid()}) resource-usage state -------------------------\n")
        getLimits(sb)
        getStatus(sb)
        getSysMemInfo(sb)
        getCpuMemoryInfo(context, sb)
//        getAllThreads(sb)
        getJvmThreadsInfo(sb, detailMode)
        getNativeThreadsInfo(sb)
        getFdInfo(sb, detailMode)
        spaceHolder = null // 释放占位对象以求在内存吃紧情况下能为下面的新建string腾出空间
        val stateStr = sb.toString()
        try {
            spaceHolder = StringBuilder(100*1024) // 继续占位。（内存吃紧时，此处可能失败）
        }catch (e:Exception){
            // 很可能即将OOM了
        }
        return stateStr
    }


    /**
     * 获取进程资源使用限制。
     * 结果读取自"/proc/self/limits"
     * @param resultReceiver 用于接收结果。
     */
    @JvmStatic
    @JvmOverloads
    fun getLimits(resultReceiver:StringBuilder?=null){
        val p = "/proc/self/limits"
        var sb = resultReceiver
        if (sb == null){
            sb=StringBuilder()
        }
        sb.append("/--- ").append(p).append(":\n")
        File(p).forEachLine {
            sb.append("$it\n")
        }
    }

    /**
     * 获取进程状态。
     * 结果读取自"/proc/self/status"
     * @param resultReceiver 用于接收结果。
     */
    @JvmStatic
    @JvmOverloads
    fun getStatus(resultReceiver:StringBuilder?=null){
        val p = "/proc/self/status"
        var sb = resultReceiver
        if (sb == null){
            sb=StringBuilder()
        }
        sb.append("/--- ").append(p).append(":\n")
        File(p).forEachLine {
            sb.append("$it\n")
        }
    }


    /**
     * 获取系统内存状况。
     * 结果读取自"/proc/meminfo"
     * @param resultReceiver 用于接收结果。
     */
    @JvmStatic
    @JvmOverloads
    fun getSysMemInfo(resultReceiver:StringBuilder?=null){
        val p = "/proc/meminfo"
        var sb = resultReceiver
        if (sb == null){
            sb=StringBuilder()
        }
        sb.append("/--- ").append(p).append(":\n")
        File(p).forEachLine {
            sb.append("$it\n")
        }
    }


    private var oomThreshold = 0
    private var largeHeapOomThreshold = 0

    /**
     * 获取CPU内存使用情况
     * @param resultReceiver 用于接收结果。
     */
    @JvmStatic
    @JvmOverloads
    fun getCpuMemoryInfo(context: Context, resultReceiver:StringBuilder?=null){
        var sb = resultReceiver
        if (sb == null){
            sb=StringBuilder()
        }
        // TODO 获取CPU使用情况

        // 获取内存使用情况
        sb.append("/--- memory state(unit:MB):\n")
        val k = 1024
        val m = 1024*k
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        am?.run {
            val sysMemInfo = ActivityManager.MemoryInfo()
            getMemoryInfo(sysMemInfo)
            sysMemInfo.run {
                sb.append("system{\n" +
                        "\ttotal:${totalMem/m}, \n" +
                        "\tavailable:${availMem/m}, \n" +
                        "\tthreshold:${threshold/m},(start killing process when available memory reach this value.)\n" +
                        "\tisLowMemory:${lowMemory},(`true` if the system considers itself to be in a low memory situation.)\n" +
                        "}\n"
                )
            }

            if (oomThreshold==0 || largeHeapOomThreshold==0) {
                val myMemInfo = getProcessMemoryInfo(intArrayOf(Process.myPid()))[0] // 该接口被限制调用频率导致拿不到准确的值
                myMemInfo.run {
                    /*
                    * memoryClass对应的是dalvik.vm.heapgrowthlimit，默认情况下，进程“Java堆内存”超过这个阈值则会触发OOM（Native层不受这个限）。
                    * 若manifest中指定android:largeHeap为true，则进程可以超过heapgrowthlimit最多达到dalvik.vm.heapsize。
                    * 可通过命令查看这两个阈值：
                    * xingbiyanshu@xingbiyanshu-pc:~$ adb shell getprop | grep dalvik.vm.heap
                        [dalvik.vm.heapgrowthlimit]: [384m]
                        [dalvik.vm.heapmaxfree]: [8m]
                        [dalvik.vm.heapminfree]: [512k]
                        [dalvik.vm.heapsize]: [512m]
                        [dalvik.vm.heapstartsize]: [16m]
                        [dalvik.vm.heaptargetutilization]: [0.75]
                    * */
//                    sb.append(
//                        "my process(${Process.myPid()})" +
//                                "{OOM Threshold:${memoryClass}(${largeMemoryClass} if android:largeHeap=\"true\"), " +
//                                "totalPss:${totalPss / k}, " +
//                                "dalvikPss:${dalvikPss / k}, " +
//                                "nativePss:${nativePss / k}, " +
//                                "otherPss:${otherPss / k}\n"
//                    )

                    oomThreshold = memoryClass
                    largeHeapOomThreshold = largeMemoryClass
                }
            }
        }

        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        val memStats = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            memInfo.memoryStats
        } else {
            emptyMap<String, String>()
        }
        if (memStats.isNotEmpty()){
            sb.append("process{\n")
            sb.append("\tjavaheap.oomThreshold:$oomThreshold,\n")
            sb.append("\tjavaheap.largeHeapOomThreshold:$largeHeapOomThreshold,\n")
            for((key, value) in memStats){
                val v = try{
                    String.format("%.2f,", value.toDouble()/k)
                }catch (e:Exception){
                    "unknown"
                }
                sb.append("\t").append(key).append(":").append(v).append("\n")
            }
            sb.append("}\n")
        }
    }


    private const val NATIVE_THR_STAT_HEAD_FORMAT = "%-10s %-20s"
    private val nativeThrStatHead = String.format(NATIVE_THR_STAT_HEAD_FORMAT, "Id(native)", "Name")
    private const val JVM_THR_STAT_HEAD_FORMAT = "%-10s %-40s %-15s %-10s %-10s"
    private val jvmThrStatHead = String.format(JVM_THR_STAT_HEAD_FORMAT, "Id", "Name", "State", "Priority", "isDaemon")
    private const val JVM_THR_STAT_HEAD_DETAIL_FORMAT = "%-10s %-40s %-15s %-10s %-10s %s"
    private val jvmThrStatHeadDetail = String.format(JVM_THR_STAT_HEAD_DETAIL_FORMAT, "Id", "Name", "State", "Priority", "isDaemon", "StackTrace")
    private val lengthBeforeStackTrace = jvmThrStatHeadDetail.length - "StackTrace".length-1

    /**
     * 获取进程的所有线程（包括JVM线程和Native线程）。
     * 结果读取自"/proc/self/task"
     * @param resultReceiver 用于接收结果。
     */
    @JvmStatic
    @JvmOverloads
    fun getAllThreads(resultReceiver:StringBuilder?=null){
        val p = "/proc/self/task"
        var sb = resultReceiver
        if (sb == null){
            sb=StringBuilder()
        }

        val allThreads = runCatching { File("/proc/self/task").listFiles() }
            .getOrElse {
                return@getOrElse emptyArray()
            }?.associate {
                val thrId = it.name
                val thrName = runCatching {
                    File(it,"comm").readText()
                }.getOrElse { "failed to read $it/comm" }.removeSuffix("\n")
                thrId to thrName
            }?: emptyMap()

        sb.append("/--- ").append(p).append(" (threads counts=${allThreads.size}):\n")
        sb.append(nativeThrStatHead).append("\n")
        for ((id, name) in allThreads){
            sb.append(String.format(NATIVE_THR_STAT_HEAD_FORMAT, id, name)).append("\n")
        }
    }

    /**
     * 获取进程中的JVM线程信息
     * @param resultReceiver 用于接收结果。
     * @param detailMode 是否详情模式。详情模式下会包含诸如调用栈等信息。
     */
    @JvmStatic
    @JvmOverloads
    fun getJvmThreadsInfo(resultReceiver:StringBuilder?=null, detailMode: Boolean = false) {
        var sb = resultReceiver
        if (sb == null){
            sb=StringBuilder()
        }
        val allStackTraces = Thread.getAllStackTraces()
        sb.append("/--- jvm threads (counts=${allStackTraces.size}):\n")
        val format = if (detailMode){
            sb.append(jvmThrStatHeadDetail).append("\n")
            JVM_THR_STAT_HEAD_DETAIL_FORMAT
        }else{
            sb.append(jvmThrStatHead).append("\n")
            JVM_THR_STAT_HEAD_FORMAT
        }

        val tsb=StringBuilder()
        for ((k, v) in allStackTraces){
            val name = if (k.name.length>40){
                val diff = k.name.length-40
                k.name.replaceRange(k.name.length-diff-2,k.name.length, "..")
            }else k.name
            if (detailMode){
                tsb.setLength(0)
                v.forEachIndexed { index, trace ->
                    if (index==0){
                        tsb.append("$trace\n")
                    }else{
                        tsb.append(" ".repeat(lengthBeforeStackTrace))
                        tsb.append("$trace\n")
                    }
                }
                sb.append(String.format(format, k.id, name, k.state, k.priority, k.isDaemon, tsb.toString())).append("\n")
            }else{
                sb.append(String.format(format, k.id, name, k.state, k.priority, k.isDaemon)).append("\n")
            }
        }
    }


    /**
     * 获取进程中的Native线程信息
     * @param resultReceiver 用于接收结果。
     */
    @JvmStatic
    @JvmOverloads
    fun getNativeThreadsInfo(resultReceiver:StringBuilder?=null) {
        var sb = resultReceiver
        if (sb == null){
            sb=StringBuilder()
        }
        val allThreadsWithoutMain = runCatching { File("/proc/self/task").listFiles() }
            .getOrElse {
                return@getOrElse emptyArray()
            }?.associate {
                val thrId = it.name
                val thrName = runCatching {
                    File(it,"comm").readText()
                }.getOrElse { "failed to read $it/comm" }.removeSuffix("\n")
                thrId to thrName
            }?.filter {(id, name)->  // 过滤掉主线程
                mainThreadNativeId.toString()!=id
            }?: emptyMap()

        val jvmThreads = Thread.getAllStackTraces().keys
        val nativeThreads = mutableListOf<String>()
//        val myThreads = mutableListOf<String>()

        for ((id, name) in allThreadsWithoutMain){
            var isJvmThr=false
            run{
                jvmThreads.forEach { jvmThr->
                    if (jvmThr.name == name /*FIXME 线程名是可以重复的，所以此处可能误判，但是想通过id比对又难以实施，因为"/proc/self/task"中的threadId是native层的，
                                               os中的真实线程id，而jvmThread拿到的id只是jvm中的一个序号而已，两者无法比对*/
                        || (name.length==15  /* FIXME "/proc/self/task"中的线程名长度超过15会被截断，所以我们做此判断，但这样做仍有可能误判。*/
                                && jvmThr.name.contains(name))
                    ){
                        isJvmThr = true
                        jvmThreads.remove(jvmThr) //FIXME 由于线程名可以重复，我们此处边比对边删除以尽力规避这种影响，但是有可能将同名线程错误归类
                        return@run // return到run是有必要的，直接return到foreach会有ConcurrentModifyException
                    }
                }
            }
            if (!isJvmThr) {
                nativeThreads.add(String.format(NATIVE_THR_STAT_HEAD_FORMAT, id, name))
            }
//            else{
//                myThreads.add(id+"\t"+name)
//            }
        }
//        sb.append("/--- non-matched jvmThreads:\n")
//        jvmThreads.forEach {
//            sb.append(it.name).append("\n")
//        }
//        sb.append("/--- my threads (all Threadscounts=${allThreadsWithoutMain.size}, counts=${myThreads.size}):\n")
//        myThreads.forEach {
//            sb.append(it).append("\n")
//        }

        sb.append("/--- native threads (counts=${nativeThreads.size}):\n")
        sb.append(nativeThrStatHead).append("\n")
        nativeThreads.forEach {
            sb.append(it).append("\n")
        }
    }


    /**
     * 获取进程中的文件描述符信息
     * @param resultReceiver 用于接收结果。
     * @param detailMode 是否详情模式。详情模式下会打印每一个fd，fd可能有很多，若禁用详情模式则某些类别fd仅有汇总信息。
     */
    @JvmStatic
    @JvmOverloads
    fun getFdInfo(resultReceiver:StringBuilder?=null, detailMode: Boolean = false) {
        var anonInodeEventFdCount=0     /* anon_inode:[eventfd]
                                           匿名inode的fd，没有专门的inode，而是链接到内核一个公用的唯一的inode。通过eventfd创建的。
                                           一个Looper会创建一组"eventfd和eventpoll"用于线程间通信（HandlerThread包含一个Looper）*/
        var anonInodeEventpollCount=0   /* anon_inode:[eventpoll]
                                           匿名inode的fd，通过epoll_create创建的。*/
        var anonInodeSyncFileCount=0    /* anon_inode:sync_file
                                           匿名inode的fd，通常用于代表临时数据或进程通信*/
        var anonInodeDmabufCount=0      /*anon_inode:dmabuf*/
        var ashmemCount=0               /* /dev/ashmem6dde78f2-72dd-4185-b145-db9b828dc01a
                                           匿名共享内存一般用于进程间传递大块数据，如provider机制，带bitmap的通知等，因为
                                           共享内存是最高效的，这种场景下其他通信方式就不是首选了，如binder之类。
                                           Java线程或attach到JVM的native线程会创建一个ashmem（匿名共享内存）；
                                           数据库cursor也会创建ashmem；
                                           BitMap在IPC传递时需要打开fd，实际上是通过传递指向bitmap所在的匿名共享内存的fd进行传递的，如带Bitmap的通知。
                                           content provider用于大块数据的跨进程传递，所以也是基于匿名共享内存的。
                                           */
        var socketCount=0               /* socket:[2434493] "[]"中的是inode
                                           除了应用的网络相关操作会创建socket外，很多其它操作也会导致。framework层很多模块的进程间通信机制是采用的socket，如InputManagerService和客户端（App）之间。
                                           新增Window会创建一对socket用于构造inputchannel，以实现从InputDispatcher那里得到input事件。除了Activity外，Dialog，Toast，PopWindow等都会新增Window。
                                        */
        var pipeCount=0                 // pipe:[2417584]  "[]"中的是inode
        var binderCount=0               // /dev/binderfs/hwbinder
        var jarCount=0                  // /system/framework/framework.jar
        var apkCount=0                  // /data/app/~~UZ8Vx8Q4ceuA6JQzURK7nA==/com.kedacom.truetouch.sky.rtc-7qs-Hleg9KQ4_huj7YS89g==/base.apk
        var devnullCount=0              // /dev/null
        var dmabuffCount=0              /* /dmabuf:METADATA
                                           dma_buf是内核中一个独立的子系统，提供了一个让不同设备、子系统之间进行共享缓存的统一框架，这里说的缓存通常是指通过DMA方式访问的和硬件交互的内存。
                                           比如，来自摄像头采集的通过pciv驱动传输的内存、gpu内部管理的内存等等
                                           */
        var notFoundCount=0             // readlink(/proc/12319/fd/125) failed: android.system.ErrnoException: readlink failed: ENOENT (No such file or directory)

        var othersCount=0               /* 一般为普通文件，类似/storage/emulated/0/Android/data/com.example.launchmodedemo/files/fd_test/f0。
                                            在代码中体现为未关闭的输入输出流。（File对象不影响该值，FileStream才会）
                                         */

        fun count(path:String):Boolean{
            var hit = true
            if (path.startsWith("pipe:")) {
                pipeCount++
            } else if (path.startsWith("socket:")) {
                socketCount++
            } else if (path.startsWith("/dev/ashmem")) {
                ashmemCount++
            } else if (path.endsWith(".jar")) {
                jarCount++
            } else if (path.endsWith(".apk")) {
                apkCount++
            } else if (path.equals("/dev/null")) {
                devnullCount++
            } else if (path.startsWith("/dmabuf:")) {
                dmabuffCount++
            } else if (path.contains("binder")) {
                binderCount++
            } else if (path.matches("anon_inode:.*eventfd.*".toRegex())) {
                anonInodeEventFdCount++
            } else if (path.matches("anon_inode:.*eventpoll.*".toRegex())) {
                anonInodeEventpollCount++
            } else if (path.matches("anon_inode:.*sync_file.*".toRegex())) {
                anonInodeSyncFileCount++
            } else if (path.matches("anon_inode:.*dmabuf.*".toRegex())) {
                anonInodeDmabufCount++
            }else{
                othersCount++
                hit=false
            }

            return hit
        }

        val fdFile = File("/proc/" + Process.myPid() + "/fd")
        val files = fdFile.listFiles() // 列出当前目录下所有的文件
        var length = 0 // 进程中的fd数量
        if (files != null) {
            length = files.size
        }

        val sb2 = StringBuilder()

        for (i in 0 until length) {
            val path = files!![i].absolutePath
            try {
                val strFile = Os.readlink(path) // 得到软链接实际指向的文件
                if (detailMode){
                    sb2.append(strFile + "\n")
                }
                val counted = count(strFile)
                if (!counted && !detailMode){
                    sb2.append(strFile + "\n")
                }
            } catch (x: Exception) {
                notFoundCount++
                sb2.append("readlink($path) failed: $x\n")
            }
        }

        var sb = resultReceiver
        if (sb == null){
            sb=StringBuilder()
        }
        sb.append("/--- file descriptors (counts=$length):\n")
        sb.append("--- summary:\n")
        sb.append("anonInodeEventFdCount=$anonInodeEventFdCount\n")
        sb.append("anonInodeEventpollCount=$anonInodeEventpollCount\n")
        sb.append("anonInodeSyncFileCount=$anonInodeSyncFileCount\n")
        sb.append("anonInodeDmabufCount=$anonInodeDmabufCount\n")
        sb.append("socketCount=$socketCount\n")
        sb.append("pipeCount=$pipeCount\n")
        sb.append("ashmemCount=$ashmemCount\n")
        sb.append("binderCount=$binderCount\n")
        sb.append("jarCount=$jarCount\n")
        sb.append("apkCount=$apkCount\n")
        sb.append("devnullCount=$devnullCount\n")
        sb.append("dmabuffCount=$dmabuffCount\n")
        sb.append("notFoundCount=$notFoundCount\n")
        sb.append("othersCount=$othersCount\n")
        sb.append("--- detail(some items, such as `pipe` `socket`, show only if `detailMode=true`):\n")
        sb.append(sb2.toString())
    }


    /**
     * 导出java heap
     * 注意：该接口会阻塞
     * @param savePath 保存路径
     */
    @JvmStatic
    @Throws(IOException::class)
    fun dumpHprof(savePath:String){
        Debug.dumpHprofData(savePath)
    }

}