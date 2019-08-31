package cn.test.mapper;

import java.util.List;
import java.util.Map;

import cn.test.bean.User;
import cn.test.bean.UserCustom;
import cn.test.bean.UserQueryPo;

public interface UserMapper {

	public List<User> findUserByIDs(UserQueryPo userQueryPo) throws Exception;

	public List<UserCustom> findUserListByDynamicSQL(UserQueryPo userQueryPo) throws Exception;

	public List<UserCustom> findUserListByResultMap(int id) throws Exception;

	public List<UserCustom> findUserListByMap(Map<String, Object> map) throws Exception;

	public List<UserCustom> findUserList(UserQueryPo userQueryPo) throws Exception;

	public User findUserById(int id) throws Exception;

	public List<User> findUserByName(String name) throws Exception;

	public void addUser(User user) throws Exception;

	public void deleteById(int id) throws Exception;

	public void updateUser(User user) throws Exception;

}
