package com.sissi.apm.proc.demo

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sissi.apm.proc.FdInfo
import com.sissi.apm.proc.MemInfo
import com.sissi.apm.proc.ThreadInfo
import com.sissi.apm.proc.toStr

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val memInfo = MemInfo.get()
        println("/############ memInfo\n$memInfo")
        val thrInfo = ThreadInfo.getAll()
        var count = ThreadInfo.getCount()
        println("/############count=$count thrInfo(${thrInfo.size})\n${thrInfo.toStr()}")
        val fdInfos = FdInfo.getAll()
        count= FdInfo.getCount()
        println("/############count=$count FdInfo\n${fdInfos.toStr(true)}")
    }
}