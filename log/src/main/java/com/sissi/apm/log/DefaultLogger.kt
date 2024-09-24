package com.sissi.apm.log

import android.util.Log

class DefaultLogger :Logger{

    private fun getDefaultTag():String{
        val curThr = Thread.currentThread()
        val sts = curThr.stackTrace
        /*
        * sts中的栈帧情况：
        * * ----------
        * |Thread#getStackTrace|
        * ----------
        * |Thread#currentThread|
        * ----------
        * |getDefaultTag|
        * ----------
        * |v/d/...     |
        * ----------
        * fun caller(){ // target fun, index=4
        *   logger.v()
        * }
        * ----------
        * |   ...  |
        * */
        return sts[4].fileName
    }


    private fun createPrefix():String{
        val curThr = Thread.currentThread()
        val sts = curThr.stackTrace
        val st = sts[4]
        return "[${st.methodName}(${st.fileName}:${st.lineNumber})]"
    }

    /**
     * 输出verbose等级日志
     * msg会添加前缀，完整的日志消息体为：[$methodName($fileName:$lineNumber)] msg
     * */
    override fun v(tag: String, msg: String) {
        Log.v(tag, "${createPrefix()} $msg")
    }

    /**
     * 输出verbose等级日志
     * tag使用调用者所在文件名；
     * msg会添加前缀，完整的日志消息体为：[$methodName($fileName:$lineNumber)] msg
     * */
    override fun v(msg: String) {
        Log.v(getDefaultTag(), "${createPrefix()} $msg")
    }

    override fun d(tag: String, msg: String) {
        Log.d(tag, "${createPrefix()} $msg")
    }

    override fun d(msg: String) {
        Log.d(getDefaultTag(), "${createPrefix()} $msg")
    }

    override fun i(tag: String, msg: String) {
        Log.i(tag, "${createPrefix()} $msg")
    }

    override fun i(msg: String) {
        Log.i(getDefaultTag(), "${createPrefix()} $msg")
    }

    override fun w(tag: String, msg: String) {
        Log.w(tag, "${createPrefix()} $msg")
    }

    override fun w(msg: String) {
        Log.w(getDefaultTag(), "${createPrefix()} $msg")
    }

    override fun e(tag: String, msg: String) {
        Log.e(tag, "${createPrefix()} $msg")
    }

    override fun e(msg: String) {
        Log.e(getDefaultTag(), "${createPrefix()} $msg")
    }

    override fun f(tag: String, msg: String) {
        Log.wtf(tag, "${createPrefix()} $msg")
    }

    override fun f(msg: String) {
        Log.wtf(getDefaultTag(), "${createPrefix()} $msg")
    }

}


fun main(){
    val logger = DefaultLogger()
    logger.i("####default logger")
}