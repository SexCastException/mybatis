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
package org.apache.ibatis.logging.jdbc;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * ConnectionLogger继承了BaseJdbcLogger 抽象类，其中封装了{@link Connection}对象并同时实现了${@link InvocationHandler} 接口。
 * <p>
 * Connection proxy to add logging.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public final class ConnectionLogger extends BaseJdbcLogger implements InvocationHandler {

  private final Connection connection;

  /**
   * 构造函数私有化
   *
   * @param conn
   * @param statementLog
   * @param queryStack
   */
  private ConnectionLogger(Connection conn, Log statementLog, int queryStack) {
    super(statementLog, queryStack);
    this.connection = conn;
  }

  /**
   * 代理对象的核心方法,为 prepareStatement()、prepareCall()和createStatement()方法等方法提供了代理
   *
   * @param proxy
   * @param method
   * @param params
   * @return
   * @throws Throwable
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] params)
    throws Throwable {
    try {
      // 如果调用的是从父类继承的方法,则直接调用,不做任何其他处理
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, params);
      }
      // 如果调用的是prepareStatement 方法
      if ("prepareStatement".equals(method.getName())) {
        if (isDebugEnabled()) {
          debug(" Preparing: " + removeBreakingWhitespace((String) params[0]), true);
        }
        // 调用Connection对象底层封装的的prepareStatement()方法，得到PreparedStatement对象
        PreparedStatement stmt = (PreparedStatement) method.invoke(connection, params);
        // 获取PreparedStatement动态代理对象
        stmt = PreparedStatementLogger.newInstance(stmt, statementLog, queryStack);
        return stmt;
      } else if ("prepareCall".equals(method.getName())) {// 如果调用的是prepareCall方法
        if (isDebugEnabled()) {
          debug(" Preparing: " + removeBreakingWhitespace((String) params[0]), true);
        }
        // 调用Connection对象底层封装的的prepareCall()方法，得到PreparedStatement对象
        PreparedStatement stmt = (PreparedStatement) method.invoke(connection, params);
        // 获取PreparedStatement动态代理对象
        stmt = PreparedStatementLogger.newInstance(stmt, statementLog, queryStack);
        return stmt;
      } else if ("createStatement".equals(method.getName())) {  // 如果调用的是createStatement()方法
        // 调用Connection对象底层封装的的createStatement()方法，得到Statement对象
        Statement stmt = (Statement) method.invoke(connection, params);
        // 获取Statement动态代理对象
        stmt = StatementLogger.newInstance(stmt, statementLog, queryStack);
        return stmt;
      } else {
        // 其他方法则直接调用
        return method.invoke(connection, params);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /**
   * 创建{@link Connection}的动态代理对象
   * Creates a logging version of a connection.
   *
   * @param conn - the original connection
   * @return - the connection with logging
   */
  public static Connection newInstance(Connection conn, Log statementLog, int queryStack) {
    InvocationHandler handler = new ConnectionLogger(conn, statementLog, queryStack);
    ClassLoader cl = Connection.class.getClassLoader();
    // 返回动态代理对象
    return (Connection) Proxy.newProxyInstance(cl, new Class[]{Connection.class}, handler);
  }

  /**
   * return the wrapped connection.
   *
   * @return the connection
   */
  public Connection getConnection() {
    return connection;
  }

}
