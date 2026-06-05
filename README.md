# enterprise-ai-blueprint

**企业级 AI 应用工程化参考实现** · *Enterprise AI Application Engineering Blueprint*

> Built with Spring AI · 多模型聚合 · Prompt 即代码 · Docker 一键启动

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.6-6db33f.svg)](https://spring.io/projects/spring-ai)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)

---

## 这是什么

一个**可直接运行**的企业级 AI 应用工程化参考实现。用一个"企业知识库 RAG 问答"场景作为载体，演示生产环境 AI 应用应该具备的工程能力：

- 🔌 **多模型聚合**：chat 走 MiniMax、embedding 走 DashScope，纯 YAML 配置即可切换厂商
- 📝 **Prompt 即代码**：模板带 frontmatter 元数据、版本化管理、灰度切换
- 🧩 **RAG 工程化**：知识切片 + 向量检索 + Prompt 注入 + 思考块剥离，完整链路
- 🌍 **多环境配置**：dev / prod profile 自动切换 prompt 版本（灰度发布的最朴素实现）
- 🐳 **生产级 Docker**：multi-stage 构建、非 root 用户、健康检查、Maven 缓存层
- 📊 **可观测性**：每次 LLM 调用的 prompt 全文 + 变量 + token + 折算成本 + 耗时全程留痕，`/api/traces` 可查、可聚合
- 🖥️ **演示控制台**：零构建静态单页（`http://localhost:8080/`），RAG 问答 + Prompt v1/v2 对比 + 可观测性面板 + preset 一页看全

## 这不是什么

- ❌ 不是 RAG 入门教程（如果你刚接触 RAG，看 [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/) 更合适）
- ❌ 不是开箱即用的 SaaS 产品
- ❌ 不是 AI 网关（用 [OneAPI](https://github.com/songquanpeng/one-api) / [LiteLLM](https://github.com/BerriAI/litellm) 更对路）

**它是**：一份"我做企业 AI 落地时，希望一开始就有的脚手架"。

---

## 5 分钟 Quickstart

### 前置

- Docker Desktop（macOS / Windows / Linux 都行）
- **任意一对 OpenAI 兼容的 chat + embedding API Key**——本项目**不绑死任何厂商**，下文给出默认 + 多组替代方案：
  - **默认（最便宜，国内可直连）**：[MiniMax](https://platform.minimaxi.com/) chat + [阿里通义 DashScope](https://bailian.console.aliyun.com/) embedding
  - 替代：DeepSeek、智谱 GLM、月之暗面 Kimi、OpenAI 等任意 OpenAI 兼容厂商（见下文 [切换厂商](#切换-chat--embedding-厂商)）

### 一行启动

```bash
git clone https://github.com/rocky8023/enterprise-ai-blueprint.git
cd enterprise-ai-blueprint
cp compose.env.example .env
# 编辑 .env，至少填入 BLUEPRINT_CHAT_API_KEY 和 BLUEPRINT_EMBEDDING_API_KEY
docker-compose up --build
```

首次构建 5-10 分钟（拉镜像 + 下依赖），之后改代码再 build 30 秒。

### 三条 curl 验证

```bash
# 1. 健康检查
curl http://localhost:8080/actuator/health
# 期待: {"status":"UP"}

# 2. 知识库内问题（v1 默认）
curl -G "http://localhost:8080/api/rag/ask" --data-urlencode "q=年假怎么算"

# 3. 切换 Prompt v2（同问题，输出风格完全不同）
curl -G "http://localhost:8080/api/rag/ask" \
  --data-urlencode "q=年假怎么算" \
  --data-urlencode "promptVersion=v2"

# 4. 看刚才这几次调用花了多少钱、用了多少 token
curl http://localhost:8080/api/traces/stats
curl http://localhost:8080/api/traces          # 最近调用摘要列表
# 取某条 traceId 看 prompt 全文 + 返回正文
curl http://localhost:8080/api/traces/<traceId>
```

> 🖥️ 不想敲 curl？直接浏览器打开 **<http://localhost:8080/>** —— 自带的工程化演示控制台，RAG 问答、Prompt v1/v2 并排对比、调用 token/成本面板、厂商 preset 一页看全。零构建、纯静态页（`src/main/resources/static/index.html`），由 Spring Boot 直接托管。

### 切换 chat / embedding 厂商

**不改代码、不重建镜像、不查文档记 URL —— 改一个名字**：

```bash
# === 切 chat 到 DeepSeek（embedding 保持 DashScope）===
BLUEPRINT_CHAT_API_KEY=sk-xxx
BLUEPRINT_CHAT_PRESET=deepseek

# === 切 chat 到智谱 GLM ===
BLUEPRINT_CHAT_API_KEY=xxx
BLUEPRINT_CHAT_PRESET=glm

# === 切 chat + embedding 都到 OpenAI（需翻墙）===
BLUEPRINT_CHAT_API_KEY=sk-xxx
BLUEPRINT_EMBEDDING_API_KEY=sk-xxx
BLUEPRINT_CHAT_PRESET=openai
BLUEPRINT_EMBEDDING_PRESET=openai
```

内置 preset：**minimax / dashscope / deepseek / glm / kimi / openai**。要列出所有可用 preset：

```bash
curl http://localhost:8080/api/providers | jq .
```

完整 preset 列表 + 自定义示例（内网代理、同厂商换便宜版本等）见 [`compose.env.example`](compose.env.example)。

> 💡 **原理**：`BLUEPRINT_*_PRESET` 在 Spring 启动早期由 `ProviderPresetEnvironmentPostProcessor` 解析为具体的 `base-url / model / temperature`，再注入到 `spring.ai.openai.{chat,embedding}.*`。**preset 字典在 [`providers.yml`](src/main/resources/providers.yml)，用户可在自己的 `application.yml` 中追加内网厂商。** 这就是 "多模型聚合" 的工程化形态——**配置即聚合**。

---

## 架构

<img width="2840" height="860" alt="diagram-1779430305633" src="https://github.com/user-attachments/assets/4a90fcbb-a298-40e6-be78-96d0d68ac29f" />


**分层原则**：

| 包 | 职责 |
|---|---|
| `api/` | REST 接口层，只做参数校验与编排转发 |
| `rag/` | RAG 业务编排（检索 + Prompt 注入 + 生成） |
| `prompt/` | Prompt 注册表、模板定义、元数据解析 |
| `config/` | Spring Bean 装配（VectorStore、ChatClient） |
| `infra/llm/` | 多模型聚合层：Preset 字典 + `EnvironmentPostProcessor` 把"厂商别名"解析为 `spring.ai.openai.*` 具体值（v1.1 第一阶段完成；v1.1 第二阶段抽象统一接口） |
| `infra/observability/` | 可观测性层：`LlmTracer` 调用埋点 + `TraceStore` 内存留痕 + 定价表折算成本 |

---

## 核心特性逐条

### 1. 多模型聚合（Preset 字典 + EnvironmentPostProcessor）

Spring AI 的 OpenAI client `chat` 和 `embedding` 配置**可独立**，继承自同一个父类。这是基础能力，本项目在它之上再加一层 **Preset 字典**，把"知道每家厂商的 base-url"这件事从用户脑子里搬到代码仓库里。

```yaml
# providers.yml —— 仓库自带的厂商字典
blueprint:
  provider:
    providers:
      minimax:
        base-url: https://api.minimaxi.com
        chat-model: MiniMax-M2.7
        chat-temperature: 0.7
      deepseek:
        base-url: https://api.deepseek.com
        chat-model: deepseek-chat
      dashscope:
        base-url: https://dashscope.aliyuncs.com/compatible-mode
        embedding-model: text-embedding-v3
      # ... glm / kimi / openai
```

切厂商：

```bash
BLUEPRINT_CHAT_PRESET=deepseek          # 一个名字搞定
# 或显式覆盖 preset 中的字段：
BLUEPRINT_CHAT_PRESET=minimax
BLUEPRINT_CHAT_MODEL=MiniMax-Text-01     # 同一家换更便宜的模型
```

底层实现：`ProviderPresetEnvironmentPostProcessor`（`infra/llm/`）在 Spring 启动早期、`OpenAiAutoConfiguration` 绑定 properties **之前**，把 preset 解析为 `spring.ai.openai.chat.base-url` 等具体值并注入 Environment。这是为什么不需要重启 / 不需要重建镜像就能切厂商。

**用户可在自己的 `application.yml` 中追加 preset**（合并行为，不需要 fork providers.yml）：

```yaml
blueprint:
  provider:
    providers:
      intra-proxy:           # 公司内网 LLM 代理
        base-url: https://llm.intra.example.com
        chat-model: qwen2.5-72b
```

未知 preset 名直接 fail-fast，错误信息附带可用 preset 列表 —— 配置错误不留运行时谜团。

### 2. Prompt 即代码（frontmatter 元数据）

`src/main/resources/prompts/*.md`：

```markdown
---
key: rag.company-qa
version: v2
author: rocky8023
description: 改良版，无表格 + 分点 + 限字数
status: beta
based_on: v1
examples:
  - question: 年假怎么算
    expected_keywords: ["5 天", "leave_policy"]
---

你是企业知识助手。请基于【知识库】回答【用户问题】...

【知识库】
{context}

【用户问题】
{question}
```

**Prompt 是代码 → 它就应该有 metadata + 自带测试用例**。`PromptRegistry` 启动时扫描 + 解析 + 注册，运行时通过 `?promptVersion=v2` 灰度。

### 3. RAG 完整链路

```
用户问题 → DashScope 嵌入 → SimpleVectorStore 相似度检索 (top-K)
   ↓
Prompt 模板渲染 (context + question) → MiniMax 生成
   ↓
<think>...</think> 推理块剥离 → 返回 answer + sources + promptUsed
```

每次调用日志记录使用的 prompt fullId（`rag.company-qa@v2`），便于回溯。

### 4. 多环境配置 = 最朴素的灰度

| 配置项 | dev | prod |
|---|---|---|
| Prompt 活跃版本 | **v2 (beta)** | **v1 (stable)** |
| 日志级别 | DEBUG | WARN/INFO |
| Actuator 暴露 | 全部端点 | 仅 health |
| topK | 6 | 4 |

```bash
# 本地开发：自动跑 beta prompt 验证
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# Docker 容器：默认走 prod
docker-compose up
```

### 5. 可观测性：每次调用看得见成本

AI 应用上生产，绕不开三个问题：**这次调用发的什么 prompt？花了多少 token？折成钱是多少？** 本项目在每个 LLM 调用点包一层 `LlmTracer`，把这些自动采下来：

- **采什么**：prompt 全文、渲染时传入的变量、命中的模型与 preset、prompt/completion/total token、折算成本、耗时、成功或异常。
- **怎么采**：用「显式包裹」而非 AOP——调用方把 `call().chatResponse()` 包进 `tracer.traceChat(...)` 即可，读代码一眼看清观测发生在哪、采了什么。
- **成本怎么算**：定价表外置在 `blueprint.observability.pricing`（model → 元/1K tokens，input/output 分开，未命中走 `default`）。厂商调价或拿到折扣价，改配置即可，不动代码。
- **怎么看**：`GET /api/traces` 看摘要列表、`/api/traces/{id}` 看单次全文、`/api/traces/stats` 看总 token / 总成本 / 平均耗时聚合。

```bash
$ curl -s localhost:8080/api/traces/stats
{"count":12,"totalTokens":8460,"totalCost":0.0631,"avgLatencyMs":1840,"errorCount":0}
```

> 内存环形缓冲仅作演示，重启即丢；生产应替换为数据库 / Langfuse 等持久化方案（见 Roadmap v1.2）。

### 6. 生产级 Docker

- **multi-stage**：构建阶段用 JDK，运行阶段用 JRE，最终镜像不带 Maven
- **缓存层**：`pom.xml` 先 copy 跑 `dependency:go-offline`，改代码不重新下依赖
- **非 root**：`USER blueprint`，符合企业安全基线
- **健康检查**：`HEALTHCHECK` + docker-compose `healthcheck` 双层
- **环境隔离**：`.dockerignore` 严格控制构建上下文，secrets 不入镜像

---

## 已踩过的工程化坑（也是我的公众号选题）

这些坑都是**真实踩过 + 已经修好**的，每个对应一个 commit / 配置项：

1. **Spring AI base-url 不能带 `/v1`**：`completionsPath` 默认就是 `/v1/chat/completions`，base-url 再带 `/v1` → 双 v1 → 404
2. **OpenAI auto-config 不用的要 exclude**：audio / image / moderation 的 bean 启动时强制要 `spring.ai.openai.api-key` 顶层 key，不显式禁用就启动失败
3. **国产推理模型的 `<think>` 块**：MiniMax-M2.7 / DeepSeek-R1 等会输出 `<think>...</think>` 推理过程，需要正则剥离再返回给用户
4. **chat 和 embedding 可独立配 YAML**：很多人以为 Spring AI 只能 chat 和 embedding 同源，其实早就支持分离
5. **Prompt 自带 metadata + 测试用例**：Prompt 是代码，frontmatter 是它的"package.json"
6. **灰度发布 = 一行 SPRING_PROFILES_ACTIVE**：不用引入 feature flag 系统
7. **Docker multi-stage + 依赖缓存层**：单纯 multi-stage 还不够，必须把 `pom.xml` 单独 copy 一次跑 `dependency:go-offline`，否则改一行代码也要重新下 Maven 依赖
8. **非 root + healthcheck 是企业级容器基线**：交付给政企客户的镜像不带这两个会被打回
9. **docker-compose 不要把可选变量"显式但空"传进容器**：`${VAR:-}` 会把未设置的变量传成空字符串 → Spring 的 `${VAR:default}` 不会 fallback（已定义但为空）→ Spring AI OpenAI client 看到 base-url=空 → fallback 到 `api.openai.com` 默认值 → 你的国产 key 发给 OpenAI → 401。正确做法：用 `env_file:` 让 .env "有什么传什么"
10. **Spring Boot 3 的 SPI 注册分两套，别用错**：`META-INF/spring/...AutoConfiguration.imports` **只对 `AutoConfiguration` 生效**；`EnvironmentPostProcessor` 仍然必须用老的 `META-INF/spring.factories`。混用的代价是：你的 EPP 写得再对，Spring 根本不会加载它，启动表现成"就像这段代码不存在"——本项目的 `ProviderPresetEnvironmentPostProcessor` 第一次提交就踩进这个坑

每个坑会在公众号 [**第二曲线成长**](#关于作者) 出一篇深度拆解。

---

## Roadmap

### v1.1（接下来）
- [x] **Preset 字典 + EnvironmentPostProcessor**：`infra/llm/` 第一阶段，切厂商 = 改一个名字（见上方 [核心特性 #1](#1-多模型聚合preset-字典--environmentpostprocessor)）
- [ ] **`infra/llm/` 统一接口抽象（第二阶段）**：把 ChatClient / EmbeddingModel 包到自家接口下，运行时按 preset 动态选实例（chat 也能在请求维度切换）
- [ ] **评测框架**：自动跑 prompt 的 `examples`，回归测试 prompt 修改对效果的影响
- [x] **可观测性增强**：调用链 trace（每次调用的 prompt 全文 + 变量 + 耗时 + token + 厂商成本）—— `infra/observability/` + `/api/traces`，详见上方 [核心特性 #5](#5-可观测性每次调用看得见成本)

### v1.2（看反馈）
- [ ] 向量库替换为 PGVector / Milvus（演示生产级向量存储切换）
- [ ] RAG advisor 模式（用 Spring AI 的 `QuestionAnswerAdvisor` 重构对比）
- [ ] 接入 Langfuse 做调用追踪

### v2.0（远期）
- [ ] Spring AI Alibaba 集成（原生支持通义系列）
- [ ] Agent 工作流编排示例

---

## 关于作者

**rocky8023** · 21 年技术老兵

从政企信息化到 1 号店、京东电商，再到跨境电商和现在的政企 AI 落地。这个项目是我把"做企业 AI 应用时希望一开始就有的工程脚手架"整理成可直接复用的开源版。

如果这个项目对你有帮助，欢迎关注我的公众号 **第二曲线成长** —— 我会持续分享：
- 21 年老兵转型 AI 时代的实战经验
- 中年技术人的副业探索路径
- 企业 AI 落地的工程化心得

> 📷 公众号二维码：

<img width="344" height="344" alt="qrcode_for_gh_6da650e0e7fb_344" src="https://github.com/user-attachments/assets/d85815ac-81c2-4271-af5a-4ba2720496e7" />

---

## License

Apache License 2.0 · 见 [LICENSE](LICENSE)

代码可商用，但请保留作者信息。如果你基于本项目搭建了自己的 AI 应用并上线，**特别欢迎来 issue 告诉我**——你的实践会成为我下一篇文章的灵感来源。

---

## 致谢

- [Spring AI](https://github.com/spring-projects/spring-ai) — 整个项目的基础
- [MiniMax](https://platform.minimaxi.com/) / [阿里通义](https://bailian.console.aliyun.com/) — 国产大模型的真实可用让这个项目成本极低
- 所有在 issue 区跟我交流的同行
