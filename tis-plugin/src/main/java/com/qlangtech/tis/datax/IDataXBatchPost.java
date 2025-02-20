/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qlangtech.tis.datax;

import com.qlangtech.tis.exec.IExecChainContext;
import com.qlangtech.tis.fullbuild.indexbuild.IRemoteTaskTrigger;
import com.qlangtech.tis.plugin.ds.ISelectedTab;

/**
 * 当datax任务有多个子任务完成之后（例如：hive数据同步，多个子库的表导入hdfs完成），需要将执行一次hive数据同步工作
 *
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2022-03-09 11:45
 **/
public interface IDataXBatchPost {

    public IRemoteTaskTrigger createPreExecuteTask(IExecChainContext execContext, ISelectedTab tab);

    public IRemoteTaskTrigger createPostTask(IExecChainContext execContext, ISelectedTab tab);

}
