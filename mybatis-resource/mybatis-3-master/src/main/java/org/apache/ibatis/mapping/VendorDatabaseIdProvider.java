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
package org.apache.ibatis.mapping;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * Vendor DatabaseId provider.
 * <p>
 * It returns database product name as a databaseId.
 * If the user provides a properties it uses it to translate database product name
 * key="Microsoft SQL Server", value="ms" will return "ms".
 * It can return null, if no database product name or
 * a properties was specified and no translation was found.
 *
 * @author Eduardo Macarron
 */
public class VendorDatabaseIdProvider implements DatabaseIdProvider {

  private Properties properties;

  /**
   * 接收到 {@link DataSource} 对象时，会先解析{@link DataSource}所连接的数据库产品名称，
   * 之后根据<databaseIdProvider>节点配置的数据库产品名称与databaseId的对应关系确定最终的databaseId.
   *
   * @param dataSource
   * @return
   */
  @Override
  public String getDatabaseId(DataSource dataSource) {
    if (dataSource == null) {
      throw new NullPointerException("dataSource cannot be null");
    }
    try {
      return getDatabaseName(dataSource);
    } catch (Exception e) {
      LogHolder.log.error("Could not get a databaseId from dataSource", e);
    }
    // 找不到匹配的databaseId，返回null
    return null;
  }

  @Override
  public void setProperties(Properties p) {
    this.properties = p;
  }

  /**
   * @param dataSource
   * @return
   * @throws SQLException
   */
  private String getDatabaseName(DataSource dataSource) throws SQLException {
    // 解析DataSource连接数据库产品的名称
    String productName = getDatabaseProductName(dataSource);
    if (this.properties != null) {
      // 根据<databaseIdProvider>子节点配置的数据库产品和databaseId之间对应关系，确定最终使用的databaseId
      for (Map.Entry<Object, Object> property : properties.entrySet()) {
        if (productName.contains((String) property.getKey())) {
          return (String) property.getValue();
        }
      }
      // no match, return null
      // 找不到合适的datasourceId，返回null
      return null;
    }
    return productName;
  }

  private String getDatabaseProductName(DataSource dataSource) throws SQLException {
    Connection con = null;
    try {
      // 获取数据库连接的Connection对象
      con = dataSource.getConnection();
      DatabaseMetaData metaData = con.getMetaData();
      // 获取数据库产品的名称
      return metaData.getDatabaseProductName();
    } finally {
      if (con != null) {
        try {
          con.close();
        } catch (SQLException e) {
          // ignored
        }
      }
    }
  }

  private static class LogHolder {
    private static final Log log = LogFactory.getLog(VendorDatabaseIdProvider.class);
  }

}
