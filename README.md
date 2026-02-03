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

## 命令行参数详解

### record 命令 - 录制流量

**语法**：
```bash
flowreplay record --port <port> --target <host:port> --output <path> [选项]
```

**必需参数**：
- `--port <port>` - 代理服务器监听端口
- `--target <host:port>` - 目标服务器地址（格式：host:port）
- `--output <path>` - 录制数据存储路径

**可选参数**：
- `--protocol <http|tcp>` - 协议类型，默认为 `http`
  - `http` - HTTP/HTTPS 协议
  - `tcp` - TCP/Socket 协议
- `--protocol-parser <parser>` - TCP 协议解析器，默认为 `raw`
  - `raw` - 原始字节流（不解析）
  - `redis` - Redis 协议（待实现）
  - `mysql` - MySQL 协议（待实现）

**示例**：
```bash
# HTTP 录制
flowreplay record --port 8080 --target localhost:8080 --output ./recordings

# TCP 原始模式录制
flowreplay record --protocol tcp --port 9999 --target localhost:9999 --output ./recordings-tcp --protocol-parser raw
```

### replay 命令 - 回放流量

**语法**：
```bash
flowreplay replay --input <path> --target <url> [选项]
```

**必需参数**：
- `--input <path>` - 录制数据存储路径
- `--target <url>` - 目标服务器地址
  - HTTP 协议：`http://host:port` 或 `https://host:port`
  - TCP 协议：`host:port`（不需要协议前缀）

**可选参数**：
- `--compare` - 启用响应比对功能
- `--report <path>` - HTML 报告输出路径（需要配合 `--compare` 使用）
- `--config <path>` - 自定义比对规则配置文件路径（YAML 格式）

**示例**：
```bash
# 基础 HTTP 回放
flowreplay replay --input ./recordings --target http://localhost:9090

# TCP 回放并比对
flowreplay replay --input ./recordings-tcp --target 192.168.1.100:8080 --compare --report ./report.html

# 使用自定义比对规则
flowreplay replay --input ./recordings --target http://localhost:9090 --compare --report ./report.html --config ./rules.yaml
```

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

## 配置项说明

### 1. 协议类型配置

**参数**：`--protocol <http|tcp>`

**说明**：指定录制的协议类型。

**可选值**：
- `http`（默认）- HTTP/HTTPS 协议
- `tcp` - TCP/Socket 协议

**示例**：
```bash
# HTTP 协议（默认）
flowreplay record --port 8080 --target localhost:8080 --output ./recordings

# TCP 协议
flowreplay record --protocol tcp --port 9999 --target localhost:9999 --output ./recordings-tcp
```

### 2. 协议解析器配置

**参数**：`--protocol-parser <parser>`

**说明**：指定 TCP 协议的解析器类型。

**可选值**：
- `raw`（默认）- 原始字节流，不进行协议解析
- `redis` - Redis 协议（待实现）
- `mysql` - MySQL 协议（待实现）

**示例**：
```bash
# 原始模式
flowreplay record --protocol tcp --port 9999 --target localhost:9999 --output ./recordings --protocol-parser raw

# Redis 协议（待实现）
flowreplay record --protocol tcp --port 6380 --target localhost:6379 --output ./recordings --protocol-parser redis
```

## 比对策略

### 内置比对策略详解

#### 1. ExactMatchStrategy - 完全匹配策略

**策略类型**：`exact-match`

**说明**：对响应进行字节级完全匹配，包括状态码、响应头和响应体。

**适用场景**：
- TCP/Socket 协议（系统自动选择）
- 静态资源（CSS、JS、图片等）
- 需要严格一致性验证的场景

**配置示例**：
```yaml
strategies:
  - type: "exact-match"
```

**比对规则**：
- 状态码必须完全相同
- 响应体字节数组必须完全相同
- 任何差异都会导致比对失败

---

#### 2. HttpStatusStrategy - HTTP状态码策略

**策略类型**：`http-status`

**说明**：仅比对HTTP响应状态码，忽略响应头和响应体。

**适用场景**：
- 快速验证接口可用性
- 响应体内容动态变化但状态码稳定的场景
- 性能测试场景

**配置示例**：
```yaml
strategies:
  - type: "http-status"
```

**比对规则**：
- 仅比较 statusCode 字段
- 200 vs 200 → 匹配
- 200 vs 404 → 不匹配

---

#### 3. JsonStructureStrategy - JSON结构化策略

**策略类型**：`json-structure`

**说明**：对JSON响应进行结构化比对，支持忽略动态字段、数组顺序等高级配置。

**适用场景**：
- RESTful API 接口
- 响应中包含时间戳、UUID等动态字段
- 需要灵活比对规则的场景

**配置示例**：
```yaml
strategies:
  - type: "json-structure"
    config:
      ignoreFields:
        - "timestamp"
        - "requestId"
        - "traceId"
        - "data.*.id"          # 忽略data数组中所有元素的id字段
        - "**.createdAt"       # 忽略所有层级的createdAt字段
      ignoreArrayOrder: false  # 是否忽略数组元素顺序
```

**配置参数**：
- `ignoreFields`：忽略的字段列表，支持JSONPath表达式
  - 简单字段：`"timestamp"`
  - 嵌套字段：`"data.user.id"`
  - 数组元素：`"items.*.id"`
  - 所有层级：`"**.timestamp"`
- `ignoreArrayOrder`：是否忽略数组元素顺序
  - `false`（默认）：数组顺序必须一致
  - `true`：数组顺序可以不同，只要元素相同即可

**比对规则**：
- 自动检测响应是否为JSON格式（Content-Type或首字节）
- 非JSON响应会跳过此策略
- 递归比对JSON结构
- 忽略配置中指定的字段
- 根据配置决定是否忽略数组顺序

**示例**：
```json
// 录制的响应
{
  "code": 200,
  "timestamp": "2026-02-03T10:00:00",
  "data": {
    "userId": "123",
    "name": "张三"
  }
}

// 回放的响应
{
  "code": 200,
  "timestamp": "2026-02-03T11:00:00",  // 时间不同
  "data": {
    "userId": "123",
    "name": "张三"
  }
}

// 配置忽略 timestamp 后 → 匹配成功
```

---

### 自定义比对策略

可通过实现`ComparisonStrategy`接口自定义比对策略：

```java
public interface ComparisonStrategy {
    ComparisonResult compare(ResponseData recorded, ResponseData replayed);
}
```

**实现步骤**：
1. 实现 `ComparisonStrategy` 接口
2. 在 `Comparator` 类中注册策略
3. 在配置文件中使用自定义策略类型

### 智能策略选择

系统会根据协议类型自动选择合适的比对策略：

- **HTTP 协议**：使用配置文件中定义的策略（默认：http-status + json-structure）
- **TCP/Socket 协议**：自动使用 exact-match（完全匹配）策略
- **兜底机制**：如果所有策略都被跳过，自动使用 exact-match 策略

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

#### 报告结构

**1. 统计摘要**
- 总请求数
- 匹配成功数
- 匹配失败数
- 成功率百分比

**2. 详细差异列表**

每个请求包含以下信息：
- **请求ID**：唯一标识符
- **协议类型**：HTTP、SOCKET等
- **URI**：请求路径
- **方法**：GET、POST、raw等
- **匹配状态**：✓ 匹配 / ✗ 不匹配（带颜色标识）

**3. 可折叠内容区域**

- **原始请求**：显示请求头和请求体
- **响应对比**：并排显示录制的响应和回放的响应
  - 左侧：录制的响应（期望值）- 绿色背景
  - 右侧：回放的响应（实际值）- 红色背景
  - 包含状态码、响应头、响应体

**4. 交互功能**

- **展开全部**按钮：一键展开所有请求详情
- **折叠全部**按钮：一键折叠所有请求详情
- 点击标题可单独展开/折叠某个请求

#### 报告特性

- ✅ 美观的HTML格式，带颜色标识
- ✅ 响应式设计，支持移动端查看
- ✅ 差异高亮显示
- ✅ 可折叠内容，便于浏览大量数据
- ✅ 并排对比视图，快速定位差异
- ✅ 支持二进制数据显示（十六进制）

#### 报告示例

```bash
# 生成报告
flowreplay replay --input ./recordings --target http://localhost:9090 \
  --compare --report ./report.html

# 在浏览器中打开报告
open ./report.html  # Mac
start ./report.html # Windows
xdg-open ./report.html # Linux
```

## 常见问题与故障排查

### 1. HTTP代理无响应

**问题描述**：通过代理端口访问时，请求没有返回响应。

**可能原因**：
- 目标服务器未启动或地址错误
- 网络连接问题
- 代理服务器异常

**解决方案**：
```bash
# 1. 检查目标服务器是否可访问
curl http://localhost:8080/api/test

# 2. 检查代理日志，查看是否有错误信息

# 3. 确认代理配置正确
flowreplay record --port 8081 --target localhost:8080 --output ./recordings
```

---

### 2. TCP回放时响应不匹配

**问题描述**：Socket协议回放时，请求和响应对不上，或者完全不同的响应被标记为匹配。

**可能原因**：
- 并发回放导致响应顺序错乱（已修复）
- 使用了不适合Socket的比对策略

**解决方案**：
- 系统已自动为Socket/TCP协议使用`exact-match`策略
- 如果仍有问题，检查录制数据是否完整

---

### 3. JSON比对失败（动态字段）

**问题描述**：JSON响应中包含时间戳、UUID等动态字段，导致每次比对都失败。

**解决方案**：
创建`comparison-rules.yaml`配置文件，忽略动态字段：
```yaml
rules:
  - name: "API接口"
    urlPattern: "/api/.*"
    strategies:
      - type: "json-structure"
        config:
          ignoreFields:
            - "timestamp"
            - "requestId"
            - "traceId"
            - "**.createdAt"  # 忽略所有层级的createdAt
```

然后使用配置文件回放：
```bash
flowreplay replay --input ./recordings --target http://localhost:9090 \
  --compare --report ./report.html --config ./comparison-rules.yaml
```

---

### 4. 并发回放时响应顺序错乱

**问题描述**：回放多个请求时，A请求的响应变成了B请求的响应。

**解决方案**：
此问题已在最新版本中修复。系统使用固定大小数组和索引对应关系，确保响应顺序正确。

如果仍有问题，请检查：
- 是否使用最新版本
- 目标服务器是否正常处理并发请求

---

### 5. 存储空间不足

**问题描述**：录制大量流量后，磁盘空间不足。

**解决方案**：
- 使用采样策略（待实现）
- 定期清理旧的录制数据
- 只录制关键接口

---

### 6. HTTPS流量无法录制

**问题描述**：HTTPS流量无法通过代理录制。

**解决方案**：
HTTPS支持（MITM代理）功能待实现。当前版本仅支持HTTP和TCP协议。

**临时方案**：
- 在测试环境使用HTTP协议
- 或在应用层集成录制逻辑

---

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

