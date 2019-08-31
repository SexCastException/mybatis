/*
Navicat MySQL Data Transfer

Source Server         : localhost_3306
Source Server Version : 50545
Source Host           : localhost:3306
Source Database       : mybatis

Target Server Type    : MYSQL
Target Server Version : 50545
File Encoding         : 65001

Date: 2019-07-19 17:44:44
*/

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for items
-- ----------------------------
DROP TABLE IF EXISTS `items`;
CREATE TABLE `items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL COMMENT '商品名称',
  `price` float(10,1) NOT NULL COMMENT '商品定价',
  `detail` text COMMENT '商品描述',
  `pic` varchar(64) DEFAULT NULL COMMENT '商品图片',
  `createtime` datetime NOT NULL COMMENT '生产日期',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of items
-- ----------------------------
INSERT INTO `items` VALUES ('1', '我就测试一下', '12.3', '日体育局', 'D:\\upload\\pic\\371589a7-df26-439c-b2d5-a7ecc79db6b6_bear.jpg', '2015-02-03 13:22:53');
INSERT INTO `items` VALUES ('2', '笔记本', '9999.0', '笔记本性能好，质量好！！！！！', null, '2015-02-09 13:22:57');
INSERT INTO `items` VALUES ('3', '背包', '6666.0', '名牌背包，容量大质量好！！！！', null, '2015-02-06 13:23:02');

-- ----------------------------
-- Table structure for orderdetail
-- ----------------------------
DROP TABLE IF EXISTS `orderdetail`;
CREATE TABLE `orderdetail` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `orders_id` int(11) NOT NULL COMMENT '订单id',
  `items_id` int(11) NOT NULL COMMENT '商品id',
  `items_num` int(11) DEFAULT NULL COMMENT '商品购买数量',
  PRIMARY KEY (`id`),
  KEY `FK_orderdetail_1` (`orders_id`),
  KEY `FK_orderdetail_2` (`items_id`),
  CONSTRAINT `FK_orderdetail_1` FOREIGN KEY (`orders_id`) REFERENCES `orders` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_orderdetail_2` FOREIGN KEY (`items_id`) REFERENCES `items` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of orderdetail
-- ----------------------------
INSERT INTO `orderdetail` VALUES ('11', '111', '1', '1');
INSERT INTO `orderdetail` VALUES ('22', '222', '2', '2');
INSERT INTO `orderdetail` VALUES ('33', '222', '3', '3');
INSERT INTO `orderdetail` VALUES ('44', '444', '2', '4');

-- ----------------------------
-- Table structure for orders
-- ----------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL COMMENT '下单用户id',
  `number` varchar(32) NOT NULL COMMENT '订单号',
  `createtime` datetime NOT NULL COMMENT '创建订单时间',
  `note` varchar(100) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `FK_orders_1` (`user_id`),
  CONSTRAINT `FK_orders_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=667 DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of orders
-- ----------------------------
INSERT INTO `orders` VALUES ('7', '5555', '1000014', '2018-09-15 09:15:26', null);
INSERT INTO `orders` VALUES ('111', '1111', '1000010', '2015-02-04 13:22:35', null);
INSERT INTO `orders` VALUES ('222', '2222', '1000011', '2015-02-03 13:22:41', null);
INSERT INTO `orders` VALUES ('333', '3333', '1000012', '2015-02-12 16:13:23', null);
INSERT INTO `orders` VALUES ('444', '4444', '1000013', '2018-09-18 13:16:59', null);
INSERT INTO `orders` VALUES ('666', '1111', '1000015', '2018-09-15 09:15:26', null);

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(32) NOT NULL COMMENT '用户名称',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `sex` char(1) DEFAULT NULL COMMENT '性别',
  `address` varchar(256) DEFAULT NULL COMMENT '地址',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5556 DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES ('1', 'vn', '2018-09-12', '0', 'America');
INSERT INTO `user` VALUES ('2', '狮子狗', '2018-09-12', '1', null);
INSERT INTO `user` VALUES ('3', '不详', '2018-09-12', '1', null);
INSERT INTO `user` VALUES ('4', '寒冰姐姐', '2018-09-12', '1', null);
INSERT INTO `user` VALUES ('5', '桂花', '2018-09-12', '1', null);
INSERT INTO `user` VALUES ('6', '二狗子', '2018-09-12', '0', null);
INSERT INTO `user` VALUES ('7', '瞎子', '2018-09-13', '1', null);
INSERT INTO `user` VALUES ('10', '石头人', '2018-09-12', '0', 'America');
INSERT INTO `user` VALUES ('16', '劫', '2018-09-12', '0', 'China');
INSERT INTO `user` VALUES ('322', '老鼠', '2018-09-13', '0', 'China');
INSERT INTO `user` VALUES ('1111', '暴走萝莉', '2018-09-12', '0', 'China');
INSERT INTO `user` VALUES ('2222', '光辉', '2018-09-12', '0', 'China');
INSERT INTO `user` VALUES ('3333', '麻花', '2018-09-12', '1', 'Chian');
INSERT INTO `user` VALUES ('4444', '翠花', '2018-09-12', '1', 'Chian');
INSERT INTO `user` VALUES ('5555', '花花', '2018-09-12', '0', 'China');
