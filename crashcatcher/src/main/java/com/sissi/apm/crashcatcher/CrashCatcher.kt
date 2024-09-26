package com.sissi.apm.crashcatcher

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Process
import android.os.SystemClock
import android.util.Printer
import com.sissi.apm.log.DefaultLogger
import com.sissi.apm.log.Logger
import java.lang.ref.WeakReference
import java.util.Stack
import java.util.Timer
import java.util.TimerTask
import kotlin.math.min
import kotlin.system.exitProcess

object CrashCatcher {

    private lateinit var app:Application

    private var forceQuitAfterCrashHandled=true

    private var callback: Callback?=null

    const val TYPE_NATIVE_CRASH=1
    const val TYPE_UNCAUGHT_JVM_EXCEPTION=2
    const val TYPE_ANR=3

    private val handler=Handler(Looper.getMainLooper())

    private lateinit var logger:Logger

    init {
        System.loadLibrary("nativecrashcatcher")
    }


    private val actLifecycleCb = object :ActivityLifecycleCallbacks{
        private val actStack= Stack<WeakReference<Activity>>()
        private val actStackLock=Any()
        private val RUNNING=1
        private val STOPPING=2
        private val STOPPED=3
        private var state= RUNNING

        private fun Stack<WeakReference<Activity>>.info(): String {
            val sb=StringBuffer()
            sb.append("activity stack(size:${actStack.size}):\n")
            for (i in actStack.lastIndex downTo 0) {
                val act = actStack[i]
                sb.append("${act.get()}\n")
            }
            return sb.toString()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
//            KLog.p("onActivityCreated $activity")
            if (!isState(RUNNING)){
                handler.post {
                    activity.finish()
                }
                return
            }
            synchronized(actStackLock){
                actStack.push(WeakReference(activity))
//                KLog.p(actStack.info())
            }
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityDestroyed(activity: Activity) {
            synchronized(actStackLock) {
                if (!isState(RUNNING)) {
                    logger.i("state=$state, onActivityDestroyed $activity")
                }

                for (actR in actStack){
                    val act=actR.get()
                    if (act===activity){
                        actStack.remove(actR)
                        break
                    }
                }

                if (actStack.isEmpty()){
                    logger.i("activity stack become empty! state=$state")
                    if (isState(STOPPING)){
                        setState(STOPPED)
//                        if (forceQuitAfterCrashHandled) {
//                            // 等待activity完全退出（经实测需延时，系统才不会重新拉起App。猜测onDestroy后ActivityManager不会立即收到）
//                            SystemClock.sleep(2000)
//                            Process.killProcess(Process.myPid())
//                            exitProcess(0)
//                        }
                    }
                }
            }
        }


        fun clearActivityStack(){
            synchronized(actStackLock) {
                logger.i("clear ActivityStack, size=${actStack.size}")
                setState(STOPPING)
                for (i in actStack.lastIndex downTo 0) {
                    val act = actStack[i].get()
                    logger.i("finish activity[$i] $act")
                    act?.finish()
                }

//                KLog.p("clear service")
//        stopService(Intent(this, MyService::class.java))
            }
        }


        @Synchronized
        private fun setState(st:Int){
            state=st
        }

        @Synchronized
        private fun isState(st: Int)=state==st

    }


    /**
     * 初始化
     * @param app Android App实例
     * @param forceQuitAfterCrashHandled 崩溃被“处理后”是否强制退出App，默认退出。“处理后”指[Callback.callback]返回。
     *                                  该参数主要用于应对以下情况：
     *                                  1、非主线程崩溃后App可能继续运行不会退出，这可能不是用户期望的。
     *                                  2、当App因异常崩溃退出后，Android系统有自动拉起该App的行为，
     *                                    这往往不是用户期望的。当该参数设置为true时，会阻止该重启行为。
     *                                  说明：若该参数设置为false不代表保持App不退出，而是不做任何处理保持系统默认行为。
     */
    @JvmStatic
    @JvmOverloads
    fun init(app:Application, forceQuitAfterCrashHandled:Boolean=true, logger:Logger=DefaultLogger()) {
        logger.i("forceQuitAfterCrashHandled=$forceQuitAfterCrashHandled")
        if (this::app.isInitialized){
            logger.e("has inited already!")
            return
        }

        CrashCatcher.app = app
        CrashCatcher.forceQuitAfterCrashHandled = forceQuitAfterCrashHandled

        if (forceQuitAfterCrashHandled){
            app.registerActivityLifecycleCallbacks(actLifecycleCb)
        }

        fun callbackToUI(crashInfo: CrashInfo){
            callback?.callback(crashInfo)
            if (forceQuitAfterCrashHandled) {
                actLifecycleCb.clearActivityStack()

                if (crashInfo.type == TYPE_UNCAUGHT_JVM_EXCEPTION) { // 经实测，Native崩溃情况下，不能手动退出进程，否则影响系统dump出native崩溃栈
                    // 延时一段时间然后退出进程。
                    Timer("exitProcess").schedule(object : TimerTask() {
                        override fun run() {
                            CrashCatcher.logger.w("exit process!!")
                            Process.killProcess(Process.myPid())
                            exitProcess(0)
                        }
                    }, 3000)
                }

                if (Thread.currentThread()==Looper.getMainLooper().thread){
                    // 主线程崩溃会导致looper退出，而activity生命周期事件处理依赖looper，为了使activity完全退出，
                    // 我们重启looper处理完finish activity
                    Looper.loop()
                }else{
                    SystemClock.sleep(2000)
                }
            }
        }

        initNativeCrashCatcher(object : NativeCrashCallback {
            override fun callback(crashInfo: String) {
                val curThread=Thread.currentThread()
                callbackToUI(CrashInfo(TYPE_NATIVE_CRASH, curThread.name, curThread.id, "\n$crashInfo"))
            }
        })

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            callbackToUI(CrashInfo(TYPE_UNCAUGHT_JVM_EXCEPTION, t.name, t.id, "\n${e.stackTraceToString()}"))
        }

    }

    @JvmStatic
    private external fun initNativeCrashCatcher(cb: NativeCrashCallback)

    @JvmStatic
    fun setCallback(cb: Callback){
        callback =cb
    }

    private interface NativeCrashCallback {
        fun callback(msg:String)
    }

    interface Callback{
        fun callback(info: CrashInfo)
    }

    data class CrashInfo(val type:Int, val threadName:String, val threadId:Long, val exception:String)


    interface StuckListener{
        fun onStuck(stackTrace:String)
    }

    var stuckListener: StuckListener?=null
    var stuckTimeLimit=1000L // unit:ms
    const val LOOPER_PROCESS_STARTED=1
    const val LOOPER_PROCESS_FINISHED=2
    const val LOOPER_PROCESS_TIMEOUT=3
    val stuckMonitorThread=HandlerThread("stuckMonitorThread")
    init{
        stuckMonitorThread.start()
    }
    val stuckMonitorLock=Any()
    val stuckMonitorHandler = object : Handler(stuckMonitorThread.looper){
        override fun handleMessage(msg: Message) {
            when(msg.what){
                LOOPER_PROCESS_STARTED ->{
                    synchronized(stuckMonitorLock){
                        val m=Message.obtain()
                        m.what= LOOPER_PROCESS_TIMEOUT
                        m.obj=msg.obj
//                        KLog.p("recv msg LOOPER_PROCESS_STARTED(%s), send LOOPER_PROCESS_TIMEOUT(%s)", msg.obj, msg.obj)
                        sendMessageDelayed(m, stuckTimeLimit)
                    }
                }

                LOOPER_PROCESS_FINISHED ->{
                    synchronized(stuckMonitorLock){
//                        KLog.p("recv msg LOOPER_PROCESS_FINISHED(%s), remove LOOPER_PROCESS_TIMEOUT(%s)", msg.obj, msg.obj)
                        removeMessages(LOOPER_PROCESS_TIMEOUT, msg.obj)
                    }
                }

                LOOPER_PROCESS_TIMEOUT ->{
                    synchronized(stuckMonitorLock) {
//                        KLog.p("recv msg LOOPER_PROCESS_TIMEOUT(%s), stuckListener=%s",msg.obj,stuckListener)
                        if (stuckListener != null) {
                            val stack = Looper.getMainLooper().thread.stackTrace
                            val sb = StringBuilder()
                            sb.append("main thread processing msg("+msg.obj+") stuck exceeding " + stuckTimeLimit + "ms:\n")
                            for (frame in stack) {
                                sb.append(frame.toString())
                                sb.append("\n")
                            }
                            stuckListener!!.onStuck(sb.toString())
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    fun setMainThreadStuckListener(listener: StuckListener?, timeLimit:Long){
        stuckListener = listener
        stuckTimeLimit = timeLimit
        val printer=if (listener!=null){
            object : Printer {
                var count=0
                lateinit var strCount:String
                val DELAY=100L
                var beginTimeStamp=0L
                override fun println(x: String) {
                    if (x.startsWith(">>>>> Dispatching to")){// ">>>>> Dispatching to"是looper处理消息的开始标志（android内部实现相关）
                        synchronized(stuckMonitorLock){
                            val msg=Message.obtain()
                            msg.what= LOOPER_PROCESS_STARTED
                            ++count
                            strCount=count.toString() // 基本类型会自动装箱拆箱，影响相等性判断，我们使用String
                            msg.obj=strCount
//                            KLog.p("try sending msg LOOPER_PROCESS_STARTED(%s)", strCount)
                            beginTimeStamp = System.currentTimeMillis()
                            stuckMonitorHandler.sendMessageDelayed(msg,
                                DELAY  // 绝大多数looper任务执行时长都非常短，延迟一小段时间避免无意义的消息投递（而且会造成消息风暴，导致正常消息被阻滞）
                            )
                        }
                    }else if (x.startsWith("<<<<< Finished to")){ // "<<<<< Finished to"是looper处理消息的结束标志，“ Dispatching to”和"Finished to"成对。（android内部实现相关）
                        synchronized(stuckMonitorLock){
                            val costTime=System.currentTimeMillis()-beginTimeStamp
                            if (costTime<DELAY){
//                                KLog.p("revoke msg LOOPER_PROCESS_STARTED(%s)", strCount)
                                stuckMonitorHandler.removeMessages(LOOPER_PROCESS_STARTED, strCount)
                            }else{
                                val msg=Message.obtain()
                                msg.what= LOOPER_PROCESS_FINISHED
                                msg.obj=strCount
//                                KLog.p("sending msg LOOPER_PROCESS_FINISHED(%s)", strCount)
                                if (costTime> min(500, stuckTimeLimit)){
                                    logger.w("main looper process msg($strCount) cost ${costTime}ms")
                                }
                                stuckMonitorHandler.sendMessage(msg)
                            }
                        }
                    }
                }
            }
        }else null

        Looper.getMainLooper().setMessageLogging(printer)
    }

}
