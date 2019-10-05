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

/**
 * @author Clinton Begin
 */
public class GenericTokenParser {

  /**
   * 解析开始标签
   */
  private final String openToken;
  /**
   * 解析结束标签
   */
  private final String closeToken;
  /**
   * 处理器
   */
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    // search open token
    // openToken索引
    int start = text.indexOf(openToken);
    if (start == -1) {
      return text;
    }
    char[] src = text.toCharArray();
    int offset = 0;
    // 用于保存处理器handler解析和普通文本内容，并用于方法返回
    final StringBuilder builder = new StringBuilder();
    // 用于保存每一次openToken和closeToken之间内容
    StringBuilder expression = null;
    while (start > -1) {
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        // 如果openToken被转义了，则删除反斜杠，即把openToken视为普通文本，不需要解析，直接保存
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token.
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          // 清空内容
          expression.setLength(0);
        }
        // 保存普通文本
        builder.append(src, offset, start - offset);
        offset = start + openToken.length();
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            // 如果closeToken被转义了，则删除反斜杠，即把closeToken视为普通文本，不需要解析，直接保存
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          } else {
            // 保存当前偏移在openToken和closeToken之间的内容
            expression.append(src, offset, end - offset);
            break;
          }
        }
        // 没有结束标签，表明当前解析的位置到文本最后位置只是带开始标签的普通文本，不需要解析，普通文本直接保存
        if (end == -1) {
          // close token was not found.
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {  // 解析当前openToken和closeToken之间的内容并保存结果
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }
      // 下一对openToken和closeToken解析
      start = text.indexOf(openToken, offset);
    }
    // 保存已经没有openToken和closeToken之后的普通文本
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }

}
