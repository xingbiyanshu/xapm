package com.sissi.apm.log

interface Logger {
    fun v(tag:String, msg:String)
    fun d(tag:String, msg:String)
    fun i(tag:String, msg:String)
    fun w(tag:String, msg:String)
    fun e(tag:String, msg:String)
    fun f(tag:String, msg:String)

    fun v(msg:String)
    fun d(msg:String)
    fun i(msg:String)
    fun w(msg:String)
    fun e(msg:String)
    fun f(msg:String)

}