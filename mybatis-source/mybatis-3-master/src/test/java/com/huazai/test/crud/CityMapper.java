package com.huazai.test.crud;

import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * @author pyh
 * @date 2020/4/4 10:44
 */
public interface CityMapper {
  @Select("select * from city")
  List<Map> list();
}
