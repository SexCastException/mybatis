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
package org.apache.ibatis.datasource.pooled;

import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 封装了真正的数据库连接对象 {@link Connection} 以及其代理对象.
 * 这里的代理对象是通过JDK动态代理产生的。PooledConnection 继承了 {@link InvocationHandler}
 *
 * @author Clinton Begin
 */
class PooledConnection implements InvocationHandler {

  private static final String CLOSE = "close";
  private static final Class<?>[] IFACES = new Class<?>[]{Connection.class};

  /**
   * 数据库连接对象的hashCode
   */
  private final int hashCode;
  /**
   * 记录当前PooledConnection对象所在的PooledDataSource对象。该PooledConnection是从该PooledDataSource中获取的;
   * 当调用close()方法时会将PooledConnection放回该 PooledDataSource 中
   */
  private final PooledDataSource dataSource;
  /**
   * 真正的数据库连接对象
   */
  private final Connection realConnection;
  /**
   * 数据库连接代理对象
   */
  private final Connection proxyConnection;
  /**
   * 从数据库连接池中取出该连接时的时间戳
   */
  private long checkoutTimestamp;
  /**
   * 该连接创建的时间戳
   */
  private long createdTimestamp;
  /**
   * 最后一次使用该连接的时间戳
   */
  private long lastUsedTimestamp;
  /**
   * 由数据库URL,用户名和密码计算出来的hash值,可用于标识该连接所在的连接池
   */
  private int connectionTypeCode;
  /**
   * 检测当前PooledConnection是否有效，主要是为了防止程序通过close() 方法将连接归还给连接池之后，依然通过该连接操作数据库
   */
  private boolean valid;

  /**
   * Constructor for SimplePooledConnection that uses the Connection and PooledDataSource passed in.
   *
   * @param connection - the connection that is to be presented as a pooled connection
   * @param dataSource - the dataSource that the connection is from
   */
  public PooledConnection(Connection connection, PooledDataSource dataSource) {
    this.hashCode = connection.hashCode();
    this.realConnection = connection;
    this.dataSource = dataSource;
    this.createdTimestamp = System.currentTimeMillis();
    this.lastUsedTimestamp = System.currentTimeMillis();
    this.valid = true;
    // 引用动态代理生成的代理对象
    this.proxyConnection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), IFACES, this);
  }

  /**
   * 使连接无效
   * Invalidates the connection.
   */
  public void invalidate() {
    valid = false;
  }

  /**
   * Method to see if the connection is usable.
   *
   * @return True if the connection is usable
   */
  public boolean isValid() {
    return valid && realConnection != null && dataSource.pingConnection(this);
  }

  /**
   * Getter for the *real* connection that this wraps.
   *
   * @return The connection
   */
  public Connection getRealConnection() {
    return realConnection;
  }

  /**
   * Getter for the proxy for the connection.
   *
   * @return The proxy
   */
  public Connection getProxyConnection() {
    return proxyConnection;
  }

  /**
   * Gets the hashcode of the real connection (or 0 if it is null).
   *
   * @return The hashcode of the real connection (or 0 if it is null)
   */
  public int getRealHashCode() {
    return realConnection == null ? 0 : realConnection.hashCode();
  }

  /**
   * Getter for the connection type (based on url + user + password).
   *
   * @return The connection type
   */
  public int getConnectionTypeCode() {
    return connectionTypeCode;
  }

  /**
   * Setter for the connection type.
   *
   * @param connectionTypeCode - the connection type
   */
  public void setConnectionTypeCode(int connectionTypeCode) {
    this.connectionTypeCode = connectionTypeCode;
  }

  /**
   * Getter for the time that the connection was created.
   *
   * @return The creation timestamp
   */
  public long getCreatedTimestamp() {
    return createdTimestamp;
  }

  /**
   * Setter for the time that the connection was created.
   *
   * @param createdTimestamp - the timestamp
   */
  public void setCreatedTimestamp(long createdTimestamp) {
    this.createdTimestamp = createdTimestamp;
  }

  /**
   * Getter for the time that the connection was last used.
   *
   * @return - the timestamp
   */
  public long getLastUsedTimestamp() {
    return lastUsedTimestamp;
  }

  /**
   * Setter for the time that the connection was last used.
   *
   * @param lastUsedTimestamp - the timestamp
   */
  public void setLastUsedTimestamp(long lastUsedTimestamp) {
    this.lastUsedTimestamp = lastUsedTimestamp;
  }

  /**
   * Getter for the time since this connection was last used.
   *
   * @return - the time since the last use
   */
  public long getTimeElapsedSinceLastUse() {
    return System.currentTimeMillis() - lastUsedTimestamp;
  }

  /**
   * Getter for the age of the connection.
   *
   * @return the age
   */
  public long getAge() {
    return System.currentTimeMillis() - createdTimestamp;
  }

  /**
   * Getter for the timestamp that this connection was checked out.
   *
   * @return the timestamp
   */
  public long getCheckoutTimestamp() {
    return checkoutTimestamp;
  }

  /**
   * Setter for the timestamp that this connection was checked out.
   *
   * @param timestamp the timestamp
   */
  public void setCheckoutTimestamp(long timestamp) {
    this.checkoutTimestamp = timestamp;
  }

  /**
   * Getter for the time that this connection has been checked out.
   *
   * @return the time
   */
  public long getCheckoutTime() {
    return System.currentTimeMillis() - checkoutTimestamp;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  /**
   * 允许将此连接与另一个连接进行比较
   * Allows comparing this connection to another.
   *
   * @param obj - the other connection to test for equality
   * @see Object#equals(Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof PooledConnection) {
      // 两个 PoolConnection 对象中的realConnection相等,则视为同一个 PoolConnection 对象
      return realConnection.hashCode() == ((PooledConnection) obj).realConnection.hashCode();
    } else if (obj instanceof Connection) {
      return hashCode == obj.hashCode();
    } else {
      return false;
    }
  }

  /**
   * Required for InvocationHandler implementation.
   *
   * @param proxy  - not used
   * @param method - the method to be executed
   * @param args   - the parameters to be passed to the method
   * @see java.lang.reflect.InvocationHandler#invoke(Object, java.lang.reflect.Method, Object[])
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    // 如果调用close()方法，则将其重新放入连接池，而不是使用真正连接对象关闭数据库连接
    if (CLOSE.hashCode() == methodName.hashCode() && CLOSE.equals(methodName)) {
      dataSource.pushConnection(this);
      // 结束方法,不调用真正结束方法
      return null;
    }
    try {
      // 如果不是Object的方法
      if (!Object.class.equals(method.getDeclaringClass())) {
        // issue #579 toString() should never fail
        // throw an SQLException instead of a Runtime
        // 通过valid字段检测连接是否有效,无效则结束方法
        checkConnection();
      }
      return method.invoke(realConnection, args);
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }

  }

  /**
   * 检查连接,如果不可用,则抛出 {@link SQLException} 异常
   *
   * @throws SQLException
   */
  private void checkConnection() throws SQLException {
    if (!valid) {
      throw new SQLException("Error accessing PooledConnection. Connection is invalid.");
    }
  }

}
