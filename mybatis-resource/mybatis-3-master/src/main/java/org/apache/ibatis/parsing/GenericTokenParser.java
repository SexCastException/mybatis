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
 * 顺序查找openToken和closeToken,解析得到占位符的字面值，并将其交给TokenHandler 处理，然后将解析结果重新拼装成字符串并返回。
 *
 * @author Clinton Begin
 */
public class GenericTokenParser {

  /**
   * 占位符开始标记
   */
  private final String openToken;
  /**
   * 占位符结束标记
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
    // 查找开始标记
    int start = text.indexOf(openToken);
    if (start == -1) {
      return text;
    }
    char[] src = text.toCharArray();
    int offset = 0;
    // 用于记录处理器handler解析和普通文本内容，并用于方法返回
    final StringBuilder builder = new StringBuilder();
    // 用于记录每一次占位符对之间的字符
    StringBuilder expression = null;
    while (start > -1) {
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        // 检测startToken前面是否转义标识（\），则直接将startToken前面的字符串以及开始标记追加到builder中
        builder.append(src, offset, start - offset - 1).append(openToken);
        // 设置偏移量，以便查找下一个openToken
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token.
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          // 清空上一次已经解析占位符字面值
          expression.setLength(0);
        }
        // 保存当前正在解析的占位符开始标记前面未保存的普通文本
        builder.append(src, offset, start - offset);
        offset = start + openToken.length();
        // 从offset后面查找占位符结束标记
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            // 检测endToken前面是否转义标识（\），则直接将前面的字符串以及结束标记追加到builder中
            expression.append(src, offset, end - offset - 1).append(closeToken);
            // 设置偏移量，以便查找下一个closeToken
            offset = end + closeToken.length();
            // 可能会有 ${xxxx\}}，其中xxx}也是需要解析的内容
            end = text.indexOf(closeToken, offset);
          } else {
            // 保存当前正在解析的开始标记和结束标记之间的内容
            expression.append(src, offset, end - offset);
            break;
          }
        }
        // 没有占位结束标记，表明当前解析的位置到文本最后位置只是带占位符开始标记的普通文本，不需要解析，普通文本直接保存
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
