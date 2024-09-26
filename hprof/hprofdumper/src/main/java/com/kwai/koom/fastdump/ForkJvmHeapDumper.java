/*
 * Copyright 2020 Kwai, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * A jvm hprof dumper which use fork and don't block main process.
 *
 * @author Rui Li <lirui05@kuaishou.com>
 */

package com.kwai.koom.fastdump;

//import static com.kwai.koom.base.Monitor_ApplicationKt.sdkVersionMatch;
//import static com.kwai.koom.base.Monitor_SoKt.loadSoQuietly;

import java.io.File;
import java.io.IOException;

import android.os.Build;
import android.os.Debug;
import android.util.Log;

//import com.kwai.koom.base.MonitorBuildConfig;
//import com.kwai.koom.base.MonitorLog;
//import com.kwai.koom.base.MonitorManager;

public class ForkJvmHeapDumper implements HeapDumper {
  private boolean mLoadSuccess;

  private static class Holder {
    private static final ForkJvmHeapDumper INSTANCE = new ForkJvmHeapDumper();
  }

  public static ForkJvmHeapDumper getInstance() {
    return ForkJvmHeapDumper.Holder.INSTANCE;
  }

  private ForkJvmHeapDumper() {}

  private void init () {
    if (mLoadSuccess) {
      return;
    }

    System.loadLibrary("hprofdumper");
    mLoadSuccess = true;
    nativeInit();
  }

  @Override
  public synchronized boolean dump(String path) {
//    MonitorLog.i(TAG, "dump " + path);
//    if (!sdkVersionMatch()) {
//      throw new UnsupportedOperationException("dump failed caused by sdk version not supported!");
//    }

    init();
    if (!mLoadSuccess) {
//      MonitorLog.e(TAG, "dump failed caused by so not loaded!");
      return false;
    }

    File f = new File(path).getParentFile();
    if (f==null){
      return false;
    }
    if (!f.exists()){
      f.mkdirs();
    }

    File hprof = new File(path);
    if (!hprof.exists()){
        try {
            hprof.createNewFile();
            hprof.setReadable(true);
            hprof.setWritable(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    boolean dumpRes = false;
    try {
//      MonitorLog.i(TAG, "before suspend and fork.");
      int pid = suspendAndFork();
      if (pid == 0) {
        // Child process
        Log.i("DUMP", "start dump");
        Debug.dumpHprofData(path);
        Log.i("DUMP", "finish dump");
        exitProcess();
      } else if (pid > 0) {
        // Parent process
        Log.i("DUMP", "waiting dump proc "+pid);
        dumpRes = resumeAndWait(pid);
        Log.i("DUMP", "dump proc finished!");
//        MonitorLog.i(TAG, "dump " + dumpRes + ", notify from pid " + pid);
      }
    } catch (IOException e) {
//      MonitorLog.e(TAG, "dump failed caused by " + e);
      e.printStackTrace();
    }
    return dumpRes;
  }

  /**
   * Init before do dump.
   */
  private native void nativeInit();

  /**
   * Suspend the whole ART, and then fork a process for dumping hprof.
   *
   * @return return value of fork
   */
  private native int suspendAndFork();

  /**
   * Resume the whole ART, and then wait child process to notify.
   *
   * @param pid pid of child process.
   */
  private native boolean resumeAndWait(int pid);

  /**
   * Exit current process.
   */
  private native void exitProcess();
}
