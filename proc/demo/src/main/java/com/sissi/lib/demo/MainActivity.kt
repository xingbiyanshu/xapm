package com.sissi.lib.demo

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sissi.lib.proc.MemInfo
import com.sissi.lib.proc.ThreadInfo
import com.sissi.lib.proc.toStr

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
        val thrInfo = ThreadInfo.getAll()
        println("/############ memInfo\n${memInfo.toString()}")
        val count = ThreadInfo.getCount()
        println("/############count=$count thrInfo(${thrInfo.size})\n${thrInfo.toStr()}")
    }
}