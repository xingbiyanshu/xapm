package com.sissi.lab.xapmdemo

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.kwai.koom.javaoom.hprof.ForkStripHeapDumper
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }


    var dumped=false
    fun onDumpClicked(v: View){
        Log.i("MainActivity", ">>> onDumpClicked")
        if (dumped){
            Log.i("MainActivity", "<<< dumped")
            return
        }
        dumped=true

        //Pull the hprof from the devices.
        //adb shell "run-as com.kwai.koom.demo cat 'files/test.hprof'" > ~/temp/test.hprof
//        ForkJvmHeapDumper.getInstance().dump(
//            getExternalFilesDir(null)!!.absolutePath + File.separator + "test.hprof"
//        )
        val result = ForkStripHeapDumper.getInstance().dump(
            getExternalFilesDir(null)!!.absolutePath + File.separator + "test.hprof"
        )
        Log.i("MainActivity", "<<< onDumpClicked result=$result")
    }

}