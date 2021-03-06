/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.io;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * A default implementation of {@link VFS} that works for most application servers.
 *
 * @author Ben Gunter
 */
public class DefaultVFS extends VFS {
  private static final Log log = LogFactory.getLog(DefaultVFS.class);

  /**
   * 指示JAR（ZIP）文件的魔术头。用来判断是否为JAR (ZIP) 文件
   * JAR文件的魔法值为: 50(P) 4B(K) 03 04 即:
   * The magic header that indicates a JAR (ZIP) file.
   */
  private static final byte[] JAR_MAGIC = {'P', 'K', 3, 4};

  /**
   * 检测当前 VFS 对象在当前环境下是否有效
   *
   * @return
   */
  @Override
  public boolean isValid() {
    return true;
  }

  /**
   * @param url  The URL that identifies the resource to list.                                 该资源路径对应的URL对象
   * @param path
   * @return
   * @throws IOException
   */
  @Override
  public List<String> list(URL url, String path) throws IOException {
    InputStream is = null;
    try {
      List<String> resources = new ArrayList<>();

      // First, try to find the URL of a JAR file containing the requested resource. If a JAR
      // file is found, then we'll list child resources by reading the JAR.
      // 如果url指向的资源在一个Jar包中，则获取该Jar包对应的URL,否则返回null
      URL jarUrl = findJarForResource(url);
      if (jarUrl != null) {
        // 获取该jar包的输入流
        is = jarUrl.openStream();
        if (log.isDebugEnabled()) {
          log.debug("Listing " + url);
        }
        // 遍历Jar中的资源，并返回以path开头的资源列表
        resources = listResources(new JarInputStream(is), path);
      } else {  //
        // 保存url指定的jar包下所有资源的名称
        List<String> children = new ArrayList<>();
        try {
          if (isJar(url)) { // 如果是jar包
            // Some versions of JBoss VFS might give a JAR stream even if the resource
            // referenced by the URL isn't actually a JAR
            is = url.openStream();
            try (JarInputStream jarInput = new JarInputStream(is)) {
              if (log.isDebugEnabled()) {
                log.debug("Listing " + url);
              }
              for (JarEntry entry; (entry = jarInput.getNextJarEntry()) != null; ) {
                if (log.isDebugEnabled()) {
                  log.debug("Jar entry: " + entry.getName());
                }
                children.add(entry.getName());
              }
            }
          } else {  // 否则不是jar包
            /*
             * Some servlet containers allow reading from directory resources like a
             * text file, listing the child resources one per line. However, there is no
             * way to differentiate between directory and file resources just by reading
             * them. To work around that, as each line is read, try to look it up via
             * the class loader as a child of the current resource. If any line fails
             * then we assume the current resource is not a directory.
             */
            is = url.openStream();
            // 保存读取url指定资源的字符
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
              for (String line; (line = reader.readLine()) != null; ) {
                if (log.isDebugEnabled()) {
                  log.debug("Reader entry: " + line);
                }
                lines.add(line);
                if (getResources(path + "/" + line).isEmpty()) {
                  lines.clear();
                  break;
                }
              }
            }
            if (!lines.isEmpty()) {
              if (log.isDebugEnabled()) {
                log.debug("Listing " + url);
              }
              children.addAll(lines);
            }
          }
        } catch (FileNotFoundException e) {
          /*
           * For file URLs the openStream() call might fail, depending on the servlet
           * container, because directories can't be opened for reading. If that happens,
           * then list the directory directly instead.
           */
          // 判断url指定的资源是否数文件资源
          if ("file".equals(url.getProtocol())) {
            File file = new File(url.getFile());
            if (log.isDebugEnabled()) {
              log.debug("Listing directory " + file.getAbsolutePath());
            }
            if (file.isDirectory()) {
              if (log.isDebugEnabled()) {
                log.debug("Listing " + url);
              }
              children = Arrays.asList(file.list());
            }
          } else {  // 不是文件资源则抛出 FileNotFoundException 异常
            // No idea where the exception came from so rethrow it
            throw e;
          }
        }

        // The URL prefix to use when recursively listing child resources
        // 递归列出子资源时使用的URL前缀,末尾不是以"/"结尾,则添加 "/"
        String prefix = url.toExternalForm();
        if (!prefix.endsWith("/")) {
          prefix = prefix + "/";
        }

        // Iterate over immediate children, adding files and recursing into directories
        for (String child : children) {
          String resourcePath = path + "/" + child;
          resources.add(resourcePath);
          URL childUrl = new URL(prefix + child);
          resources.addAll(list(childUrl, resourcePath));
        }
      }

      return resources;
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (Exception e) {
          // Ignore
        }
      }
    }
  }

  /**
   * List the names of the entries in the given {@link JarInputStream} that begin with the
   * specified {@code path}. Entries will match with or without a leading slash.
   *
   * @param jar  The JAR input stream. jar输入流
   * @param path The leading path to match. 匹配的路径
   * @return The names of all the matching entries.
   * @throws IOException If I/O errors occur.
   */
  protected List<String> listResources(JarInputStream jar, String path) throws IOException {
    // Include the leading and trailing slash when matching names
    // 没有以"/" 开头,则添加 "/"
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    // 没有以"/" 结尾,则添加 "/"
    if (!path.endsWith("/")) {
      path = path + "/";
    }

    // Iterate over the entries and collect those that begin with the requested path
    // resources 保存以请求路径开头的资源列表
    List<String> resources = new ArrayList<>();
    for (JarEntry entry; (entry = jar.getNextJarEntry()) != null; ) {
      // 不是目录(文件)  ZipEntry是以末尾是否有无"/"来判断是文件还是目录
      if (!entry.isDirectory()) {
        // Add leading slash if it's missing
        // 获取资源文件名称
        StringBuilder name = new StringBuilder(entry.getName());
        // 如果资源文件名称不是以"/"开头则添加"/"
        if (name.charAt(0) != '/') {
          name.insert(0, '/');
        }

        // Check file name
        // 检测该资源文件名称是否在path目录下
        if (name.indexOf(path) == 0) {
          if (log.isDebugEnabled()) {
            log.debug("Found resource: " + name);
          }
          // Trim leading slash
          // 保存资源名称
          resources.add(name.substring(1));
        }
      }
    }
    return resources;
  }

  /**
   * 如果url指向的资源在一个Jar包中，则获取该Jar包对应的URL,否则返回null
   * <p>
   * Attempts to deconstruct the given URL to find a JAR file containing the resource referenced
   * by the URL. That is, assuming the URL references a JAR entry, this method will return a URL
   * that references the JAR file containing the entry. If the JAR cannot be located, then this
   * method returns null.
   *
   * @param url The URL of the JAR entry.
   * @return The URL of the JAR file, if one is found. Null if not.
   * @throws MalformedURLException
   */
  protected URL findJarForResource(URL url) throws MalformedURLException {
    if (log.isDebugEnabled()) {
      log.debug("Find JAR URL: " + url);
    }

    // If the file part of the URL is itself a URL, then that URL probably points to the JAR
    boolean continueLoop = true;
    while (continueLoop) {
      try {
        url = new URL(url.getFile());
        if (log.isDebugEnabled()) {
          log.debug("Inner URL: " + url);
        }
      } catch (MalformedURLException e) {
        // This will happen at some point and serves as a break in the loop
        continueLoop = false;
      }
    }

    // Look for the .jar extension and chop off everything after that
    StringBuilder jarUrl = new StringBuilder(url.toExternalForm());
    int index = jarUrl.lastIndexOf(".jar");
    if (index >= 0) {
      jarUrl.setLength(index + 4);
      if (log.isDebugEnabled()) {
        log.debug("Extracted JAR URL: " + jarUrl);
      }
    } else {
      if (log.isDebugEnabled()) {
        log.debug("Not a JAR: " + jarUrl);
      }
      return null;
    }

    // Try to open and test it
    try {
      URL testUrl = new URL(jarUrl.toString());
      if (isJar(testUrl)) {
        return testUrl;
      } else {
        // WebLogic fix: check if the URL's file exists in the filesystem.
        if (log.isDebugEnabled()) {
          log.debug("Not a JAR: " + jarUrl);
        }
        jarUrl.replace(0, jarUrl.length(), testUrl.getFile());
        File file = new File(jarUrl.toString());

        // File name might be URL-encoded
        if (!file.exists()) {
          try {
            file = new File(URLEncoder.encode(jarUrl.toString(), "UTF-8"));
          } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unsupported encoding?  UTF-8?  That's unpossible.");
          }
        }

        if (file.exists()) {
          if (log.isDebugEnabled()) {
            log.debug("Trying real file: " + file.getAbsolutePath());
          }
          testUrl = file.toURI().toURL();
          if (isJar(testUrl)) {
            return testUrl;
          }
        }
      }
    } catch (MalformedURLException e) {
      log.warn("Invalid JAR URL: " + jarUrl);
    }

    if (log.isDebugEnabled()) {
      log.debug("Not a JAR: " + jarUrl);
    }
    return null;
  }

  /**
   * Converts a Java package name to a path that can be looked up with a call to
   * {@link ClassLoader#getResources(String)}.
   *
   * @param packageName The Java package name to convert to a path
   */
  protected String getPackagePath(String packageName) {
    return packageName == null ? null : packageName.replace('.', '/');
  }

  /**
   * Returns true if the resource located at the given URL is a JAR file.
   *
   * @param url The URL of the resource to test.
   */
  protected boolean isJar(URL url) {
    return isJar(url, new byte[JAR_MAGIC.length]);
  }

  /**
   * Returns true if the resource located at the given URL is a JAR file.
   *
   * @param url    The URL of the resource to test.
   * @param buffer A buffer into which the first few bytes of the resource are read. The buffer
   *               must be at least the size of {@link #JAR_MAGIC}. (The same buffer may be reused
   *               for multiple calls as an optimization.)
   */
  protected boolean isJar(URL url, byte[] buffer) {
    InputStream is = null;
    try {
      // 获取url指向的资源的输入流
      is = url.openStream();
      // 读取该资源的魔法值
      is.read(buffer, 0, JAR_MAGIC.length);
      if (Arrays.equals(buffer, JAR_MAGIC)) {
        if (log.isDebugEnabled()) {
          log.debug("Found JAR: " + url);
        }
        return true;
      }
    } catch (Exception e) {
      // Failure to read the stream means this is not a JAR
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (Exception e) {
          // Ignore
        }
      }
    }

    return false;
  }
}
