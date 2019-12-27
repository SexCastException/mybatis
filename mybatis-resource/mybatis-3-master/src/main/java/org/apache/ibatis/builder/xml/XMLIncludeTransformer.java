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

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * XML映射文件<include>节点装换器
 * <p>
 * 在解析SQL节点之前，首先通过 XMLIncludeTransformer 解析SQL语句中的<include>节点，
 * 该过程会将<include>节点替换成<sql>节点中定义的SQL片段，并将其中的“${xx}”占位符替换成真实的参数。
 *
 * @author Frank D. Martinez [mnesarco]
 */
public class XMLIncludeTransformer {

  private final Configuration configuration;
  private final MapperBuilderAssistant builderAssistant;

  public XMLIncludeTransformer(Configuration configuration, MapperBuilderAssistant builderAssistant) {
    this.configuration = configuration;
    this.builderAssistant = builderAssistant;
  }

  public void applyIncludes(Node source) {
    Properties variablesContext = new Properties();
    // 获取mybatis-config.xml中<properties>节点下定义的变量集合
    Properties configurationVariables = configuration.getVariables();
    Optional.ofNullable(configurationVariables).ifPresent(variablesContext::putAll);
    applyIncludes(source, variablesContext, false);
  }

  /**
   * Recursively apply includes through all SQL fragments.
   *
   * @param source           Include node in DOM tree
   * @param variablesContext Current context for static variables with values
   */
  private void applyIncludes(Node source, final Properties variablesContext, boolean included) {
    if (source.getNodeName().equals("include")) { // 处理<include>节点
      // 查找refid属性指向的<sq1>节点，返回的是其深克隆的Node对象
      Node toInclude = findSqlFragment(getStringAttribute(source, "refid"), variablesContext);
      // 解析<include>节点下的<property>节点,将得到的键值对添加到variablesContext中,并形成新的Properties对象返回，用于替换占位符
      Properties toIncludeContext = getVariablesContext(source, variablesContext);
      // 递归处理<include>节点属性值refid指定的<sql>节点，在<sql>节 点中可能会使用<include>引用了其他SQL片段
      applyIncludes(toInclude, toIncludeContext, true);
      if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
        toInclude = source.getOwnerDocument().importNode(toInclude, true);
      }
      // 将<sql>节点替代<include>节点，将<sql>节点的文本内容插入到<sql>节点之前，最后删除<sql>节点，完美用<sql>节点的内容替换了<include>节点
      // 将<include>节点替换成<sql>节点
      source.getParentNode().replaceChild(toInclude, source);
      while (toInclude.hasChildNodes()) { // 将<sq1>节点的子节点（文本节点）添加到<sql>节点前面
        toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
      }
      // 从父节点中删除<sql>节点
      toInclude.getParentNode().removeChild(toInclude);
    } else if (source.getNodeType() == Node.ELEMENT_NODE) { // 处理除<include>元素节点，如<select>、<update>和<sql>等
      if (included && !variablesContext.isEmpty()) {
        // replace variables in attribute values
        // 解析替换元素节点属性值的占位符
        NamedNodeMap attributes = source.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
          Node attr = attributes.item(i);
          // 解析属性值中带有“${}”的占位符值
          attr.setNodeValue(PropertyParser.parse(attr.getNodeValue(), variablesContext));
        }
      }
      NodeList children = source.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        applyIncludes(children.item(i), variablesContext, included);
      }
    } else if (included && (source.getNodeType() == Node.TEXT_NODE || source.getNodeType() == Node.CDATA_SECTION_NODE)
      && !variablesContext.isEmpty()) { // 处理文本节点或<![CDATA[]]>节点
      // replace variables in text node
      // 解析并替换文本节点占位符
      source.setNodeValue(PropertyParser.parse(source.getNodeValue(), variablesContext));
    }
  }

  /**
   * @param refid     <include>节点refid属性值
   * @param variables
   * @return
   */
  private Node findSqlFragment(String refid, Properties variables) {
    // 解析并替换refid属性值中带“${}”的占位符
    refid = PropertyParser.parse(refid, variables);
    // 加上命名空间
    refid = builderAssistant.applyCurrentNamespace(refid, true);
    try {
      // 通过refid指向的<sql>节点的id获取封装<sql>节点的XNode对象
      XNode nodeToInclude = configuration.getSqlFragments().get(refid);
      // 返回<sql>节点的XNode克隆对象
      return nodeToInclude.getNode().cloneNode(true);
    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("Could not find SQL statement to include with refid '" + refid + "'", e);
    }
  }

  private String getStringAttribute(Node node, String name) {
    return node.getAttributes().getNamedItem(name).getNodeValue();
  }

  /**
   * Read placeholders and their values from include node definition.
   *
   * @param node                      Include node instance   <include>元素节点
   * @param inheritedVariablesContext Current context used for replace variables in new variables values
   * @return variables context from include instance (no inherited values)
   */
  private Properties getVariablesContext(Node node, Properties inheritedVariablesContext) {
    Map<String, String> declaredProperties = null;
    // 获取<include>节点的子节点<property>
    NodeList children = node.getChildNodes();
    // 遍历<property>节点
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (n.getNodeType() == Node.ELEMENT_NODE) {
        // 获取<property>节点的name属性值
        String name = getStringAttribute(n, "name");
        // Replace variables inside
        // 获取<property>节点的value属性值，并解析替换带有“${}”占位符的值，意味着value可以使用占位符，而name不可以
        String value = PropertyParser.parse(getStringAttribute(n, "value"), inheritedVariablesContext);
        if (declaredProperties == null) {
          declaredProperties = new HashMap<>();
        }
        if (declaredProperties.put(name, value) != null) {
          throw new BuilderException("Variable " + name + " defined twice in the same include definition");
        }
      }
    }
    if (declaredProperties == null) {
      return inheritedVariablesContext;
    } else {
      // 合并inheritedVariablesContext和declaredProperties的值并返回
      Properties newProperties = new Properties();
      newProperties.putAll(inheritedVariablesContext);
      newProperties.putAll(declaredProperties);
      return newProperties;
    }
  }
}
