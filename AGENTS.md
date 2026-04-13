# Knowledge Base References

This project references documentation and analysis notes stored in an Obsidian vault.

## Obsidian Vault Location

```
/mnt/dav/obsidian/mine/opencode/
```

## Code Quality Rules

### File Size Limit: 800 Lines

**硬性约束**：单个 Kotlin 源文件不得超过 800 行。

- 编写或修改代码时，使用 `wc -l <file>` 检查文件行数
- 如果修改后文件超过 800 行，**不得直接继续编码**，必须先进行拆分规划：
  1. 分析文件职责，识别可独立的功能块
  2. 评估每个功能块的依赖关系和拆分影响面
  3. 制定拆分方案（提取工具类、拆分 ViewModel/Composable、职责下沉到 Repository 等）
  4. 将拆分方案写入 `.sisyphus/drafts/{name}.md`，经用户确认后再执行
- 以下文件豁免（但超出时仍需说明理由）：
  - `domain/model/` 下的 sealed class 层级（如 Part、SseEvent）— 由 API 契约决定
  - `TestFixtures.kt` — 测试数据工厂，膨胀可接受
  - 自动生成的代码（Hilt、Room 生成的类）

### Build Verification

**硬性约束**：涉及 Kotlin 源代码变更时，完成前必须通过构建验证。

- 执行 `./gradlew assembleDebug` 确认编译通过
- 如果存在对应模块的单元测试，执行 `./gradlew test` 确认测试通过
- 如果修改涉及 Room Entity/DAO，必须执行 `./gradlew testDebugUnitTest --tests "*MigrationTest*"` 验证 schema 兼容性
- 构建或测试失败时，**不得标记任务完成**，必须修复后再验证

### Test Planning

**硬性约束**：规划阶段必须同步考虑单元测试覆盖。

- 每个实现任务的计划中必须包含对应的测试计划
- 按层级匹配测试要求：
  - **EventReducer** — 新增或修改事件处理必须有单元测试覆盖状态转换
  - **ViewModel** — 新增或修改 Intent/UiState 必须有状态断言测试
  - **Repository** — 新增数据流或缓存逻辑必须有协调测试
  - **API Client** — 新增接口必须有 MockEngine 请求/响应解析测试
  - **Composable** — 重大 UI 变更应有截图测试
- 测试必须使用项目现有基础设施（`TestFixtures`、`CoroutineTestRule`、`TimeoutRule`），不要自行发明测试工具
- 纯 UI 样式调整（颜色、间距、动画）可豁免测试要求

### Error Handling

**硬性约束**：禁止静默吞掉异常。所有 `catch` 必须至少包含日志记录，大多数场景需要用户可感知的错误处理。

- **禁止的模式**：
  - 空 `catch` 块：`catch(e: Exception) {}`
  - 仅注释占位：`catch(e: Exception) { /* TODO */ }`
  - 吞掉异常无任何记录
- **最低要求**：每个 `catch` 必须包含日志，且包含三个要素 —— 类名（Tag）、操作上下文（发生了什么）、异常对象（堆栈）：
  ```kotlin
  Log.e("ChatViewModel", "Failed to send message: ${e.message}", e)
  ```
- **按场景的处理要求**：
  - **ViewModel 调 API** — `catch` → 映射为 `ApiResult.Error(kind, message, retryable)` → UI 展示错误
  - **EventReducer 处理事件** — `catch` → 日志记录 + skip 该事件，继续处理后续事件（SSE 流不能因单事件中断）
  - **Repository 操作** — `catch` → 转换为有意义的错误状态，或向上传播给 ViewModel 处理
  - **Composable 副作用** — `LaunchedEffect` 中的异常必须流向 ViewModel，不允许在 Composable 里静默处理
- **豁免**：仅在用户显式要求忽略某类异常时，才允许省略处理逻辑

## E2E Test Execution

**必须使用 Gradle 传递参数运行 androidTest**，因为 `adb shell am instrument -e` 无法正确传递含特殊字符（`!`, `&`, `^`, `*`）的参数值，Android 的 shell 会错误展开这些字符。

```bash
# 正确方式：通过 Gradle 传递 instrumentation arguments
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.serverName=TestServer \
  -Pandroid.testInstrumentationRunnerArguments.serverUrl=http://192.168.31.52:4000 \
  -Pandroid.testInstrumentationRunnerArguments.username=xiaok \
  -Pandroid.testInstrumentationRunnerArguments.password='R4&nW7*bJ3^fH6!' \
  -Pandroid.testInstrumentationRunnerArguments.projectPath=/home/xiaok/projects/test
```

- **禁止**使用 `adb shell am instrument -e` 传递含特殊字符的参数
- 参数值不含空格时可省略引号，含空格时必须用引号包裹
- 当前测试设备: Pixel 4a (5G), 192.168.31.218:5555
- 测试服务器: 192.168.31.52:4000

## Device Testing Rules

- **优先使用 `adb shell uiautomator dump`** 探查 Android 界面结构和元素
- 只有在需要深度视觉分析或最终视觉确认时才使用截图（screencap）
- uiautomator 能提供界面元素的文本、bounds、class 等结构化信息，比截图更高效
