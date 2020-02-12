/**
 * Copyright 2009-2019 the original author or authors.
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
 */
package org.apache.ibatis.scripting.xmltags;

import ognl.OgnlContext;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * DynamicContext主要用于记录解析动态SQL语句之后产生的SQL语句片段，可以认为它是一个用于记录动态SQL语句解析结果的容器。
 *
 * @author Clinton Begin
 */
public class DynamicContext {

  /**
   * 映射参数的key值
   */
  public static final String PARAMETER_OBJECT_KEY = "_parameter";
  /**
   * 映射 databaseId的key值
   */
  public static final String DATABASE_ID_KEY = "_databaseId";

  static {
    OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
  }

  /**
   * {@link Map}类型对象，用于保存用户实参和动态节点某些属性值与其所代表含义的映射关系
   */
  private final ContextMap bindings;
  /**
   * 在 {@link SqlNode}解析动态SQL时，会将解析后的SQL语句片段添加到该属性中保存，最终拼凑出一条完成的SQL语句,
   * “ ”是拼接SQL语句后的默认的分隔符
   */
  private final StringJoiner sqlBuilder = new StringJoiner(" ");
  /**
   * 用于拼接生成新的“#{}”占位符名称，以防和被解析节点外的占位符里的字符串重名
   */
  private int uniqueNumber = 0;

  /**
   * 实例化 {@link DynamicContext#bindings}集合
   *
   * @param configuration
   * @param parameterObject 运行时用户传入的实参，包含了后续用于替换“#{}”占位符的参数
   */
  public DynamicContext(Configuration configuration, Object parameterObject) {
    // 如果实参类型不是Map
    if (parameterObject != null && !(parameterObject instanceof Map)) {
      MetaObject metaObject = configuration.newMetaObject(parameterObject);
      // 是否存在parameterObject的类型处理器
      boolean existsTypeHandler = configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass());
      bindings = new ContextMap(metaObject, existsTypeHandler);
    } else {
      bindings = new ContextMap(null, false);
    }
    bindings.put(PARAMETER_OBJECT_KEY, parameterObject);
    bindings.put(DATABASE_ID_KEY, configuration.getDatabaseId());
  }

  public Map<String, Object> getBindings() {
    return bindings;
  }

  public void bind(String name, Object value) {
    bindings.put(name, value);
  }

  /**
   * 追加sal片段
   *
   * @param sql
   */
  public void appendSql(String sql) {
    sqlBuilder.add(sql);
  }

  /**
   * 获取解析后完整的sql语句
   *
   * @return
   */
  public String getSql() {
    return sqlBuilder.toString().trim();
  }

  public int getUniqueNumber() {
    return uniqueNumber++;
  }

  static class ContextMap extends HashMap<String, Object> {
    private static final long serialVersionUID = 2977601501966151582L;
    /**
     * 将用户传入的参数封装成 {@link MetaObject}
     */
    private final MetaObject parameterMetaObject;
    /**
     * 标志 {@link ContextMap#parameterMetaObject}封装的原始类型是否存在相应的类型处理器
     */
    private final boolean fallbackParameterObject;

    public ContextMap(MetaObject parameterMetaObject, boolean fallbackParameterObject) {
      this.parameterMetaObject = parameterMetaObject;
      this.fallbackParameterObject = fallbackParameterObject;
    }

    @Override
    public Object get(Object key) {
      String strKey = (String) key;
      if (super.containsKey(strKey)) {  // 如果已存在该key，则直接返回对应的结果
        return super.get(strKey);
      }

      if (parameterMetaObject == null) {
        return null;
      }

      // 不存在key映射的值，且parameterMetaObject没有key指定字符串相应的类型处理器，直接返回实参对象
      if (fallbackParameterObject && !parameterMetaObject.hasGetter(strKey)) {
        return parameterMetaObject.getOriginalObject();
      } else {  // 不存在key映射的值，且parameterMetaObject存在key指定字符串相应的类型处理器，则返回key字符串指定属性的属性值
        // issue #61 do not modify the context when reading
        return parameterMetaObject.getValue(strKey);
      }
    }
  }

  static class ContextAccessor implements PropertyAccessor {

    @Override
    public Object getProperty(Map context, Object target, Object name) {
      Map map = (Map) target;

      Object result = map.get(name);
      if (map.containsKey(name) || result != null) {
        return result;
      }

      Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
      if (parameterObject instanceof Map) {
        return ((Map) parameterObject).get(name);
      }

      return null;
    }

    @Override
    public void setProperty(Map context, Object target, Object name, Object value) {
      Map<Object, Object> map = (Map<Object, Object>) target;
      map.put(name, value);
    }

    @Override
    public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }

    @Override
    public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }
  }
}
