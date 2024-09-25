package com.sissi.apm.oom.demo

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kwai.koom.javaoom.hprof.ForkStripHeapDumper
import com.sissi.apm.log.DefaultLogger
import com.sissi.apm.log.Logger
import com.sissi.apm.proc.MemInfo
import kotlinx.coroutines.delay
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    lateinit var logger:Logger
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger = DefaultLogger()
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    internal class Test {
        var bytes: ByteArray = ByteArray(1024)
    }

    var dumped=false
    fun onOomClicked(v: View){
        Toast.makeText(this, "clicked", Toast.LENGTH_SHORT).show() // 用于判断dump hprof时是否阻塞主线程
        if (dumped){
            return
        }
        val thresHold = MemInfo.getProcJavaHeapOomThreshold()
        // JavaHeapSuddenlySwell or JavaHeapOverFlow
        thread {
            val l= mutableListOf<Test>()
            var hprofDumped=false
            for (i in 0..1000*1000*1000){
                l.add(Test())
                val over = i/1024.0>thresHold*0.9
                if (over) {
                    Thread.sleep(1)
                    if (i%100==0){
                        logger.i("size=${i/1024.0}MB")
                    }
                }
                if (over && !hprofDumped){
                    logger.i("==== dump hprof")
                    ForkStripHeapDumper.getInstance().dump(
                        filesDir!!.absolutePath + File.separator + "test.hprof"
                    )
                    hprofDumped = true
                }
            }
        }

        // ThreadOverFlow
//        thread {
//            (0..1000).forEach {
//                thread(name="myThread$it") {
//                    logger.i("thread $it")
//                    Thread.sleep(30*1000)
//                }
//                Thread.sleep(20)
//            }
//        }

        // FdExhausted
//        thread {
//            val l= mutableListOf<BufferedWriter>()
//            (0..1500).forEach {
//                logger.i("fd $it")
//                val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(File("/dev/null"))))
//                writer.write("null")
//                l.add(writer)
//                Thread.sleep(20)
//            }
//            Thread.sleep(60*1000)
//        }

        dumped = true
    }
}