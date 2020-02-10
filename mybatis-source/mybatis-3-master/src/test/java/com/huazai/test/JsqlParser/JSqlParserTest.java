package com.huazai.test.JsqlParser;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.JSqlParser;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;
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
    String selectSql = "SELECT id,username,`password`,email FROM `author` LEFT JOIN `user` u ON u.author_id = `author`.id WHERE id >= 5 GROUP BY username ORDER BY email DESC;";
    String insertSql = "INSERT INTO `author` (id, username, password, email) VALUES (1345, 'NPException', '123456', 'email@email.com')";
    String updateSql = "UPDATE `author` SET username = 'huazai' WHERE id = 1";
    String deleteSql = "DELETE FROM `author` WHERE id = 1";

    sqlParse(selectSql);
    sqlParse(insertSql);
    sqlParse(updateSql);
    sqlParse(deleteSql);
  }

  private String sqlParse(String sql) throws JSQLParserException {
    // CCJSqlParserUtil 是 JSqlParser 中比较重要的工具类，它会解析SQL语句并返回 Statement 对象，Statement 对象可用于导航描述SQL语句的结构
    Statement statement = CCJSqlParserUtil.parse(sql);
    if (statement instanceof Select) {  // 检测被解析的SQL语句是否为select 语句
      Select select = (Select) statement;
      parseSelect(select);
    } else if (statement instanceof Insert) {  // 检测被解析的SQL语句是否为insert 语句
      Insert insert = (Insert) statement;
      parseInsert(insert);
    } else if (statement instanceof Update) {  // 检测被解析的SQL语句是否为update 语句
      Update update = (Update) statement;
      parseUpdate(update);
    } else if (statement instanceof Delete) {  // 检测被解析的SQL语句是否为delete 语句
      Delete delete = (Delete) statement;
      parseDelete(delete);
    }
    return null;
  }

  private void parseSelect(Select select) {
    System.out.println("\nselect语句：");
    // 解析select查询语句的列名
    System.out.println("\n列名：");
    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
    List<SelectItem> selectItems = plainSelect.getSelectItems();
    if (selectItems != null) {
      selectItems.forEach(System.out::println);
    }

    // 解析select查询语句的表名
    System.out.println("\n表名：");
    TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
    List<String> tableList = tablesNamesFinder.getTableList(select);
    if (tableList != null) {
      tableList.forEach(System.out::println);
    }

    // 解析select查询语句的where部分
    Expression where = plainSelect.getWhere();
    System.out.println("\nwhere：" + where.toString());

    // 解析select查询语句的group by部分的列名
    System.out.println("\ngroup by部分的列名");
    List<Expression> groupByColumnReferences = plainSelect.getGroupByColumnReferences();
    if (groupByColumnReferences != null) {
      groupByColumnReferences.forEach(System.out::println);
    }

    // 解析select查询语句的where部分
    System.out.println("\norder by部分的列名");
    List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
    if (orderByElements != null) {
      orderByElements.forEach(System.out::println);
    }

    // 解析select查询语句的join部分
    System.out.println("\njoin：");
    List<Join> joins = plainSelect.getJoins();
    if (joins != null) {
      joins.forEach(System.out::println);
    }
  }

  private void parseInsert(Insert insert) {
    System.out.println("\ninsert语句：");
    // 解析insert语句的列名
    System.out.println("\n列名：");
    List<Column> columns = insert.getColumns();
    if (columns != null) {
      columns.forEach(System.out::println);
    }

    System.out.println("\n表名：");
    System.out.println(insert.getTable().getName());

    System.out.println("\n列值：");
    List<Expression> expressions = ((ExpressionList) insert.getItemsList()).getExpressions();
    if (expressions != null) {
      expressions.forEach(System.out::println);
    }
  }

  private void parseUpdate(Update update) {
    System.out.println("\nupdate语句：");
    // 解析update语句的列名
    System.out.println("\n列名：");
    List<Column> columns = update.getColumns();
    if (columns != null) {
      columns.forEach(System.out::println);
    }

    System.out.println("\n表名：");
    List<Table> tables = update.getTables();
    if (tables != null) {
      tables.forEach(System.out::println);
    }

    System.out.println("\nwhere：");
    System.out.println(update.getWhere().toString());

    System.out.println("\nexpressions：");
    List<Expression> expressions = update.getExpressions();
    if (expressions != null) {
      expressions.forEach(System.out::println);
    }
  }

  private void parseDelete(Delete delete) {
    System.out.println("\ndelete语句：");
    System.out.println("\n表名：");
    List<Table> tables = delete.getTables();
    if (tables != null) {
      tables.forEach(System.out::println);
    }

    System.out.println("\nwhere：");
    System.out.println(delete.getWhere().toString());
  }


  @Test
  public void testCreate() throws JSQLParserException {
    String originalSelectSql = "SELECT * FROM t_user;";
    String[] columns = {"user_name", "age", "email", "order_id", "sum"};
    String[] tables = {"t_user", "t_order"};
    String where = " user_id > 1357 ";
    String[] groups = {" age "};
    String[] orders = {"username", "age DESC"};

    createSelect(originalSelectSql, columns, tables, where, groups, orders);
  }

  private void createSelect(String originalSelectSql, String[] columns, String[] tables, String where, String[] groups, String[] orders) throws JSQLParserException {
    Select select = (Select) CCJSqlParserUtil.parse(originalSelectSql);
    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
    createSelectColumns(plainSelect, columns);
    createSelectTables(plainSelect, columns);
    createSelectWhere(plainSelect, columns);
    createSelectGroupBy(plainSelect, columns);
    createSelectOrderBy(plainSelect, columns);

    // 重置SelectBody
    select.setSelectBody(plainSelect);
    // 输出拼装的SQL语句
    System.out.println(select.toString());
  }


  private void createSelectColumns(PlainSelect plainSelect, String[] columns) throws JSQLParserException {
    SelectItem[] selectItems = new SelectItem[columns.length];
    // 清空原有SQL语句查询的列名
    plainSelect.setSelectItems(null);
    for (int i = 0; i < columns.length; i++) {
      // 将items转换成SelectItem对象
      selectItems[i] = new SelectExpressionItem(CCJSqlParserUtil.parseExpression(columns[i]));
      plainSelect.addSelectItems(selectItems[i]);
    }
  }

  private void createSelectTables(PlainSelect plainSelect, String[] columns) {
  }

  private void createSelectWhere(PlainSelect plainSelect, String[] columns) {
  }

  private void createSelectGroupBy(PlainSelect plainSelect, String[] columns) {
  }

  private void createSelectOrderBy(PlainSelect plainSelect, String[] columns) {
  }
}
