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
package org.apache.ibatis.datasource;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * 比较常见的第三方数据源组件有Apache Common DBCP、C3P0、Proxool等, MyBatis 不仅可以集成第三方数据源组件，还提供了自己的数据源实现。
 * <p>
 * 常见的数据源组件都实现了 {@link DataSource} 接口，MyBatis自身实现的数据源实现也不例外。
 * MyBatis 提供了两个 {@link DataSource} 接口实现，分别是 {@link PooledDataSource} 和 {@link UnpooledDataSource}。
 * <p>
 * Mybatis 使用不同的 {@link DataSourceFactory} 接口实现创建不同类型的 {@link DataSource}
 *
 * @author Clinton Begin
 */
public interface DataSourceFactory {

  /**
   * 设置{@link DataSource}的相关属性，-般紧跟在初始化完成之后
   *
   * @param props
   */
  void setProperties(Properties props);

  /**
   * 获取数据源
   *
   * @return
   */
  DataSource getDataSource();

}
