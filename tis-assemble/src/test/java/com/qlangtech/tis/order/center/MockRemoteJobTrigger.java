/**
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.qlangtech.tis.order.center;

import com.qlangtech.tis.fullbuild.indexbuild.IRemoteTaskTrigger;
import com.qlangtech.tis.fullbuild.indexbuild.RunningStatus;

/**
 * @author 百岁（baisui@qlangtech.com）
 * @create: 2020-05-21 12:35
 */
public class MockRemoteJobTrigger implements IRemoteTaskTrigger {

    private final boolean success;

    public MockRemoteJobTrigger(boolean success) {
        this.success = success;
    }

    @Override
    public void run() {
    }

    @Override
    public String getTaskName() {
        return null;
    }

    @Override
    public RunningStatus getRunningStatus() {
        if (success) {
            return RunningStatus.SUCCESS;
        } else {
            return RunningStatus.FAILD;
        // throw new IllegalStateException("run faild");
        }
    // return success ? RunningStatus.SUCCESS : RunningStatus.FAILD;
    }
}
