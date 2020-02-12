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
package org.apache.ibatis.builder;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.scripting.xmltags.DynamicContext;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 在经过 {@link SqlNode#apply(DynamicContext)}方法的解析之后，SQL语句会被传递到SqlSourceBuilder 中进行进一步的解析。
 * SqlSourceBuilder 主要完成了两方面的操作：
 * 一、解析SQL语句中的“#{}”占位符中定义的属性，格式类似于#{_frc.item_0, javaType=int, jdbcType=NUMERIC,typeHandler=MyTypeHandler}。
 * 二、将SQL语句中的“#{}”占位符替换成“?”占位符。
 *
 * @author Clinton Begin
 */
public class SqlSourceBuilder extends BaseBuilder {

  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  /**
   * SQL占位符的替换和 {@link ParameterMapping}集合的创建，并返回 {@link StaticSqlSource}对象。
   *
   * @param originalSql          {@link SqlNode} 处理之后的SQL语句
   * @param parameterType        用户传入的实参类型
   * @param additionalParameters 记录了形参和实参的对应关系，其实就是经过SqlNode.apply()方法处理后的DynamicContext.bindings集合
   * @return
   */
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
    // 创建ParameterMappingTokenHandler对象，它是解析“#{}”占位符中的参数属性以及替换占位符的核心
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    // 带有“#{}”标识为占位符
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);
    // 处理了“#{}”占位符之后的SQL语句
    String sql = parser.parse(originalSql);
    // 创建StaticSqlSource,其中封装了占位符被替换成"?"的SQL语句以及参数对应的ParameterMapping集合
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }

  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {

    /**
     * 记录解析得到的 {@link ParameterMapping}集合
     */
    private List<ParameterMapping> parameterMappings = new ArrayList<>();
    /**
     * 参数类型
     */
    private Class<?> parameterType;
    /**
     * {@link DynamicContext#bindings} 集合对应的 {@link MetaObject}对象
     */
    private MetaObject metaParameters;

    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    /**
     * 将占位符的内容保存到{@link ParameterMapping}，并将占位符替换为“?”
     *
     * @param content
     * @return
     */
    @Override
    public String handleToken(String content) {
      // 创建ParameterMapping对象并添加到parameterMappings集合中保存
      parameterMappings.add(buildParameterMapping(content));
      return "?";
    }

    /**
     * 根据占位符（如：#{_frc.item_0, javaType=int, jdbcType=NUMERIC, typeHandler=MyTypeHandler}）的内容，
     * 并将解析后的值分别设置到{@link ParameterMapping}对象属性值并返回该对象
     *
     * @param content
     * @return
     */
    private ParameterMapping buildParameterMapping(String content) {
      Map<String, String> propertiesMap = parseParameterMapping(content);
      // 获取key为 property的值，也为配置的javabean属性值
      String property = propertiesMap.get("property");
      Class<?> propertyType;
      // 判断是否存在property指定字符串的getter方法
      if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        propertyType = java.sql.ResultSet.class;
      } else if (property == null || Map.class.isAssignableFrom(parameterType)) {
        propertyType = Object.class;
      } else {
        MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
        if (metaClass.hasGetter(property)) {
          propertyType = metaClass.getGetterType(property);
        } else {
          propertyType = Object.class;
        }
      }
      // 创建ParameterMapping的构造器
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        // 获取参数名称
        String name = entry.getKey();
        // 获取参数值
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
        }
      }
      if (typeHandlerAlias != null) {
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }

}
