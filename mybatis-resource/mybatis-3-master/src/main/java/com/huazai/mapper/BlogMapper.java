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
package com.huazai.mapper;

import com.huazai.bean.Blog;
import com.huazai.bean.Comment;

import java.util.List;

/**
 * @author pyh
 * @email pyh@efala.com
 * @date 2019/12/23 19:10:47
 */
public interface BlogMapper {
  Blog selectBlogDetail(Blog blog);

  List<Comment> selectComment(Integer postId);
}
