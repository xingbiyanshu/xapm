/**
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
 *
 * @author Rui Li <lirui05@kuaishou.com>
 */

package com.kwai.koom.javaoom.hprof;

import android.os.Build;

import com.kwai.koom.fastdump.ForkJvmHeapDumper;

public class ForkStripHeapDumper {
  private boolean mLoadSuccess;

  private static class Holder {
    private static final ForkStripHeapDumper INSTANCE = new ForkStripHeapDumper();
  }

  public static ForkStripHeapDumper getInstance() {
    return ForkStripHeapDumper.Holder.INSTANCE;
  }

  private ForkStripHeapDumper() {}

  private void init() {
    if (mLoadSuccess) {
      return;
    }
    System.loadLibrary("hprofclipper");
    mLoadSuccess = true;
    initStripDump();
  }

  public synchronized boolean dump(String path) {
    int sdkInt = Build.VERSION.SDK_INT;
    if (!(Build.VERSION_CODES.LOLLIPOP <= sdkInt
            && sdkInt <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)){
      // not supported
      return false;
    }

    init();
    if (!mLoadSuccess) {
      return false;
    }
    boolean dumpRes = false;
    try {
      hprofName(path);
      dumpRes = ForkJvmHeapDumper.getInstance().dump(path);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return dumpRes;
  }

  public native void initStripDump();

  public native void hprofName(String name);
}