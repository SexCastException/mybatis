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

import org.apache.ibatis.builder.InitializingObject;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;
import org.apache.ibatis.cache.decorators.*;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * {@link Cache}的建造者
 *
 * @author Clinton Begin
 */
public class CacheBuilder {
  /**
   * {@link Cache}对象的唯一标识，一般情况下对应映射文件中的配置namespace
   */
  private final String id;
  /**
   * Cache接口的真正实现类，即底层被装饰的缓存对象，默认是 PerpetualCache.class
   */
  private Class<? extends Cache> implementation;
  /**
   * 缓存装饰器集合，即装饰 implementation 实例化方法的装饰器，默认只包含 LruCache.class
   */
  private final List<Class<? extends Cache>> decorators;
  /**
   * {@link Cache}的大小
   */
  private Integer size;
  /**
   * 清空缓存时间周期
   */
  private Long clearInterval;
  /**
   * 是否可读写
   */
  private boolean readWrite;
  /**
   * 其他配置信息
   */
  private Properties properties;
  /**
   * 是否阻塞
   */
  private boolean blocking;

  public CacheBuilder(String id) {
    this.id = id;
    this.decorators = new ArrayList<>();
  }

  public CacheBuilder implementation(Class<? extends Cache> implementation) {
    this.implementation = implementation;
    return this;
  }

  public CacheBuilder addDecorator(Class<? extends Cache> decorator) {
    if (decorator != null) {
      this.decorators.add(decorator);
    }
    return this;
  }

  public CacheBuilder size(Integer size) {
    this.size = size;
    return this;
  }

  public CacheBuilder clearInterval(Long clearInterval) {
    this.clearInterval = clearInterval;
    return this;
  }

  public CacheBuilder readWrite(boolean readWrite) {
    this.readWrite = readWrite;
    return this;
  }

  public CacheBuilder blocking(boolean blocking) {
    this.blocking = blocking;
    return this;
  }

  public CacheBuilder properties(Properties properties) {
    this.properties = properties;
    return this;
  }

  public Cache build() {
    // 如果 implementation 字段和 decorators 集合为空，则为其设置默认值
    setDefaultImplementations();
    // 根据 implementation 指定的类型，通过反射获取参数为 String 类型的构造方法，并通过该构造方法创建 Cache 对象
    Cache cache = newBaseCacheInstance(implementation, id);
    // 根据<cache>节点下配置的<property>信息，初始化Cache对象
    setCacheProperties(cache);
    // issue #352, do not apply decorators to custom caches
    // 检测cache对象的类型，如果是PerpetualCache类型，则为其添加decorators集合中的装饰器;
    // 如果是自定义类型的Cache接口实现，则不添加decorators集合中的装饰器
    if (PerpetualCache.class.equals(cache.getClass())) {  // 无论是if分支还是else分支，都会为其添加 LoggingCache
      for (Class<? extends Cache> decorator : decorators) {
        // 通过反射获取参数为Cache类型的构造方法，并通过该构造方法创建装饰器（cache为底层被装饰的缓存对象）
        cache = newCacheDecoratorInstance(decorator, cache);
        // 根据<cache>节点下配置的<property>信息，初始化Cache对象相应的字段
        setCacheProperties(cache);
      }
      // 添加MyBatis中提供的标准装饰器
      cache = setStandardDecorators(cache);
    } else if (!LoggingCache.class.isAssignableFrom(cache.getClass())) {
      // 如果不是LoggingCache的子类，则添加LoggingCache装饰器
      cache = new LoggingCache(cache);
    }
    return cache;
  }

  /**
   * 如果 implementation 字段和 decorators 集合为空，则为其设置默认值
   */
  private void setDefaultImplementations() {
    if (implementation == null) {
      implementation = PerpetualCache.class;
      if (decorators.isEmpty()) {
        decorators.add(LruCache.class);
      }
    }
  }

  /**
   * 根据CacheBuilder中各个字段的值，为{@link Cache}对象添加对应的装饰器。
   *
   * @param cache
   * @return
   */
  private Cache setStandardDecorators(Cache cache) {
    try {
      MetaObject metaCache = SystemMetaObject.forObject(cache);

      // 如果cache对象定义有 size字段，则初始化该值
      if (size != null && metaCache.hasSetter("size")) {
        metaCache.setValue("size", size);
      }
      // 如果配置了清空缓存的周期，则添加周期性清理缓存的功能
      if (clearInterval != null) {
        cache = new ScheduledCache(cache);
        ((ScheduledCache) cache).setClearInterval(clearInterval);
      }
      // 如果可以读写，在添加缓存序列化装饰器
      if (readWrite) {
        cache = new SerializedCache(cache);
      }
      // 默认添加 LoggingCache（提供日志功能的缓存装饰器） 和 SynchronizedCache（实现同步的缓存装饰器） 装饰器
      cache = new LoggingCache(cache);
      cache = new SynchronizedCache(cache);
      // 如果能阻塞，则添加阻塞缓存装饰器
      if (blocking) {
        cache = new BlockingCache(cache);
      }
      return cache;
    } catch (Exception e) {
      throw new CacheException("Error building standard cache decorators.  Cause: " + e, e);
    }
  }

  /**
   * 根据<cache>节点下配置的<property>信息，初始化Cache对象
   *
   * @param cache
   */
  private void setCacheProperties(Cache cache) {
    if (properties != null) {
      MetaObject metaCache = SystemMetaObject.forObject(cache);
      for (Map.Entry<Object, Object> entry : properties.entrySet()) {
        // <cache>子标签<property>的name属性值
        String name = (String) entry.getKey();
        // <cache>子标签<property>的value属性值
        String value = (String) entry.getValue();
        if (metaCache.hasSetter(name)) {  // 检测Cache中是否有对应的setter方法，没有则不配置（无用配置，不会抛出异常）
          // 获取该属性的类型
          Class<?> type = metaCache.getSetterType(name);
          // 根据setter形参的类型进行类型转换
          if (String.class == type) {
            metaCache.setValue(name, value);
          } else if (int.class == type
            || Integer.class == type) {
            metaCache.setValue(name, Integer.valueOf(value));
          } else if (long.class == type
            || Long.class == type) {
            metaCache.setValue(name, Long.valueOf(value));
          } else if (short.class == type
            || Short.class == type) {
            metaCache.setValue(name, Short.valueOf(value));
          } else if (byte.class == type
            || Byte.class == type) {
            metaCache.setValue(name, Byte.valueOf(value));
          } else if (float.class == type
            || Float.class == type) {
            metaCache.setValue(name, Float.valueOf(value));
          } else if (boolean.class == type
            || Boolean.class == type) {
            metaCache.setValue(name, Boolean.valueOf(value));
          } else if (double.class == type
            || Double.class == type) {
            metaCache.setValue(name, Double.valueOf(value));
          } else {
            throw new CacheException("Unsupported property type for cache: '" + name + "' of type " + type);
          }
        }
      }
    }
    // 如果Cache类继承了 InitializingObject 接口，则调用其 initialize()方法继续自定义的初始化操作
    if (InitializingObject.class.isAssignableFrom(cache.getClass())) {
      try {
        ((InitializingObject) cache).initialize();
      } catch (Exception e) {
        throw new CacheException("Failed cache initialization for '"
          + cache.getId() + "' on '" + cache.getClass().getName() + "'", e);
      }
    }
  }

  /**
   * 根据 implementation 指定的类型，通过反射获取参数为 String 类型的构造方法，并通过该构造方法创建 Cache 对象
   *
   * @param cacheClass
   * @param id
   * @return
   */
  private Cache newBaseCacheInstance(Class<? extends Cache> cacheClass, String id) {
    // 根据cacheClass形参获取只带有String形参的构造器
    Constructor<? extends Cache> cacheConstructor = getBaseCacheConstructor(cacheClass);
    try {
      // 使用id初始化实例化Cache对象并返回
      return cacheConstructor.newInstance(id);
    } catch (Exception e) {
      throw new CacheException("Could not instantiate cache implementation (" + cacheClass + "). Cause: " + e, e);
    }
  }

  /**
   * 根据cacheClass形参获取只带有{@link String}形参的构造器，如果没有定义该构造器，则抛出异常
   *
   * @param cacheClass
   * @return
   */
  private Constructor<? extends Cache> getBaseCacheConstructor(Class<? extends Cache> cacheClass) {
    try {
      return cacheClass.getConstructor(String.class);
    } catch (Exception e) {
      throw new CacheException("Invalid base cache implementation (" + cacheClass + ").  "
        + "Base cache implementations must have a constructor that takes a String id as a parameter.  Cause: " + e, e);
    }
  }

  /**
   * 通过缓存装饰器构造器实例化装饰器对象
   *
   * @param cacheClass
   * @param base
   * @return
   */
  private Cache newCacheDecoratorInstance(Class<? extends Cache> cacheClass, Cache base) {
    Constructor<? extends Cache> cacheConstructor = getCacheDecoratorConstructor(cacheClass);
    try {
      return cacheConstructor.newInstance(base);
    } catch (Exception e) {
      throw new CacheException("Could not instantiate cache decorator (" + cacheClass + "). Cause: " + e, e);
    }
  }

  /**
   * 获取只带{@link Cache}形参的缓存装饰器构造器，没有则该构造器则抛出异常
   *
   * @param cacheClass
   * @return
   */
  private Constructor<? extends Cache> getCacheDecoratorConstructor(Class<? extends Cache> cacheClass) {
    try {
      return cacheClass.getConstructor(Cache.class);
    } catch (Exception e) {
      throw new CacheException("Invalid cache decorator (" + cacheClass + ").  "
        + "Cache decorators must have a constructor that takes a Cache instance as a parameter.  Cause: " + e, e);
    }
  }
}
