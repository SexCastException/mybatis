package com.example.mybatis.onetoone.bean;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * �û�ʵ����
 * 
 * @author Administrator
 *
 */
public class User implements Serializable{
	/**
	 * �洢�ڶ��������µĶ����Ӧ���������л��Ų��ᱨ��
	 */
	private static final long serialVersionUID = 1L;
	
	private int id; // �û�id
	private String username;// �û�����
	private String sex;// �Ա�
	private Date birthday;// ����
	private String address;// ��ַ
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
