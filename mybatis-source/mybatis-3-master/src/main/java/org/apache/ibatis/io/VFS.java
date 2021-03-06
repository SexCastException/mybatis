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
package org.apache.ibatis.io;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * VFS表示虚拟文件系统(Virtual File System),它用来查找指定路径下的资源。
 * Provides a very simple API for accessing resources within an application server.
 *
 * @author Ben Gunter
 */
public abstract class VFS {
  private static final Log log = LogFactory.getLog(VFS.class);

  /**
   * 记录了Mybatis提供的两个VFS实现类
   * The built-in implementations.
   */
  public static final Class<?>[] IMPLEMENTATIONS = {JBoss6VFS.class, DefaultVFS.class};

  /**
   * 记录了用户自定义的VFS实现类。VFS.addImplClass()方法会将指定的VFS实现对应的Class对象添加到 USER_IMPLEMENTATIONS 集合中
   * <p>
   * mybatis该项自定义VFS的配置方式是在mybatis-config.xml配置文件中的<settings>的子标签<setting>的设置属性name为vfsImpl，
   * value为VFS的实现类的全限定名（多个值用“,”隔开），并用{@link Resources}加载值生成的Class对象通过addImplClass()方法添加到
   * USER_IMPLEMENTATIONS集合中，此实现具体在{@link XMLConfigBuilder}中实现
   * <p>
   * The list to which implementations are added by {@link #addImplClass(Class)}.
   */
  public static final List<Class<? extends VFS>> USER_IMPLEMENTATIONS = new ArrayList<>();

  /**
   * Singleton instance holder.
   */
  private static class VFSHolder {
    // 单例模式，记录了全局唯一的VFS对象
    static final VFS INSTANCE = createVFS();

    @SuppressWarnings("unchecked")
    static VFS createVFS() {
      // Try the user implementations first, then the built-ins
      // 优先使用用户自定义的VFS实现，如果没有自定义VFS实现，则使用MyBatis提供的VFS实现,List有序的
      List<Class<? extends VFS>> impls = new ArrayList<>();
      impls.addAll(USER_IMPLEMENTATIONS);
      impls.addAll(Arrays.asList((Class<? extends VFS>[]) IMPLEMENTATIONS));

      // Try each implementation class until a valid one is found
      // 遍历impls集合，依次实例化VFS对象并检测VFS对象是否有效，一旦得到有效的VFS对象，则结束循环
      VFS vfs = null;
      for (int i = 0; vfs == null || !vfs.isValid(); i++) {
        Class<? extends VFS> impl = impls.get(i);
        try {
          // 通过构造器实例化
          vfs = impl.getDeclaredConstructor().newInstance();
          // 无效,则打印日志
          if (!vfs.isValid()) {
            if (log.isDebugEnabled()) {
              log.debug("VFS implementation " + impl.getName() +
                " is not valid in this environment.");
            }
          }
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
          log.error("Failed to instantiate " + impl, e);
          return null;
        }
      }

      if (log.isDebugEnabled()) {
        log.debug("Using VFS adapter " + vfs.getClass().getName());
      }

      return vfs;
    }
  }

  /**
   * Get the singleton {@link VFS} instance. If no {@link VFS} implementation can be found for the
   * current environment, then this method returns null.
   */
  public static VFS getInstance() {
    return VFSHolder.INSTANCE;
  }

  /**
   * Adds the specified class to the list of {@link VFS} implementations. Classes added in this
   * manner are tried in the order they are added and before any of the built-in implementations.
   *
   * @param clazz The {@link VFS} implementation class to add.
   */
  public static void addImplClass(Class<? extends VFS> clazz) {
    if (clazz != null) {
      USER_IMPLEMENTATIONS.add(clazz);
    }
  }

  /**
   * 加载类,如果找不到该类,则返回null
   * Get a class by name. If the class is not found then return null.
   */
  protected static Class<?> getClass(String className) {
    try {
      return Thread.currentThread().getContextClassLoader().loadClass(className);
//      return ReflectUtil.findClass(className);
    } catch (ClassNotFoundException e) {
      if (log.isDebugEnabled()) {
        log.debug("Class not found: " + className);
      }
      return null;
    }
  }

  /**
   * 返回指定类的方法的{@link Method}对象，找不到则返回null，不会抛出异常
   * <p>
   * Get a method by name and parameter types. If the method is not found then return null.
   *
   * @param clazz          The class to which the method belongs.
   * @param methodName     The name of the method.
   * @param parameterTypes The types of the parameters accepted by the method.
   */
  protected static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
    if (clazz == null) {
      return null;
    }
    try {
      return clazz.getMethod(methodName, parameterTypes);
    } catch (SecurityException e) {
      log.error("Security exception looking for method " + clazz.getName() + "." + methodName + ".  Cause: " + e);
      return null;
    } catch (NoSuchMethodException e) {
      log.error("Method not found " + clazz.getName() + "." + methodName + "." + methodName + ".  Cause: " + e);
      return null;
    }
  }

  /**
   * Invoke a method on an object and return whatever it returns.
   *
   * @param method     The method to invoke.
   * @param object     The instance or class (for static methods) on which to invoke the method.
   * @param parameters The parameters to pass to the method.
   * @return Whatever the method returns.
   * @throws IOException      If I/O errors occur
   * @throws RuntimeException If anything else goes wrong
   */
  @SuppressWarnings("unchecked")
  protected static <T> T invoke(Method method, Object object, Object... parameters)
    throws IOException, RuntimeException {
    try {
      return (T) method.invoke(object, parameters);
    } catch (IllegalArgumentException | IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof IOException) {
        throw (IOException) e.getTargetException();
      } else {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Get a list of {@link URL}s from the context classloader for all the resources found at the
   * specified path.
   *
   * @param path The resource path.
   * @return A list of {@link URL}s, as returned by {@link ClassLoader#getResources(String)}.
   * @throws IOException If I/O errors occur
   */
  protected static List<URL> getResources(String path) throws IOException {
    return Collections.list(Thread.currentThread().getContextClassLoader().getResources(path));
  }

  /**
   * 检测当前 VFS 对象在当前环境下是否有效
   * Return true if the {@link VFS} implementation is valid for the current environment.
   */
  public abstract boolean isValid();

  /**
   * 查找指定的资源名称列表
   * <p>
   * Recursively list the full resource path of all the resources that are children of the
   * resource identified by a URL.
   *
   * @param url     The URL that identifies the resource to list.                                 该资源路径对应的URL对象
   * @param forPath The path to the resource that is identified by the URL. Generally, this is the
   *                value passed to {@link #getResources(String)} to get the resource URL.        资源路径
   * @return A list containing the names of the child resources.
   * @throws IOException If I/O errors occur
   */
  protected abstract List<String> list(URL url, String forPath) throws IOException;

  /**
   * Recursively list the full resource path of all the resources that are children of all the
   * resources found at the specified path.
   *
   * @param path The path of the resource(s) to list.
   * @return A list containing the names of the child resources.
   * @throws IOException If I/O errors occur
   */
  public List<String> list(String path) throws IOException {
    List<String> names = new ArrayList<>();
    for (URL url : getResources(path)) {
      names.addAll(list(url, path));
    }
    return names;
  }
}
