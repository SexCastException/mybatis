package com.huazai.resource.dao;

import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface UserMapper {
    @Select("SELECT * FROM `user`")
    List<Map<String, Object>> query();
}
