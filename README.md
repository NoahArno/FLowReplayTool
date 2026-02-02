# FlowReplay - 流量录制和回放工具

基于Java 21+开发的轻量级流量录制和回放工具，支持HTTP、Socket、WebService等协议。

## 特性

- ✅ **轻量级架构**：最小依赖，开箱即用
- ✅ **多协议支持**：HTTP/HTTPS、TCP/Socket、WebService
- ✅ **高性能**：使用Java 21 Virtual Threads，支持高并发
- ✅ **强大的比对引擎**：支持多种比对策略，高度可扩展
- ✅ **语言无关**：代理模式，适用于多语言系统

## 项目结构

```
flowreplay/
├── flowreplay-core/      # 核心模块（数据模型、存储、比对、回放）
├── flowreplay-proxy/     # 代理服务器（HTTP/TCP代理）
├── flowreplay-cli/       # 命令行工具
└── README.md
```

## 快速开始

### 1. 构建项目

```bash
mvn clean package
```

构建完成后，会在`flowreplay-cli/target/`目录下生成两个jar文件：
- `flowreplay-cli-1.0.0-SNAPSHOT.jar`：普通jar（不含依赖）
- `flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar`：fat jar（包含所有依赖）

**使用fat jar运行**（推荐）：
```bash
java -jar flowreplay-cli/target/flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

**或使用启动脚本**：
```bash
# Linux/Mac
./flowreplay.sh

# Windows
flowreplay.bat
```

### 2. 录制流量

启动HTTP代理服务器，录制流量到本地文件：

```bash
# 使用jar文件
java -jar flowreplay-cli/target/flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar record \
  --port 8080 \
  --target localhost:8080 \
  --output ./recordings

# 或使用启动脚本
./flowreplay.sh record --port 8080 --target localhost:8080 --output ./recordings
```

启动TCP代理服务器，录制Socket流量：

```bash
# Redis协议示例
java -jar flowreplay-cli/target/flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar record \
  --protocol tcp \
  --port 6380 \
  --target localhost:6379 \
  --output ./recordings \
  --protocol-parser redis

# 原始模式（录制字节流）
java -jar flowreplay-cli/target/flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar record \
  --protocol tcp \
  --port 9999 \
  --target localhost:9999 \
  --output ./recordings \
  --protocol-parser raw
```

### 3. 回放流量

将录制的流量回放到新系统：

**基础HTTP回放**：
```bash
java -jar flowreplay-cli/target/flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar replay \
  --input ./recordings \
  --target http://localhost:9090
```

**TCP回放**：
```bash
# 注意：TCP回放的target不需要http://前缀，直接使用host:port格式
java -jar flowreplay-cli/target/flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar replay \
  --input ./recordings \
  --target 192.168.109.180:9090
```

### 4. 回放并比对

回放流量并自动比对结果，生成HTML差异报告：

**使用默认比对规则**：
```bash
java -jar flowreplay-cli/target/flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar replay \
  --input ./recordings \
  --target http://localhost:9090 \
  --compare \
  --report ./report.html
```

**使用自定义比对规则**：
```bash
java -jar flowreplay-cli/target/flowreplay-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar replay \
  --input ./recordings \
  --target http://localhost:9090 \
  --compare \
  --report ./report.html \
  --config ./comparison-rules.yaml
```

比对完成后会输出：
- 总请求数
- 匹配成功数
- 匹配失败数
- 成功率
- HTML报告路径

## 核心模块说明

### flowreplay-core

核心功能模块，包含：

- **数据模型**：TrafficRecord、RequestData、ResponseData等
- **存储层**：TrafficStorage接口和FileStorage实现
- **比对引擎**：ComparisonStrategy接口和多种比对策略
- **回放引擎**：TrafficReplayer，使用Virtual Threads并发回放

### flowreplay-proxy

代理服务器模块，基于Netty实现：

- **HttpProxyServer**：HTTP代理服务器
- **HttpProxyHandler**：HTTP请求处理器，负责转发和录制

### flowreplay-cli

命令行工具，提供record、replay、compare命令。

## 技术栈

- **Java 21**：使用Virtual Threads提升并发性能
- **Netty**：高性能网络框架
- **Jackson**：JSON序列化/反序列化
- **Maven**：项目构建工具

## 比对策略

内置多种比对策略：

1. **ExactMatchStrategy**：完全匹配（字节级）
2. **HttpStatusStrategy**：仅比对HTTP状态码
3. **JsonStructureStrategy**：JSON结构化比对，支持字段忽略

可通过实现`ComparisonStrategy`接口自定义比对策略。

### 比对规则配置

创建`comparison-rules.yaml`文件来配置比对规则：

```yaml
rules:
  # API接口比对规则
  - name: "API接口比对"
    urlPattern: "/api/.*"
    strategies:
      - type: "http-status"
      - type: "json-structure"
        config:
          ignoreFields:
            - "timestamp"
            - "requestId"
            - "traceId"
            - "serverTime"
          ignoreArrayOrder: false

  # 静态资源完全匹配
  - name: "静态资源比对"
    urlPattern: "/static/.*"
    strategies:
      - type: "exact-match"

  # 默认规则（仅比对HTTP状态码）
  - name: "默认规则"
    urlPattern: ".*"
    strategies:
      - type: "http-status"
```

**配置说明**：
- `urlPattern`：URL匹配模式（正则表达式）
- `strategies`：比对策略列表，按顺序执行
- `ignoreFields`：JSON比对时忽略的字段列表
- `ignoreArrayOrder`：是否忽略数组元素顺序

### HTML差异报告

使用`--report`参数生成HTML差异报告，报告包含：

- **统计摘要**：总请求数、匹配成功数、匹配失败数、成功率
- **详细差异列表**：每个请求的比对结果
  - 请求URI和方法
  - 匹配状态（✓ 匹配 / ✗ 不匹配）
  - 差异详情（路径、期望值、实际值）

报告采用美观的HTML格式，带有颜色标识和样式，便于快速定位问题。

## 开发计划

### 已完成 ✅
- 核心数据模型
- 文件存储实现
- HTTP代理服务器
- TCP代理服务器（支持Socket协议）
- 流量回放引擎（使用Virtual Threads）
- 基础比对策略（完全匹配、HTTP状态码、JSON结构化）
- 配置化比对规则（YAML配置文件）
- HTML差异报告生成器
- 命令行工具（record、replay、compare）

### 待实现 🚧
- HTTPS支持（MITM代理）
- WebService支持
- 协议解析器SPI（Redis、MySQL等）
- 数据库存储
- 采样策略
- 数据脱敏
- 性能指标比对
- Web管理界面

## 许可证

MIT License

## 贡献

欢迎提交Issue和Pull Request！

