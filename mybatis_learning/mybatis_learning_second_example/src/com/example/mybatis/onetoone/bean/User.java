package com.example.mybatis.onetoone.bean;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 用户实体类
 * 
 * @author Administrator
 *
 */
public class User implements Serializable{
	/**
	 * 存储在二级缓存下的对象对应的类需序列化才不会报错
	 */
	private static final long serialVersionUID = 1L;
	
	private int id; // 用户id
	private String username;// 用户姓名
	private String sex;// 性别
	private Date birthday;// 生日
	private String address;// 地址
	private List<Orders> orders;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getSex() {
		return sex;
	}

	public void setSex(String sex) {
		this.sex = sex;
	}

	public Date getBirthday() {
		return birthday;
	}

	public void setBirthday(Date birthday) {
		this.birthday = birthday;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public List<Orders> getOrders() {
		return orders;
	}

	public void setOrders(List<Orders> orders) {
		this.orders = orders;
	}

	@Override
	public String toString() {
		return "User [id=" + id + ", username=" + username + ", sex=" + sex + ", birthday=" + birthday + ", address="
				+ address + "]";
	}

}
