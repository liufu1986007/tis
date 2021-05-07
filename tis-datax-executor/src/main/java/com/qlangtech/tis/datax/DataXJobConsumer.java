/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 * This program is free software: you can use, redistribute, and/or modify
 * it under the terms of the GNU Affero General Public License, version 3
 * or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.qlangtech.tis.datax;

import com.alibaba.datax.core.util.container.JarLoader;
import com.qlangtech.tis.TIS;
import com.qlangtech.tis.cloud.AdapterTisCoordinator;
import com.qlangtech.tis.cloud.ITISCoordinator;
import com.qlangtech.tis.manage.common.CenterResource;
import com.qlangtech.tis.manage.common.Config;
import com.qlangtech.tis.manage.common.HttpUtils;
import com.qlangtech.tis.solrj.util.ZkUtils;
import com.tis.hadoop.rpc.RpcServiceReference;
import com.tis.hadoop.rpc.StatusRpcClient;
import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.queue.DistributedQueue;
import org.apache.curator.framework.recipes.queue.QueueBuilder;
import org.apache.curator.framework.recipes.queue.QueueConsumer;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * DataX 执行器
 *
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-05-06 14:57
 **/
public class DataXJobConsumer implements QueueConsumer<CuratorTaskMessage> {
    private static final Logger logger = LoggerFactory.getLogger(DataXJobConsumer.class);
    private final DataxExecutor dataxExecutor;
    private final CuratorFramework curatorClient;

    public DataXJobConsumer(DataxExecutor dataxExecutor, CuratorFramework curatorClient) {
        this.dataxExecutor = dataxExecutor;
        this.curatorClient = curatorClient;
    }


    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            throw new IllegalArgumentException("args length can not small than 2");
        }

//        List<String> children = zkClient.getChildren("/", null, true);
//        Objects.requireNonNull(children);

        String zkQueuePath = args[1]; //System.getProperty(DataxUtils.DATAX_QUEUE_ZK_PATH);
        String zkAddress = args[0]; //System.getProperty(DataxUtils.DATAX_ZK_ADDRESS);


        final JarLoader uberClassLoader = new JarLoader(new String[]{"."}) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                return TIS.get().getPluginManager().uberClassLoader.findClass(name);
            }
        };

        CuratorFramework curatorClient = getCuratorFramework(zkAddress);
        ITISCoordinator coordinator = getCoordinator(zkAddress, curatorClient);

        RpcServiceReference statusRpc = StatusRpcClient.getService(coordinator);

        DataxExecutor dataxExecutor = new DataxExecutor(statusRpc, uberClassLoader);
        // String dataxName, Integer jobId, String jobName, String jobPath
        DataXJobConsumer dataXJobConsume = new DataXJobConsumer(dataxExecutor, curatorClient);

        dataXJobConsume.createQueue(zkQueuePath);

        synchronized (dataXJobConsume) {
            dataXJobConsume.wait();
        }
    }

    private void createQueue(String zkQueuePath) {
        createQueue(this.curatorClient, zkQueuePath, this);
    }

    public static CuratorFramework getCuratorFramework(String zkAddress) {
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFrameworkFactory.Builder curatorBuilder = CuratorFrameworkFactory.builder();
        curatorBuilder.retryPolicy(retryPolicy);
        CuratorFramework curatorClient = curatorBuilder
//                .zookeeperFactory(new DefaultZookeeperFactory() {
//                    @Override
//                    public ZooKeeper newZooKeeper(String connectString, int sessionTimeout, Watcher watcher, boolean canBeReadOnly) throws Exception {
//                        if (StringUtils.equals(connectString, Config.getZKHost())) {
//                            logger.info("use the TIS system zookeeper instance,system zkHost:{},plugin zkHost:{}", Config.getZKHost(), connectString);
//                            return zkClient.getZK().getSolrZooKeeper();
//                        } else {
//                            logger.info("create TIS new zookeeper instance with ,system zkHost:{},plugin zkHost:{}", Config.getZKHost(), connectString);
//                            return super.newZooKeeper(connectString, sessionTimeout, watcher, canBeReadOnly);
//                        }
//                    }
//                })
                .connectString(zkAddress).build();
        curatorClient.start();
        return curatorClient;
    }

    private static ITISCoordinator getCoordinator(String zkAddress, CuratorFramework curatorClient) throws Exception {
        ITISCoordinator coordinator = null;
        //if (StringUtils.equals(zkAddress, Config.getZKHost())) {
        ZooKeeper zooKeeper = curatorClient.getZookeeperClient().getZooKeeper();
        coordinator = new AdapterTisCoordinator() {
            @Override
            public List<String> getChildren(String zkPath, Watcher watcher, boolean b) {
                try {
                    return zooKeeper.getChildren(zkPath, watcher);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public byte[] getData(String zkPath, Watcher o, Stat stat, boolean b) {
                try {
                    return zooKeeper.getData(zkPath, o, stat);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        logger.info("create TIS new zookeeper instance with ,system zkHost:{}", Config.getZKHost());
//        } else {
//            coordinator = new TisZkClient(Config.getZKHost(), 60000);
//            logger.info("use the TIS system zookeeper instance,system zkHost:{},plugin zkHost:{}", Config.getZKHost(), zkAddress);
//        }
        return coordinator;
    }

    public static DistributedQueue<CuratorTaskMessage> createQueue(CuratorFramework curatorClient, String zkQueuePath
            , QueueConsumer<CuratorTaskMessage> consumer) {
        try {
            if (StringUtils.isEmpty(zkQueuePath)) {
                throw new IllegalArgumentException("param zkQueuePath can not be null");
            }
            // TaskConfig taskConfig = TaskConfig.getInstance();
            int count = 0;
            while (!curatorClient.getZookeeperClient().isConnected()) {
                if (count++ > 4) {
                    throw new IllegalStateException(" zookeeper server can not be established");
                }
                logger.info("waiting connect to zookeeper server");
                Thread.sleep(5000);
            }

            ZkUtils.guaranteeExist(curatorClient.getZookeeperClient().getZooKeeper(), zkQueuePath);

            QueueBuilder<CuratorTaskMessage> builder = QueueBuilder.builder(curatorClient, consumer, new MessageSerializer(), zkQueuePath);
            // .maxItems(4);

            DistributedQueue<CuratorTaskMessage> queue = builder.buildQueue();
            queue.start();
            return queue;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void consumeMessage(CuratorTaskMessage msg) throws Exception {
        //  String dataxName, Integer jobId, String jobName, String jobPath
        String dataxName = msg.getDataXName();
        Integer jobId = msg.getJobId();
        String jobPath = msg.getJobPath();
        String jobName = msg.getJobName();
        logger.info("process DataX job, dataXName:{},jobid:{},jobName:{},jobPath:{}", dataxName, jobId, jobName, jobPath);
        DataxExecutor.synchronizeDataXPluginsFromRemoteRepository(dataxName);
        try {
            dataxExecutor.startWork(dataxName, jobId, jobName, jobPath);
        } finally {
            TIS.cleanTIS();
        }
    }

    @Override
    public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
        logger.warn("curator stateChanged to new Status:" + connectionState);
    }
}
