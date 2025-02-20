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
package com.qlangtech.tis.extension.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.qlangtech.tis.TIS;
import com.qlangtech.tis.extension.Describable;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.IPropertyType;
import com.qlangtech.tis.extension.PluginFormProperties;
import com.qlangtech.tis.plugin.IdentityName;
import com.qlangtech.tis.plugin.annotation.SubForm;
import com.qlangtech.tis.util.DescriptorsJSON;
import com.qlangtech.tis.util.UploadPluginMeta;
import groovy.lang.GroovyClassLoader;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author 百岁（baisui@qlangtech.com）
 * @date 2021-04-11 13:22
 */
public class SuFormProperties extends PluginFormProperties implements IPropertyType {

    public static final ThreadLocal<SuFormGetterContext> subFormGetterProcessThreadLocal = ThreadLocal.withInitial(() -> {
        return new SuFormGetterContext();
    });

    public static SuFormGetterContext setSuFormGetterContext(Describable plugin, UploadPluginMeta pluginMeta, String subFormDetailId) {
        SuFormProperties.SuFormGetterContext subFormContext = subFormGetterProcessThreadLocal.get();
        subFormContext.plugin = plugin;
        pluginMeta.putExtraParams(IPropertyType.SubFormFilter.PLUGIN_META_SUBFORM_DETAIL_ID_VALUE, subFormDetailId);
        subFormContext.param = pluginMeta;
        return subFormContext;
    }

    public final Map<String, /*** fieldname */PropertyType> fieldsType;
    public final Field subFormField;
    public final Descriptor subFormFieldsDescriptor;
    public Class instClazz;
    public final SubForm subFormFieldsAnnotation;
    public final Class<?> parentClazz;
    public final PropertyType pkPropertyType;

    private DescriptorsJSON.IPropGetter subFormFieldsEnumGetter;

    public static SuFormProperties copy(Map<String, /*** fieldname */PropertyType> fieldsType
            , Class<?> instClazz, Descriptor subFormFieldsDescriptor, SuFormProperties old) {
        SuFormProperties result = new SuFormProperties(old.parentClazz, old.subFormField
                , old.subFormFieldsAnnotation, subFormFieldsDescriptor, fieldsType);
        if (!old.subFormFieldsAnnotation.desClazz().isAssignableFrom(instClazz)) {
            throw new IllegalStateException("class:" + old.subFormFieldsAnnotation.desClazz() + " must be parent of " + instClazz);
        }
        return result.overWriteInstClazz(instClazz);
    }

    public SuFormProperties(Class<?> parentClazz, Field subFormField
            , SubForm subFormFieldsAnnotation, Descriptor subFormFieldsDescriptor, Map<String, PropertyType> fieldsType) {
        Objects.requireNonNull(fieldsType, "fieldsType can not be null");
        this.parentClazz = parentClazz;
        this.fieldsType = fieldsType;
        this.subFormField = subFormField;
        this.subFormFieldsAnnotation = subFormFieldsAnnotation;
        if (subFormFieldsAnnotation != null) {
            this.instClazz = subFormFieldsAnnotation.desClazz();
        }
        if (MapUtils.isNotEmpty(fieldsType)) {
            Optional<Map.Entry<String, PropertyType>> idType = fieldsType.entrySet().stream().filter((ft) -> ft.getValue().isIdentity()).findFirst();
            if (!idType.isPresent()) {
                throw new IllegalArgumentException(subFormFieldsAnnotation.desClazz() + " has not define a identity prop");
            }
            this.pkPropertyType = idType.get().getValue();
        } else {
            this.pkPropertyType = null;
        }

        this.subFormFieldsDescriptor = subFormFieldsDescriptor;
    }

    public String getSubFormFieldName() {
        return this.subFormField.getName();
    }

    private String getIdListGetScript() {
        return this.subFormFieldsAnnotation.idListGetScript();
    }

    public <T> T newSubDetailed() {
        try {
            // Class<?> aClass = desClazz this.subFormFieldsAnnotation.desClazz();
            return (T) this.instClazz.newInstance();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public SuFormProperties overWriteInstClazz(Class instClazz) {
        this.instClazz = instClazz;
        return this;
    }

    private static final CustomerGroovyClassLoader loader = new CustomerGroovyClassLoader();

    private static final class CustomerGroovyClassLoader extends GroovyClassLoader {
        public CustomerGroovyClassLoader() {
            super(new ClassLoader(SuFormProperties.class.getClassLoader()) {
                      @Override
                      protected Class<?> findClass(String name) throws ClassNotFoundException {
                          // return super.findClass(name);
                          return TIS.get().getPluginManager().uberClassLoader.findClass(name);
                      }
                  }
            );
        }

        @SuppressWarnings("all")
        public void loadMyClass(String name, String script) throws Exception {
            CompilationUnit unit = new CompilationUnit();
            SourceUnit su = unit.addSource(name, script);
            ClassCollector collector = createCollector(unit, su);
            unit.setClassgenCallback(collector);
            unit.compile(Phases.CLASS_GENERATION);
            int classEntryCount = 0;
            for (Object o : collector.getLoadedClasses()) {
                setClassCacheEntry((Class<?>) o);
                // System.out.println(o);
                classEntryCount++;
            }
        }
    }


    public DescriptorsJSON.IPropGetter getSubFormIdListGetter() {
        try {
            if (subFormFieldsEnumGetter == null) {
                synchronized (this) {
                    if (subFormFieldsEnumGetter == null) {
                        String className = this.parentClazz.getSimpleName() + "_SubFormIdListGetter_" + subFormField.getName();
                        String pkg = this.parentClazz.getPackage().getName();
                        String script = "	package " + pkg + " ;"
                                + "import java.util.Map;"
                                + "import com.qlangtech.tis.coredefine.module.action.DataxAction; "
                                + "import com.qlangtech.tis.util.DescriptorsJSON.IPropGetter; "
                                + "import com.qlangtech.tis.extension.IPropertyType; "
                                + "class " + className + " implements IPropGetter {"
                                + "	@Override"
                                + "	public Object build(IPropertyType.SubFormFilter filter) {" + this.getIdListGetScript() + "	}" + "}";
                        //this.getIdListGetScript()
                        loader.loadMyClass(className, script);
                        Class<?> groovyClass = loader.loadClass(pkg + "." + className);
                        subFormFieldsEnumGetter = (DescriptorsJSON.IPropGetter) groovyClass.newInstance();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return subFormFieldsEnumGetter;
    }

    public <T> T visitAllSubDetailed(Map<String, /*** attr key */JSONObject> formData, ISubDetailedProcess<T> subDetailedProcess) {
        String subFormId = null;
        JSONObject subformData = null;
        Map<String, JSONObject> subform = null;
        for (Map.Entry<String, JSONObject> entry : formData.entrySet()) {
            subFormId = entry.getKey();
            subformData = entry.getValue();
            subform = Maps.newHashMap();
            for (String fieldName : subformData.keySet()) {
                subform.put(fieldName, subformData.getJSONObject(fieldName));
            }
            T result = subDetailedProcess.process(subFormId, subform);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public interface ISubDetailedProcess<T> {
        T process(String subFormId, Map<String, JSONObject> subform);
    }


    @Override
    public Set<Map.Entry<String, PropertyType>> getKVTuples() {
        return fieldsType.entrySet();
    }

    @Override
    public JSON getInstancePropsJson(Object instance) {
//        Class<?> fieldType = subFormField.getType();
//        if (!Collection.class.isAssignableFrom(fieldType)) {
//            // 现在表单只支持1对n 关系的子表单，因为1对1就没有必要有子表单了
//            throw new UnsupportedOperationException("sub form field:" + subFormField.getName()
//                    + " just support one2multi relationship,declarFieldClass:" + fieldType.getName());
//        }
//        getSubFormPropVal(instance);
//        try {
//            Object o = subFormField.get(instance);

        return createSubFormVals(getSubFormPropVal(instance));

//        } catch (IllegalAccessException e) {
//            throw new RuntimeException(e);
//        }
    }

    public Collection<IdentityName> getSubFormPropVal(Object instance) {
        Class<?> fieldType = subFormField.getType();
        if (!Collection.class.isAssignableFrom(fieldType)) {
            // 现在表单只支持1对n 关系的子表单，因为1对1就没有必要有子表单了
            throw new UnsupportedOperationException("sub form field:" + subFormField.getName()
                    + " just support one2multi relationship,declarFieldClass:" + fieldType.getName());
        }

        try {
            Object o = subFormField.get(instance);
            return (o == null) ? Collections.emptyList() : (Collection<IdentityName>) o;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public JSONObject createSubFormVals(Collection<IdentityName> subFormFieldInstance) {
        JSONObject vals = new JSONObject();

        if (subFormFieldInstance != null) {
            for (Object i : subFormFieldInstance) {
                vals.put(String.valueOf(pkPropertyType.getVal(i)), (new RootFormProperties(fieldsType)).getInstancePropsJson(i));
            }
        }

        return vals;
    }

    @Override
    public <T> T accept(IVisitor visitor) {
        return visitor.visit(this);
    }

//    public static class SuFormPropertiesBehaviorMeta {
//        String clickBtnLabel;
//        Map<String, SuFormPropertyGetterMeta> onClickFillData;
//
//        public String getClickBtnLabel() {
//            return clickBtnLabel;
//        }
//
//        public void setClickBtnLabel(String clickBtnLabel) {
//            this.clickBtnLabel = clickBtnLabel;
//        }
//
//        public Map<String, SuFormPropertyGetterMeta> getOnClickFillData() {
//            return onClickFillData;
//        }
//
//        public void setOnClickFillData(Map<String, SuFormPropertyGetterMeta> onClickFillData) {
//            this.onClickFillData = onClickFillData;
//        }
//    }

//    public static class SuFormPropertyGetterMeta {
//        String method;
//        List<String> params;
//
//        public String getMethod() {
//            return method;
//        }
//
//        public void setMethod(String method) {
//            this.method = method;
//        }
//
//        public List<String> getParams() {
//            return params;
//        }
//
//        public void setParams(List<String> params) {
//            this.params = params;
//        }
//    }

    /**
     * 子表单执行过程中与线程绑定的上下文对象
     */
    public static class SuFormGetterContext {
        public static final String FIELD_SUBFORM_ID = "id";
        public Describable plugin;
        public UploadPluginMeta param;

        private ConcurrentHashMap<String, Object> props = new ConcurrentHashMap<>();

        public <T> T getContextAttr(String key, Function<String, T> func) {
            return (T) props.computeIfAbsent(key, func);
        }

        public String getSubFormIdentityField() {
            String id = param.getExtraParam(IPropertyType.SubFormFilter.PLUGIN_META_SUBFORM_DETAIL_ID_VALUE);
            if (StringUtils.isEmpty(id)) {
                throw new IllegalStateException(IPropertyType.SubFormFilter.PLUGIN_META_SUBFORM_DETAIL_ID_VALUE + " can not be empty");
            }
            return id;
        }
    }
}
