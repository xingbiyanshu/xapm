package com.sissi.lib.proc

import android.os.Process
import android.system.Os
import java.io.File

class FdInfo {

    private var _type
    val type:String
        get() {
            return _type
        }

    private var _path:String
    val path:String
        get() {
            return _path
        }

    companion object{
        const val TYPE_NORMAL=1
        const val TYPE_SOCKET=2
        const val TYPE_PIPE=3
        const val TYPE_JAR=4
        const val TYPE_APK=5
        const val TYPE_DEVNULL=6
        const val TYPE_ASHMEM=7
        const val TYPE_BINDER=8
        const val TYPE_DMABUFF=9
        const val TYPE_ANON_INODE_EVENT_FD=10
        const val TYPE_ANON_INODE_EVENT_POLL=11
        const val TYPE_ANON_INODE_SYNC_FILE=12
        const val TYPE_ANON_INODE_DMABUF=13
    }


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

}