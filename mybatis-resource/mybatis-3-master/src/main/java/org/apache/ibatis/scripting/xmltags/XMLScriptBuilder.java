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

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Clinton Begin
 */
public class XMLScriptBuilder extends BaseBuilder {

  private final XNode context;
  /**
   * 是否为动态SQL
   */
  private boolean isDynamic;
  private final Class<?> parameterType;
  private final Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();

  public XMLScriptBuilder(Configuration configuration, XNode context) {
    this(configuration, context, null);
  }

  public XMLScriptBuilder(Configuration configuration, XNode context, Class<?> parameterType) {
    super(configuration);
    this.context = context;
    this.parameterType = parameterType;
    initNodeHandlerMap();
  }


  private void initNodeHandlerMap() {
    nodeHandlerMap.put("trim", new TrimHandler());
    nodeHandlerMap.put("where", new WhereHandler());
    nodeHandlerMap.put("set", new SetHandler());
    nodeHandlerMap.put("foreach", new ForEachHandler());
    // <if> 和 <when> 节点都是用IfHandler对象
    nodeHandlerMap.put("if", new IfHandler());
    nodeHandlerMap.put("choose", new ChooseHandler());
    nodeHandlerMap.put("when", new IfHandler());
    nodeHandlerMap.put("otherwise", new OtherwiseHandler());
    nodeHandlerMap.put("bind", new BindHandler());
  }

  public SqlSource parseScriptNode() {
    // 判断当前的节点是不是有动态SQL,包含占位符或是动态SQL的相关节点的属于动态SQL
    MixedSqlNode rootSqlNode = parseDynamicTags(context);
    SqlSource sqlSource;
    if (isDynamic) {  // 根据是否是动态SQL,创建相应的SqlSource对象
      sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
    } else {
      sqlSource = new RawSqlSource(configuration, rootSqlNode, parameterType);
    }
    return sqlSource;
  }

  /**
   * 根据节点的内容，如果包含任何标签节点以及文本节点中含有“${}”占位符，则认为是动态SQL语句；
   * 然后根据是否为动态SQL以及节点的类型（元素节点和文本节点）创建不同{@link SqlNode}实现类
   *
   * @param node
   * @return
   */
  protected MixedSqlNode parseDynamicTags(XNode node) {
    // 记录生成的SqlNode集合
    List<SqlNode> contents = new ArrayList<>();
    // 获取 <selectKey>的所有子节点
    NodeList children = node.getNode().getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      // 创建封装Node对象的XNode，并解析“${}”占位符
      XNode child = node.newXNode(children.item(i));
      if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE || child.getNode().getNodeType() == Node.TEXT_NODE) {  // 对文本节点的处理
        // <selectKey>的文本节点
        String data = child.getStringBody("");
        TextSqlNode textSqlNode = new TextSqlNode(data);
        // 解析SQL语句，如果含有未解析的“${}”占位符，则视为动态SQL
        if (textSqlNode.isDynamic()) {  // 根据是否为动态SQL，创建相应的SqlNode对象
          contents.add(textSqlNode);
          isDynamic = true;
        } else {
          contents.add(new StaticTextSqlNode(data));
        }
      } else if (child.getNode().getNodeType() == Node.ELEMENT_NODE) { // issue #628 如果子节点是一个标签，一定是动态SQL,并且根据不同的动态标签生成不同的NodeHandler
        // 获取<selectKey>子节点名称（如：if、set和where等），根据子节点名称获取对应的NodeHandler对象
        String nodeName = child.getNode().getNodeName();
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        // 不存在节点名称对应的 NodeHandler 对象则抛出异常
        if (handler == null) {
          throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
        }
        // 递归处理动态SQL，并将解析得到的SqlNode对象放入contents集合中保存
        handler.handleNode(child, contents);
        isDynamic = true;
      }
    }
    return new MixedSqlNode(contents);
  }

  /**
   * 不同的实现类，解析{@link XNode}并创建对应的{@link SqlNode}并保存targetContents集合中
   */
  private interface NodeHandler {
    void handleNode(XNode nodeToHandle, List<SqlNode> targetContents);
  }

  private class BindHandler implements NodeHandler {
    public BindHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 获取<bind>节点的 name 属性值
      final String name = nodeToHandle.getStringAttribute("name");
      // 获取<bind>节点的 value 属性值
      final String expression = nodeToHandle.getStringAttribute("value");
      final VarDeclSqlNode node = new VarDeclSqlNode(name, expression);
      targetContents.add(node);
    }
  }

  private class TrimHandler implements NodeHandler {
    public TrimHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 递归解析<trim>动态SQL节点生成相应的SqlNode对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 获取<trim>节点的 prefix 属性值
      String prefix = nodeToHandle.getStringAttribute("prefix");
      // 获取<trim>节点的 prefixOverrides 属性值
      String prefixOverrides = nodeToHandle.getStringAttribute("prefixOverrides");
      // 获取<trim>节点的 suffix 属性值
      String suffix = nodeToHandle.getStringAttribute("suffix");
      // 获取<trim>节点的 suffixOverrides 属性值
      String suffixOverrides = nodeToHandle.getStringAttribute("suffixOverrides");
      TrimSqlNode trim = new TrimSqlNode(configuration, mixedSqlNode, prefix, prefixOverrides, suffix, suffixOverrides);
      targetContents.add(trim);
    }
  }

  private class WhereHandler implements NodeHandler {
    public WhereHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      WhereSqlNode where = new WhereSqlNode(configuration, mixedSqlNode);
      targetContents.add(where);
    }
  }

  private class SetHandler implements NodeHandler {
    public SetHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 递归解析<set>动态SQL节点生成相应的SqlNode对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      SetSqlNode set = new SetSqlNode(configuration, mixedSqlNode);
      targetContents.add(set);
    }
  }

  private class ForEachHandler implements NodeHandler {
    public ForEachHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 递归解析<foreach>动态SQL节点生成相应的SqlNode对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 获取<foreach>相应的属性值
      String collection = nodeToHandle.getStringAttribute("collection");
      String item = nodeToHandle.getStringAttribute("item");
      String index = nodeToHandle.getStringAttribute("index");
      String open = nodeToHandle.getStringAttribute("open");
      String close = nodeToHandle.getStringAttribute("close");
      String separator = nodeToHandle.getStringAttribute("separator");
      ForEachSqlNode forEachSqlNode = new ForEachSqlNode(configuration, mixedSqlNode, collection, index, item, open, close, separator);
      targetContents.add(forEachSqlNode);
    }
  }

  private class IfHandler implements NodeHandler {
    public IfHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 递归解析<if>动态SQL节点生成相应的SqlNode对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // <if>节点的test属性值
      String test = nodeToHandle.getStringAttribute("test");
      IfSqlNode ifSqlNode = new IfSqlNode(mixedSqlNode, test);
      targetContents.add(ifSqlNode);
    }
  }

  private class OtherwiseHandler implements NodeHandler {
    public OtherwiseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 递归解析<otherwise>动态SQL节点生成相应的SqlNode对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      targetContents.add(mixedSqlNode);
    }
  }

  private class ChooseHandler implements NodeHandler {
    public ChooseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 保存处理<where>后的结果
      List<SqlNode> whenSqlNodes = new ArrayList<>();
      // 保存处理<otherwise>后的结果
      List<SqlNode> otherwiseSqlNodes = new ArrayList<>();
      // 处理<where>和<otherwise>节点
      handleWhenOtherwiseNodes(nodeToHandle, whenSqlNodes, otherwiseSqlNodes);
      SqlNode defaultSqlNode = getDefaultSqlNode(otherwiseSqlNodes);
      ChooseSqlNode chooseSqlNode = new ChooseSqlNode(whenSqlNodes, defaultSqlNode);
      targetContents.add(chooseSqlNode);
    }

    /**
     * 处理<where>和<otherwise>节点
     *
     * @param chooseSqlNode
     * @param ifSqlNodes
     * @param defaultSqlNodes
     */
    private void handleWhenOtherwiseNodes(XNode chooseSqlNode, List<SqlNode> ifSqlNodes, List<SqlNode> defaultSqlNodes) {
      // 遍历<choose>节点的所有子节点（<when>和<otherwise>）
      List<XNode> children = chooseSqlNode.getChildren();
      for (XNode child : children) {
        // 根据子节点的名称获取相应的 NodeHandler 对象
        String nodeName = child.getNode().getNodeName();
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        // <if> 和 <when> 节点都是用IfHandler对象
        if (handler instanceof IfHandler) { // 如果是<when>节点
          handler.handleNode(child, ifSqlNodes);
        } else if (handler instanceof OtherwiseHandler) { // 如果是<otherwise>节点
          handler.handleNode(child, defaultSqlNodes);
        }
      }
    }

    /**
     * 如果<choose>节点有多个<otherwise>子节点，则抛出异常
     *
     * @param defaultSqlNodes
     * @return
     */
    private SqlNode getDefaultSqlNode(List<SqlNode> defaultSqlNodes) {
      SqlNode defaultSqlNode = null;
      if (defaultSqlNodes.size() == 1) {
        defaultSqlNode = defaultSqlNodes.get(0);
      } else if (defaultSqlNodes.size() > 1) {
        throw new BuilderException("Too many default (otherwise) elements in choose statement.");
      }
      return defaultSqlNode;
    }
  }

}
