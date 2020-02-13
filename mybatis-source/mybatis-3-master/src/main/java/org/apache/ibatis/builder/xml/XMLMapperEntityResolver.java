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

import org.apache.ibatis.io.Resources;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Offline entity resolver for the MyBatis DTDs.
 * 离线解析MyBatis DTDs 文档
 * <p>
 * 如果解析 mybatis-config.xml 配置文件，默认联网加载 http://mybatis.org/dtd/mybatis-3-config.dtd 这个 DTD 文档，
 * 当网络比较慢时会导致验证过程缓慢。在实践 往往会提前设置 EntityResolver 接口对象加载本地的 DTD 文件，从而避免联网加载文件。
 * <p>
 * XMLMapperEntityResolver 是 MyBatis 提供的 EntityResolver 接口的实现 ，
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class XMLMapperEntityResolver implements EntityResolver {

  // 指定mybatis-config.xm文件和映射文件对应的DTD的SystemId
  private static final String IBATIS_CONFIG_SYSTEM = "ibatis-3-config.dtd";
  private static final String IBATIS_MAPPER_SYSTEM = "ibatis-3-mapper.dtd";
  private static final String MYBATIS_CONFIG_SYSTEM = "mybatis-3-config.dtd";
  private static final String MYBATIS_MAPPER_SYSTEM = "mybatis-3-mapper.dtd";

  /**
   * mybatis-config.xml 在本地DTD文件，该文件位于当前目录
   */
  private static final String MYBATIS_CONFIG_DTD = "org/apache/ibatis/builder/xml/mybatis-3-config.dtd";
  /**
   * mybatis-mapper.xml 在本地DTD文件，该文件位于当前目录
   */
  private static final String MYBATIS_MAPPER_DTD = "org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd";

  /**
   * Converts a public DTD into a local one.
   *
   * @param publicId The public id that is what comes after "PUBLIC" 对应XML文档声明的 "PUBLIC"——联网
   * @param systemId The system id that is what comes after the public id. 对应XML文档声明的 "SYSTEM"——本地
   * @return The InputSource for the DTD
   * @throws org.xml.sax.SAXException If anything goes wrong
   */
  @Override
  public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
    try {
      if (systemId != null) {
        String lowerCaseSystemId = systemId.toLowerCase(Locale.ENGLISH);
        // 读取本地DTD文件
        if (lowerCaseSystemId.contains(MYBATIS_CONFIG_SYSTEM) || lowerCaseSystemId.contains(IBATIS_CONFIG_SYSTEM)) {
          return getInputSource(MYBATIS_CONFIG_DTD, publicId, systemId);
        } else if (lowerCaseSystemId.contains(MYBATIS_MAPPER_SYSTEM) || lowerCaseSystemId.contains(IBATIS_MAPPER_SYSTEM)) {
          return getInputSource(MYBATIS_MAPPER_DTD, publicId, systemId);
        }
      }
      return null;
    } catch (Exception e) {
      throw new SAXException(e.toString());
    }
  }

  /**
   * 负责读取 DTD 文档并封装成 InputSource 对象
   *
   * @param path
   * @param publicId
   * @param systemId
   * @return
   */
  private InputSource getInputSource(String path, String publicId, String systemId) {
    InputSource source = null;
    if (path != null) {
      try {
        InputStream in = Resources.getResourceAsStream(path);
        source = new InputSource(in);
        source.setPublicId(publicId);
        source.setSystemId(systemId);
      } catch (IOException e) {
        // ignore, null is ok
      }
    }
    return source;
  }

}
