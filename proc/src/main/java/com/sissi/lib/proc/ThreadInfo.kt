package com.sissi.lib.proc

import java.io.File


private val HEAD_FORMAT = "%-40s %-10s %-10s %-10s %-15s %-10s %-10s %s"
private val HEAD = String.format(HEAD_FORMAT, "Name", "IsNative", "NativeId", "JvmId", "State", "Priority", "IsDaemon", "StackTrace")
private val lengthBeforeStackTrace = HEAD.length - "StackTrace".length

class ThreadInfo private constructor(){

    private var _isNative = false
    /**
     * 是否native线程。true：native线程；false：jvm线程
     */
    val isNative:Boolean
        get() {
            return _isNative
        }

    private var _jvmId = -1L
    /**
     * jvm的线程id
     */
    val jvmId:Long
        get() {
            return _jvmId
        }

    private var _nativeId=-1L
    /**
     * 系统的线程号，各种linux命令如top展示的线程号
     */
    val nativeId:Long
        get() {
            return _nativeId
        }

    private var _name=""
    /**
     * 线程名
     */
    val name:String
        get() {
            return _name
        }

    private var _state:Thread.State?=null
    val state:Thread.State?
        get() {
            return _state
        }

    private var _priority=-1
    /**
     * 线程优先级
     */
    val priority:Int
        get() {
            return _priority
        }

    private var _isDaemon=false
    /**
     * 是否守护线程
     */
    val isDaemon:Boolean
        get() {
            return _isDaemon
        }

    private var _stackTrace:List<StackTraceElement>?=null
    /**
     * 线程堆栈
     */
    val stackTrace:List<StackTraceElement>?
        get() {
            return _stackTrace
        }

    fun copy(thr:ThreadInfo){
        _isNative = thr._isNative
        _jvmId = thr._jvmId
        _nativeId = thr._nativeId
        _name = thr._name
        _state = thr._state
        _priority = thr._priority
        _stackTrace = thr._stackTrace
    }



    companion object{

        private fun buildThreadInfo(isNative:Boolean, jvmId:Long, nativeId:Long, name:String,
                                    state:Thread.State?, priority:Int, isDaemon:Boolean,
                                    stackTrace:List<StackTraceElement>?):ThreadInfo{
            return ThreadInfo().apply {
                _isNative=isNative
                _jvmId = jvmId
                _nativeId = nativeId
                _name = name
                _state = state
                _priority = priority
                _isDaemon=isDaemon
                _stackTrace = stackTrace
            }
        }

        /**
         * 获取进程中的JVM线程信息
         */
        private fun getJvmThreadsInfo():MutableList<ThreadInfo> {
            val allStackTraces = Thread.getAllStackTraces()
            val list = mutableListOf<ThreadInfo>()
            for ((k, v) in allStackTraces){
                list.add(buildThreadInfo(false, k.id, -1, k.name, k.state, k.priority, k.isDaemon, v.toList()))
            }
            return list
        }


        /**
         * 获取进程的所有线程（包括JVM线程和Native线程）。
         * 结果读取自"/proc/self/task"
         */
        private fun getAllThreads():List<ThreadInfo>{
            val jvmThreads = getJvmThreadsInfo()
            val allThreads = runCatching { File("/proc/self/task").listFiles() }
                .getOrElse {
                    return@getOrElse emptyArray()
                }?.map {
                    val thrId = it.name.toLong()
                    val thrName = runCatching {
                        File(it,"comm").readText()
                    }.getOrElse { "failed to read $it/comm" }.removeSuffix("\n")
                    buildThreadInfo(true, -1, thrId, thrName, null, -1, false, null)
                }/*?.filter { // 过滤掉主线程
                    FIXME /proc下拿到的主线程无法通过上面的jvmThreads过滤，不想办法过滤掉会导致误判主线程为native线程
                    mainThreadNativeId.toString()!=it._nativeId
                }*/?.toMutableList()?: mutableListOf()

            for (thr in allThreads) {
                run{
                    jvmThreads.forEach { jvmThr->
                        if (jvmThr.name == thr.name /*FIXME 线程名是可以重复的，所以此处可能误判，但是想通过id比对又难以实施，因为"/proc/self/task"中的threadId是native层的，
                                               os中的真实线程id，而jvmThread拿到的id只是jvm中的一个序号而已，两者无法比对*/
                            || (thr.name.length==15  /* FIXME "/proc/self/task"中的线程名长度超过15会被截断，所以我们做此判断，但这样做仍有可能误判。*/
                                    && jvmThr.name.contains(thr.name))
                        ){
                            jvmThreads.remove(jvmThr) //FIXME 由于线程名可以重复，我们此处边比对边删除以尽力规避这种影响，但是有可能将同名线程错误归类
                            thr.copy(jvmThr)
                            return@run
                        }
                    }
                }
            }

            jvmThreads.find {
                it.name=="main"
            }?.let {
                allThreads.add(0, it)
            }

            return allThreads.sortedBy {
                it._isNative
            }
        }

        /**
         * 获取进程的所有线程
         */
        fun getAll():List<ThreadInfo>{
            return getAllThreads()
        }


        /**
         * 获取进程中的线程数量
         */
        fun getCount():Int{
//            val THREADS_REGEX = "Threads:\\s*(\\d+)\\s*".toRegex()
//            fun Regex.matchValue(s: String) = matchEntire(s.trim())
//                ?.groupValues?.getOrNull(1)?.toInt() ?: 0
//
//            var threadsCount=0
//            File("/proc/self/status").forEachLine { line ->
//                when {
//                    line.startsWith("Threads") -> {
//                        threadsCount = THREADS_REGEX.matchValue(line)
//                    }
//                }
//            }
//            return threadsCount

            return runCatching { File("/proc/self/task").listFiles() }
                .getOrElse {
                    return@getOrElse emptyArray()
                }.size
        }

    }


    /**
     * 转换为格式化字符串
     * @param detailMode 是否启用详细模式。true启用
     *                   某些信息需要开启详细模式才会获取，如调用栈
     * */
    fun toStr(detailMode:Boolean=false): String {
        val nm = if (_name.length>40){
            val diff = _name.length-40
            _name.replaceRange(_name.length-diff-2,_name.length, "..")
        }else _name
        val sb=StringBuilder()
        if (detailMode) {
            _stackTrace?.forEachIndexed { index, trace ->
                if (index == 0) {
                    sb.append("$trace\n")
                } else {
                    sb.append(" ".repeat(lengthBeforeStackTrace)).append("$trace\n")
                }
            }
        }
        return String.format(HEAD_FORMAT, nm, _isNative, _nativeId, _jvmId, _state, _priority, _isDaemon, sb.toString())
    }
}

/**
 * 转换为格式化字符串
 * @param detailMode 是否启用详细模式。true启用
 *                   某些信息需要开启详细模式才会获取，如调用栈
 * */
fun List<ThreadInfo>.toStr(detailMode:Boolean=false):String{
    val sb=StringBuilder()
    sb.append(HEAD).append("\n")
    forEach {
        sb.append(it.toStr(detailMode)).append("\n")
    }
    return sb.toString()
}