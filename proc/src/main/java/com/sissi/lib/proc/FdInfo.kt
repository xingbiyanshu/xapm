package com.sissi.lib.proc

import android.system.Os
import java.io.File

class FdInfo private constructor(){

    private var _type=Type.OTHERS
    val type:Type
        get() {
            return _type
        }

    private var _fd=""
    val fd:String
        get() {
            return _fd
        }

    private var _description=""
    val description:String
        get() {
            return _description
        }
    
    enum class Type{

        /** 例：socket:[2434493]。 "[]"中的是inode。
         * 除了应用的网络相关操作会创建socket外，很多其它操作也会导致。framework层很多模块的进程间通信机制是采用的socket，
         * 如InputManagerService和客户端（App）之间。新增Window会创建一对socket用于构造inputchannel，
         * 以实现从InputDispatcher那里得到input事件。除了Activity外，Dialog，Toast，PopWindow等都会新增Window。*/
        SOCKET,
        /** 例： pipe:[2417584]  "[]"中的是inode */
        PIPE,
        /** 例：/system/framework/framework.jar */
        JAR,
        /** 例：/data/app/~~UZ8Vx8Q4ceuA6JQzURK7nA==/com.kedacom.truetouch.sky.rtc-7qs-Hleg9KQ4_huj7YS89g==/base.apk */
        APK,
        /** /dev/null */
        DEVNULL,
        /** 例： /dev/ashmem6dde78f2-72dd-4185-b145-db9b828dc01a
         *  匿名共享内存一般用于进程间传递大块数据，如provider机制，带bitmap的通知等，因为
         *  共享内存是最高效的，这种场景下其他通信方式就不是首选了，如binder之类。
         *  Java线程或attach到JVM的native线程会创建一个ashmem（匿名共享内存）；
         *  数据库cursor也会创建ashmem；
         *  BitMap在IPC传递时需要打开fd，实际上是通过传递指向bitmap所在的匿名共享内存的fd进行传递的，如带Bitmap的通知。
         *  content provider用于大块数据的跨进程传递，所以也是基于匿名共享内存的。 */
        ASHMEM,
        /** 例：/dev/binderfs/hwbinder */
        BINDER,
        /** 例：/dmabuf:METADATA
         *  dma_buf是内核中一个独立的子系统，提供了一个让不同设备、子系统之间进行共享缓存的统一框架，这里说的缓存通常是指通过DMA方式访问的和硬件交互的内存。
         *  比如，来自摄像头采集的通过pciv驱动传输的内存、gpu内部管理的内存等等 */
        DMABUFF,
        /** 例： anon_inode:[eventfd]
         *  匿名inode的fd，没有专门的inode，而是链接到内核一个公用的唯一的inode。通过eventfd创建的。
         *  一个Looper会创建一组"eventfd和eventpoll"用于线程间通信（HandlerThread包含一个Looper）*/
        ANON_INODE_EVENT_FD,
        /** 例： anon_inode:[eventpoll]
         *  匿名inode的fd，通过epoll_create创建的。*/
        ANON_INODE_EVENT_POLL,
        /** 例： anon_inode:sync_file
         *  匿名inode的fd，通常用于代表临时数据或进程通信*/
        ANON_INODE_SYNC_FILE,
        /** 例：anon_inode:dmabuf */
        ANON_INODE_DMABUF,

        OTHERS

    }

    companion object{
        
        private fun buildFdInfo(type:Type, fd:String, desc:String):FdInfo{
            return FdInfo().apply {
                _type = type
                _fd = fd
                _description = desc
            }
        }

        fun getAll():List<FdInfo>{
            return mutableListOf<FdInfo>().apply {
                File("/proc/self/fd").listFiles()?.forEach {
                    try {
                        add(
                            Os.readlink(it.absolutePath).run {
                                buildFdInfo(
                                    when {
                                        startsWith("socket:") -> Type.SOCKET
                                        startsWith("pipe:") -> Type.PIPE
                                        endsWith(".jar") -> Type.JAR
                                        endsWith(".apk") -> Type.APK
                                        equals("/dev/null") -> Type.DEVNULL
                                        startsWith("/dev/ashmem") -> Type.ASHMEM
                                        contains("binder") -> Type.BINDER
                                        startsWith("/dmabuf:") -> Type.DMABUFF
                                        matches("anon_inode:.*eventfd.*".toRegex()) -> Type.ANON_INODE_EVENT_FD
                                        matches("anon_inode:.*eventpoll.*".toRegex()) -> Type.ANON_INODE_EVENT_POLL
                                        matches("anon_inode:.*sync_file.*".toRegex()) -> Type.ANON_INODE_SYNC_FILE
                                        matches("anon_inode:.*dmabuf.*".toRegex()) -> Type.ANON_INODE_DMABUF
                                        else -> Type.OTHERS
                                    },
                                    it.absolutePath,
                                    this,
                                )
                            }
                        )
                    } catch (e: Exception) {

                    }
                }
            }.sortedBy {
                it.type
            }
        }

        fun getCount():Int{
            return File("/proc/self/fd").listFiles()?.size ?: 0
        }

    }


    override fun toString(): String {
        return "FdInfo(type=$type, fd='$fd', description='$description')"
    }

}

private val HEAD_FORMAT = "%-30s %-20s %s"
private val HEAD = String.format(HEAD_FORMAT, "Type", "FD", "Description")
fun List<FdInfo>.toStr(enableSummaryMode:Boolean):String{
    val sb=StringBuilder()
    if (enableSummaryMode){
        val sum = summary()
        sb.append("/-------------------- summary --------------------\n")
        sb.append("Total: ${size}\n")
        sum.forEach { (t, u) ->
            sb.append(t).append(": ").append(u).append("\n")
        }
        sb.append("/-------------------- detail --------------------\n")
    }
    sb.append(HEAD).append("\n")
    forEach {
        sb.append(String.format(HEAD_FORMAT, it.type, it.fd, it.description)).append("\n")
    }
    return sb.toString()
}

fun List<FdInfo>.summary():Map<FdInfo.Type/*type*/, Int/*count*/>{
    return mutableMapOf<FdInfo.Type, Int>().apply {
        this@summary.forEach {
            val count = this[it.type]
            this[it.type] = if (count==null) 1 else count+1
        }
    }
}