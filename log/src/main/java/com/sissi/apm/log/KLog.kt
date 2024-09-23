/*
 * @desc    日志工具类。
 *          支持高效保存日志到文件；
 *          支持开关控制台输出；
 *          支持打印调用栈；
 *          支持打印超长内容（默认情况下android会将超出一定长度（约4K）的打印截断）；
 *          支持日志统计信息；
 *          支持在Android Studio的logcat tab中通过打印跳转到源码；
 * @email   xingbiyanshu@gmail.com
 */

package com.sissi.apm.log

import android.app.Application
import com.tencent.mars.xlog.Log
import com.tencent.mars.xlog.Xlog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import android.util.Log as ALog

@Suppress("MemberVisibilityCanBePrivate", "unused")
object KLog {
    private const val TAG = "KLog"

    /**
     * has initialized or not
     */
    private var inited = false

    private var context:Application? = null

    /**
     * Log level
     * */
    const val VERBOSE = 0
    const val DEBUG = 1
    const val INFO = 2
    const val WARN = 3
    const val ERROR = 4
    const val FATAL = 5
    const val NONE = 6
    private val STR_LEVEL = arrayOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "NONE", )

    /**
     * statistics
     * */
    private val wholeTimeLogStatistics by lazy {
        Statistics()
    }
    private val recentLogStatistics by lazy {
        Statistics()
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

//    private val records = ConcurrentHashMap<Long/*recordId*/, Record>()

    @JvmStatic
    var level = VERBOSE
        set(value) {
            val l = if (value in VERBOSE..NONE) value else max(min(value, NONE), VERBOSE)
            if (BuildConfig.usexlog) {
                Log.setLevel(l, true)
            }
            field = l
        }


    /**
     * 初始化（一般在启动时调用，建议在app#onCreate，退出时请调用[clean]）
     * @param level 日志等级。取值[DEBUG]至[NONE]，低于该等级的日志不会输出。特别地若取值[NONE]表示不输出任何日志。
     * @param enableConsoleLog 是否开启控制台日志。true打开，false关闭。
     *                          若关闭则AS的logcat窗口或者系统的命令行界面不会输出调用本模块接口生成的日志，
     *                          关闭控制台日志一方面减少了开销，另一方面由于仅存的文件日志已加密提高了安全性。
     *                          建议debug版本开启release版本关闭。
     * @param singleLogFileSizeLimit 单个日志文件大小上限，超过该上限日志会被写入新的文件。单位：MB
     * @param aliveDays 日志文件存活天数。取值范围[1, 10]
     * @param logNamePrefix 日志文件名前缀。建议以设备标识为前缀，如设备型号+系统版本号。
     *                      完整名称：logNamePrefix_yyMMdd_index.xlog，
     *                       日志文件按天生成，其中index是子文件序号，如果由于[singleLogFileSizeLimit]导致当天日志被分割。
     *                       序号越大日志越新，未带序号的最旧。
     * @param logDir 日志文件存放目录。 建议`context.getExternalFilesDir("xxx")`
     *               注意：该目录为日志独占，不要存放其它内容。
     *                    该目录进程独占，多进程应用请为各进程指定单独目录。
     *                     写日志文件是异步的，存在滞后，可以在适当的时机调用[flush]保证及时写入。
     *                    若进程崩溃，完整的日志可能需要重启进程才会写入到文件！
     * */
    @JvmStatic
    @JvmOverloads
    fun init(
        app: Application? = null,
        level: Int = DEBUG,
        enableConsoleLog: Boolean = true,
        singleLogFileSizeLimit: Double = 20.0,
        aliveDays: Double = 7.0,
        logNamePrefix: String = "logcat",
        logDir: String,
    ) {
        if (inited){
            rp(WARN, "KLog (${BuildConfig.VERSION}, ${BuildConfig.TIMESTAMP}) initialized already!")
            return
        }

        context = app

        if (BuildConfig.usexlog) {
            val xlogImp = object : Xlog() {
                fun appenderOpen(
                    level: Int, mode: Int, cacheDir: String?, logDir: String?,
                    nameprefix: String?, cacheDays: Int, pubkey: String,
                ) {
                    open(true, level, mode, cacheDir, logDir, nameprefix, pubkey)
                }
            }

            Log.setLogImp(xlogImp)
            xlogImp.appenderOpen(
                level,
                Xlog.AppednerModeAsync,
                "", // 若cacheDir为空则会尝试在logDir生成缓存文件
                logDir,   // 若logDir不存在则会尝试在cacheDir生成日志文件
                logNamePrefix,
                0, // 在cacheDir暂存多少天后转存至logDir
                // 公钥
                "3276f823cfea4cac2e3557ad51b2048a9418e89a9b8242edeef44b94a1b649bfd24ad37a4e4c1483162b5dee95ff11425a1b7bd402c478a8f4485446957592a8"
            )

            xlogImp.setMaxAliveTime(0, (aliveDays * 24L * 3600).toLong())
            xlogImp.setMaxFileSize(0, (singleLogFileSizeLimit * 1024L * 1024).toLong())
            Log.setConsoleLogOpen(enableConsoleLog)
        }

        KLog.level = level

        inited = true

        rp("KLog (${BuildConfig.VERSION}, ${BuildConfig.TIMESTAMP}, xlog enabled=${BuildConfig.usexlog}) initialized!")
    }


    /**
     * 清理（程序退出时执行）
     * */
    @JvmStatic
    fun clean() {
        if (inited){
            if (BuildConfig.usexlog) {
                Log.appenderClose()
            }
            inited = false
        }
    }


    /**
     * 强制日志缓存写入文件
     * 日志是异步写入文件的，会有一定的延迟，用户可在恰当的时机调用该接口强制缓存写入文件，以保证实时性
     * 注意：不恰当的时机可能损失性能，或实时性难以保证，建议在程序切后台时执行[flush]，在程序退出时调用[clean]。
     *      不要频繁调用该接口。
     * */
    @JvmStatic
    fun flush() {
        if (BuildConfig.usexlog) {
            Log.appenderFlush()
        }
    }


    /**
     * print.
     * tag is the caller's classname, log level is [DEBUG]. log will be output only if [level] <= [DEBUG].
     * a [createPrefix] will be added before main body. for example:
     * (in KLogTest.kt file)
     *     fun onCreate(){
     *         KLog.p("hello %s", "KLog") // at line 11
     *     }
     * will output:
     * D/KLogTest: [onCreate(KLogTest.kt:11)] hello KLog
     * */
    @JvmStatic
    fun p(format: String, vararg args: Any?) {
        if (level <= DEBUG) {
            log(null, DEBUG, true, null, format, *args)
        }
    }

    /**
     * print.
     * @param lev custom log level. log will be output only if [level] <= [lev].
     * @see [p]
     * */
    @JvmStatic
    fun p(lev: Int, format: String, vararg args: Any?) {
        if (level <= lev) {
            log(null, lev, true, null, format, *args)
        }
    }


    /**
     * print with custom tag.
     * @see [p]
     * */
    @JvmStatic
    fun tp(tag: String, format: String, vararg args: Any?) {
        if (level <= DEBUG) {
            log(tag, DEBUG, true, null, format, *args)
        }
    }


    @JvmStatic
    fun tp(tag: String, lev: Int, format: String, vararg args: Any?) {
        if (level <= lev) {
            log(tag, lev, true, null, format, *args)
        }
    }


    /**
     * print with stack trace
     * */
    @JvmStatic
    fun sp(format: String, vararg args: Any?) {
        log(null, DEBUG, false, null, String.format2(format, *args) + "\n" + stackTrace(1))
    }

    /**
     * print with stack trace
     * */
    @JvmStatic
    fun sp(lev: Int, format: String, vararg args: Any?) {
        log(null, lev, false, null, String.format2(format, *args) + "\n" + stackTrace(1))
    }


    /**
     * raw print(print without [createPrefix]).
     * @see [p]
     * */
    @JvmStatic
    fun rp(format: String, vararg args: Any?) {
        if (level <= DEBUG) {
            log(null, DEBUG, false, null, format, *args)
        }
    }

    @JvmStatic
    fun rp(lev: Int, format: String, vararg args: Any?) {
        if (level <= lev) {
            log(null, lev, false, null, format, *args)
        }
    }

    @JvmStatic
    fun rp(tag:String, lev: Int, format: String, vararg args: Any?) {
        if (level <= lev) {
            log(tag, lev, false, null, format, *args)
        }
    }


    /**
     *  print with custom prefix.
     *  @param methodInPrefix 前缀中的方法名。例如下面的代码：
     *  (in KLogTest.kt file)
     *     fun onCreate(){
     *         KLog.p("hello %s", "KLog") // at line 11
     *     }
     *  输出如下日志：
     *  D/KLogTest: [onCreate(KLogTest.kt:11)] hello KLog
     *  ”[onCreate(KLogTest.kt:11)]“即为前缀，”onCreate“即为方法名。
     *  绝大多数情况下不需要特别设置该参数，本模块的大多数函数如[p]，
     *  会自动解析并组装出[method(file:linenum)]形式的前缀，但有时候自动解析的前缀不符合用户预期，特别是method部分，
     *  在lambda或回调等场景往往不太直观，此时可使用该接口人为指定自己期望的method名。
     *  @see [p]
     * */
    @JvmStatic
    fun pp(methodInPrefix:String, format: String, vararg args: Any?) {
        if (level <= DEBUG) {
            log(null, DEBUG, true, methodInPrefix, format, *args)
        }
    }

    @JvmStatic
    fun pp(lev: Int, methodInPrefix:String, format: String, vararg args: Any?) {
        if (level <= lev) {
            log(null, lev, true, methodInPrefix, format, *args)
        }
    }

    @JvmStatic
    fun pp(tag: String, lev: Int, methodInPrefix:String, format: String, vararg args: Any?) {
        if (level <= lev) {
            log(tag, lev, true, methodInPrefix, format, *args)
        }
    }


    /**
     * 生成日志主体内容的前缀
     * 例如，有如下KLog使用场景：
     * file HelloKotlin.kt:
     * 7    fun main(){
     * 8        KLog.p("hello")
     * 9    }
     * 则加了前缀的日志如下：
     * $timestamp $pid&tid I/HelloKotlin: [main(HelloKotlin.kt:8)] hello
     * 其中“[main(HelloKotlin.kt:8)] ”为前缀。
     *
     * @return [$methodName($filename:$lineNumber)]
     * */
    private fun createPrefix(methodName:String, filename:String, lineNumber: Int): String {
        val sb = StringBuilder()
//        val mn = methodName //if(methodName.length>30) methodName.substring(0, 28)+".." else methodName
        sb
            .append("[")
            .append(methodName)
            .append("(")
            .append(filename)
            .append(":")
            .append(lineNumber)
            .append(")")
            .append("] ")
        return sb.toString()
    }

    /**
     * generate stack trace
     * */
    private fun stackTrace(targetFrameIndex: Int): String {
        val trace = StringBuilder()
        val sts = Thread.currentThread().stackTrace
        for (i in targetFrameIndex + 3 until sts.size) {
            trace.append("at ").append(sts[i]).append("\n")
        }
        return trace.toString()
    }


    /**
     * 创建一条日志记录
     * */
    private fun createRecord(tag: String?, level: Int, logLength:Int): Record {
        /*
        * 以如下KLog使用场景为例：
        * fun main(){
        *       KLog.p("hello")
        *  }
        * 当前栈帧（注意，createRecord为当前执行函数，未入栈）：
        * * ----------
        * |p       |
        * ----------
        * |main    |
        * ----------
        * |   ...  |
        * */
        val curThr = Thread.currentThread()
        val sts = curThr.stackTrace
        /*
        * sts中的栈帧情况：
        * * ----------
        * |Thread#getStackTrace|
        * ----------
        * |Thread#currentThread|
        * ----------
        * |createRecord|
        * ----------
        * |log     |
        * ----------
        * |p       |
        * ----------
        * |main    | // target fun, index=5
        * ----------
        * |   ...  |
        * */
        val st = sts[5] // 获取目标栈帧（例子中main为我们的目标栈帧）

        val record = Record(
            ++Record.count,
            tag?:st.fileName,
            level,
            st.fileName,
            st.lineNumber,
            st.methodName,
            curThr.name,
            curThr.id,
            System.currentTimeMillis(),
            logLength
        )

        return record
    }


    private fun log(tag: String?, lev: Int, attachPrefix:Boolean, methodInPrefix: String?, format: String, vararg args: Any?) {
        var content = String.format2(format, *args)
        var contentLen = content.length

        val record = createRecord(tag, lev, contentLen)
        val finTag=record.tag
//        records[record.id] = record
        wholeTimeLogStatistics.addRecord(record)
        recentLogStatistics.addRecord(record)
        if (attachPrefix){
            val prefix = createPrefix(methodInPrefix?:record.method, record.file, record.lineNumber)
            content = prefix+content
            contentLen = content.length
        }

        /*
        * android的日志系统对单次打印有长度限制（略大于4000字节），
        * 为避免超长的内容被截掉我们尝试分段打印
        * */
        var chunk: String
        val chunkSize = 4000
        var cursor = 0
        while (cursor < contentLen) {
            if (cursor + chunkSize < contentLen) {
                chunk = content.substring(cursor, cursor + chunkSize)
                val lastNewLineIndex = chunk.lastIndexOf('\n') // 为了使断行更自然尝试在最后一个换行符处断行
                if (lastNewLineIndex > 0) {
                    chunk = chunk.substring(0, lastNewLineIndex)
                    cursor++ // 跳过换行
                }
            } else {
                chunk = content.substring(cursor, contentLen)
            }
            cursor += chunk.length

            if (BuildConfig.usexlog) {
                when (lev) {
                    VERBOSE -> Log.v(finTag, chunk)
                    DEBUG -> Log.d(finTag, chunk)
                    INFO -> Log.i(finTag, chunk)
                    WARN -> Log.w(finTag, chunk)
                    ERROR -> Log.e(finTag, chunk)
                    FATAL -> Log.f(finTag, chunk)
                }
            }else{
                when (lev) {
                    VERBOSE -> ALog.v(finTag, chunk)
                    DEBUG -> ALog.d(finTag, chunk)
                    INFO -> ALog.i(finTag, chunk)
                    WARN -> ALog.w(finTag, chunk)
                    ERROR -> ALog.e(finTag, chunk)
                    FATAL -> ALog.wtf(finTag, chunk)
                }
            }
        }
    }

    fun String.Companion.format2(format: String, vararg args: Any?) : String{
        return if (args.isNotEmpty()){
            String.format(format, *args)
        }else{
            String.format("%s", format)
        }
    }

    /**
     * 获取日志统计信息。
     * 格式化的统计信息，如按模块，日志条数前几位排行，日志量前几位排行，各Level的日志条数，等等。
     * 可用于辅助排查问题，如某个模块的日志条数或日志量远大于预期可能预示着模块设计有缺陷或逻辑有错误。
     * 或用于检视日志是否规范使用，如Warn/Error级别的日志过多往往意味着滥用了高优先级的日志（大部分日志应该是Info及以下级别）。
     * @param topN 统计信息排行榜的长度。如topN=10前10的条目
     * */
    @JvmStatic
    @Synchronized
    @JvmOverloads
    fun getLogStatistics(topN:Int=5):String{
        val curTs = dateFormat.format(Date(System.currentTimeMillis()))
        val wts = dateFormat.format(wholeTimeLogStatistics.timestamp)
        val sb = StringBuilder()
        sb.append("/-------------------------$curTs KLog Statistics -------------------------\n")
        wholeTimeLogStatistics.run {
            sb.append("/--- from $wts to $curTs (counts=$recordsCount, length=${recordsLength/1024f}KB):\n")
            format(sb, topN)
        }
        recentLogStatistics.run {
            val ts = dateFormat.format(timestamp)
            if (!wts.equals(ts)) {
                sb.append("/--- from $ts to  $curTs (counts=$recordsCount, length=${recordsLength / 1024f}KB):\n")
                format(sb, topN)
            }
            clean()
        }
        return sb.toString()
    }


    private data class Record(
        val id: Long,
        val tag: String,
        val level: Int,
        val file: String,
        val lineNumber: Int,
        val method: String,
        val threadName: String,
        val threadId: Long,
        val timestamp: Long,
        val length: Int,
    ){
        companion object{
            var count:Long=0
        }
    }

    private data class RecordSummaryInfo(var recordsCount: Long, var recordsLength: Long)

    private class Statistics {
        var recordsCount=0L
        var recordsLength=0L

        var timestamp=System.currentTimeMillis()

        val byTag = ConcurrentHashMap<String, RecordSummaryInfo>()
        val byFile = ConcurrentHashMap<String, RecordSummaryInfo>()
        val byLevel = ConcurrentHashMap<String, RecordSummaryInfo>()
        val byThread = ConcurrentHashMap<String, RecordSummaryInfo>()
        val byMethod = ConcurrentHashMap<String, RecordSummaryInfo>()

        private val logStatHeadFormat = "%-15s %-15s %s"
        private val logStatHead = String.format(logStatHeadFormat, "Count", "Length(KB)", "Name")

        @Synchronized
        fun addRecord(r: Record){
            recordsCount++
            recordsLength+=r.length

            //分类
            fun <T> classify(saveIn:ConcurrentHashMap<T, RecordSummaryInfo>, key:T, recordLength: Int){
                var item = saveIn[key]
                if (item==null){
                    item = RecordSummaryInfo(0, 0)
                    saveIn[key] = item
                }
                item.recordsCount++
                item.recordsLength += recordLength
            }

            classify(byTag, r.tag, r.length)
            classify(byFile, r.file, r.length)
            classify(byThread, "${r.threadName}(${r.threadId})", r.length)
            classify(byLevel, STR_LEVEL[r.level], r.length)
            classify(byMethod, "${r.method}@${r.file}:${r.lineNumber}", r.length)
        }

        @Synchronized
        fun clean(){
            recordsCount = 0
            recordsLength = 0
            timestamp=System.currentTimeMillis()
            byTag.clear()
            byFile.clear()
            byLevel.clear()
            byThread.clear()
            byMethod.clear()
        }

        /** 将统计信息转化为格式化字符串形式（排行榜形式），如：
         * Count           Length(KB)      Name
         * --- by level
         * 1553            151.759         INFO
         * 1265            530.577         DEBUG
         * 70              5.211           WARN
         * 24              2.313           ERROR
         * --- by tag
         * 1778            173.509         WebRtcManager.java
         * 193             191.826         MyMtcCallback.java
         * 132             12.250          JniKLog.java
         * 99              25.321          Request.java
         * 71              6.648           DbDaoImpl.java
         * ...
         * @param sb 用于存放格式化结果
         * @param topN 排行榜长度。如上例的topN=5。
         * @param byCount 排行榜是否按日志条数排序。true按日志条数降序排列，false按日志总长度降序排列。
         */
        @Synchronized
        fun format(sb:StringBuilder, topN:Int, byCount: Boolean=true){
            sb.append(logStatHead).append("\n")
            fun buildSegment(category:String, topX:Int, map:Map<out Any, RecordSummaryInfo>){
                sb.append("--- by $category\n")
                var count = 0
                for ((k, v) in map){
                    if (count++ == topX){
                        break
                    }
                    sb.append(String.format(logStatHeadFormat, v.recordsCount, String.format("%.3f", v.recordsLength/1024f), k)).append("\n")
                }
            }

            fun <T> sort(map:ConcurrentHashMap<T, RecordSummaryInfo>, byCount:Boolean)
                    = map.toList().sortedByDescending { (_, v) -> if(byCount) v.recordsCount else v.recordsLength}.toMap()

            buildSegment("level", topN, sort(byLevel, byCount))
            buildSegment("tag", topN, sort(byTag, byCount))
            buildSegment("method", topN, sort(byMethod, byCount))
            buildSegment("file", topN, sort(byFile, byCount))
            buildSegment("thread", topN, sort(byThread, byCount))
        }

    }

}