package cn.test.bean;

import java.util.List;

/**
 * 用户包装类
 * 
 * @author Administrator
 *
 */
public class UserQueryPo {

	private UserCustom userCustom;

	private List<Integer> ids;

	public UserCustom getUserCustom() {
		return userCustom;
	}

	public void setUserCustom(UserCustom userCustom) {
		this.userCustom = userCustom;
	}

	public List<Integer> getIds() {
		return ids;
	}

	public void setIds(List<Integer> ids) {
		this.ids = ids;
	}

}
