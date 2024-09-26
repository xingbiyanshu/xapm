package com.sissi.apm.hprof.demo

import SevenZip.Compression.LZMA.EncoderWrapper
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kwai.koom.javaoom.hprof.ForkStripHeapDumper
import java.io.File
import kotlin.concurrent.timer

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }


    var dumped=false
    fun onDumpClicked(v: View){
        Log.i("MainActivity", ">>> onDumpClicked")

        // 检测dump是否阻塞主线程
        Toast.makeText(this, "XXXXX", Toast.LENGTH_SHORT).show()

        if (dumped){
            Log.i("MainActivity", "<<< dumped")
            return
        }
        dumped=true

        /*
        * 导出裁剪过的hprof文件。复原需用到tools下面的koom-fill-crop.jar：
        * */
        //adb shell "run-as com.kwai.koom.demo cat 'files/test.hprof'" > ~/temp/test.hprof
//        ForkJvmHeapDumper.getInstance().dump(
//            getExternalFilesDir(null)!!.absolutePath + File.separator + "test.hprof"
//        )
        val hprofPath = getExternalFilesDir(null)!!.absolutePath + File.separator + "test.hprof"
        val result = ForkStripHeapDumper.getInstance().dump(hprofPath) {
            if (it) {
                EncoderWrapper().code(File(hprofPath), File("$hprofPath.enc"), null)
            }
        }

        Log.i("MainActivity", "<<< onDumpClicked result=$result")
    }

}