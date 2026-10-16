# 让 Android 界面更贴近 SukiSU-Ultra

日期：2026-10-16
分支：`android-port`
状态：待批准（本文档批准前不动代码）

## 一、意图

用户看了当前构建后说「都不像」。目标是把 opencode2api 的 Android 客户端做得**看起来就是
SukiSU-Ultra 那一套界面**：同一个外壳、同一套设置页排版、同一族行控件。不是「类似风格」，是
「同一个东西」。

需要说清楚的是：**配色层已经像了**。`ui/theme/Theme.kt` 的 `ColorMode` 枚举、miuix 与 Material
两套主题的 `resolveSeedColor` / material-kolor `PaletteStyle` / `ColorSpec` 管线，都是照着它的
`ui/theme/` 抄的，逐项对过。不像的地方**全在外壳和屏幕骨架**，不在颜色。这也是为什么第一眼
看「都不像」却很难说出哪里不对。

## 二、诊断

### 已经一致（本次不动）

| 位置 | 说明 |
| --- | --- |
| `ui/theme/Theme.kt` | `ColorMode`（SYSTEM/LIGHT/DARK/MONET_*/DARK_AMOLED）与它逐值相同 |
| `ui/theme/MiuixTheme.kt` | `ThemeController(colorSchemeMode, keyColor, colorSpec, paletteStyle, isDark)`，参数与它一致 |
| `ui/theme/MaterialTheme.kt` | 同上 |
| `ui/UiMode.kt` | miuix / material 双皮肤开关，与它的 `LocalUiMode` 同构 |
| `gradle/libs.versions.toml` | agp 9.4.1 / kotlin 2.4.20 / compose-bom 2026.09.00 / miuix 0.9.4 / material-kolor 5.0.1 与它同版本 |

### 不一致（本次要改）

1. **顶栏在外壳里，只有一根。** `ui/MainScreen.kt` 在 `O2AScaffold(topBar = …)` 里放一个
   `O2ATopBar`，标题跟着当前路由走。SukiSU 是**每个屏幕自己持有顶栏**，外壳一根都没有；
   这决定了下面第 2、3 条能不能成立。
2. **没有模糊。** 它的顶栏是 `BlurredBar(backdrop) { TopAppBar(color = barColor) }`，内容从
   栏下滚过去，栏是半透明毛玻璃；`barColor` 在模糊可用时是 `Color.Transparent`，不可用时退回
   `colorScheme.surface`。我们完全不透明。
3. **没有转场。** 它用 miuix `NavDisplay` + `NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius())`：
   进入详情页时圆角裁剪 + 视差，且有侧滑返回（每个 `entry` 传 `swipeDismiss`）。我们是
   `mutableStateListOf<Route>` 硬切，`BackHandler` 手接。
4. **标签页不可滑，且结构不对。** 它五个标签在一个 `HorizontalPager` 里，跟手滑、松手 spring
   吸附。更关键的是**它把 pager 和底栏放在根条目内部**（见下），而不是像我们这样放在外壳
   Scaffold 的 `bottomBar` 槽里 —— 结果是：**从根条目点进任何详情页，底栏和 pager 一起被盖住**。
   我们的底栏在详情页下面还杵着。
5. **底栏是钉死的。** 它是浮动胶囊（`enableFloatingBottomBar`）或贴着底边的 `NavigationBar`，
   两种情况都套 `BlurredBar`；宽屏走 `SideRail`。我们的 `NavigationBar` 钉在底边，不透明，
   宽屏也不变。
6. **屏幕骨架不一样（这条最影响观感）。** 标准配方是：
   ```kotlin
   val scrollBehavior = MiuixScrollBehavior()
   val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
   Scaffold(
       topBar = { BlurredBar(backdrop) { TopAppBar(title, scrollBehavior, color = barColor) } },
       popupHost = { },   // 有搜索的屏幕挂这里；没有就给空
       contentWindowInsets = WindowInsets.systemBars
           .add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
   ) { innerPadding ->
       LazyColumn(
           modifier = Modifier.fillMaxHeight().scrollEndHaptic().overScrollVertical()
               .nestedScroll(scrollBehavior.nestedScrollConnection),
           contentPadding = innerPadding,        // 不是 Modifier.padding
           overscrollEffect = null,              // 交给 overScrollVertical
       ) {
           item { Card(Modifier.padding(top = 12.dp)) { … } }   // 组 = 一张裸卡
           item { Spacer(Modifier.height(bottomInnerPadding)) } // 收尾
       }
   }
   ```
   我们用 `Column(verticalScroll)` + `O2ASectionTitle` 分组标题 + `O2AColumn(vertical = 8.dp)`。
   **它一组就是一张裸 `Card`，卡片之间只有 `top = 12.dp`，没有分组标题、没有分隔线。**
7. **行控件不同族。** 我用 `BasicComponent` 手搓；它的行来自 `miuix-preference`：
   `ArrowPreference`（自带右侧箭头，10×16 dp，RTL 翻转，颜色 `onSurfaceVariantActions`）、
   `SwitchPreference`、`OverlayDropdownPreference`。`startAction` 的图标统一带
   `padding(end = 6.dp)`。
8. **卡片没有按压反馈。** miuix `Card` 的默认是 `pressFeedbackType = None`、
   `showIndication = false`，它显式改成 `Sink`/`Tilt` + `showIndication = true`。我们的
   `O2ACard` 用默认值，点下去毫无反应。
9. **缺搜索浮层、下拉刷新、命令式对话框、延迟内容。** 它有 `SearchStatus` 四态机 + 顶栏
   `bottomContent` 里的假输入框 + `popupHost` 浮层；`PullToRefresh`；`rememberConfirmDialog()` /
   `rememberLoadingDialog()` 这类把对话框当句柄用的写法；`rememberContentReady()` 读
   `LocalNavTransitionScope.current.isRunning` 把重内容推迟到转场结束。
10. **没有液态玻璃。** 它的浮动底栏是一块真的玻璃：`drawBackdrop` 上叠 `vibrancy()` +
   `blur(4.dp)` + `lens(refraction 24.dp)`，选中项下面是一颗会折射、带色散（chromatic
   aberration）与按压内阴影的胶囊，边缘高光跟着重力方向转。我们只有一块不透明底栏。
   **这一条用户明确要求搬**（见「七、裁定 2」）。

## 三、目标 / 非目标 / 反目标

**目标**

- 从列表页点进详情页有圆角裁剪转场，能侧滑返回。
- 标签页跟手滑，松手吸附；**底栏和 pager 一起属于根条目，点进详情页后两者都被盖住**。
- 每个屏幕顶部有自己的一根栏，内容从栏下滚过，开启模糊时是毛玻璃。
- 设置类列表的排版与它一致：裸卡分组、无分组标题、裸卡之间 12 dp。
- 行控件换成 preference 族，箭头/开关/下拉都是它那套。
- 卡片按下去有 `Sink` 反馈，首页那张大卡用 `Tilt`。
- **浮动底栏是真的液态玻璃**：折射、色散、按压内阴影、跟着重力转的边光，不是一块半透明色块。

**非目标**

- 不搬它那些与网关管理台无关的功能，见「七、裁定」。
- **本次不做导航角标。** 理由见裁定 3。
- 不改配色层，它已经一致。
- 不引入 androidx navigation3 / navigation-compose。（它那个 `ui/navigation3/` 是**它自己的
  包名**，底下跑的是 miuix `NavDisplay`，不是 androidx 的库。）

**反目标（做了就算失败）**

- 为了「像」而砍掉任何一项网关管理功能，或让某个屏幕变得不能操作。
- 把 Material 皮肤搞坏 —— 它必须仍然可选、仍然能编译、仍然每个屏幕都有。
- 静态一致但手感不对：比如转场照抄了但滑动手势没接、或者模糊照抄了但每次滚动掉帧。
- **编译通过但设备上静默失效**：最典型的是下拉菜单不弹（见第 4 节 popup host 那条）。

## 四、约束

- **全程 GitHub Actions 代替本机。** 本机不编译、不跑 Gradle。每一次提交的验证都是 CI
  跑 `.github/workflows/android.yml`。这意味着改动要**分批落地**，每批自己 CI 绿，否则一次
  编译错误里混着十四个屏幕的改动，定位成本极高。
- `minSdk` 26、`targetSdk` 36、`compileSdk` 37.0。miuix 0.9.4 与 material-kolor 5.0.1 都要求
  消费方按 37 编译，这个不能动。
- 还没有在真机上装过。本次改动完成后，「编译通过 + APK 结构正确 + 静态检查通过」仍然是能
  给出的全部保证；转场、模糊、手势、下拉刷新都只有真机能验。
- 语言：代码注释与 UI 文案维持现状（注释英文、文案中文）。

## 五、设计

### 1. 依赖与清单

`gradle/libs.versions.toml` 增加三个坐标（版本沿用 `miuix = "0.9.4"`）：

```toml
miuix-nav = { module = "top.yukonga.miuix.kmp:miuix-nav-android", version.ref = "miuix" }
miuix-preference = { module = "top.yukonga.miuix.kmp:miuix-preference-android", version.ref = "miuix" }
miuix-blur = { module = "top.yukonga.miuix.kmp:miuix-blur-android", version.ref = "miuix" }
```

外加 `androidx.navigationevent:navigationevent-compose`（`miuix-nav` 的 `NavDisplay` 直接
`import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner`，SukiSU 显式声明了
它，照做）。版本对齐 SukiSU 的 `1.1.2`。

同时**删掉 `libs.versions.toml` 里那段说 miuix-blur 用不了、所以只依赖两个模块的注释**——它
是错的，见下。

`android/app/src/main/AndroidManifest.xml` 在 `<uses-permission>` 之后、`<application>` 之前
加一行：

```xml
<!-- miuix-blur 声明的 minSdk 高于本应用的 26；它在低于该版本的系统上不工作，但
     调用点有 isRenderEffectSupported() 兜底，所以合并时放行。 -->
<uses-sdk tools:overrideLibrary="top.yukonga.miuix.kmp.blur" />
```

`android/app/build.gradle.kts` 的 `dependencies` 加这三个 `implementation`。

**关于这一行我犯过的错。** 早先我认为 miuix-blur 的 minSdk 高于 26、本应用不能用，于是把它
删了（提交 `cc61479`）。那是错的：SukiSU 同样 `minSdk 26`，它照用 miuix-blur，靠的就是
`tools:overrideLibrary` 放行合并 + 运行时 `isRenderEffectSupported()` 兜底。SukiSU 的清单里
`overrideLibrary` **只点了 blur 一个**，这本身也说明另外四个模块（ui/icons/nav/preference）在
26 上不需要放行；如果 CI 的清单合并阶段报出别的模块，再补对应条目或抬 minSdk，这是这条路上
唯一一个「等 CI 告诉我们」的点。

### 2. 模糊：两个小函数

新增 `ui/util/BlurExt.kt`，与它的 `ui/util/BlurExt.kt` 逐行对应：

```kotlin
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }
}

@Composable
fun BlurredBar(backdrop: LayerBackdrop?, blurActive: Boolean = true, content: @Composable () -> Unit) {
    Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop, shape = RectangleShape, blurRadius = 25f,
                colors = BlurColors(blendColors = listOf(BlendColorEntry(
                    color = MiuixTheme.colorScheme.surface.copy(0.87f)))),
            )
        } else Modifier,
    ) { content() }
}
```

`rememberBlurBackdrop` 返回 `null` 时，所有调用点自动退回不透明栏，不需要各自判断。

### 3. 外壳

这一节的细节**是从它的 `MainActivity.kt` 实际读出来的，不是推的**，因为它和我原来的假设不同。

它的结构：

```kotlin
// 外壳：只有一个 Scaffold，content 就是 NavDisplay，没有 topBar/bottomBar，
// 也不覆盖 popupHost（这点很要紧，见第 4 节）。
when (uiMode) {
    UiMode.Material -> androidx.compose.material3.Scaffold(containerColor = …) { navDisplay() }
    UiMode.Miuix    -> Scaffold { navDisplay() }
}

// NavDisplay 就是全部导航
NavDisplay(
    backStack = navigator.backStack,
    effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
    onBack = { … navigator.pop() },
) {
    entry<Route.Main>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
    entry<Route.About>(swipeDismiss = swipeDismiss) { AboutScreen() }
    … 其余详情页 …
    entry<Route.Home>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
    entry<Route.SuperUser>(…) { mainScreenEntry() }   // 同一批标签页
    entry<Route.Module>(…) { mainScreenEntry() }
    entry<Route.Settings>(…) { mainScreenEntry() }
}
```

三条要点：

- **pager 和底栏在 `MainScreen` 里，也就是在 `entry<Route.Main>` 内部**，不在外壳的
  `bottomBar` 槽里。所以点进任何详情页，**底栏和 pager 一起被盖住**。我们现在的做法（底栏挂在外壳、
  详情页盖不住它）是错的，要改。
- **`Route.Home` / `Route.SuperUser` / `Route.Module` / `Route.Settings` 全都在 NavDisplay 里
  注册，而且都渲染同一个 `MainScreen`**，只是 `initialPage` 不同。它们的作用是「从外部带参数
  进入某个标签」（快捷方式、通知、intent），**不是**运行期切标签。运行期切标签靠 pager 或底栏，
  两者都只动 `MainPagerState.selectedPage`。
- 系统返回键由 `MainScreenBackHandler(mainPagerState, navController)` 接：在根条目上、栈里只有
  一个条目、且 `selectedPage != 0` 时，回退是**回到第 0 页**，否则才退出。

**本应用照此改写：**

`ui/MainScreen.kt` 拆成两个东西：

- **`ui/AppNavHost.kt`（新）** —— 对应上面那段：miuix `Scaffold { NavDisplay(...) }`，
  `effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius())`，每个
  `entry` 传 `swipeDismiss`。`Route.Main` 是根条目，渲染 `MainScreen()`。
- **`ui/MainScreen.kt`（重写）** —— 只负责 pager + 两条栏：`HorizontalPager`（五个标签，
  `flingBehavior(snapAnimationSpec = PagerNavigationSpringSpec)`）+ 底栏 / `SideRail`，
  外加 `MainScreenBackHandler` 那套返回行为。
- **`MainPagerState`（新，放 `ui/component/` 或 `ui/navigation3/`）** —— `selectedPage` +
  `animateToPage` + `isNavigating` + `syncPage`，**照抄它的 `component/bottombar/BottomBar.kt`**。
  它解决的正是「点了底栏要立刻高亮、但 pager 还在滑」这个矛盾，自己另写一遍大概率滑错。

**导航栈与路由。** `Route` 改为实现 miuix 的 `NavKey`，整个 sealed 层次加 `@Serializable`、
每个子类型加 `@SerialName`。理由不是风格：`miuix-nav` 的 `rememberNavBackStack<Route>(…)` 用
reflection-free 的 `serializer<List<T>>()` 做 `rememberSaveable`，非 `@Serializable` 的 key 会
**抛 `SerializationException` 而不是静默丢弃**，漏一个就当场炸。

**`Route.TabRoute` 删除。** 这是本次唯一一处需要改调用点的架构改动，理由：`NavDisplay` 按
**运行时类型精确匹配**条目，`TabRoute(tab)` 若注册成一个条目，每个标签值各有自己的 saveable
状态命名空间，等于「切标签时又推了一份完整外壳」。而 SukiSU 的做法是标签由 `selectedPage`
驱动。所以：

- 四个现有调用点（`MoreScreen`→MODELS、`HomeScreen`→LOGS、`GuideScreen`→CONFIG、
  `UsageScreen`→MODELS）从 `onNavigate(Route.TabRoute(x))` 改成 `onSelectTab(Tab.x)`。
- `onSelectTab` 由 `AppNavHost` 提供，行为是 **`selectedPage = tab.ordinal` + 弹回根条目**。
  到那时详情页被关掉，用户落到目标标签上 —— 与原行为视觉上一致，但少了一层假条目。

### 4. 屏幕骨架

`ui/component/O2AComponents.kt` 增加一层，屏幕只写一次、两套皮肤各自实现：

- **`O2AScreen(title, subtitle, onBack, actions, largeTitle, popupHost, content)`** —— 一根栏的
  全部。miuix 侧是 `BlurredBar(backdrop) { TopAppBar(title, scrollBehavior, color = barColor) }`，
  Material 侧是 `TopAppBar` 配自己的 scroll 行为。内部 remember `MiuixScrollBehavior` 与
  `backdrop`，并通过 `LocalO2AScrollBehavior` 往下传。
  - `largeTitle: Boolean = true`。为 `true` 时用大标题可折叠的 `TopAppBar`（需要 scrollBehavior，
    这正是整条配方存在的原因）；为 `false` 时用 `SmallTopAppBar`，即**现在这个代码库当初刻意选的**
    那个（`ui/component/MiuixComponents.kt` 里那句注释：「大标题在手机首页很迷人，在密集的工具页
    是错的」）。SukiSU 两个都用，按屏幕分。所以这个开关要留着，而不是一刀切成大标题。
- **`O2AList(content: LazyListScope.() -> Unit, bottomPadding: Dp)`** —— 第 6 条那条
  `LazyColumn(…scrollEndHaptic().overScrollVertical().nestedScroll(behavior)…)` 链子，
  `contentPadding = innerPadding`、`overscrollEffect = null`、水平 12 dp、末尾自动补
  `Spacer(height = bottomInnerPadding)`。屏幕不用再记得这五件事。
- **`Modifier.o2aGroup()`** = `fillMaxWidth().padding(top = 12.dp)`，即「一张裸卡」的间距。

**`O2AList` 是给列表型屏幕用的，不是十四屏通用。** `LazyColumn` 套 `LazyColumn` 不合法，而现在
`LogsScreen` 正是「`Column` → `O2ACard` → `LazyColumn`」的嵌套结构，`Playground` / `Config` 也不是
列表。所以第三批分两类处理：

- **列表型**（运行桌面、配置中心、模型、更多、用量、可用性、轮转、API Key、关于）：换成
  `O2AScreen` + `O2AList`，每张卡一个 `item { … }` 带 `o2aGroup()`。
- **非列表型**（日志、Playground、接入、诊断、首次运行）：保留自己的滚动容器（`Column` +
  `verticalScroll`，或就地摊平成外层那个唯一的 `LazyColumn`），只把 `O2AScreen` 的栏与
  `o2aGroup()` 的间距套上去。**日志页需要先摊平**：它的日志行要从内层 `LazyColumn` 变成外层
  `LazyColumn` 的 `items()`，否则不合法。

**popup host 有一条会静默失效的坑（必须照做）。** `O2ADropdownRow` 用的
`OverlayDropdownPreference` 默认 `renderInRootScaffold = true`，它的浮层由**最外层** Scaffold 的
`popupHost` 渲染。读过 miuix `Scaffold` 源码：`LocalPopupStates provides popupStates` 是无条件的
（内层 Scaffold 永远有自己的宿主），而 `LocalRootPopupStates provides parentRootPopupStates ?: popupStates`
只认最外层那个。所以：

- **外壳（`AppNavHost`）的那个 Scaffold 绝对不能覆盖 `popupHost`**，必须留默认的
  `{ MiuixPopupHost() }`，否则每一个 `O2ADropdownRow` 都不弹 —— 编译通过、CI 绿、只在设备上坏。
- `O2AScreen` 的 `popupHost` 参数默认给 `{}`（内层宿主是空的没关系，浮层走根宿主）；只有需要
  搜索浮层的屏幕才传入内容，且**这是加在根宿主之外的额外一层，不是替换**。

**`O2ACard` 加按压反馈。** miuix 侧传 `pressFeedbackType = PressFeedbackType.Sink`、
`showIndication = true`；首页那张主卡用 `Tilt`（对应它 Home 的 `PressFeedbackType.Tilt`）。
Material 侧用 `indication` + `ripple` 对齐。

**`O2ASectionTitle` 删除。** 它存在的理由（照 WebUI 的分组标题）在 SukiSU 的骨架里不成立：
那边**没有分组标题**，分组就是「另一张卡」。十四条调用全部删掉，卡之间靠 `o2aGroup()` 的
12 dp 区分。这一步会把所有屏幕都碰一遍，所以放在第三批。

**行控件换族。** `O2ARow` / `O2ASwitchRow` 在 miuix 侧改由 `miuix-preference` 实现：

| 中性 API | miuix 侧 |
| --- | --- |
| `O2ARow(onClick != null)` | `ArrowPreference(title, summary, startAction, endActions, onClick)` |
| `O2ARow(onClick == null)` | `BasicComponent`（**不带箭头**，理由见裁定 5） |
| `O2ASwitchRow` | `SwitchPreference(checked, onCheckedChange, …)` |
| `O2ADropdownRow`（新） | `OverlayDropdownPreference` |

`ArrowPreference` **总是**画右箭头（`ArrowPreferenceEndAction` 是它自带的一部分），所以不可点的
行不能走它。`startAction` 的图标统一 `padding(end = 6.dp)` —— 这是它全项目最常用的一个数字。
另注：`PreferenceDefaults.InsideMargin` 与 `BasicComponentDefaults.InsideMargin` 默认值不同，
换族时卡内边距会变，逐屏看一眼。

`O2AChipRow` / `O2ASelectableChip` 保留，但只用在真正需要「一眼看全、就地切换」的地方。设置
类页面里「从 N 个里选一个」改用 `O2ADropdownRow`，这才和它一致。初步划定：`MoreScreen` 的界面
风格 / 主题模式 / 运行模式、`PlaygroundScreen` 的上游 Key 改成下拉；Playground 的入口协议保留
chip（三个协议并排看比收进菜单有用）。

### 5. 外观开关

它的 `LocalEnableBlur`、`LocalEnableFloatingBottomBar`、`LocalEnableFloatingBottomBarBlur`
**默认全是 `false`**。也就是说它默认的观感是：不模糊的顶栏 + 贴底的 `NavigationBar`，
浮动胶囊和模糊都是用户自己开的。

所以我们照它的默认来，同时把开关放到已有的「外观」区（`MoreScreen`）里：

- 「界面模糊」→ `LocalEnableBlur`
- 「浮动底栏」→ `LocalEnableFloatingBottomBar`
- 「浮动底栏模糊」→ 仅在浮动底栏开启时显示

这三个值进 `data/SettingsStore.kt`，与已有的 `uiMode` / `themeMode` / `monet` 并列。
`LocalEnable*` 三个 `staticCompositionLocalOf { false }` 放 `ui/UiMode.kt` 旁边。

**不新增独立的外观页**：`MoreScreen` 已经有「外观」一组（界面风格、主题模式、系统取色），
放这里是延续，不是新概念。

### 6. 液态玻璃（浮动底栏）

用户在看过第一版设计后明确要求搬这一块 —— 我原先把它砍了，见裁定 2。读完源码后它比看起来
好办：**整层是薄薄搭在 `miuix-blur` 上的**，没有引入任何第三方图形库，shader 之外没有自造的
绘图机制。

**要搬的文件**（对应它的 `ui/component/`）：

| 新文件 | 内容 |
| --- | --- |
| `ui/component/liquid/Lens.kt` | AGSL 折射 shader，含色散变体 |
| `ui/component/liquid/Vibrancy.kt` | 三行：`colorControls(saturation = 1.5f)` |
| `ui/component/liquid/InnerShadow.kt` | 自成一体的 `DrawModifierNode` |
| `ui/component/liquid/CombinedBackdrop.kt` | 把两个 `Backdrop` 叠成一个 |
| `ui/component/animation/DampedDragAnimation.kt` | 157 行，按压 / 拖拽 / 速度 |
| `ui/component/animation/InteractiveHighlight.kt` | 128 行，跟随手指的高光点 |
| `ui/component/FloatingBottomBar.kt` | 514 行，合成体 |

**它的结构（三层，缺一层就不像）：**

1. **底板** —— 64 dp 高的 `Row`，套 `drawBackdrop(shape = CircleShape)`，效果块是
   `padding = maxOf(padding, 40.dp.toPx())` → `vibrancy()` → `blur(4.dp.toPx(), 4.dp.toPx())` →
   `lens(refractionHeight = 24.dp.toPx(), refractionAmount = 24.dp.toPx())`，外面套
   `dropShadow(radius = 10.dp, black, alpha 0.1 / 0.2)`，`onDrawSurface = { drawRect(containerColor) }`
   把 `surfaceContainer.copy(0.4f)` 铺上去。
2. **一层看不见的「幽灵副本」** —— 同一套标签内容再画一遍，`alpha(0f)` +
   `clearAndSetSemantics {}` + `layerBackdrop(tabsBackdrop)`，并给它
   `LocalContentColor provides accentColor`。它唯一的用处是**当移动胶囊的折射源**：胶囊滑到哪个
   标签，那个标签的图标与文字就在胶囊里显成强调色。这一手是整块玻璃的精髓，也最容易漏。
3. **移动胶囊** —— `drawBackdrop(combinedBackdrop, …)`，效果只给
   `lens(refractionHeight = 10.dp * pressProgress, refractionAmount = 14.dp * pressProgress,
   depthEffect = true, chromaticAberration = 0.5f)`，配
   `innerShadow(shape = CircleShape) { InnerShadow(radius = 8.dp * pressProgress, …) }`；
   `layerBlock` 里按速度做拉伸挤压（注意它是 `scaleX /= 1f - (velocity * 0.75f)…` 的除法写法）。
   胶囊位置由 `dampedDragAnimation.value * tabWidthPx` 驱动，`panelOffset` 是拖出边界时的橡皮筋
   位移。

**边光跟着重力转。** `rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees)` 读
`rememberDeviceTilt()`（加速度计，**不需要权限**），把角度量化到 3° 再算 `LightPosition`。
没有传感器的设备上 `DeviceTilt` 保持 0，`rememberQuantizedGravityAngle` 走 else 分支给
`-PI/2`，即固定一个角度 —— 天然降级，不用专门处理。

**运行时闸门（必须做，否则低版本设备上底栏会整条消失）。** `drawBackdrop` 内部是
`effectiveEnabled = enabled && isRuntimeShaderSupported()`（AGSL 要 API 33+），一旦为假，那个
modifier 就是**空的** —— 不是退化，是完全没有背景。所以：

```kotlin
val glassAvailable = isRenderEffectSupported() && isRuntimeShaderSupported()
val useGlass = enableFloatingBottomBar && enableFloatingBottomBarBlur && glassAvailable
```

`useGlass` 为假时走它的另一条分支：`clip(pillShape).background(accentColor.copy(alpha = 0.15f))`。
两条分支它都写好了，照搬即可。

**许可与署名。** 这四个 `liquid/*` 文件的头是
`// Adapted from Kyant0/AndroidLiquidGlass — … (Apache 2.0). Mirrored from compose-miuix-ui example.`，
`FloatingBottomBar.kt` 的头是
`// Adapted from compose-miuix-ui example (IosLiquidGlassNavigationBar) — Apache 2.0.`。
本仓库是 GPL-3.0，SukiSU-Ultra 也是 GPL-3.0，跨许可证问题不存在；但**这些头要逐字保留**，
因为那几个文件本身声明了它们派生自 Apache-2.0 的源码，notice 是要跟着走的。不要因为「换成
我们的包名」把这几行一起改掉。同时在 `docs/android.zh-CN.md` 里加一句交代这块玻璃的出处。

### 7. 搜索、下拉刷新、对话框（可选批次）

按价值排序，能不做就不做：

- **搜索浮层**（`SearchStatus` + `popupHost`）：只有日志页真的需要（日志会长）。做的话只给
  日志页与模型页。
- **`PullToRefresh`**：给运行桌面、日志、用量三个「有刷新按钮」的页面，把按钮换成下拉。
- **命令式对话框**（`rememberConfirmDialog` + `rememberLoadingDialog`）：现在危险操作是
  `AlertDialog` 直接内联。换成句柄式写法能少一大截样板，但**这是纯内部整洁，不影响观感**，
  放最后或不做。
- **`rememberContentReady()`**：读 `LocalNavTransitionScope.current.isRunning` +
  `withFrameNanos`，只粘一次。用在首页（它要读网关状态和一堆计数）与模型页（列表长）。

## 六、落地顺序

分批，每批自己 CI 绿。这是本设计里唯一必须有纪律的部分。

**第一批 —— 地基（不改任何屏幕的观感）**
`libs.versions.toml` 三个依赖 + `navigationevent`；清单加 `overrideLibrary`；`build.gradle.kts`
依赖；新增 `ui/util/BlurExt.kt`；`O2ACard` 加按压反馈；新增 `O2AScreen` / `O2AList` /
`O2ADropdownRow` / `Modifier.o2aGroup()`，**同时保留 `O2ASectionTitle`**。
产出：CI 绿，界面与现在**完全一致**（新 API 还没人调用）。这一步是在验证依赖与清单那件事，
把它单独隔出来——它最可能是唯一一个编译不过的地方。

**第二批 —— 外壳**
`Route` 加 `@Serializable` + `NavKey`；删 `Route.TabRoute` 并改四个调用点；`ui/AppNavHost.kt`；
`MainPagerState`；`MainScreen` 重写为 pager + 两条栏（先用普通的 `NavigationBar` / `SideRail`，
玻璃留到下一批）；五个标签页此时仍用旧骨架，但外壳已经换成新的。
产出：能滑、有转场、有底栏/侧栏、点进详情页会盖住底栏；**回归风险最大的一批**。

**第三批 —— 液态玻璃浮动底栏**
搬 `ui/component/liquid/` 四个文件、`ui/component/animation/` 两个文件、
`ui/component/FloatingBottomBar.kt`，接进 `MainScreen` 的底栏位；加 `glassAvailable` 闸门与
不透明回退分支。**单独一批**，因为它是唯一一块带 AGSL shader 与传感器的代码，编译问题与逻辑
问题容易混在一起。
产出：浮动底栏是玻璃，选中胶囊会折射、有色散、按下去有内阴影、边光跟重力转；在拿不到 AGSL
的设备上退回那块 `accent 15%` 的实心胶囊。

**第四批 —— 屏幕**
十四屏按第 4 节的两类分别改造，行控件换 preference 族，删 `O2ASectionTitle`。日志页先摊平。
产出：观感到位。按屏切，每屏一个小提交，CI 各自绿。

**第五批 —— 外观开关**，然后**第六批（可选）—— 搜索 / 下拉刷新 / 延迟内容**。

## 七、我的裁定

这些是我替用户定的，列出来以便否决。用户说的是「更拟合 SukiSU-Ultra」「都不像」，所以凡
「搬过来更像」与「按我们的情况改造更合理」冲突处，我一律偏向搬过来，除非那条对它没意义。

1. **搬外壳，且连「底栏属于根条目」这个结构一起搬。** 滑页、圆角转场、模糊栏、浮动底栏、宽屏
   侧栏、点进详情页盖住底栏，全搬。
2. **液态玻璃整套搬，不简化。** 第一版设计里我把它砍了，理由是「默认是关的、要传感器、
   只在真机上才敢说没问题」。用户否掉了这个理由，要求搬 —— 是对的：它默认关不假，但用户要的
   就是打开之后的那块东西，而「默认关」恰恰意味着它不影响默认观感的稳定性。整套搬，包括重力
   边光与色散。剩下的是诚实的风险交代：AGSL 与 `RenderEffect` 两条运行时闸门都必须有回退
   分支（第 6 节），设备上是否真的出效果、是否掉帧，只有真机能答，本次交付时它属于「未验证」。
3. **本次不做导航角标。** 我原先想把它那个角标位改成「日志未读错误数」，但查了之后站不住：
   `core/LogBus.kt` 里 `LogLine` 只有 `timestamp` / `Stream.STDOUT|STDERR` / `text`，**没有级别**，
   缓冲区是个 2000 条的 `ArrayDeque`，唯一可观察量是 `revision` 计数。要做就得先给 `LogBus`
   加「什么算错误」和「读没读过」两个新状态，而角标本身用户从没要求过。所以砍掉；将来真要做，
   那是 `LogBus` 的一件事，不是 UI 的一件事。
4. **语言层面跟随现有约定**：注释英文、文案中文，和这个仓库现在一致。
5. **`O2ARow` 不可点时不给箭头**，哪怕它内部用同一个 `ArrowPreference`。箭头是「点进去」的
   承诺，给了却不响应比不给更糟。
6. **`Route.TabRoute` 删掉**，切标签改走 `onSelectTab`。理由见第 3 节，这是 `NavDisplay` 的
   精确类型匹配逼出来的，不是审美选择。
7. **`O2ASectionTitle` 直接删**，不做兼容保留。分组标题在 SukiSU 骨架里是错的东西，留着
   只会有人再用。
8. **搜索浮层只给日志页和模型页**，不给全部十四屏。它那套四态机搬全了成本不低，而我们只有
   两屏长到需要搜。
9. **大标题按屏决定**，默认开，密集工具页（Playground、配置中心的编辑器）关掉退回
   `SmallTopAppBar`。两个都留着。

## 八、留给实现的细节

不是问题，是交接说明。实现时自行判断即可。

- 已核对存在于 miuix 0.9.4（我读过 Maven 上的 sources jar）：`MiuixScrollBehavior`、`TopAppBar`、
  `SmallTopAppBar`、`ScrollEndHaptic`、`overScrollVertical`、`PressFeedbackType`、`Card` 的
  `showIndication`、`NavDisplay` / `NavDisplayEffects` / `rememberNavSystemCornerRadius` /
  `NavSwipeDirection` / `LocalNavTransitionScope` / `rememberNavBackStack` / `NavKey`、
  `LayerBackdrop` / `rememberLayerBackdrop` / `textureBlur` / `BlurColors` / `BlendColorEntry`、
  `isRenderEffectSupported`、`ArrowPreference` / `SwitchPreference` / `OverlayDropdownPreference`。
  具体参数名以 0.9.4 的 sources 为准，**不要照抄 SukiSU 在用的更新版本**。
- `springAnimateToPage` 与 `PagerNavigationSpringSpec` 已核对存在于 0.9.4 的
  `miuix-ui`（`top.yukonga.miuix.kmp.utils.PagerGestureUtils`），不必另找替代。
- 悬浮底栏的贴底距离它算的是 `WindowInsets.navigationBars` 非零时 `8.dp + inset`、否则
  `28.dp`；左右各 28 dp。
- 内容从模糊栏下滚过，靠的是 `contentWindowInsets` **只取水平方向**，让屏幕自己处理垂直
  inset。这一点写错就变成「栏下面凭空多一条白边」。
- `rememberNavigator(Route.Main)` 那种「Navigator 对象持有 backStack + pop/current」的小抽象
  自己写一个，约五六十行；不必引入 androidx 的任何导航库。
- 玻璃那一块已核对的 miuix-blur API：`Modifier.drawBackdrop(backdrop, shape, effects, highlight,
  layerBlock, onDrawBehind, onDrawBackdrop, onDrawSurface, onDrawFront, contentBlendMode,
  progressiveGradient, enabled)`、`Modifier.layerBackdrop(LayerBackdrop)`、
  `BackdropEffectScope.blur(radiusX, radiusY)`、`colorControls(...)`、`runtimeShaderEffect(...)`、
  `isRuntimeShaderSupported()`、`Backdrop` 接口（要实现的三个成员：`isCoordinatesDependent`、
  `offsetResidualX`、`offsetResidualY`、`drawBackdrop`），以及
  `top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt`。注意 `drawBackdrop` 自己就带
  `isRuntimeShaderSupported()` 闸门，所以**不要在 effects 块里再判一次**，判在外层决定走哪条
  分支。
- `dropShadow`（`androidx.compose.ui.draw.dropShadow` + `androidx.compose.ui.graphics.shadow.Shadow`）
  Compose BOM 2026.09.00 里有；第一批编译会确认。

## 九、验证与完成判据

本机不编译。每一批的验证都是 `.github/workflows/android.yml` 跑完并上传 APK。

- **能自动验的**：编译通过；`assembleDebug` 出包；`extractNativeLibs` 与两个 ABI 的
  `.so` 回读（这两条是既有检查，不能因为改动而丢）；APK 体积没有异常膨胀（模糊与 nav 会带
  进一些代码，超过 45 MB 要问一句为什么）。
- **只能真机验的**，本次完成后仍然只能列为未验证：转场圆角与跟手程度、侧滑返回、模糊是否
  真的出效果且不掉帧、pager 滑动吸附、下拉刷新、`rememberContentReady` 是否真的躲开了转场
  卡顿、`isRenderEffectSupported()` 在低版本设备上是否真的让栏退回不透明、以及下拉菜单是否
  真的会弹（第 4 节那条 host 坑）。
- **玻璃这块额外要在真机上看的**：AGSL 折射是否真的出效果（API 33 以下应当整块走实心回退）；
  色散在浅色与深色两种主题下是否都还能看；按压内阴影与速度挤压的方向感对不对；边光跟重力转
  是否跟手（或无传感器的设备上是否稳定停在一个角度）；以及**滚动时是否掉帧** —— 这块每帧要跑
  shader，是我们唯一一处可能拖垮滚动的代码。
- **完成判据**：五批落地、CI 全绿、`docs/android.zh-CN.md` 更新到与新骨架一致（它现在描述的
  是不带模糊、不带转场的老状态，会变成错的），并在「尚未在真机上安装运行过」那条限制上补充
  本次新引入的未验证项。
