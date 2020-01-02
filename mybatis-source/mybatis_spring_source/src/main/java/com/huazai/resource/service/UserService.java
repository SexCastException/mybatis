package com.huazai.resource.service;

import com.huazai.resource.dao.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public UserService() {
        /*
               cityMapper 对象，如何被实例化的？
         */
    }

    public void queryAll() {
        List<Map<String, Object>> result = userMapper.query();
        System.out.println(result);
    }
}
