# 让 Android 界面贴近 SukiSU-Ultra —— 第一批（地基）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 miuix-nav / miuix-preference / miuix-blur 三个模块接进构建并通过清单合并，新增这一批
之后几批要用的中性组件 API —— **但一个调用点都不改**，所以界面与现在完全一致。

**Architecture:** 只动构建配置、清单、以及 `ui/component/` 与 `ui/util/` 两个叶子目录。新组件沿用现有
的两层结构：`O2AComponents.kt` 声明中性 API，`MiuixComponents.kt` / `MaterialComponents.kt` 各给一份
实现，屏幕永远只写中性名。本批新增的 API 没有任何调用点，因此**不可能改变观感**。

**Tech Stack:** Kotlin 2.4.20 / Compose BOM 2026.09.00 / AGP 9.4.1 / miuix 0.9.4（`miuix-ui`、
`miuix-icons`、`miuix-nav`、`miuix-preference`、`miuix-blur`）/ material-kolor 5.0.1。

**Spec:** `docs/superpowers/specs/2026-10-16-sukisu-ui-fidelity-design.md`（第五、六、八节是本批的依据）

## Global Constraints

- **全程 GitHub Actions 代替本机。** 本机不编译、不跑 Gradle、不下载 Gradle 发行版。唯一的构建
  命令是推送后由 `.github/workflows/android.yml` 执行；本机只允许 `git`、`gh`、`grep`、`read`。
- `minSdk` 26、`targetSdk` 36、`compileSdk` 37.0（release/minor 写法）。`compileSdk` 不能改写。
  这三个值来自根 `build.gradle.kts` 的 extra，不要在 `android/app/build.gradle.kts` 里另写字面量。
- 注释英文、UI 文案中文，与仓库现状一致。
- 每一批自己要 CI 绿。本批的 CI 必须是绿的**且 APK 校验步骤全部通过**，不允许跳过任何一步。
- **不要删 `O2ASectionTitle`。** 它还有十四条调用点，删除是第四批的事；本批只加不删。
- 提交信息结尾用 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。
- 不要碰 `.github/workflows/android.yml`（本批不改 CI 逻辑）。

## Review Focus

这批代码 CI 能证明的只有「编译通过 + APK 结构正确」。下面这些是本项目最容易在**编译全绿的前提下
坏掉**的东西，按可能性排序；每条都在对应任务里有一个可检查的落点。

1. **清单合并被别的 miuix 模块挡住。** `overrideLibrary` 目前只放行 `top.yukonga.miuix.kmp.blur`
   一个模块。如果 `nav`/`preference` 的 minSdk 也高于 26，合并会在**加完依赖的第一次 CI** 才报出来，
   而报错信息里点名的模块正是要放进 `overrideLibrary` 的那一串。→ Task 1 Step 6 的处理分支。
2. **模糊在缺 AGSL 的设备上整块消失。** `DrawBackdropModifier.kt:118` 是
   `enabled && isRuntimeShaderSupported()`，为假时 modifier 是空的 —— 不是退化，是没有背景。
   本批只引入两个函数，闸门在 Task 2 里就写死成 `isRenderEffectSupported()`。
3. **`O2AScreen` 的 content 忘了挂 `layerBackdrop`。** 背影（backdrop）没有内容源就等于模糊了个空，
   栏会变成透明的而不是毛玻璃 —— 编译通过，只在设备上看得出。→ Task 5 Step 3。
4. **`contentWindowInsets` 写成默认值。** 一旦带上垂直方向，栏下面会凭空多一条白边（Spec 第八节）。
   本批 `O2AScreen` 是唯一一处写这个值的地方。→ Task 5 Step 3。
5. **`arrowPreference` 给了不可点的行。** 箭头是「点进去」的承诺。本批不碰 `O2ARow`，但
   `O2ADropdownRow` 是新行，别顺手把箭头逻辑带进来。→ Task 6 Step 3。

---

### Task 1: 引入 miuix-nav / miuix-preference / miuix-blur，并放行清单合并

这是整批唯一一个「可能根本编译不过」的地方，所以它自己占一个任务、自己一次 CI。

**Files:**
- Modify: `android/gradle/libs.versions.toml`（删 39–47 行的旧注释；`:48-49` 后加三个坐标）
- Modify: `android/app/build.gradle.kts:128-129`（`dependencies` 块里加四条）
- Modify: `android/app/src/main/AndroidManifest.xml:11`（`<application>` 之前加一行）

**Interfaces:**
- Consumes: 无
- Produces: 类路径上出现 `top.yukonga.miuix.kmp.preference.*`、`top.yukonga.miuix.kmp.navigation.*`、
  `top.yukonga.miuix.kmp.blur.*`、`top.yukonga.miuix.kmp.shader.isRenderEffectSupported`（后续任务用）

- [ ] **Step 1: 删掉 `libs.versions.toml` 里那段错注释**

`android/gradle/libs.versions.toml:39-47` 的注释断言 miuix-blur 声明 minSdk 33、所以本应用不能用。
这是错的（SukiSU 同样 minSdk 26 并且照用），留着会让下一个人重复这个错。把 39–47 行整段替换成：

```toml
# Icons come from miuix, which draws them at 24dp in the MIUI style. The
# androidx material-icons-extended artifact is deprecated and no longer managed
# by the Compose BOM, so it is deliberately not used here.
```

- [ ] **Step 2: 加三个 miuix 坐标与 navigationevent**

在 `libs.versions.toml` 的 `[libraries]` 里、`miuix-ui` / `miuix-icons` 两行之后加：

```toml
miuix-nav = { module = "top.yukonga.miuix.kmp:miuix-nav-android", version.ref = "miuix" }
miuix-preference = { module = "top.yukonga.miuix.kmp:miuix-preference-android", version.ref = "miuix" }
# Declared explicitly even though miuix-nav already pulls it at this version: that
# transitive edge is in miuix-nav's runtime variant only, so it is deliberately
# named here to keep the version visible rather than inferred.
miuix-blur = { module = "top.yukonga.miuix.kmp:miuix-blur-android", version.ref = "miuix" }
androidx-navigationevent-compose = { module = "androidx.navigationevent:navigationevent-compose", version = "1.1.2" }
```

版本号 `1.1.2` 必须与 `miuix-nav` 声明的相同（已从 `miuix-nav-0.9.4.module` 的
`androidRuntimeElements-published` 里核出就是 1.1.2），写别的值会在依赖解析期产生冲突。

- [ ] **Step 3: 在 `build.gradle.kts` 里接上这四个坐标**

`android/app/build.gradle.kts` 的 `dependencies` 块，紧跟现有的
`implementation(libs.miuix.ui)` / `implementation(libs.miuix.icons)` 两行之后加：

```kotlin
    implementation(libs.miuix.nav)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.androidx.navigationevent.compose)
```

- [ ] **Step 4: 清单里放行 miuix-blur**

`android/app/src/main/AndroidManifest.xml`：文件已经有 `xmlns:tools`（第 3 行），
在 `<application>`（第 12 行）**之前**、权限声明之后加：

```xml
    <!-- miuix-blur declares a minSdk above our 26. It is inert below that level, and
         every call site is behind isRenderEffectSupported(), so the merge is allowed
         through. Nothing else in the miuix suite needs an entry here. -->
    <uses-sdk tools:overrideLibrary="top.yukonga.miuix.kmp.blur" />
```

- [ ] **Step 5: 本机静态确认（不编译）**

Run: `cd /root/work/o2a && git diff --stat && grep -n 'miuix-nav\|miuix-preference\|miuix-blur\|navigationevent' android/gradle/libs.versions.toml android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml`

Expected: 三个文件都出现在 diff 里；四个坐标在两个文件里各出现一次（toml 里是声明，gradle 里是引用）；
清单里出现 `tools:overrideLibrary`；`libs.versions.toml` 里不再有 `minSdk 33` 字样。

- [ ] **Step 6: 推送并等 CI**

```bash
cd /root/work/o2a && git add android/gradle/libs.versions.toml android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml
git commit -m "build(android): take the three miuix modules the new shell needs"
git push origin android-port
gh run list --workflow=android.yml --branch android-port --limit 1
gh run watch <run-id> --exit-status
```

Expected: **Build APK 成功**，且 `Verify the APK carries a runnable gateway` 那一步通过。

失败分支，按报错位置分流：

- **报在依赖解析**（`Could not find ...` / `Could not resolve ...`）→ 说明某个坐标或版本号不对。
  逐字对照 Step 2，尤其 `miuix-blur` 的模块名是 `miuix-blur-android` 而不是 `miuix-blur`。
- **报在清单合并**（`Manifest merger failed` / `uses-sdk:minSdkVersion ... cannot be smaller than`）
  → 报错信息点名的模块名补进 `tools:overrideLibrary`，多个用逗号分隔，例如
  `top.yukonga.miuix.kmp.blur,top.yukonga.miuix.kmp.nav`。**这是本批唯一预期内的意外**，改完重推。
- **报在 `navigationevent-compose` 解析不出来** → 先删掉那条 `implementation`（`miuix-nav` 运行期
  自己带），其余不动，重推。

- [ ] **Step 7: 提交（若上面已提交则跳过）**

CI 绿之后确认工作区干净：`git status --short` 应为空输出。

---

### Task 2: `ui/util/BlurExt.kt` —— 两个函数

**Files:**
- Create: `android/app/src/main/java/com/opencode2api/android/ui/util/BlurExt.kt`

**Interfaces:**
- Consumes: Task 1 引入的 `top.yukonga.miuix.kmp.blur.*` 与 `top.yukonga.miuix.kmp.shader.isRenderEffectSupported`
- Produces:
  - `@Composable fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop?`
  - `@Composable fun BlurredBar(backdrop: LayerBackdrop?, blurActive: Boolean = true, content: @Composable () -> Unit)`

- [ ] **Step 1: 写文件**

内容逐行对应 SukiSU 的 `ui/util/BlurExt.kt`（这两段是从它那里搬的，不是自创）：

```kotlin
package com.opencode2api.android.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The backdrop a blurred bar samples from, or null when blur is off or the platform
 * cannot do it at all.
 *
 * Returning null rather than a no-op backdrop is the point: every call site then falls
 * back to an opaque bar without a branch of its own.
 *
 * isRenderEffectSupported() is API 31+ and comes from miuix's shader module, which
 * miuix-blur exposes as an api dependency -- there is nothing to declare for it.
 */
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * Hosts [content] over a blurred copy of whatever [backdrop] captured. With a null
 * backdrop, or with [blurActive] off, this is a plain Box and the bar keeps its own
 * opaque colour.
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.87f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}
```

- [ ] **Step 2: 本机静态确认三个符号的来源都在 Task 1 的类路径上**

Run: `cd /root/work/o2a && grep -c 'top.yukonga.miuix.kmp.shader.isRenderEffectSupported' android/app/src/main/java/com/opencode2api/android/ui/util/BlurExt.kt && grep -n 'miuix-blur' android/gradle/libs.versions.toml`

Expected: 计数为 `1`；`miuix-blur` 仍在 toml 里。`isRenderEffectSupported` 不在 `miuix.blur` 包里
（那是 0.9.4 的事实，只有 `isRuntimeShaderSupported` 在那儿），所以这个 import 必须是 `shader` 包。

- [ ] **Step 3: 推送并等 CI**

```bash
cd /root/work/o2a && git add android/app/src/main/java/com/opencode2api/android/ui/util/BlurExt.kt
git commit -m "feat(android): add the two blur helpers the blurred bar needs"
git push origin android-port
gh run list --workflow=android.yml --branch android-port --limit 1
gh run watch <run-id> --exit-status
```

Expected: 绿。若报 `Unresolved reference: isRenderEffectSupported`，说明 `miuix-shader` 不在类路径上
（这不应发生 —— 它是 `miuix-blur` 的 api 依赖），此时补一条显式 `miuix-shader` 坐标再推。

- [ ] **Step 4: 确认没有调用点**

Run: `cd /root/work/o2a && grep -rn 'rememberBlurBackdrop\|BlurredBar' android/app/src/main/java/ | grep -v 'ui/util/BlurExt.kt'`

Expected: 空输出。本批结束时这两个函数应当还没有人调用。

---

### Task 3: `O2ACard` 加按压反馈

**Files:**
- Modify: `.../ui/component/O2AComponents.kt:82-91`（`O2ACard`）
- Modify: `.../ui/component/MiuixComponents.kt:89-96`（`MiuixCard`）
- Modify: `.../ui/component/MaterialComponents.kt`（`MaterialCard`）

**Interfaces:**
- Consumes: 无
- Produces: `enum class O2APressFeedback { Sink, Tilt }`；`O2ACard(modifier, onClick, pressFeedback: O2APressFeedback = O2APressFeedback.Sink, content)`

- [ ] **Step 1: 中性层加枚举与参数**

在 `O2AComponents.kt` 的 `O2ATextStyle` 旁边加：

```kotlin
/**
 * How a card reacts to being held. miuix draws its own feedback and defaults to none;
 * SukiSU turns it on explicitly, so the neutral default here is on too.
 */
enum class O2APressFeedback { Sink, Tilt }
```

`O2ACard` 的签名改成：

```kotlin
@Composable
fun O2ACard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    pressFeedback: O2APressFeedback = O2APressFeedback.Sink,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isMiuix) MiuixCard(modifier, onClick, pressFeedback, content)
    else MaterialCard(modifier, onClick, pressFeedback, content)
}
```

参数插在 `onClick` 之后、`content` 之前，这样现有的 `O2ACard(modifier = …) { … }` 调用点全部不受影响。

- [ ] **Step 2: miuix 侧接上真反馈**

`MiuixComponents.kt` 的 `MiuixCard` 改成：

```kotlin
@Composable
internal fun MiuixCard(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    pressFeedback: O2APressFeedback,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        // miuix defaults both of these to off, which is why our cards currently give
        // no sign of being pressed at all. Only meaningful on a clickable card.
        pressFeedbackType = when {
            onClick == null -> PressFeedbackType.None
            pressFeedback == O2APressFeedback.Tilt -> PressFeedbackType.Tilt
            else -> PressFeedbackType.Sink
        },
        showIndication = onClick != null,
        onClick = onClick,
        content = content,
    )
}
```

新增 import：`top.yukonga.miuix.kmp.utils.PressFeedbackType`。

- [ ] **Step 3: Material 侧对齐**

`MaterialComponents.kt` 的 `MaterialCard`（`:116-126`）**已经有** `onClick` 空/非空的分支，所以只需
把它照如下改完即可 —— 不要重写这个分支：

```kotlin
@Composable
internal fun MaterialCard(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    pressFeedback: O2APressFeedback,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Material 3's clickable Card already carries a ripple, which is this skin's
    // equivalent of miuix's Sink and Tilt alike, so the two feedback kinds collapse here.
    if (onClick == null) {
        Card(modifier = modifier) { content() }
    } else {
        Card(onClick = onClick, modifier = modifier) { content() }
    }
}
```

- [ ] **Step 4: 本机静态确认没有调用点被破坏**

Run: `cd /root/work/o2a && grep -rn 'O2ACard(' android/app/src/main/java/ | wc -l && grep -rn 'O2ACard(.*pressFeedback' android/app/src/main/java/ | wc -l`

Expected: 第一个数 > 0（现有调用点），第二个为 `0`（没人显式传新参数，说明默认值在兜着）。

- [ ] **Step 5: 推送并等 CI**

```bash
cd /root/work/o2a && git add -A android/app/src/main/java/com/opencode2api/android/ui/component/
git commit -m "feat(android): give cards a press feedback"
git push origin android-port
gh run watch <run-id> --exit-status
```

Expected: 绿。静止外观不变，按下去才有反应。

---

### Task 4: `Modifier.o2aGroup()` 与 `O2AList`

**Files:**
- Modify: `.../ui/component/O2AComponents.kt`
- Modify: `.../ui/component/MiuixComponents.kt`
- Modify: `.../ui/component/MaterialComponents.kt`

**Interfaces:**
- Consumes: `LocalUiMode`（已有）
- Produces:
  - `fun Modifier.o2aGroup(): Modifier` —— `fillMaxWidth().padding(top = 12.dp)`
  - `@Composable fun O2AList(contentPadding: PaddingValues, modifier: Modifier = Modifier, bottomPadding: Dp = 24.dp, content: LazyListScope.() -> Unit)`
  - `val LocalO2AScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }`（定义在
    `MiuixComponents.kt` 里，见 Step 3）

- [ ] **Step 1: 加 `o2aGroup()`**

它不需要皮肤分派，是一个纯 Modifier 扩展，直接写在 `O2AComponents.kt` 末尾：

```kotlin
/**
 * The spacing of one group in a settings list. SukiSU's lists have no section headings
 * and no separators -- a group is simply another card, and the gap between cards is all
 * that tells them apart. This is that gap.
 */
fun Modifier.o2aGroup(): Modifier = fillMaxWidth().padding(top = 12.dp)
```

- [ ] **Step 2: 写 `O2AList` 的中性声明**

```kotlin
/**
 * The scroll container the list-shaped screens use.
 *
 * It exists so a screen does not have to remember five separate things: the end-of-scroll
 * haptic, the miuix overscroll effect (which replaces the platform one, hence
 * overscrollEffect = null), the nested-scroll link to the collapsing top bar, the 12dp
 * gutter, and the trailing spacer that keeps the last card clear of the bottom bar.
 *
 * [contentPadding] is the Scaffold's innerPadding, which is why O2AScreen hands it to the
 * content lambda: the vertical part must be consumed inside the list, not applied as a
 * modifier, or the content never scrolls under the bar.
 */
@Composable
fun O2AList(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 24.dp,
    content: LazyListScope.() -> Unit,
) {
    if (isMiuix) MiuixList(contentPadding, modifier, bottomPadding, content)
    else MaterialList(contentPadding, modifier, bottomPadding, content)
}
```

- [ ] **Step 3: miuix 侧写那条链子**

先在这个文件里加那个局部量。它刻意**不放在** `ui/UiMode.kt`：它的类型 `ScrollBehavior` 是 miuix 自己
的接口（`top.yukonga.miuix.kmp.basic.ScrollBehavior`，声明在 `basic/TopAppBar.kt:422`），放进
`MiuixComponents.kt` 才不会把一个 miuix 类型漏进中性层。

```kotlin
/**
 * The current screen's collapsing-top-bar scroll behavior, for the list to link against.
 * Null when no screen has provided one, which is the Material skin's permanent state: it
 * has no such behavior here.
 */
internal val LocalO2AScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }
```

然后是列表本体：

```kotlin
@Composable
internal fun MiuixList(
    contentPadding: PaddingValues,
    modifier: Modifier,
    bottomPadding: Dp,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = LocalO2AScrollBehavior.current
    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .scrollEndHaptic()
            .overScrollVertical()
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp),
        contentPadding = contentPadding,
        overscrollEffect = null,
    ) {
        content()
        item { Spacer(Modifier.height(bottomPadding)) }
    }
}
```

注意 `.padding(horizontal = 12.dp)`（外）与 `contentPadding`（内）是**两层**，它们叠加是对的：
`contentPadding` 里只有水平方向的系统 inset（见 Task 5），不重复。

`ScrollBehavior` 需要 `import top.yukonga.miuix.kmp.basic.ScrollBehavior`。

- [ ] **Step 4: Material 侧**

Material 的 `LazyColumn` 不需要 miuix 那条链，也不能设 `overscrollEffect = null`：

```kotlin
@Composable
internal fun MaterialList(
    contentPadding: PaddingValues,
    modifier: Modifier,
    bottomPadding: Dp,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxHeight().padding(horizontal = 12.dp),
        contentPadding = contentPadding,
    ) {
        content()
        item { Spacer(Modifier.height(bottomPadding)) }
    }
}
```

- [ ] **Step 5: 推送并等 CI**

```bash
cd /root/work/o2a && git add -A android/app/src/main/java/com/opencode2api/android/ui/component/
git commit -m "feat(android): add the shared settings-list container"
git push origin android-port
gh run watch <run-id> --exit-status
```

Expected: 绿。仍然没有任何调用点。

- [ ] **Step 6: 确认无调用点**

Run: `cd /root/work/o2a && grep -rn 'O2AList(\|o2aGroup()' android/app/src/main/java/ | grep -v 'ui/component/'`

Expected: 空输出。

---

### Task 5: `O2AScreen`

**Files:**
- Modify: `.../ui/UiMode.kt`（加 `LocalEnableBlur`）
- Modify: `.../ui/component/O2AComponents.kt`
- Modify: `.../ui/component/MiuixComponents.kt`
- Modify: `.../ui/component/MaterialComponents.kt`

**Interfaces:**
- Consumes: `rememberBlurBackdrop` / `BlurredBar`（Task 2）、`LocalO2AScrollBehavior`（Task 4）
- Produces:
  - `val LocalEnableBlur = staticCompositionLocalOf { false }`
  - `@Composable fun O2AScreen(title, modifier, subtitle, onBack, actions, largeTitle, popupHost, content: @Composable (PaddingValues) -> Unit)`

- [ ] **Step 1: 加 `LocalEnableBlur`**

放在 `ui/UiMode.kt` 里 `LocalUiMode` 旁边：

```kotlin
/**
 * Whether the app blurs. Off by default, exactly as SukiSU ships it: its floating bar and
 * its blur are both things the user turns on.
 */
val LocalEnableBlur = staticCompositionLocalOf { false }
```

（列表用的 `LocalO2AScrollBehavior` 已经在 Task 4 里定义好了，就在 `MiuixComponents.kt`。）

- [ ] **Step 2: 中性层声明 `O2AScreen`**

```kotlin
@Composable
fun O2AScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    largeTitle: Boolean = true,
    popupHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    if (isMiuix) {
        MiuixScreen(title, modifier, subtitle, onBack, actions, largeTitle, popupHost, content)
    } else {
        MaterialScreen(title, modifier, subtitle, onBack, actions, largeTitle, popupHost, content)
    }
}
```

`largeTitle` 默认为 `true`（SukiSU 的默认），密集工具页显式传 `false` 退回 `SmallTopAppBar`。
`popupHost` 默认 `{}` —— **内层宿主留空是对的**，下拉的浮层由最外层 Scaffold 渲染，见 Spec 第 4 节；
这里传内容是在根宿主之外**再加**一层，不是替换它。

- [ ] **Step 3: miuix 侧（本批唯一有分量的实现）**

```kotlin
@Composable
internal fun MiuixScreen(
    title: String,
    modifier: Modifier,
    subtitle: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    largeTitle: Boolean,
    popupHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = enableBlur && backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    CompositionLocalProvider(LocalO2AScrollBehavior provides scrollBehavior) {
        Scaffold(
            modifier = modifier,
            topBar = {
                BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                    // The back arrow only appears when there is somewhere to go back to.
                    val navigationIcon = @Composable {
                        if (onBack != null) {
                            O2AIconButton(MiuixIcons.Regular.Back, "返回", onBack)
                        }
                    }
                    if (largeTitle) {
                        TopAppBar(
                            title = title,
                            subtitle = subtitle,
                            color = barColor,
                            scrollBehavior = scrollBehavior,
                            navigationIcon = navigationIcon,
                            actions = actions,
                        )
                    } else {
                        SmallTopAppBar(
                            title = title,
                            subtitle = subtitle,
                            color = barColor,
                            scrollBehavior = scrollBehavior,
                            navigationIcon = navigationIcon,
                            actions = actions,
                        )
                    }
                }
            },
            popupHost = popupHost,
            // Horizontal only. Taking the vertical inset here too is what puts a stray
            // strip under the bar instead of letting content scroll beneath it.
            contentWindowInsets = WindowInsets.systemBars
                .add(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal),
        ) { innerPadding ->
            Box(
                modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
            ) {
                content(innerPadding)
            }
        }
    }
}
```

`MiuixIcons.Regular.Back` 与它的 import `top.yukonga.miuix.kmp.icon.MiuixIcons` 沿用
`ui/MainScreen.kt:35,65` 里已经在用的那个符号，不要另找。

- [ ] **Step 4: Material 侧**

`MaterialComponents.kt` 的文件头（`:48-54`）写明了它**刻意不用** `TopAppBar`，因为它还在
`@ExperimentalMaterial3Api` 后面、会把 opt-in 摊到每个屏幕。这条决定继续遵守：Material 侧复用
现有的 `MaterialTopBar`（`:66-95`），**不要**引入 `TopAppBar` / `LargeTopAppBar`。

```kotlin
@Composable
internal fun MaterialScreen(
    title: String,
    modifier: Modifier,
    subtitle: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    largeTitle: Boolean,
    popupHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    // subtitle, largeTitle and popupHost have no Material counterpart here: this skin's
    // bar is the hand-built one, it does not collapse, and Material 3's Scaffold has no
    // popup slot. They stay in the signature so both skins take the same call.
    Scaffold(
        modifier = modifier,
        topBar = {
            MaterialTopBar(
                title = title,
                navigationIcon = onBack?.let { back ->
                    { O2AIconButton(MiuixIcons.Regular.Back, "返回", back) }
                },
                actions = actions,
            )
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        content = content,
    )
}
```

`androidx.compose.material3.Scaffold` 的 `contentWindowInsets` 是稳定 API，不需要 opt-in。

- [ ] **Step 5: 本机静态确认**

Run: `cd /root/work/o2a && grep -n 'contentWindowInsets\|layerBackdrop\|only(WindowInsetsSides.Horizontal)' android/app/src/main/java/com/opencode2api/android/ui/component/MiuixComponents.kt && grep -rn 'O2AScreen(' android/app/src/main/java/ | grep -v 'ui/component/' | wc -l`

Expected: miuix 侧三处都在；最后一个计数为 `0`（还没有调用点）。

- [ ] **Step 6: 推送并等 CI**

```bash
cd /root/work/o2a && git add -A android/app/src/main/java/com/opencode2api/android/ui/
git commit -m "feat(android): add the screen skeleton every screen will move to"
git push origin android-port
gh run watch <run-id> --exit-status
```

Expected: 绿。

---

### Task 6: `O2ADropdownRow`

**Files:**
- Modify: `.../ui/component/O2AComponents.kt`
- Modify: `.../ui/component/MiuixComponents.kt`
- Modify: `.../ui/component/MaterialComponents.kt`

**Interfaces:**
- Consumes: Task 1 的 `miuix-preference`
- Produces: `@Composable fun O2ADropdownRow(title, items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier, summary, enabled)`

- [ ] **Step 1: 中性声明**

```kotlin
/**
 * A row that shows the current choice and opens a menu of the others. Settings screens use
 * this where a chip row would show every option at once: past four or five, the menu is the
 * calmer control.
 *
 * [selectedIndex] indexes [items]; -1 means nothing is chosen yet.
 */
@Composable
fun O2ADropdownRow(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) { … }
```

- [ ] **Step 2: miuix 侧**

```kotlin
OverlayDropdownPreference(
    items = items,
    selectedIndex = selectedIndex,
    title = title,
    modifier = modifier,
    summary = summary,
    enabled = enabled,
    onSelectedIndexChange = onSelect,
)
```

**不要动 `renderInRootScaffold`** —— 它的默认 `true` 正是浮层由最外层 Scaffold 渲染的原因；跟着
Spec 第 4 节，外壳那个 Scaffold 不覆盖 `popupHost`，两边一起才成立。

- [ ] **Step 3: Material 侧**

用**稳定的** `DropdownMenu` + `DropdownMenuItem`，不用 `ExposedDropdownMenuBox` —— 后者在
`@ExperimentalMaterial3Api` 后面，与这个文件「只用稳定 API」的立场冲突（见 Task 5 Step 4）。
行本体复用现成的 `MaterialRow`（`:139-170`），它已经处理好了可点与不可点两种形态，所以**不需要
为这一行单独加右箭头**：

```kotlin
@Composable
internal fun MaterialDropdownRow(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    summary: String?,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        MaterialRow(
            title = title,
            modifier = Modifier,
            summary = summary,
            trailing = {
                Text(
                    text = items.getOrElse(selectedIndex) { "" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = if (enabled) ({ expanded = true }) else null,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}
```

这里 `Box` 是必需的：`DropdownMenu` 要靠它定位到行上。

- [ ] **Step 4: 本机静态确认**

Run: `cd /root/work/o2a && grep -n 'renderInRootScaffold\|OverlayDropdownPreference' android/app/src/main/java/com/opencode2api/android/ui/component/MiuixComponents.kt && grep -rn 'O2ADropdownRow(' android/app/src/main/java/ | grep -v 'ui/component/' | wc -l`

Expected: `OverlayDropdownPreference` 在；`renderInRootScaffold` **不在**（没被改写）；计数为 `0`。

- [ ] **Step 5: 推送并等 CI**

```bash
cd /root/work/o2a && git add -A android/app/src/main/java/com/opencode2api/android/ui/component/
git commit -m "feat(android): add the dropdown row the settings screens will use"
git push origin android-port
gh run watch <run-id> --exit-status
```

Expected: 绿。

---

## 后续批次（各自另开计划）

第一批刻意不含任何调用点，所以它与后面几批之间没有必须一起改的接口。后面每一批都能独立出包、
独立 CI 绿，所以按 `writing-plans` 的规矩各写一份：

- **第二批 —— 外壳。** `Route` 加 `@Serializable` + `NavKey`；删 `Route.TabRoute` 并改四个调用点；
  新建 `ui/AppNavHost.kt`；`MainPagerState`；`MainScreen` 重写为 pager + 两条栏（先用普通
  `NavigationBar` / `SideRail`）。**这一批要顺带在 CI 里加一条静态检查**：断言外壳那个 Scaffold
  没有覆盖 `popupHost` —— 这是唯一一条「编译绿、设备上静默失效」的风险（Spec 反目标第 4 条）。
  回归风险最大的一批。
- **第三批 —— 液态玻璃浮动底栏。** 搬 `ui/component/liquid/` 四文件、`ui/component/animation/`
  两文件、`FloatingBottomBar.kt`，接进底栏位，加 `glassAvailable` 闸门与实心回退分支。单独一批，
  因为它是唯一带 AGSL shader 与传感器的代码。**那四个文件的 Apache-2.0 派生声明要逐字保留。**
  另注：Spec 第八节说 `dropShadow` 会「第一批编译确认」，但第一批并不引用它 ——
  那个 API 到这一批才第一次上编译，确认点在这里。
- **第四批 —— 十四屏。** 按 Spec 第 4 节分成列表型与非列表型两类改造，行控件换 preference 族，
  删 `O2ASectionTitle`（十四条调用）。日志页先摊平。按屏一个小提交。
- **第五批 —— 外观开关**（模糊 / 浮动底栏 / 浮动底栏模糊三个 `SwitchPreference` 进 `MoreScreen`
  的「外观」组，值进 `data/SettingsStore.kt`）。
- **第六批（可选）—— 搜索浮层 / 下拉刷新 / `rememberContentReady`。**

## 本批的完成判据

- 六个任务的提交都在 `android-port` 上，每一次推送的 `android` workflow 都绿。
- `Verify the APK carries a runnable gateway` 一步始终通过：`extractNativeLibs` 与两个 ABI 的
  `.so` 都还在包里。这条不能因为加依赖而丢。
- 下面这些在 App 里都**还没有调用点**（本批保证观感不变）：
  `rememberBlurBackdrop`、`BlurredBar`、`O2AScreen`、`O2AList`、`o2aGroup()`、`O2ADropdownRow`、
  `O2APressFeedback`。逐条用 `grep` 确认过。
- APK 体积记录一下：模糊与 nav 会带进一些代码和资源。若超过 45 MB，在提交信息里问一句为什么。
- **本批没有、也不可能有单元测试**（`android/app/src/` 下只有 `main/`，CI 只跑 `assembleDebug`）。
  因此本批交付的保证只有「编译通过 + APK 结构正确」，观感与行为一律未验证 —— 这一点在向用户汇报时
  要照实说。
