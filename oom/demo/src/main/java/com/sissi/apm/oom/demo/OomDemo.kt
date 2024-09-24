package com.sissi.apm.oom.demo

import android.app.Application
import com.sissi.apm.log.DefaultLogger
import com.sissi.apm.log.Logger
import com.sissi.apm.oom.OomMonitor
import com.sissi.apm.proc.FdInfo
import com.sissi.apm.proc.MemInfo
import com.sissi.apm.proc.ThreadInfo
import com.sissi.apm.proc.toStr

class OomDemo : Application() {

    lateinit var logger:Logger
    override fun onCreate() {
        super.onCreate()
        logger = DefaultLogger()
        logger.i(MemInfo.get().toString())
        OomMonitor.apply {
            init(logger=logger)
            addListener(object :OomMonitor.Listener{
                override fun onOom(type: OomMonitor.OomType, info: Any) {
                    val oomInfo = when(type){
                        OomMonitor.OomType.JavaHeapOverFlow,
                        OomMonitor.OomType.JavaHeapSuddenlySwell->(info as MemInfo).toString()

                        OomMonitor.OomType.SystemMemoryExhausted -> (info as MemInfo).toString()
                        OomMonitor.OomType.CmaFailed -> (info as MemInfo).toString()
                        OomMonitor.OomType.FdExhausted -> (info as List<FdInfo>).toStr(true)
                        OomMonitor.OomType.ThreadOverFlow -> (info as List<ThreadInfo>).toStr()
                    }
                    logger.i("oomtype=$type, info=\n$oomInfo")
                    stop()
                }
            })
            start()
        }
    }

}