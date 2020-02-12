/**
 * Copyright 2009-2018 the original author or authors.
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

import org.apache.ibatis.session.Configuration;

import java.util.*;

/**
 * @author Clinton Begin
 */
public class TrimSqlNode implements SqlNode {

  /**
   * &lt;trim>节点的子节点
   */
  private final SqlNode contents;
  /**
   * &lt;trim>节点的prefix属性值，前缀字符串
   */
  private final String prefix;
  /**
   * &lt;trim>节点的suffix属性值，后缀字符串
   */
  private final String suffix;
  /**
   * 如果&lt;trim>节点包裹的SQL语句是空语句(经常出现在if判断为否的情况下)，删除指定的前缀，如“where”
   */
  private final List<String> prefixesToOverride;
  /**
   * 如果&lt;trim>包裹的SQL语句是空语句(经常出现在if判断为否的情况下),删除指定的后缀，如“,”
   */
  private final List<String> suffixesToOverride;
  private final Configuration configuration;

  public TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, String prefixesToOverride, String suffix, String suffixesToOverride) {
    this(configuration, contents, prefix, parseOverrides(prefixesToOverride), suffix, parseOverrides(suffixesToOverride));
  }

  protected TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, List<String> prefixesToOverride, String suffix, List<String> suffixesToOverride) {
    this.contents = contents;
    this.prefix = prefix;
    this.prefixesToOverride = prefixesToOverride;
    this.suffix = suffix;
    this.suffixesToOverride = suffixesToOverride;
    this.configuration = configuration;
  }

  /**
   * 先解析子节点，然后根据子节点的解析结果处理前缀和后缀
   *
   * @param context
   * @return
   */
  @Override
  public boolean apply(DynamicContext context) {
    FilteredDynamicContext filteredDynamicContext = new FilteredDynamicContext(context);
    // 处理<trim>子节点
    boolean result = contents.apply(filteredDynamicContext);
    filteredDynamicContext.applyAll();
    return result;
  }

  private static List<String> parseOverrides(String overrides) {
    if (overrides != null) {
      // 多个使用“|”分隔
      final StringTokenizer parser = new StringTokenizer(overrides, "|", false);
      // 分隔后的数量初始化集合
      final List<String> list = new ArrayList<>(parser.countTokens());
      while (parser.hasMoreTokens()) {
        list.add(parser.nextToken().toUpperCase(Locale.ENGLISH));
      }
      return list;
    }
    return Collections.emptyList();
  }

  private class FilteredDynamicContext extends DynamicContext {
    /**
     * 被代理对象
     */
    private DynamicContext delegate;

    /**
     * 是否处理过前缀，默认false
     */
    private boolean prefixApplied;
    /**
     * 是否处理过后缀
     */
    private boolean suffixApplied;
    /**
     * 保存 子节点 解析后的结果，而不是调用FilteredDynamicContext.appendSql()方法
     */
    private StringBuilder sqlBuffer;

    public FilteredDynamicContext(DynamicContext delegate) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefixApplied = false;
      this.suffixApplied = false;
      this.sqlBuffer = new StringBuilder();
    }

    public void applyAll() {
      // 获取子节点解析后的结果，并转为大写
      sqlBuffer = new StringBuilder(sqlBuffer.toString().trim());
      String trimmedUppercaseSql = sqlBuffer.toString().toUpperCase(Locale.ENGLISH);
      if (trimmedUppercaseSql.length() > 0) {
        applyPrefix(sqlBuffer, trimmedUppercaseSql);  // 处理前缀
        applySuffix(sqlBuffer, trimmedUppercaseSql);  // 处理后缀
      }
      // 将处理后的后的子节点追加到代理对象的sqlBuilder当中
      delegate.appendSql(sqlBuffer.toString());
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
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

    /**
     * 注意此处调用的不是代理对象的append()方法，解析&lt;trim>子节点生成的sql语句暂存在sqlBuffer，待处理后再追加到
     * 代理对象delegate的sqlBuilder中去
     *
     * @param sql
     */
    @Override
    public void appendSql(String sql) {
      sqlBuffer.append(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    private void applyPrefix(StringBuilder sql, String trimmedUppercaseSql) {
      if (!prefixApplied) {
        // 标志已处理过前缀
        prefixApplied = true;
        if (prefixesToOverride != null) {
          for (String toRemove : prefixesToOverride) {
            // 每一项如果以toRemove开头，则移除toRemove
            if (trimmedUppercaseSql.startsWith(toRemove)) {
              sql.delete(0, toRemove.trim().length());
              break;
            }
          }
        }
        // 添加prefix前缀
        if (prefix != null) {
          sql.insert(0, " ");
          sql.insert(0, prefix);
        }
      }
    }

    private void applySuffix(StringBuilder sql, String trimmedUppercaseSql) {
      if (!suffixApplied) {
        // 标志以处理过后缀
        suffixApplied = true;
        if (suffixesToOverride != null) {
          for (String toRemove : suffixesToOverride) {
            // 每一项如果以toRemove结束，则移除toRemove
            if (trimmedUppercaseSql.endsWith(toRemove) || trimmedUppercaseSql.endsWith(toRemove.trim())) {
              int start = sql.length() - toRemove.trim().length();
              int end = sql.length();
              sql.delete(start, end);
              break;
            }
          }
        }
        // 添加suffix后缀
        if (suffix != null) {
          sql.append(" ");
          sql.append(suffix);
        }
      }
    }

  }

}
