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
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

import java.util.List;
import java.util.Locale;

/**
 * @author Clinton Begin
 */
public class XMLStatementBuilder extends BaseBuilder {

  private final MapperBuilderAssistant builderAssistant;
  /**
   * 封装SQL语句节点（&lt;select>、&lt;update>、&lt;delete>和&lt;insert>）的{@link XNode}对象
   */
  private final XNode context;
  private final String requiredDatabaseId;

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context) {
    this(configuration, builderAssistant, context, null);
  }

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context, String databaseId) {
    super(configuration);
    this.builderAssistant = builderAssistant;
    this.context = context;
    this.requiredDatabaseId = databaseId;
  }

  public void parseStatementNode() {
    // 获取SQL语句节点的id属性
    String id = context.getStringAttribute("id");
    // 获取Statement节点的databaseId属性
    String databaseId = context.getStringAttribute("databaseId");

    if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
      return;
    }

    // SQL语句节点的名称（select、update、delete和insert）
    String nodeName = context.getNode().getNodeName();
    // 根据SQL语句节点的类型确定执行SQL语句的类型
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
    // 是否为<select>节点
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;

    // 以下是SQL语句节点属性值为boolean类型的配置，其他属性值则在 XMLIncludeTransformer解析
    // 查询语句默认不刷新缓存
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
    // 如不配置useCache属性值，则默认为查询语句使用缓存
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);
    // 默认为false，该属性值主要用于嵌套映射
    boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);

    // Include Fragments before parsing
    // 在解析SQL语句之前，先处理其中的<include>节点，即将<include>节点替换为<sql>的文本节点
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    includeParser.applyIncludes(context.getNode());

    // 解析parameterType属性值指定的对象的类型
    String parameterType = context.getStringAttribute("parameterType");
    Class<?> parameterTypeClass = resolveClass(parameterType);

    // 获取lang属性值指定的语言驱动对象
    String lang = context.getStringAttribute("lang");
    LanguageDriver langDriver = getLanguageDriver(lang);

    // Parse selectKey after includes and remove them.
    // 处理<selectKey>节点，并将处理后KeyGenerator对象保存到Configuration对象的keyGenerators集合中
    processSelectKeyNodes(id, parameterTypeClass, langDriver);

    // Parse the SQL (pre: <selectKey> and <include> were parsed and removed)
    KeyGenerator keyGenerator;
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
    // 获取以上processSelectKeyNodes() 方法解析的KeyGenerator对象，配置了<selectKey>节点会使useGeneratedKeys属性值无效
    if (configuration.hasKeyGenerator(keyStatementId)) {
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    } else {
      // 如果配置了useGeneratedKeys属性为true，并且SQL类型为insert，则默认使用Jdbc3KeyGenerator
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
        configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
        ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
    }

    SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);
    // 默认：PREPARED
    StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    Integer fetchSize = context.getIntAttribute("fetchSize");
    Integer timeout = context.getIntAttribute("timeout");
    String parameterMap = context.getStringAttribute("parameterMap");
    String resultType = context.getStringAttribute("resultType");
    Class<?> resultTypeClass = resolveClass(resultType);
    String resultMap = context.getStringAttribute("resultMap");
    String resultSetType = context.getStringAttribute("resultSetType");
    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);
    if (resultSetTypeEnum == null) {
      resultSetTypeEnum = configuration.getDefaultResultSetType();
    }
    String keyProperty = context.getStringAttribute("keyProperty");
    String keyColumn = context.getStringAttribute("keyColumn");
    String resultSets = context.getStringAttribute("resultSets");

    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
      fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
      resultSetTypeEnum, flushCache, useCache, resultOrdered,
      keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
  }

  /**
   * 处理<selectKey>节点
   *
   * @param id                 SQL语句节点的id属性值
   * @param parameterTypeClass parameterType属性值指定的java对象类型
   * @param langDriver         lang属性值指定的{@link LanguageDriver}
   */
  private void processSelectKeyNodes(String id, Class<?> parameterTypeClass, LanguageDriver langDriver) {
    // 获取所有<selectKey>节点，只有<insert>和<update>节点才有<selectKey>节点
    List<XNode> selectKeyNodes = context.evalNodes("selectKey");
    if (configuration.getDatabaseId() != null) {
      // 解析<selectKey>节点
      parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, configuration.getDatabaseId());
    }
    parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, null);
    // 解析成功后移除<selectKey>节点
    removeSelectKeyNodes(selectKeyNodes);
  }

  /**
   * 为&lt;selectKey>节点生成id，检测 databaseId 是否匹配以及是否已经加载过相同id且 databaseId 不为空的&lt;selectKey>节点，
   * 并调用 parseSelectKeyNode()方法处理每个&lt;selectKey>节点。
   *
   * @param parentId             SQL语句节点的id属性值
   * @param list                 <selectKey>节点集合
   * @param parameterTypeClass   parameterType属性值指定的java对象类型
   * @param langDriver           lang属性值指定的{@link LanguageDriver}
   * @param skRequiredDatabaseId
   */
  private void parseSelectKeyNodes(String parentId, List<XNode> list, Class<?> parameterTypeClass, LanguageDriver langDriver, String skRequiredDatabaseId) {
    // 遍历<selectKey>节点
    for (XNode nodeToHandle : list) {
      // SQL语句节点id属性值+!selectKey，如：insert!selectKey
      String id = parentId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
      // 获取<selectKey>节点databaseId属性值
      String databaseId = nodeToHandle.getStringAttribute("databaseId");
      if (databaseIdMatchesCurrent(id, databaseId, skRequiredDatabaseId)) {
        parseSelectKeyNode(id, nodeToHandle, parameterTypeClass, langDriver, databaseId);
      }
    }
  }

  /**
   * 解析&lt;selectKey节点，为&lt;selectKey创建相应的 {@link MappedStatement}，并加入 {@link Configuration}相应属性中，
   * 之后也会创建 {@link KeyGenerator}对象加入{@link Configuration}中
   *
   * @param id
   * @param nodeToHandle
   * @param parameterTypeClass
   * @param langDriver
   * @param databaseId
   */
  private void parseSelectKeyNode(String id, XNode nodeToHandle, Class<?> parameterTypeClass, LanguageDriver langDriver, String databaseId) {
    // 获取<selectKey>节点的resultType属性值
    String resultType = nodeToHandle.getStringAttribute("resultType");
    Class<?> resultTypeClass = resolveClass(resultType);
    // 获取<selectKey>节点的statementType属性值，默认：PREPARED
    StatementType statementType = StatementType.valueOf(nodeToHandle.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    // 获取<selectKey>节点的keyProperty属性值
    String keyProperty = nodeToHandle.getStringAttribute("keyProperty");
    // 获取<selectKey>节点的keyColumn属性值
    String keyColumn = nodeToHandle.getStringAttribute("keyColumn");
    // 获取<selectKey>节点的order属性值，默认：AFTER
    boolean executeBefore = "BEFORE".equals(nodeToHandle.getStringAttribute("order", "AFTER"));

    //defaults
    // <selectKey>节点没有一下属性，所以初始化为默认值
    boolean useCache = false;
    boolean resultOrdered = false;
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    // 通过 LanguageDriver 生成 SqlSource
    SqlSource sqlSource = langDriver.createSqlSource(configuration, nodeToHandle, parameterTypeClass);
    // <selectKey>节点中只能配置select语句
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

     /*
      通过MapperBuilderAssistant创建 MappedStatement 对象，并添加到 Configuration.mappedStatements集合中保存，
      该集合为StrictMap<MappedStatement>类型
      处理<select>，<update>等节点会生成相应的MappedStatement对象，<selectKey>节点也会生成相应的MappedStatement对象
     */
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
      fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
      resultSetTypeEnum, flushCache, useCache, resultOrdered,
      keyGenerator, keyProperty, keyColumn, databaseId, langDriver, null);

    // id = id + namespace
    id = builderAssistant.applyCurrentNamespace(id, false);

    /*
      创建<selectKey>节点对应的KeyGenerator, 添加到Configuration.keyGenerators 集合中保存，
      Configuration.keyGenerators 字段是strictMap<KeyGenerator>类型的对象
     */
    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    configuration.addKeyGenerator(id, new SelectKeyGenerator(keyStatement, executeBefore));
  }

  /**
   * 移除<selectKey>节点
   *
   * @param selectKeyNodes
   */
  private void removeSelectKeyNodes(List<XNode> selectKeyNodes) {
    for (XNode nodeToHandle : selectKeyNodes) {
      // 从父节点中移除<selectKey>节点
      nodeToHandle.getParent().getNode().removeChild(nodeToHandle.getNode());
    }
  }

  /**
   * 当前配置的dataBaseId是否与数据库环境的dataBaseId是否相同
   *
   * @param id                 Statement节点的id
   * @param databaseId         Statement节点配置databaseId属性值
   * @param requiredDatabaseId 当前数据库环境的dataBaseId
   * @return
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    if (databaseId != null) {
      return false;
    }
    // id = id+namespace
    id = builderAssistant.applyCurrentNamespace(id, false);
    if (!this.configuration.hasStatement(id, false)) {
      return true;
    }
    // skip this statement if there is a previous one with a not null databaseId
    MappedStatement previous = this.configuration.getMappedStatement(id, false); // issue #2
    return previous.getDatabaseId() == null;
  }

  /**
   * 获取lang属性值指定的{@link LanguageDriver}对象，不配置则返回默认的
   *
   * @param lang
   * @return
   */
  private LanguageDriver getLanguageDriver(String lang) {
    Class<? extends LanguageDriver> langClass = null;
    if (lang != null) {
      langClass = resolveClass(lang);
    }
    return configuration.getLanguageDriver(langClass);
  }

}
