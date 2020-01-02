/**
 * Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.datasource.unpooled;

import org.apache.ibatis.datasource.DataSourceException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Clinton Begin
 */
public class UnpooledDataSourceFactory implements DataSourceFactory {

  /**
   * 驱动前缀
   */
  private static final String DRIVER_PROPERTY_PREFIX = "driver.";
  /**
   * 驱动前缀长度
   */
  private static final int DRIVER_PROPERTY_PREFIX_LENGTH = DRIVER_PROPERTY_PREFIX.length();

  protected DataSource dataSource;

  public UnpooledDataSourceFactory() {
    this.dataSource = new UnpooledDataSource();
  }

  @Override
  public void setProperties(Properties properties) {
    // 记录数据源相关的配置信息
    Properties driverProperties = new Properties();
    // 创建 DataSource  相应的 MetaObject
    MetaObject metaDataSource = SystemMetaObject.forObject(dataSource);
    // 遍历properties集合，该集合中配置了数据源需要的信息
    for (Object key : properties.keySet()) {
      String propertyName = (String) key;
      // 以"driver."开头的配置项是对DataSource的配置
      if (propertyName.startsWith(DRIVER_PROPERTY_PREFIX)) {
        String value = properties.getProperty(propertyName);
        // 键为去掉 driver." 之后的字符
        driverProperties.setProperty(propertyName.substring(DRIVER_PROPERTY_PREFIX_LENGTH), value);
      } else if (metaDataSource.hasSetter(propertyName)) {  // 不是以 "driver."开头的配置,直接写DataSource的属性名配置也可以,比如DataSource的username
        String value = (String) properties.get(propertyName);
        // 根据属性类型进行类型转换，主要是Integer、Long、Boolean三种类型的转换
        Object convertedValue = convertValue(metaDataSource, propertyName, value);
        // 把配置数据源的值赋值到成员变量dataSource的属性中
        metaDataSource.setValue(propertyName, convertedValue);
      } else {  // 如果配置的Properties对象的key不是以"driver."开头或者属性为DataSource对象的字段名,则抛出异常
        throw new DataSourceException("Unknown DataSource property: " + propertyName);
      }
    }
    if (driverProperties.size() > 0) {
      metaDataSource.setValue("driverProperties", driverProperties);
    }
  }

  @Override
  public DataSource getDataSource() {
    return dataSource;
  }

  /**
   * 根据属性类型进行类型转换，主要是Integer、Long、Boolean三种类型的转换
   *
   * @param metaDataSource
   * @param propertyName
   * @param value
   * @return
   */
  private Object convertValue(MetaObject metaDataSource, String propertyName, String value) {
    Object convertedValue = value;
    Class<?> targetType = metaDataSource.getSetterType(propertyName);
    if (targetType == Integer.class || targetType == int.class) {
      convertedValue = Integer.valueOf(value);
    } else if (targetType == Long.class || targetType == long.class) {
      convertedValue = Long.valueOf(value);
    } else if (targetType == Boolean.class || targetType == boolean.class) {
      convertedValue = Boolean.valueOf(value);
    }
    return convertedValue;
  }

}
