package com.sissi.apm.oom.demo

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sissi.apm.log.DefaultLogger
import com.sissi.apm.log.Logger
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

    fun onOomClicked(v: View){
        // JavaHeapSuddenlySwell or JavaHeapOverFlow
//        thread {
//            val l= mutableListOf<Test>()
//            for (i in 0..1000*1000*1000){
//                l.add(Test())
//                if (i>1000*330) {
//                    Thread.sleep(1)
//                    if (i%100==0){
//                        logger.i("size=${i/1000.0}MB")
//                    }
//                }
//            }
//        }

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

    }
}