package com.huazai.test.javassist;

import javassist.*;

import java.lang.reflect.Method;

/**
 * Javassist是一个开源的生成Java字节码的类库，其主要优点在于简单、快速，
 * 直接使用Javassist提供的Java API就能动态修改类的结构，或是动态的生成类。<br>
 *
 * @author pyh
 * @date 2020/1/16 0:12
 */
public class JavassistMain {
  public static void main(String[] args) throws Exception {
    // 实例化对象
    Object o = generateClass().newInstance();
    Method executeMethod = o.getClass().getMethod("execute", new Class[]{});
    executeMethod.invoke(o, new Object[]{});
  }

  public static Class generateClass() throws Exception{
    ClassPool cp = ClassPool.getDefault();
    // 指定要生成的类名称
    CtClass ctClass = cp.makeClass("com.huazai.test.javassist.JavassistClass");

    // 创建字段，指定字段类型、名称和声明该字段的类
    CtField ctField = new CtField(cp.get("java.lang.String"), "prop", ctClass);
    // 指定字段修饰符
    ctField.setModifiers(Modifier.PRIVATE);

    // 创建prop属性的getter和setter方法
    ctClass.addMethod(CtNewMethod.getter("getProp", ctField));
    ctClass.addMethod(CtNewMethod.setter("setProp", ctField));
    // 设置prop字段的初始值，并将prop字段添加到ctClass中
    ctClass.addField(ctField, CtField.Initializer.constant("myName"));

    // 创建构造方法，指定构造方法的参数类型和声明的类
    CtConstructor ctConstructor = new CtConstructor(new CtClass[]{}, ctClass);
    // 设置方法体
    ctConstructor.setBody("\nsetProp(\"MyName is PangYinghua\");");
    ctClass.addConstructor(ctConstructor);

    // 创建方法，指定了方法返回值，方法名称、方法参数类型和声明该方法的类
    CtMethod execute = new CtMethod(CtClass.voidType, "execute", new CtClass[]{}, ctClass);
    CtMethod test = new CtMethod(cp.get("java.lang.String"), "test", new CtClass[]{cp.get("java.lang.String"), CtClass.intType}, ctClass);

    // 方法的修饰符
    execute.setModifiers(Modifier.PUBLIC);
    test.setModifiers(Modifier.PUBLIC);

    // 设置方法体
    execute.setBody("\n System.out.println(\"execute():\" + this.prop\n);");
    ctClass.addMethod(execute);

    test.setBody("\n {System.out.println(\"test():\" + this.prop\n);\nreturn \"test()\";\n}");
    ctClass.addMethod(test);

    // 生成的class文件保存路径，默认路径和当前项目src为同一等级目录
    ctClass.writeFile();

    // 加载ctClass
    Class c = ctClass.toClass();
    return c;
  }
}
