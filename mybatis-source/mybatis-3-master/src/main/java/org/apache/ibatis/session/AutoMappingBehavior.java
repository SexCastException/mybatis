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
package org.apache.ibatis.session;

/**
 * 自动映射行为
 * Specifies if and how MyBatis should automatically map columns to fields/properties.
 *
 * @author Eduardo Macarron
 */
public enum AutoMappingBehavior {

  /**
   * 取消自动映射
   * Disables auto-mapping.
   */
  NONE,

  /**
   * 部分映射，只会自动映射没有定义嵌套的结果集
   * Will only auto-map results with no nested result mappings defined inside.
   */
  PARTIAL,

  /**
   * 全自动映射，将自动映射任何复杂性的结果集（无论是否嵌套）
   * Will auto-map result mappings of any complexity (containing nested or otherwise).
   */
  FULL
}
