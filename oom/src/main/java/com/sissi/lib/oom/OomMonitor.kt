package com.sissi.lib.oom

import com.sissi.lib.proc.MemInfo
import com.sissi.lib.proc.ThreadInfo

class OomMonitor {
    fun start(){
        val memInfo=MemInfo.get()
        val thrInfo = ThreadInfo.getAll()
    }
}