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
package com.qlangtech.tis.util;

import com.google.common.collect.Lists;
import com.qlangtech.tis.IPluginEnum;
import com.qlangtech.tis.extension.Describable;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.IPropertyType;
import com.qlangtech.tis.plugin.ds.PostedDSProp;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 解析提交的plugin元数据信息，如果plugin为"xxxplugin:require" 则是在告诉服务端，该plugin必须要有输入内容，该plugin不可缺省
 *
 * @author 百岁（baisui@qlangtech.com）
 * @create: 2020-07-20 11:00
 */
public class UploadPluginMeta {

    public static final String KEY_PLUGIN_META = UploadPluginMeta.class.getName();

    private static final String ATTR_KEY_VALUE_SPLIT = "_";

    private static final String KEY_JUST_GET_ITEM_RELEVANT = "justGetItemRelevant";

    private static final Pattern PATTERN_PLUGIN_ATTRIBUTE = Pattern.compile("[" + ATTR_KEY_VALUE_SPLIT + "\\-\\w\\.]+");

    public static final Pattern PATTERN_PLUGIN_ATTRIBUTE_KEY_VALUE_PAIR
            = Pattern.compile("([^" + ATTR_KEY_VALUE_SPLIT + "]+?)" + ATTR_KEY_VALUE_SPLIT + "(" + PATTERN_PLUGIN_ATTRIBUTE.pattern() + ")");

    private static final Pattern PATTERN_PLUGIN_META = Pattern.compile("(.+?)(:(,?(" + PATTERN_PLUGIN_ATTRIBUTE + "))+)?");

    public static final String KEY_REQUIRE = "require";

    public static final String KEY_UNCACHE = "uncache";

    //纯添加类型，更新之前需要将之前的类型plugin先load出来再更新
    public static final String KEY_APPEND = "append";

    // 服务端对目标插件的Desc进行过滤
    public static final String KEY_TARGET_PLUGIN_DESC = "targetItemDesc";

    // 禁止向context中写入biz状态
    public static final String KEY_DISABLE_BIZ_SET = "disableBizStore";

    private final String name;

    // plugin form must contain field where prop required is true
    private boolean required;
    // 除去 required 之外的其他参数
    private Map<String, String> extraParams = new HashMap<>();
    private final IPluginContext context;

    public boolean isUpdate() {
        return this.getBoolean(PostedDSProp.KEY_UPDATE);
    }


    public void putExtraParams(String key, String val) {
        this.extraParams.put(key, val);
    }

    /**
     * 纯添加类型，更新之前需要将之前的类型plugin先load出来再更新合并之后再更新
     *
     * @return
     */
    public boolean isAppend() {
        return this.getBoolean(KEY_APPEND);
    }

    public String getTargetPluginDesc() {
        return this.getExtraParam(KEY_TARGET_PLUGIN_DESC);
    }

    public boolean isDisableBizSet() {
        return this.getBoolean(KEY_DISABLE_BIZ_SET);
    }

    public static void main(String[] args) throws Exception {

        Matcher matcher = PATTERN_PLUGIN_ATTRIBUTE_KEY_VALUE_PAIR.matcher("dsname_dsname_yuqing_zj2_bak");

        System.out.println(matcher.matches());
        System.out.println(matcher.group(1));
        System.out.println(matcher.group(2));

//        Matcher matcher = PATTERN_PLUGIN_ATTRIBUTE.matcher("_3sfgG");
////        if(matcher.matches()){
////
////        }
//        System.out.println(matcher.matches());
    }

    public static List<UploadPluginMeta> parse(String[] plugins) {
        return parse(null, plugins);
    }

    public static List<UploadPluginMeta> parse(IPluginContext context, String[] plugins) {
        if (plugins == null || plugins.length < 1) {
            throw new IllegalArgumentException("plugin size:" + plugins.length + " length can not small than 1");
        }
        List<UploadPluginMeta> metas = Lists.newArrayList();
        for (String plugin : plugins) {
            metas.add(parse(context, plugin));
        }
        if (plugins.length != metas.size()) {
            throw new IllegalStateException("param plugins length:" + plugins.length + " must equal with metaSize:" + metas.size());
        }
        return metas;
    }

    public IPluginContext getPluginContext() {
        return this.context;
    }


    public static UploadPluginMeta parse(String plugin) {
        return parse(null, plugin);
    }

    /**
     * @param plugin
     * @return
     */
    public static UploadPluginMeta parse(IPluginContext context, String plugin) {
        Matcher matcher, attrKVMatcher;
        UploadPluginMeta pmeta;
        Matcher attrMatcher;
        String attr;
        matcher = PATTERN_PLUGIN_META.matcher(plugin);
        if (matcher.matches()) {
            pmeta = new UploadPluginMeta(context, matcher.group(1));
            if (matcher.group(2) != null) {
                attrMatcher = PATTERN_PLUGIN_ATTRIBUTE.matcher(matcher.group(2));
                while (attrMatcher.find()) {
                    attr = attrMatcher.group();
                    switch (attr) {
                        case KEY_REQUIRE:
                            pmeta.required = true;
                            break;
                        default:
                            attrKVMatcher = PATTERN_PLUGIN_ATTRIBUTE_KEY_VALUE_PAIR.matcher(attr);
                            if (!attrKVMatcher.matches()) {
                                throw new IllegalStateException("attr:" + attr + " is not match:" + PATTERN_PLUGIN_ATTRIBUTE_KEY_VALUE_PAIR.pattern());
                            }
//                            String[] pair = StringUtils.split(attr, ATTR_KEY_VALUE_SPLIT);
//                            if (pair.length != 2) {
//                                throw new IllegalStateException("attr:" + attr + " is illegal");
//                            }
                            pmeta.extraParams.put(attrKVMatcher.group(1), attrKVMatcher.group(2));
                    }
                }
            }
            return pmeta;
            //metas.add(pmeta);
        } else {
            throw new IllegalStateException("plugin:'" + plugin + "' is not match the pattern:" + PATTERN_PLUGIN_META);
        }
    }

    public IPluginEnum getHeteroEnum() {
        return HeteroEnum.of(this.getName());
    }


    public String getName() {
        return name;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }


    public Optional<IPropertyType.SubFormFilter> getSubFormFilter() {

        String targetDesc = this.getExtraParam(IPropertyType.SubFormFilter.PLUGIN_META_TARGET_DESCRIPTOR_NAME);
        String targetDescImpl = this.getExtraParam(IPropertyType.SubFormFilter.PLUGIN_META_TARGET_DESCRIPTOR_IMPLEMENTION);

        String subFormField = this.getExtraParam(IPropertyType.SubFormFilter.PLUGIN_META_SUB_FORM_FIELD);
        if (StringUtils.isNotEmpty(targetDesc)) {
            return Optional.of(new IPropertyType.SubFormFilter(this, targetDesc, targetDescImpl, subFormField));
        }
        return Optional.empty();
    }

    public String getExtraParam(String key) {
        return this.extraParams.get(key);
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(this.getExtraParam(key));
    }

    private UploadPluginMeta(IPluginContext context, String name) {
        this.name = name;
        this.context = context;
    }

    @Override
    public String toString() {
        return "UploadPluginMeta{" + "name='" + name + '\'' + ", required=" + required +
                "," + this.extraParams.entrySet().stream().map((e) -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",")) + '}';
    }

    public <T extends Describable<T>> HeteroList<T> getHeteroList(IPluginContext pluginContext) {
        IPluginEnum hEnum = getHeteroEnum();
        HeteroList<T> hList = new HeteroList<>(this);
        hList.setCaption(hEnum.getCaption());
        hList.setIdentityId(hEnum.getIdentity());
        hList.setExtensionPoint(hEnum.getExtensionPoint());
        List<T> items = hEnum.getPlugins(pluginContext, this);
        hList.setItems(items);

        List<Descriptor<T>> descriptors = hEnum.descriptors();
        if (StringUtils.isNotEmpty(this.getTargetPluginDesc())) {
            descriptors = descriptors.stream()
                    .filter((desc) -> this.getTargetPluginDesc().equals(desc.getDisplayName()))
                    .collect(Collectors.toList());
        }
        String targetDesc = this.getExtraParam(IPropertyType.SubFormFilter.PLUGIN_META_TARGET_DESCRIPTOR_NAME);
        boolean justGetItemRelevant = Boolean.parseBoolean(this.getExtraParam(KEY_JUST_GET_ITEM_RELEVANT));
        if (justGetItemRelevant) {
            Set<String> itemRelevantDescNames = items.stream().map((i) -> i.getDescriptor().getDisplayName()).collect(Collectors.toSet());
            descriptors = descriptors.stream().filter((d) -> itemRelevantDescNames.contains(d.getDisplayName())).collect(Collectors.toList());
        } else if (StringUtils.isNotEmpty(targetDesc)) {
            descriptors = descriptors.stream().filter((d) -> targetDesc.equals(d.getDisplayName())).collect(Collectors.toList());
        }
        hList.setDescriptors(descriptors);

        hList.setSelectable(hEnum.getSelectable());
        return hList;
    }
}
