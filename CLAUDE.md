# CLAUDE.md

## 构建与运行

### 后端 (Java/Spring Boot)
```bash
# 构建（使用本地 Maven 3.6.3 + 自定义仓库）
mvn clean package -DskipTests -f pom.xml
# 运行
mvn spring-boot:run -f pom.xml
# 运行测试
mvn test -f pom.xml
# 运行单个测试类
mvn test -Dtest=WordToPdfConverterTest -f pom.xml
```


### 前端 (React/TypeScript)
```bash
cd frontend
npm run dev      # 开发服务器（端口 3000，/api 代理到 localhost:8080）
npm run build    # 生产构建
```

## 架构

### 后端 — Spring Boot 3.3.5 + Java 17

**包结构** `com.file.conversion`:

- `controller/` — REST 端点
  - `ChatController` — SSE 流式聊天 (`POST /api/chat/stream`)，接收用户消息和文件 ID 列表
  - `FileController` — 文件上传/下载/预览/删除 (`/api/files/*`)
- `service/`
  - `AiChatService` — 包装 Spring AI `ChatClient` + 工具调用 (Function Calling)，SSE 流式响应
  - `ConversionService` — 单文件转换：从 `ConverterRegistry` 查找转换器，注册输出到 `FileStorageService`
  - `BatchConversionService` — 批量转换 + ZIP 打包
  - `FileStorageService` — 基于 `ConcurrentHashMap` 的内存文件注册表，文件过期管理，磁盘 I/O
- `converter/` — 可插拔转换引擎
  - `Converter` 接口: `supportedFormats()` + `convert(File, targetFormat)`
  - `ConverterRegistry` — 通过 DI 自动发现所有 `Converter` bean，映射 `FormatPair(源格式, 目标格式)` → 转换器
  - 实现类: `WordToPdfConverter`, `WordToTxtConverter`, `MarkdownToPdfConverter`, `MdToHtmlConverter`, `HtmlToPdfConverter`, `ExcelToJsonConverter`, `ExcelToCsvConverter`, `CsvToJsonConverter`, `JsonToCsvConverter`, `PngToJpgConverter`
- `tools/`
  - `ConversionTools` — Spring AI `@Tool` 注解方法，暴露给 AI 调用: `convertSingleFile`, `batchConvertAndZip`, `getSupportedConversions`
- `model/` — POJO: `ConversionResult`, `FileInfo`, `ChatRequest`
- `util/` — `ZipUtils`, `ChineseFontUtils`（跨平台中文字体发现，支持 macOS/Linux/Windows）
- `task/` — `FileCleanupTask`（定时清理过期文件，默认 5 分钟）
- `config/` — `WebConfig`（CORS 配置，作用于 `/api/**`）

**核心数据流:** 用户上传文件 → `FileController.store` 保存到磁盘，返回带 `id` 的 `FileInfo` → 用户发送聊天消息附带文件 ID 列表 → `AiChatService` 构建 system prompt 列出已上传文件 → AI 决定调用 `convertSingleFile(fileId, targetFormat)` → `ConversionService` 按扩展名→目标格式查找转换器 → 转换器生成输出文件 → 输出注册到 `FileStorageService` → 返回下载 URL → AI 响应下载链接。

**技术栈:** Spring AI (OpenAI 兼容/DeepSeek，通过 `spring-ai-starter-model-openai`), Apache POI 5.3.0 (Word & Excel), iText 7 (PDF 生成), flexmark 0.64.8 (Markdown 解析), openhtmltopdf 1.0.10 (HTML→PDF), Project Lombok。

### 前端 — React 18 + TypeScript + Vite 6

- `App.tsx` — 根组件，管理已上传文件列表、聊天消息、SSE 流式通信（通过 AbortController）
- `components/ChatUI.tsx` — 消息展示（含下载卡片，通过 `/api/files/download/` URL 匹配触发），加载状态，空状态
- `components/FileUploader.tsx` — 拖拽 + 点击上传，显示上传中状态
- `services/api.ts` — `uploadFiles` (multipart POST), `chatStream` (基于 fetch 的 SSE 解析，处理 `data:content\n\n` 格式及 JSON 引号去除)

**Vite 开发服务器** 运行在端口 3000，将 `/api` 代理到 `http://localhost:8080`。

### 支持的转换类型
| 源格式 | 目标格式 |
|--------|----------|
| Word (.doc/.docx) | PDF, TXT |
| Markdown (.md) | PDF, HTML |
| Excel (.xls/.xlsx) | JSON, CSV |
| CSV | JSON |
| JSON | CSV |
| HTML/HTM | PDF |
| PNG | JPG/JPEG |

### 文件生命周期
- 上传文件存储在 `uploads/` 目录（UUID 前缀保存）
- 元数据保存在内存中（`ConcurrentHashMap`），带过期时间戳
- 定时任务每 5 分钟清理过期文件（通过 `app.file.cleanup-interval-ms` 配置）
- 默认 TTL: 60 分钟（`app.file.expiration-minutes`）
