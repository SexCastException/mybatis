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
package org.apache.ibatis.binding;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 封装了Mapper接口中对应方法的信息，以及对应SQL语句的信息。
 * 可以将MapperMethod看作连接Mapper 接口以及映射配置文件中定义的SQL语句的桥梁。
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

  /**
   * SQL语句的名称和类型
   */
  private final SqlCommand command;
  /**
   * 方法签名，记录了Mapper接口中对应方法的信息
   */
  private final MethodSignature method;

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  /**
   * 根据 SQL 语句的类型调用 {@link SqlSession} 应的方法完成数据库操作，若果是查询语句，则根据Mapper方法类型（返回值类型+形参列表）
   * 决定调用{@link SqlSession}相应的查询语句。
   *
   * @param sqlSession
   * @param args
   * @return
   */
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) {  // 根据 SQL 语句的类型调用 SqlSession 对应的方法
      case INSERT: {
        Object param = method.convertArgsToSqlCommandParam(args);
        // 根据sqlSession 查询的结果类型转换成方法返回值对象的类型
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        if (method.returnsVoid() && method.hasResultHandler()) {  // 处理返回值为void且ResultSet通过ResultHandler处理的方法
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {  // 处理返回值为集合或数组的方法
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) { // 处理返回值为Map的方法且配置了MapKey注解
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {  // 处理返回值为Cursor 的方法
          result = executeForCursor(sqlSession, args);
        } else {  // 处理返回值为单一对象的方法
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
          // 如果返回值不是Optional类型，则返回Optional对象封装的value值，否则返回Optional类型
          if (method.returnsOptional()
            && (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
      case FLUSH: // 如果sql为FLUSH类型，则直接调用flushStatements()方法
        result = sqlSession.flushStatements();
        break;
      default:  // UNKNOWN
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
        + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }

  /**
   * 根据method字段中记录的方法的返回值类型对结果进行转换
   * <p>
   * 当执行 INSERT, UPDATE, DELETE方法时，返回值都是int类型，rowCountResult() 方法会将该 int 值转换成 Mapper 接口中对应方法的返回值
   *
   * @param rowCount
   * @return
   */
  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) { // 如果Mapper方法返回值为void类型，则不返回受影响的行数，而是直接返回null
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
      // 如果返回值本身就是int类型的，则不需要类型转换
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      result = (long) rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  /**
   * 处理返回值为void且 {@link ResultSet}通过 {@link ResultHandler}处理的方法，此时参数args必定有 {@link ResultHandler}类型的对象
   *
   * @param sqlSession
   * @param args
   */
  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    // 获取 SQL 语句对应 MappedStatement 对象， MappedStatement 中记录了 SQL 语句相关信息
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    // 当使用 ResultHandler 处理结果集时，必须指定ResultMap或ResultType，否则抛出异常
    if (!StatementType.CALLABLE.equals(ms.getStatementType()) && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName()
        + " needs either a @ResultMap annotation, a @ResultType annotation,"
        + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  /**
   * 处理返回值为集合或数组的方法，即有多条查询记录，通过 {@link RowBounds}来分页，如果该对象不为null
   *
   * @param sqlSession
   * @param args
   * @param <E>
   * @return
   */
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {  // 判断Mapper方法形参是否有 RowBounds对象
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  /**
   * 处理返回值为 {@link Cursor}的方法
   *
   * @param sqlSession
   * @param args
   * @param <T>
   * @return
   */
  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {// 判断Mapper方法形参是否有 RowBounds对象
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    Object array = Array.newInstance(arrayComponentType, list.size());
    if (arrayComponentType.isPrimitive()) {
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    } else {
      return list.toArray((E[]) array);
    }
  }

  /**
   * 处理返回值为Map的方法且配置了 {@link MapKey}注解
   *
   * @param sqlSession
   * @param args
   * @param <K>
   * @param <V>
   * @return
   */
  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {  // 判断Mapper方法形参是否有 RowBounds对象
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    /**
     * 不允许获取不存在key对应的value，否则抛出 {@link BindingException}
     *
     * @param key
     * @return
     */
    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  public static class SqlCommand {

    /**
     * SQL语句的id（namespace + SQL节点属性id），即：statementId
     */
    private final String name;
    /**
     * SQL语句的类型,枚举类型
     */
    private final SqlCommandType type;

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      // Mapper接口方法名称
      final String methodName = method.getName();
      // Mapper的Class对象
      final Class<?> declaringClass = method.getDeclaringClass();
      // 从Configuration中获取 MappedStatement 对象
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
        configuration);
      if (ms == null) { // 如果获取不到statementId绑定的MappedStatement对象，除非该Mapper方法配置了Flush注解，否则抛出异常
        if (method.getAnnotation(Flush.class) != null) {
          name = null;
          type = SqlCommandType.FLUSH;
        } else {
          throw new BindingException("Invalid bound statement (not found): "
            + mapperInterface.getName() + "." + methodName);
        }
      } else {
        name = ms.getId();
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    /**
     * 获取Mapper接口方法对应的statementId绑定的{@link MappedStatement}
     *
     * @param mapperInterface Mapper接口Class对象
     * @param methodName      方法名称
     * @param declaringClass  methodName对应的Method对象所声明的类，有可能是mapperInterface或者mapperInterface的父接口
     * @param configuration   全局配置文件
     * @return
     */
    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
                                                   Class<?> declaringClass, Configuration configuration) {
      // statementId是由Mapper接口名称和方法名称组成
      String statementId = mapperInterface.getName() + "." + methodName;
      if (configuration.hasStatement(statementId)) {  // 检测是否为有效的SQL语句
        return configuration.getMappedStatement(statementId);
      } else if (mapperInterface.equals(declaringClass)) {
        // 如果methodName指定的Method对象所声明的类就是Mapper接口，以上判断条件找不到MappedStatement，遍历父接口照样找不到，返回null，结束方法
        return null;
      }
      // 本接口中获取不到 MappedStatement对象，则从父接口中获取
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        if (declaringClass.isAssignableFrom(superInterface)) {
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
            declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

  public static class MethodSignature {

    /**
     * 返回类型是否是Collection类型或数组类型
     */
    private final boolean returnsMany;
    /**
     * 返回类型是否Map类型
     */
    private final boolean returnsMap;
    /**
     * 返回类型是否void类型
     */
    private final boolean returnsVoid;
    /**
     * 返回类型是否{@link Cursor}类型
     */
    private final boolean returnsCursor;
    /**
     * 返回值类型是否{@link Optional}类型，Optional，JDK8新特性
     */
    private final boolean returnsOptional;
    /**
     * 返回值类型
     */
    private final Class<?> returnType;
    /**
     * 如果返回类型是Map类型并且是否配置了 {@link MapKey}注解，如果true，则mapKey记录了作为key的别名
     */
    private final String mapKey;
    /**
     * 用来标记该方法参数列表中{@link ResultHandler}类型参数的位置
     */
    private final Integer resultHandlerIndex;
    /**
     * 用来标记该方法参数列表中{@link RowBounds}类型参数的位置
     */
    private final Integer rowBoundsIndex;
    /**
     * 该方法对应的 {@link ParamNameResolver} 对象
     */
    private final ParamNameResolver paramNameResolver;

    /**
     * 构造函数中会解析相应 Method 对象，并初始化上述字段
     *
     * @param configuration
     * @param mapperInterface
     * @param method
     */
    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 解析方法返回值类型
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        this.returnType = method.getReturnType();
      }
      // 以下判断返回值类型
      this.returnsVoid = void.class.equals(this.returnType);
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      this.returnsCursor = Cursor.class.equals(this.returnType);
      this.returnsOptional = Optional.class.equals(this.returnType);

      // 若MethodSignature对应方法的返回值是Map且指定了@MapKey注解，则使用getMapKey()方法处理
      this.mapKey = getMapKey(method);
      this.returnsMap = this.mapKey != null;
      // 解析RowBounds在参数列表中的位置
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      // 解析ResultHandler在参数列表中的位置
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }

    /**
     * 负责将args[]数组(用户传入的实参列表)转换成SQL语句对应的参数列表
     *
     * @param args
     * @return
     */
    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    /**
     * 返回形参args中的 {@link RowBounds}对象
     *
     * @param args
     * @return
     */
    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    /**
     * 返回形参args中的 {@link ResultHandler}对象
     *
     * @param args
     * @return
     */
    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public String getMapKey() {
      return mapKey;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * return whether return type is {@code java.util.Optional}.
     *
     * @return return {@code true}, if return type is {@code java.util.Optional}
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }

    /**
     * 查找指定参数在参数列表中的位置
     *
     * @param method
     * @param paramType
     * @return
     */
    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      // 获取方法的参数列表类型
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) {
          if (index == null) {
            index = i;
          } else {  // RowBounds 和 ResultHandler 类型的参数只能有一个，不能重复出现，否则抛出异常
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }

    /**
     * 获取方法返回值为 {@link Map}使用注解{@link MapKey}指定的value值
     *
     * @param method
     * @return
     */
    private String getMapKey(Method method) {
      String mapKey = null;
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }
  }

}
