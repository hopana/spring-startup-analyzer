# 1. 简介

随着业务的发展，应用中引入的jar包越来越多，一些应用运行的fatjar有200多M，启动时间维持在6-7分钟左右，严重影响对线上问题的响应速度，同时也严重影响着研发效率。急需进行应用启动时长的优化。这篇文章《[一些可以显著提高 Java 启动速度方法](https://heapdump.cn/article/4136322)》提供了一个非常好的思路，优化效果很明显。结合这篇文章提供的思路，实现了这个项目。**无观测不优化**，本项目实现对应用启动整体过程的观测，主要包含以下能力：

<details open>
  <summary style='cursor: pointer'><strong>UI首页</strong></summary>

  ![](./docs/home-ui.jpg)
</details>

<details>
  <summary style='cursor: pointer'><strong>Spring bean加载耗时timeline可视化分析</strong></summary>

  ![](./docs/spring-bean-timeline.jpg)
</details>

<details>
  <summary style='cursor: pointer'><strong>调用链路跟踪</strong></summary>

  ![](./docs/invoke-tracer.jpg)
</details>

<details>
  <summary style='cursor: pointer'><strong>应用启动过程线程wall clock火焰图(支持指定线程名称，不指定则采集全部线程)</strong></summary>

![](./docs/flame-graph.jpg)
</details>

<details>
  <summary style='cursor: pointer'><strong>各个Bean加载耗时</strong></summary>

![](./docs/details-of-bean.jpg)
</details>

<details>
  <summary style='cursor: pointer'><strong>方法调用次数、耗时统计(支持自定义方法)</strong></summary>

  ![](./docs/details-of-invoke.jpg)
</details>

<details>
  <summary style='cursor: pointer'><strong>应用未加载的jar包(帮助fatjar瘦身)</strong></summary>

  ![](./docs/unused-jar.jpg)

  <strong>&emsp;需要注意的是: 有一些jar可能会在运行时加载，要删除启动时没有加载的jar包，需要做好测试，以免线上出现ClassNotFoundException</strong>
</details>

<details open>
  <summary style='cursor: pointer'><strong>支持针对方法/类/包维度的自定义扩展</strong></summary>
    &emsp;&emsp;系统预留了扩展接口，可以通过实现接口完成自定义功能扩展，<a href="#25-自定义扩展">详情查看</a>
</details>

# 2. 使用

因为项目需要对Spring Bean初始化时序及调用关系的可视化，选择了将数据上报到[jaeger](https://www.jaegertracing.io/)，由jaeger ui进行展示，所以需要本地启动jaeger。

## 2.1. 启动jaeger

```shell
docker run -d \
--name jaeger  \
-e COLLECTOR_ZIPKIN_HOST_PORT=:9411  \
-e COLLECTOR_OTLP_ENABLED=true  \
-p 6831:6831/udp  \
-p 6832:6832/udp  \
-p 5778:5778 \
-p 16686:16686 \
-p 4317:4317 \
-p 4318:4318 \
-p 14250:14250  \
-p 14268:14268 \
-p 14269:14269 \ 
-p 9411:9411 \
linyimin520812/all-in-one:v1.30.3
```

访问[http://127.0.0.1:16686](http://127.0.0.1:16686)成功即说明jaeger已启动完成。

## 2.2. 安装jar包

### 2.2.1 手动安装

1. 点击[realease](https://github.com/linyimin-bupt/java-profiler-boost/releases/download/v1.0.0/java-profiler-boost.tar.gz)下载最新版tar.gz包
2. 新建文件夹，并解压

```shell
mkdir -p ${HOME}/java-profiler-boost
cd 下载路径
tar -zxvf java-profiler-boost.tar.gz ${HOME}/java-profiler-boost
```

### 2.2.2 脚本安装

```shell
curl -sS https://raw.githubusercontent.com/linyimin-bupt/java-profiler-boost/main/bin/setup.sh | sh
```

## 2.3 配置项

配置项在`$HOME/java-profiler/config/java-profiler.properties`文件中指定，请务必配置`java-profiler.app.status.check.endpoints`选项，不然会一直采集直到应用启动检查超时(默认20分钟)才会停止，每隔1秒请求一次endpoint，请求响应头状态码为200则认为应用启动完成。

| 配置项                                               | 说明                                                      | 默认值                       |
| ---------------------------------------------------- | --------------------------------------------------------- | ---------------------------- |
| java-profiler.app.status.check.timeout               | 应用启动检查超时时间，单位为分钟                          | 20                           |
| **java-profiler.app.status.check.endpoints**         | 应用启动成功检查url，可配置多个，以","分隔                | http://127.0.0.1:8080/actuator/health |
| java-profiler.jaeger.grpc.export.endpoint            | jaeger的export endpoint                                   | http://localhost:14250       |
| java-profiler.jaeger.ui.endpoint                     | jaeger的UI endpoint                                       | http://localhost:16686       |
| java-profiler.invoke.chain.packages                  | 进行调用trace的包名，支持配置多个，以","进行分隔          | main方法类所处的包名         |
| java-profiler.jaeger.span.min.sample.duration.millis | Jaeger span的最小导出时间(ms)                             | 10                           |
| java-profiler.admin.http.server.port                 | 管理端口                                                  | 8065                         |
| java-profiler.async.profiler.sample.thread.names     | async profiler采集的线程名称，支持配置多个，以","进行分隔 | main                         |
| **java-profiler.async.profiler.interval.millis**     | async profiler采集间隔时间(ms)                            | 5                            |
| java-profiler.spring.bean.init.min.millis            | statistics中展示Bean的最小时间(ms)                        | 100                          |

## 2.4 应用启动

此项目是以agent的方式启动的，所以在启动命令中添加参数`-javaagent:$HOME/java-profiler-boost/lib/java-profiler-agent.jar`即可。如果是以java命令行的方式启动应用，则在命令行中添加，如果是在IDEA中启动，则需要在VM options选项中添加。

日志文件路径：`$HOME/java-profiler-boost/logs`

- startup.log: 启动过程中的日志
- transform.log: 被re-transform的类/方法信息

应用启动完成后会在console和startup.log文件中输出`======= java-profiler-boost stop, click %s to view detailed info about the startup process ======`，可以通过此输出来判断采集是否完成。

## 2.5 自定义扩展

如果需要自定义观测能力，需要引入`java-profiler-starter`的pom作为扩展项目的父pom，然后就可以使用项目对外暴露的接口进行扩展。更多的细节可以参考[java-profiler-extension](https://github.com/linyimin-bupt/java-profiler-boost/tree/main/java-profiler-extension)的实现

```xml
<parent>
    <groupId>com.github.linyimin</groupId>
    <artifactId>java-profiler-starter</artifactId>
    <version>1.0.10-SNAPSHOT</version>
</parent>
```

### 2.5.1 扩展接口

<details>
<summary style='cursor: pointer'>com.github.linyimin.profiler.api.EventListener</summary>

```java
public interface EventListener extends Startable {

    /**
     * 应用启动时调用
     */
    void start();

    /**
     * 应用启动完成后调用
     */
    void stop();
    
    /**
     * 需要增强的类
     * @param className 类全限定名, 如果为空, 默认返回为true

     * @return true: 进行增强, false: 不进行增强
     */
    boolean filter(String className);

    /**
     * 需要增强的方法(此方法会依赖filter(className), 只有filter(className)返回true时，才会执行到此方法)
     * @param methodName 方法名
     * @param methodTypes 方法参数列表
     * @return true: 进行增强, false: 不进行增强
     */
    default boolean filter(String methodName, String[] methodTypes) {
        return true;
    }

    /**
     * 事件响应处理逻辑
     * @param event 触发的事件
     */
    void onEvent(Event event);

    /**
     * 监听的事件
     * @return 需要监听的事件列表
     */
    List<Event.Type> listen();

}
```
</details>

其中`start()和stop()`方法代表系统的生命周期，分别在应用开始启动和应用启动完成时调用。`filter()`方法指定需要增强的类/方法。`listen()`方法指定监听的事件，包括`进入方法`和`方法返回`两种事件。`onEvent()`方法在监听的事件发生时会被调用

例如下面是一个统计应用启动过程中java.net.URLClassLoader.findResource(String)方法调用次数的扩展

<details>
    <summary style='cursor: pointer'>FindResourceCounter demo</summary>

```java
@MetaInfServices
public class FindResourceCounter implements EventListener {

    private final AtomicLong COUNT = new AtomicLong(0);

    @Override
    public boolean filter(String className) {
        return "java.net.URLClassLoader".equals(className);
    }

    @Override
    public boolean filter(String methodName, String[] methodTypes) {
       if (!"findResource".equals(methodName)) {
           return false;
       }

       return methodTypes != null && methodTypes.length == 1 && "java.lang.String".equals(methodTypes[0]);
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof AtEnterEvent) {
            // 开始进入findResource方法
        } else if (event instanceof AtExitEvent) {
            // findResource方法返回
        }

        // 统计调用次数
        COUNT.incrementAndGet();

    }

    @Override
    public List<Event.Type> listen() {
        return Arrays.asList(Event.Type.AT_ENTER, Event.Type.AT_EXIT);
    }

    @Override
    public void start() {
        System.out.println("============== my extension start =============");
    }

    @Override
    public void stop() {
        System.out.println("============== my extension end =============");
        System.out.println("findResource count: " + COUNT.get());
    }
}
```
</details>

需要注意**EventListener接口的实现需要使用@MetaInfServices标识**，因为扩展的接口是通过SPI进行加载的，使用`@MetaInfServices`标识后，在代码编译时会自动将实现类写入META-INF/services/com.github.linyimin.profiler.api.EventListener文件中。如果没有使用`@MetaInfServices`标识，需要手动将实现类的全限定名写入META-INF/services/com.github.linyimin.profiler.api.EventListener文件中，否则将加载不到此扩展实现。

### 2.5.2 UI扩展接口

在实现对某个类/方法的扩展后，如果需要将统计数据同步到jaeger-ui展示，可以使用相关的UI接口。本项目提供了2种接口：

**1. 如果需要展示调用关系，可以使用jaeger tracer接口**

<details>
    <summary style='cursor: pointer'>UI样式</summary>

![](./docs/home-ui.jpg)
</details>

```java
Jaeger jaeger = new Jaeger();
jaeger.start();

Tracer tracer = jaeger.createTracer("xxx-tracer");

Span span = tracer.spanBuilder("xxx-span").startSpan();

try (Scope scope = span.makeCurrent()) {

} finally {
span.end();
}

jaeger.stop();
```

**2. markdown content接口**

<details>
    <summary style='cursor: pointer'>UI样式</summary>

![](./docs/markdown-content.jpg)

</details>

```java
// 写入markdown内容，默认order为100，order越小，显示越靠前
MarkdownWriter.write(String content);
// 指定显示order
MarkdownWriter.write(int order, String content);
```

**3. markdown statistics接口**

<details>
    <summary style='cursor: pointer'>UI样式</summary>

![](./docs/markdown-statistics.jpg)
</details>

```java
// 写入markdown统计数值，默认order为100，order越小，显示越靠前
MarkdownStatistics.write(String label, String value);
// 指定显示order
MarkdownStatistics.write(int order, String label, String value);
```


### 2.5.3 打包运行

在`java-profiler-starter`的pom中已经定义了打包plugin，默认会将生成的jar包拷贝到`$HOME/java-profiler-boost/extension`文件下。

```shell
mvn clean package
```

只要按照步骤[安装jar包](#2-2-安装jar包)安装好此项目，再执行上述的打包命令，打包好后再[启动应用](#2-4-应用启动)即可加载扩展jar包。

# 3. 后续计划

目前已完成应用启动过程的观测，可以知道应用启动过程中的卡点。所以接下来需要针对一些常见的卡点提供一套解决方案，比如：

- [ ] Jar Index

- [ ] Spring bean异步加载

- [ ] 通用的优化方案

# 4. 实现原理



# 5. 为项目添砖加瓦

欢迎提出 [issues](https://github.com/linyimin-bupt/java-profiler-boost/issues) 与 [pull requests](https://github.com/linyimin-bupt/java-profiler-boost/pulls)!。

# 6. 🙏感谢支持

如果这个项目对你产生了一点的帮助，请为这个项目点上一颗 ⭐️
