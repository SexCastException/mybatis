package com.huazai.test.JsqlParser;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.JSqlParser;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@link JSqlParser}是一个SQL语句的解析器，主要用于完成对SQL语句进行解析和组装的工作。<br>
 * {@link JSqlParser}会解析SQL语句关键词之间的内容，并形成树状结构，树状结构中的节点是由相应的Java对象表示的。<br>
 * {@link JSqlParser}可以解析多种数据库产品支持的SQL语句，例如Oracle、SQLServer、MySQL、PostgreSQL等。<br>
 * {@link JSqlParser}除了可以解析SQL语句，还提供了修改SQL语句的功能。<br>
 *
 * @author pyh
 * @date 2020/2/10 0:29
 */
public class JSqlParserTest {
  @Test
  public void testSqlParse() throws JSQLParserException {
    String selectSql = "SELECT id,username,`password`,email FROM `author` WHERE id >= 5 GROUP BY username ORDER BY email DESC;";
    sqlParse(selectSql);
  }

  private String sqlParse(String sql) throws JSQLParserException {
    // CCJSqlParserUtil 是 JSqlParser 中比较重要的工具类，它会解析SQL语句并返回 Statement 对象，Statement 对象可用于导航描述SQL语句的结构
    Statement statement = CCJSqlParserUtil.parse(sql);
    if (statement instanceof Select) {  // 检测被解析的SQL语句是否为select 语句
      Select select = (Select) statement;
      parseSelect(select);
    }
    return null;
  }

  private void parseSelect(Select select) {
    // 解析select查询语句的列名
    System.out.println("\n列名：");
    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
    List<SelectItem> selectItems = plainSelect.getSelectItems();
    if (selectItems != null) {
      for (SelectItem selectItem : selectItems) {
        System.out.println(selectItem.toString() + " ");        // 输出列名
      }
    }

    // 解析select查询语句的表名
    System.out.println("\n表名：");
    TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
    List<String> tableList = tablesNamesFinder.getTableList(select);
    if (tableList != null) {
      for (String tableName : tableList) {
        System.out.println(tableName);  // 输出表名
      }
    }

    // 解析select查询语句的where部分
    Expression where = plainSelect.getWhere();
    System.out.println("\nwhere：" + where.toString());  // 输出where条件语句

    // 解析select查询语句的group by部分的列名
    System.out.println("\ngroup by部分的列名");
    List<Expression> groupByColumnReferences = plainSelect.getGroupByColumnReferences();
    if (groupByColumnReferences != null) {
      for (Expression groupByColumnReference : groupByColumnReferences) {
        System.out.println(groupByColumnReference.toString() + " ");  // 输出group by的列名
      }
    }

    // 解析select查询语句的where部分
    System.out.println("\norder by部分的列名");
    List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
    if (orderByElements != null) {
      for (OrderByElement orderByElement : orderByElements) {
        System.out.println(orderByElement.toString() + " ");
      }
    }
  }
}
