package com.huazai.test;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

public class XPathTest {
  @Test
  public void testXPath() throws Exception {
    Document document = createDocument();

    XPathFactory xPathFactory = XPathFactory.newInstance();
    // 创建 XPath 对象
    XPath xPath = xPathFactory.newXPath();
    // 编译 XPath 表达式，如果一个XPpath表达式需要重复执行多次，建议先进行编译
    XPathExpression expression = xPath.compile("//book[author='华仔']/title/text()");
    /*
      第一个参数指定了 XPath 表达式进行查询的上下文节点，也就是在指定节点下查找符合 XPath 的节点 本例中的上下文节点是整个文档；
      第二个参数指定了 XPath 表达式的返回类型
     */
    NodeList nodeList = (NodeList) expression.evaluate(document, XPathConstants.NODESET);
    if (nodeList != null) {
      for (int i = 0; i < nodeList.getLength(); i++) {
        System.out.println(nodeList.item(i).getNodeValue());
      }
    }

    NodeList nodeList1 = (NodeList) xPath.evaluate("//book[@year>2001]/title/text()", document, XPathConstants.NODESET);
    if (nodeList1 != null) {
      for (int i = 0; i < nodeList1.getLength(); i++) {
        System.out.println(nodeList1.item(i).getNodeValue());
      }
    }
  }

  private Document createDocument() throws Exception {
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    // 开启校验
    documentBuilderFactory.setValidating(true);
    documentBuilderFactory.setNamespaceAware(false);
    documentBuilderFactory.setIgnoringComments(true);
    documentBuilderFactory.setIgnoringElementContentWhitespace(false);
    documentBuilderFactory.setCoalescing(false);
    documentBuilderFactory.setExpandEntityReferences(true);

    // 创建文档对象
    DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
    // 设置异常处理对象
    documentBuilder.setErrorHandler(new ErrorHandler() {
      @Override
      public void warning(SAXParseException exception) throws SAXException {
        System.out.println("warning:" + exception.getMessage());
      }

      @Override
      public void error(SAXParseException exception) throws SAXException {
        System.out.println("error:" + exception.getMessage());
      }

      @Override
      public void fatalError(SAXParseException exception) throws SAXException {
        System.out.println("fatalError:" + exception.getMessage());
      }
    });

    // 解析xml
    Document document = documentBuilder.parse("src/test/java/resources/XPathTest.xml");
    return document;
  }
}
