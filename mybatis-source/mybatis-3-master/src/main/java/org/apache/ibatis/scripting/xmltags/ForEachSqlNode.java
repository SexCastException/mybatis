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

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.session.Configuration;

import java.util.Map;

/**
 * 在动态SQL语句中构建IN条件语句的时候，通常需要对一一个集合进行迭代，MyBatis 提供了&lt;foreach>标签实现该功能。
 * 在使用&lt;foreach>标签迭代集合时，不仅可以使用集合的元素和索引值，还可以在循环开始之前或结束之后添加指定的字符串，
 * 也允许在迭代过程中添加指定的分隔符。
 *
 * @author Clinton Begin
 */
public class ForEachSqlNode implements SqlNode {
  /**
   * 用于拼接生成新的“#{}”占位符名称，以防和被解析节点外的占位符里的字符串重名
   */
  public static final String ITEM_PREFIX = "__frch_";

  /**
   * 用于判断循环的终止条件，构造方法中会创建该对象
   */
  private final ExpressionEvaluator evaluator;
  /**
   * 迭代集合表达式——collection指定的属性值
   */
  private final String collectionExpression;
  /**
   * 封装&lt;foreach>子节点的 {@link SqlNode}对象
   */
  private final SqlNode contents;
  /**
   * 循环开始前需要添加的字符串——collection指定的属性值
   */
  private final String open;
  /**
   * 循环结束后需要添加的字符串——open 指定的属性值
   */
  private final String close;
  /**
   * 循环过程中，每项的分隔符——close 指定的属性值
   */
  private final String separator;
  /**
   * 本次迭代的元素，如果迭代 {@link Map}，则item是value值——separator 指定的属性值
   */
  private final String item;
  /**
   * 当前迭代的索引值，如果迭代 {@link Map}，则index是key值——item 指定的属性值
   */
  private final String index;
  private final Configuration configuration;

  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
    // 保存解析之后的参数
    Map<String, Object> bindings = context.getBindings();
    // 解析集合表达式对应的实数
    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings);
    if (!iterable.iterator().hasNext()) {
      return true;
    }
    boolean first = true;
    // 循环开始前，追加open属性指定的表达式
    applyOpen(context);
    int i = 0;
    // 迭代实参
    for (Object o : iterable) {
      // 记录当前 DynamicContext 对象
      DynamicContext oldContext = context;
      // 没有指定分割符，并且第一次循环，则不添加任何前缀
      if (first || separator == null) {
        // 使用PrefixedContext代理对象覆盖原来DynamicContext对象
        context = new PrefixedContext(context, "");
      } else {  // 否则每次遍历添加 separator 属性值指定的分隔符
        // 使用PrefixedContext代理对象覆盖原来DynamicContext对象
        context = new PrefixedContext(context, separator);
      }
      // uniqueNumber从0开始，每次递增1,用于转换生成新的“#{}”占位符名称，以防和foreach外的占位符里的字符串重名
      int uniqueNumber = context.getUniqueNumber();
      // Issue #709
      // 如果实参是Map，则绑定处理过的key和value
      if (o instanceof Map.Entry) {
        @SuppressWarnings("unchecked")
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      } else {  // 否则绑定当前索引的下标和当前索引值
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }
      // 处理<foreach>子节点
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }
      // 还原原来的context
      context = oldContext;
      i++;
    }
    // 结束循环，添加close指定的字符串
    applyClose(context);
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }

  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
      context.bind(index, o);
      context.bind(itemizeItem(index, i), o);
    }
  }

  private void applyItem(DynamicContext context, Object o, int i) {
    if (item != null) {
      context.bind(item, o);
      context.bind(itemizeItem(item, i), o);
    }
  }

  /**
   * 添加前缀，即<foreach>open属性值
   *
   * @param context
   */
  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  /**
   * 添加前缀，即<foreach>close属性值
   *
   * @param context
   */
  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  private static String itemizeItem(String item, int i) {
    return ITEM_PREFIX + item + "_" + i;
  }

  /**
   * 负责处理“#{}”占位符，但它并未完全解析“#{}” 占位符
   */
  private static class FilteredDynamicContext extends DynamicContext {
    /**
     * 底层被代理的对象
     */
    private final DynamicContext delegate;
    /**
     * 集合的索引位置
     */
    private final int index;
    /**
     * 索引值
     */
    private final String itemIndex;
    /**
     * 索引项
     */
    private final String item;

    public FilteredDynamicContext(Configuration configuration, DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public void appendSql(String sql) {
      // 处理<foreache>子节点中带有“#{}”占位符的字符串
      GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
        String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
        if (itemIndex != null && newContent.equals(content)) {
          newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
        }
        return "#{" + newContent + "}";
      });

      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }


  private class PrefixedContext extends DynamicContext {
    /**
     * 底层被代理的对象，实际保存解析后的动态SQL对象
     */
    private final DynamicContext delegate;
    /**
     * 指定的前缀
     */
    private final String prefix;
    /**
     * 是否已经处理过前缀
     */
    private boolean prefixApplied;

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    /**
     * 追加的SQL语句，添加前缀 prefix 之后才将sql语句加入被代理对象中
     *
     * @param sql
     */
    @Override
    public void appendSql(String sql) {
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
        // 添加前缀
        delegate.appendSql(prefix);
        prefixApplied = true;
      }
      // 将SQL片段保存到底层被代理对象
      delegate.appendSql(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
