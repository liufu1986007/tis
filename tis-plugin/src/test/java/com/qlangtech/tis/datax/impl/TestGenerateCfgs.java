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

package com.qlangtech.tis.datax.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.qlangtech.tis.common.utils.Assert;
import com.qlangtech.tis.datax.IDataxProcessor;
import org.apache.commons.collections.CollectionUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2022-03-09 22:30
 **/
public class TestGenerateCfgs {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();


    @Test
    public void testReadFromGen() throws Exception {
        DataXCfgGenerator.GenerateCfgs genCfgs = new DataXCfgGenerator.GenerateCfgs();
        long timestamp = System.currentTimeMillis();
        genCfgs.setGenTime(timestamp);
        Map<String, List<String>> groupedChildTask = Maps.newHashMap();
        String tabName = "user";
        groupedChildTask.put(tabName, Lists.newArrayList(tabName + "_1", tabName + "_2"));
        genCfgs.setGroupedChildTask(groupedChildTask);

        File dataxCfgDir = folder.newFolder();
        genCfgs.write2GenFile(dataxCfgDir);

        DataXCfgGenerator.GenerateCfgs generateCfgs = DataXCfgGenerator.GenerateCfgs.readFromGen(dataxCfgDir);

        Assert.assertEquals(timestamp, generateCfgs.getGenTime());

        List<String> childTasks = generateCfgs.getDataXTaskDependencies(tabName);

        Assert.assertNotNull(childTasks);

        Assert.assertTrue(
                CollectionUtils.isEqualCollection(groupedChildTask.get(tabName)
                                .stream().map((childTsk) -> childTsk + IDataxProcessor.DATAX_CREATE_DATAX_CFG_FILE_NAME_SUFFIX).collect(Collectors.toList())
                        , childTasks));

    }
}
