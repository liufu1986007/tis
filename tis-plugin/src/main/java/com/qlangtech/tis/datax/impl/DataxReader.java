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
import com.qlangtech.tis.TIS;
import com.qlangtech.tis.annotation.Public;
import com.qlangtech.tis.datax.IDataXPluginMeta;
import com.qlangtech.tis.datax.IDataxReader;
import com.qlangtech.tis.extension.Describable;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.PluginFormProperties;
import com.qlangtech.tis.extension.impl.SuFormProperties;
import com.qlangtech.tis.plugin.IPluginStore;
import com.qlangtech.tis.plugin.KeyedPluginStore;
import com.qlangtech.tis.plugin.PluginStore;
import com.qlangtech.tis.util.IPluginContext;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * datax Reader
 *
 * @author 百岁（baisui@qlangtech.com）
 * @date 2021-04-07 14:48
 */
@Public
public abstract class DataxReader implements Describable<DataxReader>, IDataxReader, IPluginStore.RecyclableController {
    public static final String HEAD_KEY_REFERER = "Referer";
    public static final ThreadLocal<DataxReader> dataxReaderThreadLocal = new ThreadLocal<>();

    public static DataxReader getThreadBingDataXReader() {
        DataxReader reader = dataxReaderThreadLocal.get();
        return reader;
    }

    public static KeyedPluginStore<DataxReader> getPluginStore(IPluginContext pluginContext, String appname) {
        return getPluginStore(pluginContext, false, appname);
    }

    /**
     * @param pluginContext
     * @param db            是否是db相关配置
     * @param appname
     * @return
     */
    public static KeyedPluginStore<DataxReader> getPluginStore(IPluginContext pluginContext, boolean db, String appname) {
        return TIS.dataXReaderPluginStore.get(createDataXReaderKey(pluginContext, db, appname));
    }

    public static void cleanPluginStoreCache(IPluginContext pluginContext, boolean db, String appname) {
        TIS.DataXReaderAppKey appKey = createDataXReaderKey(pluginContext, db, appname);
        TIS.dataXReaderPluginStore.clear(appKey);
        //if (pluginContext != null && pluginContext.getExecId() != null) {

        if (pluginContext != null) {
            // 保证在DataXAction+doUpdateDatax 方法中两次调用以下方法块只被调用一次
            return;
        }
        // 需要将非编辑模式下的对象也跟新一下=====================================
        Set<Map.Entry<SubFieldFormAppKey<? extends Describable>, KeyedPluginStore<? extends Describable>>>
                entries = TIS.dataXReaderSubFormPluginStore.getEntries();
        List<SubFieldFormAppKey<? extends Describable>> willDelete = Lists.newArrayList();
        entries.forEach((e) -> {
            SubFieldFormAppKey key = e.getKey();
            if (StringUtils.equals(key.keyVal.getVal(), appname) && key.isDB == db) {
                willDelete.add(key);
            }
//                if (key.isSameAppName(appname, db) && StringUtils.isEmpty(key.keyVal.getSuffix())) {
//                    setReaderSubFormProp(props, e.getValue().getPlugin(), pluginStore.getPlugin());
//                }
        });
        willDelete.forEach((deleteKey) -> {
            TIS.dataXReaderSubFormPluginStore.clear(deleteKey);
        });
        //}
    }

    private static TIS.DataXReaderAppKey createDataXReaderKey(IPluginContext pluginContext, boolean db, String appname) {
        final TIS.DataXReaderAppKey key = new TIS.DataXReaderAppKey(pluginContext, db, appname, new PluginStore.IPluginProcessCallback<DataxReader>() {
            @Override
            public void afterDeserialize(final DataxReader reader) {

                List<PluginFormProperties> subFieldFormPropertyTypes = reader.getDescriptor().getSubPluginFormPropertyTypes();
                if (subFieldFormPropertyTypes.size() > 0) {
                    // 加载子字段
                    subFieldFormPropertyTypes.forEach((pt) -> {
                        pt.accept(new PluginFormProperties.IVisitor() {
                            @Override
                            public Void visit(final SuFormProperties props) {
//                                SubFieldFormAppKey<? extends Describable> subFieldKey
//                                        = new SubFieldFormAppKey<>(pluginContext, db, appname, props, props.subFormFieldsAnnotation.desClazz());

                                SubFieldFormAppKey<? extends Describable> subFieldKey
                                        = new SubFieldFormAppKey<>(pluginContext, db, appname, props, DataxReader.class);


                                KeyedPluginStore<? extends Describable> subFieldStore = KeyedPluginStore.getPluginStore(subFieldKey);

                                // 子表单中的内容更新了之后，要同步父表单中的状态
                                subFieldStore.addPluginsUpdateListener(
                                        new PluginStore.PluginsUpdateListener(subFieldKey.getSerializeFileName(), reader) {
                                            @Override
                                            public void accept(PluginStore<Describable> pluginStore) {
                                                setReaderSubFormProp(props, pluginStore.getPlugins());
                                            }
                                        });
                                List<? extends Describable> subItems = subFieldStore.getPlugins();
                                if (CollectionUtils.isEmpty(subItems)) {
                                    return null;
                                }
                                setReaderSubFormProp(props, subItems);
                                return null;
                            }

                            private void setReaderSubFormProp(SuFormProperties props, List<? extends Describable> subItems) {
                                setReaderSubFormProp(props, reader, subItems);
                            }

                            private void setReaderSubFormProp(SuFormProperties props, DataxReader reader, List<? extends Describable> subItems) {
                                if (reader == null) {
                                    return;
                                }

                                subItems.forEach((item) -> {
                                    if (!props.instClazz.isAssignableFrom(item.getClass())) {
                                        throw new IllegalStateException("appname:" + appname + ",item class[" + item.getClass().getSimpleName()
                                                + "] is not type of " + props.instClazz.getName());
                                    }
                                });
                                try {
                                    props.subFormField.set(reader, subItems);
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException("get subField:" + props.getSubFormFieldName(), e);
                                }
                            }
                        });
                    });
                }
            }
        });
        return key;
    }


    public interface IDataxReaderGetter {
        DataxReader get(String appName);
    }

    /**
     * 测试用
     */
    public static IDataxReaderGetter dataxReaderGetter;

    public static DataxReader load(IPluginContext pluginContext, String appName) {
        return load(pluginContext, false, appName);
    }

    /**
     * load
     *
     * @param appName
     * @return
     */
    public static DataxReader load(IPluginContext pluginContext, boolean isDB, String appName) {

        DataxReader reader = null;
        if (dataxReaderGetter != null) {
            reader = dataxReaderGetter.get(appName);
            DataxReader.dataxReaderThreadLocal.set(reader);
            return reader;
        }

        reader = getPluginStore(pluginContext, isDB, appName).getPlugin();
        Objects.requireNonNull(reader, "appName:" + appName + " relevant appSource can not be null");
        DataxReader.dataxReaderThreadLocal.set(reader);
        return reader;
    }

    //    public static class DBKey extends KeyedPluginStore.Key<DataxReader> {
//        public DBKey(IPluginContext pluginContext, String appname) {
//            super(TIS.DB_GROUP_NAME, AppKey.calAppName(pluginContext, appname), DataxReader.class);
//        }
//    }

    public static class SubFieldFormAppKey<TT extends Describable> extends KeyedPluginStore.AppKey<TT> {
        public final SuFormProperties subfieldForm;

        public SubFieldFormAppKey(IPluginContext pluginContext, boolean isDB, String appname, SuFormProperties subfieldForm, Class<TT> clazz) {
            super(pluginContext, isDB, Objects.requireNonNull(appname, "appname can not be empty"), clazz);
            this.subfieldForm = subfieldForm;
        }

        @Override
        protected String getSerializeFileName() {
            return super.getSerializeFileName() + "." + this.subfieldForm.getSubFormFieldName();
        }
    }

    //IPluginStore.Recyclable relevant
    private transient boolean dirty = false;

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public void signDirty() {
        // 标记可以在PluginStore中被剔出了
        this.dirty = true;
    }

    //IPluginStore.Recyclable stop
    @Override
    public final Descriptor<DataxReader> getDescriptor() {
        Descriptor<DataxReader> descriptor = TIS.get().getDescriptor(this.getClass());
        Class<BaseDataxReaderDescriptor> expectClass = getExpectDescClass();
        if (!(expectClass.isAssignableFrom(descriptor.getClass()))) {
            throw new IllegalStateException(descriptor.getClass() + " must implement the Descriptor of " + expectClass.getName());
        }
        return descriptor;
    }

    protected <TT extends DataxReader.BaseDataxReaderDescriptor> Class<TT> getExpectDescClass() {
        return (Class<TT>) BaseDataxReaderDescriptor.class;
    }

    public static abstract class BaseDataxReaderDescriptor extends Descriptor<DataxReader> {

        @Override
        public final Map<String, Object> getExtractProps() {
            Map<String, Object> eprops = new HashMap<>();
            eprops.put("rdbms", this.isRdbms());
            if (this.getEndType() != null) {
                eprops.put("endType", this.getEndType().getVal());
            }
            return eprops;
        }

        /**
         * 如果返回null则说明不支持增量同步功能
         *
         * @return
         */
        protected IDataXPluginMeta.EndType getEndType() {
            return null;
        }


        /**
         * 像Mysql会有明确的表名，而OSS没有明确的表名,RDBMS 关系型数据库 应该都为true
         *
         * @return
         */
        public boolean hasExplicitTable() {
            return this.isRdbms();
        }

        /**
         * 是否可以选择多个表，像Mysql这样的 ,RDBMS 关系型数据库 应该都为true
         *
         * @return
         */
        public abstract boolean isRdbms();
    }
}
