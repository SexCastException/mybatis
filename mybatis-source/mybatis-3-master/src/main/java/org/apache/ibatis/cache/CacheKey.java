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
package org.apache.ibatis.cache;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ArrayUtil;
import org.apache.ibatis.session.RowBounds;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 在{@link Cache}中唯一确定一个缓存项需要使用缓存项的key,MyBatis中因为涉及动态SQL等多方面因素，
 * 其缓存项的key不能仅仅通过一一个 {@link String} 表示，所以MyBatis提供了CacheKey类来表示缓存项的key,
 * 在一个CacheKey对象中可以封装多个影响缓存项的因素。
 * <p>
 * CacheKey中可以添加多个对象，由这些对象共同确定两个CacheKey 对象是否相同。<br>
 * 即：{@link CacheKey#multiplier}、{@link CacheKey#hashcode}、{@link CacheKey#checksum}和{@link CacheKey#updateList}集合
 * 所有项共同确定是否为同一个CacheKey对象
 *
 * @author Clinton Begin
 */
public class CacheKey implements Cloneable, Serializable {

  private static final long serialVersionUID = 1146682552656046210L;

  public static final CacheKey NULL_CACHE_KEY = new CacheKey() {
    @Override
    public void update(Object object) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }

    @Override
    public void updateAll(Object[] objects) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }
  };

  /**
   * 默认的multiplier值
   */
  private static final int DEFAULT_MULTIPLIER = 37;
  /**
   * 默认的hashcode值
   */
  private static final int DEFAULT_HASHCODE = 17;

  /**
   * 参与计算hashcode，默认值是 {@link CacheKey#DEFAULT_MULTIPLIER}，即37
   */
  private final int multiplier;
  /**
   * CacheKey的hashcode，默认值是{@link CacheKey#DEFAULT_HASHCODE}，即17
   */
  private int hashcode;
  /**
   * 校验和
   */
  private long checksum;
  // 8/21/2017 - Sonarlint flags this as needing to be marked transient.  While true if content is not serializable, this is not always true and thus should not be marked transient.
  /**
   * 由该集合中的所有对象决定CacheKey是否相同
   * 通常情况下：updateList通过加入以下四个部分的数据来唯一标识CacheKey
   * 1、{@link MappedStatement}的id
   * 2、指定查询结果集的范围，即：{@link RowBounds#offset}和{@link RowBounds#limit}
   * 3、查询所使用的SQL语句，即boundSql.getSql() 方法返回的SQL语句，其中可能包含“?”占位符
   * 4、用户传递给上述SQL语句的实际参数值
   * <p>
   * 向updateList添加对象时，使用的是{@link CacheKey#update(java.lang.Object)}方法
   */
  private List<Object> updateList;
  /**
   * {@link CacheKey#updateList}集合的个数，初始值是0
   */
  private int count;


  public CacheKey() {
    this.hashcode = DEFAULT_HASHCODE;
    this.multiplier = DEFAULT_MULTIPLIER;
    this.count = 0;
    this.updateList = new ArrayList<>();
  }

  /**
   * 把objects加入到updateList集合中去
   *
   * @param objects
   */
  public CacheKey(Object[] objects) {
    this();
    updateAll(objects);
  }

  public int getUpdateCount() {
    return updateList.size();
  }

  /**
   * 和旧版本的update实现思路些许不同
   *
   * @param object
   */
  public void update(Object object) {
    // 计算object的hashcode值，注意：空对象也加入，hashcode为1
    int baseHashCode = object == null ? 1 : ArrayUtil.hashCode(object);

    // 统计向update添加的的个数
    count++;
    checksum += baseHashCode;
    baseHashCode *= count;

    hashcode = multiplier * hashcode + baseHashCode;

    updateList.add(object);
  }

  public void updateAll(Object[] objects) {
    for (Object o : objects) {
      update(o);
    }
  }

  /**
   * 重写了equals() 和 hashcode() 方法，这两个方法使用 count、checksum、hashcode和updateList比较两个CacheKey是否相同，
   * 四者中有一个不相同则视为与比较的对象不相同
   *
   * @param object
   * @return
   */
  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof CacheKey)) {  // 类型是否相同
      return false;
    }

    final CacheKey cacheKey = (CacheKey) object;

    if (hashcode != cacheKey.hashcode) {  // 比较hashcode
      return false;
    }
    if (checksum != cacheKey.checksum) {  // 比较checksum
      return false;
    }
    if (count != cacheKey.count) {  // 比较count
      return false;
    }

    for (int i = 0; i < updateList.size(); i++) { // 比较updateList的每一项，有一项不相同则返回false
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      if (!ArrayUtil.equals(thisObject, thatObject)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  /**
   * 输出格式：hashcode:checksum:updateList.get(0):updateList.get(1):updateList.get(2):......
   * 例如：-422414763:-245293739:1:hello:null:Tue Dec 10 00:22:21 CST 2019
   *
   * @return
   */
  @Override
  public String toString() {
    StringJoiner returnValue = new StringJoiner(":");
    returnValue.add(String.valueOf(hashcode));
    returnValue.add(String.valueOf(checksum));
    updateList.stream().map(ArrayUtil::toString).forEach(returnValue::add);
    return returnValue.toString();
  }

  @Override
  public CacheKey clone() throws CloneNotSupportedException {
    CacheKey clonedCacheKey = (CacheKey) super.clone();
    clonedCacheKey.updateList = new ArrayList<>(updateList);
    return clonedCacheKey;
  }

}
