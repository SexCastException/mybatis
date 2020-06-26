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
package org.apache.ibatis.plugin;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.executor.statement.StatementHandler;

import java.lang.reflect.InvocationHandler;
import java.util.Properties;

/**
 * MyBatis允许用户使用自定义拦截器对SQL语句执行过程中的某一点进行拦截，可以改变mybatis的一些默认行为。
 * 默认情况下，MyBatis允许拦截器拦截{@link Executor} 的方法、{@link ParameterHandler} 的方法、{@link ResultSetHandler} 的
 * 方法以及{@link StatementHandler}的方法。具体可拦截的方法如下:
 * <p>
 * {@link Executor}：update()、query()、flushStatements()、commit()、rollback()、getTransaction()、close()和isClosed()<br>
 * {@link ParameterHandler}：getParameterObject()和setParameters()<br>
 * {@link ResultSetHandler}：handleResultSets()和handleOutputParameters()<br>
 * {@link StatementHandler}：prepare()、parameterize()、batch()、update()和query()<br>
 *
 * @author Clinton Begin
 */
public interface Interceptor {

  /**
   * 执行拦截逻辑的方法，即 {@link InvocationHandler#invoke}核心调用代码
   *
   * @param invocation
   * @return
   * @throws Throwable
   */
  Object intercept(Invocation invocation) throws Throwable;

  /**
   * 创建代理对象
   *
   * @param target
   * @return
   */
  default Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  /**
   * 根据配置初始化Interceptor对象
   *
   * @param properties
   */
  default void setProperties(Properties properties) {
    // NOP
  }

}
