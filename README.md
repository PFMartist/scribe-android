# Scribe — 自动手记人偶 (Android)

Kotlin + Jetpack Compose 构建的 AI 对话客户端，支持 OpenAI 兼容 / Anthropic 双 API、SSE 流式响应、角色扮演 Skill 系统。

源自 Bash 终端版 [Scribe](https://github.com/PFMartist/scribe)。

## 功能

- **双 API 兼容** — OpenAI 兼容（DeepSeek 等）与 Anthropic 原生 API，一键切换
- **SSE 流式响应** — 实时流式输出，AI 推理思考过程可在气泡内展开/折叠
- **Skill 角色系统** — 加载角色设定文件（YAML frontmatter + Markdown），AI 切换为特定人设
- **对话管理** — 多场对话并存，Room 数据库持久化完整历史
- **Skill 导入** — 粘贴文本或选择 zip 压缩包导入新角色
- **Material 3** — 支持深色模式、edge-to-edge

## 构建

依赖：JDK 21 + Android SDK 35。

```bash
export ANDROID_HOME=$HOME/Android
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew assembleRelease
```

或用 Android Studio 打开项目目录。

## 协议

MIT License。

## 第三方内容声明

附带角色 skill 文件涉及《明日方舟》（© Hypergryph）的角色名称和设定，知识产权归鹰角网络所有，不包含在 MIT 许可范围内。本项目为非商业性粉丝创作。
