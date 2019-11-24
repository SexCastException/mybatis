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
package org.apache.ibatis.logging;

/**
 * 在MyBatis的日志模块中，使用了适配器模式。
 * MyBatis 内部调用其日志模块时，使用了其内部接口。但是Log4j、 Log4j2、Apache Commons Log、slf4和java.util.logging等第三方日志组件对外提供的接口各不相同，
 * MyBatis 为了集成和复用这些第三方日志组件，在其日志模块中提供了多种Adapter， 将这些第三方日志组件对外的接口适配成了{@link Log}接口。
 * 这样MyBatis内部就可以统一通过{@link Log}接口调用第三方日志组件的功能了。
 *
 * @author Clinton Begin
 */
public interface Log {

  boolean isDebugEnabled();

  boolean isTraceEnabled();

  void error(String s, Throwable e);

  void error(String s);

  void debug(String s);

  void trace(String s);

  void warn(String s);

}
