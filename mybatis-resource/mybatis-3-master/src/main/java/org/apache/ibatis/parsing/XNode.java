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
package org.apache.ibatis.parsing;

import org.w3c.dom.CharacterData;
import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * {@link Node}对象的封装和解析，提供了对节点元素，属性，文本的访问等
 *
 * @author Clinton Begin
 */
public class XNode {

  /**
   * 正在解析的xml节点对象
   */
  private final Node node;
  /**
   * node节点名称
   */
  private final String name;
  /**
   * 节点内容
   */
  private final String body;
  /**
   * 节点属性集合
   */
  private final Properties attributes;
  /**
   * mybatis-config.xml 配置文件中 <properties>节点下定义的键值对
   */
  private final Properties variables;
  /**
   * xpath解析器，当前XDode对象由此xpathParser生成
   */
  private final XPathParser xpathParser;

  public XNode(XPathParser xpathParser, Node node, Properties variables) {
    this.xpathParser = xpathParser;
    this.node = node;
    this.variables = variables;
    // 获取node的信息初始化name、attributes和body成员变量
    this.name = node.getNodeName();
    this.attributes = parseAttributes(node);
    this.body = parseBody(node);
  }

  public XNode newXNode(Node node) {
    return new XNode(xpathParser, node, variables);
  }

  /**
   * 获取父节点
   *
   * @return
   */
  public XNode getParent() {
    Node parent = node.getParentNode();
    if (!(parent instanceof Element)) {
      return null;
    } else {
      return new XNode(xpathParser, parent, variables);
    }
  }

  /**
   * 获取节点的xpath路径
   *
   * @return
   */
  public String getPath() {
    StringBuilder builder = new StringBuilder();
    // 当前节点
    Node current = node;
    while (current instanceof Element) {
      if (current != node) {
        builder.insert(0, "/");
      }
      builder.insert(0, current.getNodeName());
      // 不断向上回溯
      current = current.getParentNode();
    }
    return builder.toString();
  }

  public String getValueBasedIdentifier() {
    StringBuilder builder = new StringBuilder();
    //
    XNode current = this;
    while (current != null) {
      if (current != this) {
        builder.insert(0, "_");
      }
      // 先获取id属性值，获取不到则获取value属性值，获取不到获取property属性值，再获取不到返回null
      // 优先级：id > value > property
      String value = current.getStringAttribute("id",
        current.getStringAttribute("value",
          current.getStringAttribute("property", null)));
      if (value != null) {
        value = value.replace('.', '_');
        builder.insert(0, "]");
        builder.insert(0,
          value);
        builder.insert(0, "[");
      }
      builder.insert(0, current.getName());
      current = current.getParent();
    }
    return builder.toString();
  }

  /**
   * 通过xpath表达式获取当前节点上下文信息，{@link XNode#node}为当前节点上下文，下同
   * @param expression
   * @return
   */
  public String evalString(String expression) {
    return xpathParser.evalString(node, expression);
  }

  public Boolean evalBoolean(String expression) {
    return xpathParser.evalBoolean(node, expression);
  }

  public Double evalDouble(String expression) {
    return xpathParser.evalDouble(node, expression);
  }

  public List<XNode> evalNodes(String expression) {
    return xpathParser.evalNodes(node, expression);
  }

  public XNode evalNode(String expression) {
    return xpathParser.evalNode(node, expression);
  }

  public Node getNode() {
    return node;
  }

  public String getName() {
    return name;
  }

  public String getStringBody() {
    return getStringBody(null);
  }

  public String getStringBody(String def) {
    if (body == null) {
      return def;
    } else {
      return body;
    }
  }

  public Boolean getBooleanBody() {
    return getBooleanBody(null);
  }

  public Boolean getBooleanBody(Boolean def) {
    if (body == null) {
      return def;
    } else {
      return Boolean.valueOf(body);
    }
  }

  public Integer getIntBody() {
    return getIntBody(null);
  }

  public Integer getIntBody(Integer def) {
    if (body == null) {
      return def;
    } else {
      return Integer.parseInt(body);
    }
  }

  public Long getLongBody() {
    return getLongBody(null);
  }

  public Long getLongBody(Long def) {
    if (body == null) {
      return def;
    } else {
      return Long.parseLong(body);
    }
  }

  public Double getDoubleBody() {
    return getDoubleBody(null);
  }

  public Double getDoubleBody(Double def) {
    if (body == null) {
      return def;
    } else {
      return Double.parseDouble(body);
    }
  }

  public Float getFloatBody() {
    return getFloatBody(null);
  }

  public Float getFloatBody(Float def) {
    if (body == null) {
      return def;
    } else {
      return Float.parseFloat(body);
    }
  }

  public <T extends Enum<T>> T getEnumAttribute(Class<T> enumType, String name) {
    return getEnumAttribute(enumType, name, null);
  }

  public <T extends Enum<T>> T getEnumAttribute(Class<T> enumType, String name, T def) {
    String value = getStringAttribute(name);
    if (value == null) {
      return def;
    } else {
      return Enum.valueOf(enumType, value);
    }
  }

  /**
   * 通过属性名获取属性值，获取不到返回null
   *
   * @param name
   * @return
   */
  public String getStringAttribute(String name) {
    return getStringAttribute(name, null);
  }

  /**
   * 通过属性名获取属性值，获取不到返回默认值
   *
   * @param name
   * @param def
   * @return
   */
  public String getStringAttribute(String name, String def) {
    // 获取属性值
    String value = attributes.getProperty(name);
    if (value == null) {
      // 获取不到返回默认值
      return def;
    } else {
      return value;
    }
  }

  /**
   * 通过属性名获取Boolean类型属性值，获取不到返回null
   *
   * @param name
   * @return
   */
  public Boolean getBooleanAttribute(String name) {
    return getBooleanAttribute(name, null);
  }

  /**
   * 通过属性名获取Boolean类型属性值，获取不到返回默认值
   *
   * @param name
   * @param def
   * @return
   */
  public Boolean getBooleanAttribute(String name, Boolean def) {
    String value = attributes.getProperty(name);
    if (value == null) {
      return def;
    } else {
      return Boolean.valueOf(value);
    }
  }

  /**
   * 通过属性名获取Integer类型属性值，获取不到返回null
   *
   * @param name
   * @return
   */
  public Integer getIntAttribute(String name) {
    return getIntAttribute(name, null);
  }

  /**
   * 通过属性名获取Integer类型属性值，获取不到返回默认值
   *
   * @param name
   * @param def
   * @return
   */
  public Integer getIntAttribute(String name, Integer def) {
    String value = attributes.getProperty(name);
    if (value == null) {
      return def;
    } else {
      return Integer.parseInt(value);
    }
  }

  /**
   * 通过属性名获取Long类型属性值，获取不到返回null
   *
   * @param name
   * @return
   */
  public Long getLongAttribute(String name) {
    return getLongAttribute(name, null);
  }

  /**
   * 通过属性名获取Long类型属性值，获取不到返回默认值
   *
   * @param name
   * @param def
   * @return
   */
  public Long getLongAttribute(String name, Long def) {
    String value = attributes.getProperty(name);
    if (value == null) {
      return def;
    } else {
      return Long.parseLong(value);
    }
  }

  /**
   * 通过属性名获取Double类型属性值，获取不到返回null
   *
   * @param name
   * @return
   */
  public Double getDoubleAttribute(String name) {
    return getDoubleAttribute(name, null);
  }

  /**
   * 通过属性名获取Double类型属性值，获取不到返回默认值
   *
   * @param name
   * @param def
   * @return
   */
  public Double getDoubleAttribute(String name, Double def) {
    String value = attributes.getProperty(name);
    if (value == null) {
      return def;
    } else {
      return Double.parseDouble(value);
    }
  }

  /**
   * 通过属性名获取Float类型属性值，获取不到返回null
   *
   * @param name
   * @return
   */
  public Float getFloatAttribute(String name) {
    return getFloatAttribute(name, null);
  }

  /**
   * 通过属性名获取Float类型属性值，获取不到返回默认值
   *
   * @param name
   * @param def
   * @return
   */
  public Float getFloatAttribute(String name, Float def) {
    String value = attributes.getProperty(name);
    if (value == null) {
      return def;
    } else {
      return Float.parseFloat(value);
    }
  }

  /**
   * 获取当前节点的所有子节点
   *
   * @return
   */
  public List<XNode> getChildren() {
    List<XNode> children = new ArrayList<>();
    NodeList nodeList = node.getChildNodes();
    if (nodeList != null) {
      for (int i = 0, n = nodeList.getLength(); i < n; i++) {
        Node node = nodeList.item(i);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          children.add(new XNode(xpathParser, node, variables));
        }
      }
    }
    return children;
  }

  /**
   *
   * @return
   */
  public Properties getChildrenAsProperties() {
    Properties properties = new Properties();
    for (XNode child : getChildren()) {
      String name = child.getStringAttribute("name");
      String value = child.getStringAttribute("value");
      if (name != null && value != null) {
        properties.setProperty(name, value);
      }
    }
    return properties;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    toString(builder, 0);
    return builder.toString();
  }

  private void toString(StringBuilder builder, int level) {
    builder.append("<");
    builder.append(name);
    for (Map.Entry<Object, Object> entry : attributes.entrySet()) {
      builder.append(" ");
      builder.append(entry.getKey());
      builder.append("=\"");
      builder.append(entry.getValue());
      builder.append("\"");
    }
    List<XNode> children = getChildren();
    if (!children.isEmpty()) {
      builder.append(">\n");
      for (int k = 0; k < children.size(); k++) {
        indent(builder, level + 1);
        children.get(k).toString(builder, level + 1);
      }
      indent(builder, level);
      builder.append("</");
      builder.append(name);
      builder.append(">");
    } else if (body != null) {
      builder.append(">");
      builder.append(body);
      builder.append("</");
      builder.append(name);
      builder.append(">");
    } else {
      builder.append("/>");
      indent(builder, level);
    }
    builder.append("\n");
  }

  private void indent(StringBuilder builder, int level) {
    for (int i = 0; i < level; i++) {
      // 四个空格，一个tab的长度
      builder.append("    ");
    }
  }

  private Properties parseAttributes(Node n) {
    Properties attributes = new Properties();
    // 获取节点的属性集合
    NamedNodeMap attributeNodes = n.getAttributes();
    if (attributeNodes != null) {
      for (int i = 0; i < attributeNodes.getLength(); i++) {
        Node attribute = attributeNodes.item(i);
        // 使用 PropertyParser 解析属性中的占位符
        String value = PropertyParser.parse(attribute.getNodeValue(), variables);
        attributes.put(attribute.getNodeName(), value);
      }
    }
    return attributes;
  }

  private String parseBody(Node node) {
    String data = getBodyData(node);
    // 当前节点不是文本节点
    if (data == null) {
      // 处理子节点
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node child = children.item(i);
        data = getBodyData(child);
        if (data != null) {
          break;
        }
      }
    }
    return data;
  }

  private String getBodyData(Node child) {
    // ／只处理文本内容
    if (child.getNodeType() == Node.CDATA_SECTION_NODE
      || child.getNodeType() == Node.TEXT_NODE) {
      String data = ((CharacterData) child).getData();
      // 解析占位符
      data = PropertyParser.parse(data, variables);
      return data;
    }
    return null;
  }

}
