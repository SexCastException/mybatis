package com.huazai.test.plugin;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 拦截 {@link Executor#query(MappedStatement, Object, RowBounds, ResultHandler)}和 {@link Executor#close(boolean)}方法
 *
 * @author pyh
 * @date 2020/2/9 18:09
 */
@Intercepts({
  @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
  @Signature(type = Executor.class, method = "close", args = {boolean.class})
})
public class ExamplePlugin implements Interceptor {
  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    System.out.println("自定义插件拦截Executor的query(MappedStatement, Object, RowBounds, ResultHandler)方法");
    return invocation.proceed();
  }
}
