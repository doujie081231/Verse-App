# Verse Explorer X - Design System

基于 Stripe 和现代 UI 设计最佳实践构建的安卓端设计系统。

## 1. Visual Theme & Atmosphere

融合金融级精致与现代科技感，打造专业级 Minecraft 启动器 UI。

### 核心特色
- **Signature Purple** (`#533AFD`) - 主品牌色，用于 CTA 和交互高亮
- **Deep Navy** (`#061B31`) - 深海军蓝，用于标题和重要文字
- **Stripe 多层蓝调阴影系统** - 品牌化的阴影深度感
- **轻量级字体** (Weight 300) - 自信、精致、不张扬

### 设计原则
- 精确、克制 - 避免过度装饰
- 金融级精致感 - Stripe 风格的专业度
- 移动优先 - 适配触屏交互
- 响应式 - 支持深色/浅色主题

---

## 2. Color Palette & Roles

### 主色调

| Token | Hex | 用途 |
|-------|-----|------|
| `Primary Purple` | `#533AFD` | 主要按钮、CTA、链接 |
| `Primary Purple Hover` | `#4434D4` | 悬停状态 |
| `Primary Purple Light` | `#B9B9F9` | 选中边框、浅色背景 |
| `Primary Purple Soft` | `#D6D9FC` | 柔和强调背景 |

### 深海军蓝 (Headings)

| Token | Hex | 用途 |
|-------|-----|------|
| `Deep Navy` | `#061B31` | 主要标题 |
| `Navy Label` | `#273951` | 表单标签 |
| `Navy Body` | `#64748B` | 正文、次要文本 |

### 功能色

| Token | Hex | 用途 |
|-------|-----|------|
| `Green Success` | `#15BE53` | 成功状态、安装完成 |
| `Red Danger` | `#DC2626` | 错误、删除、危险操作 |
| `Orange Warning` | `#EA580C` | 警告、老版本标识 |
| `Ruby` | `#EA2261` | 特殊强调 |
| `Magenta` | `#F96BEE` | 渐变装饰 |

### 背景色

| Token | Hex | 用途 |
|-------|-----|------|
| `Bg Primary` | `#FFFFFF` | 卡片背景 |
| `Bg Secondary` | `#F8F9FC` | 页面背景 |
| `Bg Tertiary` | `#F1F3F8` | 分组背景 |
| `Bg Hover` | `#F4F6FB` | 悬停背景 |
| `Bg Active` | `#EEF1F7` | 按下背景 |

### 深色主题

| Token | Hex | 用途 |
|-------|-----|------|
| `Dark Bg Primary` | `#0D0E14` | 深色页面背景 |
| `Dark Bg Secondary` | `#13141D` | 深色卡片背景 |
| `Dark Bg Card` | `#1E1F2A` | 深色表面 |
| `Dark Text Primary` | `#F8F9FC` | 深色文字 |

### 阴影色

| Token | Value | 用途 |
|-------|-------|------|
| `Shadow Blue` | `rgba(50,50,93,0.25)` | 主阴影 - 蓝紫色调 |
| `Shadow Dark Blue` | `rgba(3,3,39,0.25)` | 深色阴影 |
| `Shadow Black` | `rgba(0,0,0,0.10)` | 次要阴影 |
| `Shadow Ambient` | `rgba(23,23,23,0.08)` | 环境阴影 |

---

## 3. Typography Rules

### 字体家族
- **Primary**: 系统字体 (SF Pro / Roboto)
- **Weight 300**: 标题和正文 - 轻量自信
- **Weight 400**: 标准正文
- **Weight 500**: 中等强调
- **Weight 600**: 按钮文字

### 字号系统

| Token | Size | Weight | Line Height | Letter Spacing | 用途 |
|-------|------|--------|-------------|----------------|------|
| `Display Hero` | 48px | 300 | 50px | -0.96px | 英雄标题 |
| `Display Large` | 40px | 300 | 46px | -0.80px | 大标题 |
| `Section Heading` | 32px | 300 | 35px | -0.64px | 章节标题 |
| `Subheading Large` | 26px | 300 | 29px | -0.26px | 卡片标题 |
| `Subheading` | 22px | 300 | 24px | -0.22px | 小标题 |
| `Body Large` | 18px | 400 | 25px | 0 | 特性描述 |
| `Body` | 16px | 400 | 22px | 0 | 标准正文 |
| `Body Small` | 14px | 400 | 20px | 0 | 次要正文 |
| `Button` | 16px | 500 | 16px | 0 | 按钮文字 |
| `Button Small` | 14px | 500 | 14px | 0 | 小按钮 |
| `Caption` | 13px | 400 | 18px | 0 | 说明文字 |
| `Caption Small` | 12px | 400 | 16px | 0 | 标签 |
| `Label Small` | 11px | 500 | 14px | 0 | 小标签 |
| `Label Micro` | 10px | 500 | 12px | 0.1px | 微标签 |

---

## 4. Component Stylings

### Buttons

#### Primary Button
```kotlin
VerseButton(
    text = "开始安装",
    variant = VerseButtonVariant.PRIMARY,
    size = VerseButtonSize.MEDIUM
)
```
- Background: `#533AFD`
- Text: White
- Padding: 16px × 10px
- Radius: 6px
- Hover: `#4434D4`

#### Secondary Button
```kotlin
VerseButton(
    text = "取消",
    variant = VerseButtonVariant.SECONDARY
)
```
- Background: `#F8F9FC`
- Text: `#061B31`
- Border: none

#### Outlined Button
```kotlin
VerseButton(
    text = "查看详情",
    variant = VerseButtonVariant.OUTLINED
)
```
- Background: Transparent
- Text: `#533AFD`
- Border: 1px `#B9B9F9`
- Hover: Purple 5% overlay

#### Danger Button
```kotlin
VerseButton(
    text = "删除",
    variant = VerseButtonVariant.DANGER
)
```
- Background: `#DC2626`
- Text: White

#### Success Button
```kotlin
VerseButton(
    text = "安装完成",
    variant = VerseButtonVariant.SUCCESS
)
```
- Background: `#15BE53`
- Text: White

#### Gradient Button
```kotlin
VerseButton(
    text = "立即启动",
    variant = VerseButtonVariant.GRADIENT
)
```
- Background: Linear gradient `#533AFD` → `#7C3AED`

### Cards

#### Elevated Card
```kotlin
VerseCard(variant = VerseCardVariant.ELEVATED) {
    // Content
}
```
- Background: White
- Border: none
- Shadow: `0 3dp elevation`
- Radius: 8px
- Hover: Shadow intensifies

#### Outlined Card
```kotlin
VerseCard(variant = VerseCardVariant.OUTLINED) {
    // Content
}
```
- Background: White
- Border: 1px `#E5EDF5`
- Shadow: none
- Hover: Border turns purple soft

#### Glass Card
```kotlin
VerseCard(variant = VerseCardVariant.GLASS) {
    // Content
}
```
- Background: `rgba(255,255,255,0.9)`
- Border: subtle
- For overlays and modals

### Inputs

#### Text Field
```kotlin
VerseTextField(
    value = text,
    onValueChange = { text = it },
    label = "游戏目录",
    hint = "选择安装路径..."
)
```
- Border: 1px `#E5EDF5`
- Focus Border: `#533AFD`
- Error Border: `#DC2626`
- Radius: 6px

#### Search Field
```kotlin
VerseSearchField(
    value = query,
    onValueChange = { query = it },
    onSearch = { /* search */ }
)
```
- Icon: Search
- Clear button when has value
- Auto-focus on submit

#### Password Field
```kotlin
VersePasswordField(
    value = password,
    onValueChange = { password = it },
    label = "密码"
)
```
- Toggle visibility icon
- Password dots by default

### Chips

#### Filter Chip
```kotlin
VerseChip(
    text = "Fabric",
    selected = true,
    onClick = { /* toggle */ }
)
```
- Selected: Purple background, white text
- Unselected: Gray background
- Radius: 4px

#### Segmented Control
```kotlin
VerseSegmentedControl(
    options = listOf("正式版", "快照", "已安装"),
    selectedIndex = 0,
    onOptionSelected = { /* select */ }
)
```
- Tab-group style selection
- Smooth background animation

---

## 5. Layout Principles

### Spacing System
- Base unit: 4px
- Scale: 4, 8, 12, 16, 20, 24, 32, 40, 48, 64px
- Card padding: 16px
- Section gap: 24px
- Page margin: 16px (mobile)

### Border Radius
| Level | Radius | Use |
|-------|--------|-----|
| Micro | 4px | Chips, badges, small buttons |
| Standard | 6px | Buttons, inputs, cards |
| Comfortable | 8px | Large cards, dialogs |
| Large | 12px | Bottom sheets, modals |
| Full | 9999px | Pills, avatars |

### Elevation Levels

| Level | Treatment | Use |
|-------|-----------|-----|
| 0 | Flat | Inline elements |
| 1 | `2dp` | Small interactive |
| 2 | `3dp` | Standard cards |
| 3 | `6dp` | Dropdowns, hover |
| 4 | `8dp` | Modals, sheets |
| 5 | `12dp` | Tooltips |

---

## 6. Components Catalog

### Navigation

#### Bottom Navigation
```kotlin
VerseBottomNavigation(
    items = listOf(
        BottomNavItem("首页", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem("版本", Icons.Filled.List, Icons.Outlined.List, badgeCount = 3),
        BottomNavItem("下载", Icons.Filled.Download, Icons.Outlined.Download),
        BottomNavItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings)
    ),
    selectedIndex = 0,
    onItemSelected = { /* navigate */ }
)
```

### Status Components

#### Status Badge
```kotlin
VerseStatusBadge(status = StatusType.SUCCESS)  // SUCCESS, ERROR, WARNING, INFO, PENDING
```

#### Progress Bar
```kotlin
VerseProgressBar(
    progress = 0.65f,
    showPercentage = true
)
```

#### Circular Progress
```kotlin
VerseCircularProgress(progress = 0.75f)
```

### Empty States

#### Empty State
```kotlin
VerseEmptyState(
    icon = Icons.Outlined.SearchOff,
    title = "未找到结果",
    description = "尝试调整搜索关键词",
    action = { VerseButton(text = "清除搜索", ...) }
)
```

#### Loading State
```kotlin
VerseLoadingState(message = "正在下载...")
```

#### Error State
```kotlin
VerseErrorState(
    message = "网络连接失败",
    onRetry = { /* retry */ }
)
```

---

## 7. Do's and Don'ts

### Do ✅
- 使用轻量级字体 (Weight 300) 表示自信
- 使用 Purple (#533AFD) 作为主要交互色
- 使用 Deep Navy (#061B31) 作为标题色
- 保持 border-radius 在 4-8px 范围内
- 使用蓝调阴影增加品牌深度感
- 在深色背景上使用浅色文字

### Don't ❌
- 不要使用过粗的字体 (700+) 作为标题
- 不要在卡片上使用过大的圆角 (12px+)
- 不要使用纯黑 (#000000) 作为文字色
- 不要在交互元素上使用模糊阴影
- 不要混用多种主色调

---

## 8. Responsive Behavior

### Breakpoints
- Mobile: 0-599dp
- Tablet: 600-839dp
- Desktop: 840dp+

### Touch Targets
- Minimum: 48dp × 48dp
- Recommended: 56dp × 56dp
- Icon buttons: 40dp minimum

### Layout Adaptation
- Single column on mobile
- Two columns on tablet (version list)
- Three columns on desktop (resource grid)

---

## 9. Implementation Guide

### 导入组件
```kotlin
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.components.*
```

### 使用主题
```kotlin
VerseExplorerTheme(darkTheme = false) {
    // Your content
}
```

### 快速构建页面
```kotlin
@Composable
fun ExampleScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.BgSecondary)
            .padding(16.dp)
    ) {
        VerseSectionHeader(title = "版本列表")

        VerseCard(onClick = { /* install */ }) {
            Text("Minecraft 1.20.4", style = VerseTypography.Body)
            Spacer(Modifier.height(8.dp))
            VerseProgressBar(progress = 0.65f, showPercentage = true)
        }

        Spacer(Modifier.height(16.dp))

        VerseButton(
            onClick = { /* launch */ },
            text = "启动游戏",
            variant = VerseButtonVariant.GRADIENT,
            fullWidth = true
        )
    }
}
```

---

## 10. Dark Theme Support

所有组件均支持深色主题，自动适配 `VerseColors` 中的深色变量。

```kotlin
@Composable
fun AdaptiveCard(darkTheme: Boolean) {
    VerseCard(
        modifier = Modifier.background(
            if (darkTheme) VerseColors.DarkBgCard
            else VerseColors.BgCard
        )
    ) {
        Text(
            "内容",
            color = if (darkTheme) VerseColors.DarkTextPrimary
                    else VerseColors.TextPrimary
        )
    }
}
```

---

## Changelog

### v1.0.0 (2026-05-11)
- Initial design system based on Stripe
- Core components: Button, Card, Input
- Navigation components: BottomNav, Status, Progress
- Typography system with 14 text styles
- Complete color palette with light/dark themes


---

## 11. Stitch Minecraft Launcher Design System (v2.0)

The Stitch design layer introduces a dedicated Minecraft Launcher aesthetic, inspired by the official Minecraft Launcher. This system runs alongside the existing Stripe design system and is used for the core launcher experience.

### Minecraft Color Tokens

| Token | Hex | Use |
|-------|-----|-----|
| `McGreen` | `#5D8C3E` | Primary accent, buttons, selected states |
| `McGreenDark` | `#4A7030` | Darker green for gradients, hero backgrounds |
| `McGreenLight` | `#7BAF58` | Light green, subtle accents |
| `McBg` | `#1E1E2A` | Main page background |
| `McDarkBg` | `#14141E` | Darker background, hero base |
| `McSidebarBg` | `#1A1A28` | Sidebar background |
| `McCardBg` | `#262638` | Card and surface backgrounds |
| `McCardBorder` | `#3A3A52` | Card borders, dividers |
| `McTextPrimary` | `#E8E8E8` | Primary text on dark backgrounds |
| `McTextSecondary` | `#B0B0C0` | Secondary text |
| `McTextMuted` | `#787890` | Muted/placeholder text |
| `McGold` | `#FFAA00` | Snapshot badges, premium indicators |
| `McWater` | `#3F76E4` | Water/network color, info states |
| `McRedstone` | `#AA0000` | Danger/destructive actions |

### White Theme Color Tokens

| Token | Hex | Use |
|-------|-----|-----|
| `WhiteBg` | `#FAFAFA` | White theme page background |
| `WhiteBgCard` | `#FFFFFF` | White theme card surfaces |
| `WhiteBgSecondary` | `#F5F5F5` | Secondary white backgrounds |
| `WhiteBgInput` | `#F0F2F5` | Input field backgrounds |
| `WhiteTextPrimary` | `#1A1A2E` | Primary text on white |
| `WhiteTextSecondary` | `#4A4A5E` | Secondary text on white |
| `WhiteTextMuted` | `#787890` | Muted text on white |
| `WhiteBorder` | `#E2E5EA` | Borders and separators |
| `WhiteBorderFocus` | `#5D8C3E` | Focus state border |
| `WhiteAccent` | `#5D8C3E` | White theme accent (green) |
| `WhiteAccentHover` | `#4A7030` | Hover state accent |
| `WhiteAccentLight` | `#E8F5E0` | Light accent backgrounds |
| `WhiteAccentSoft` | `#F0F7EC` | Soft accent backgrounds |
| `WhiteDanger` | `#DC3545` | Danger/error |
| `WhiteDangerLight` | `#FEE8EB` | Light danger background |
| `WhiteSuccess` | `#28A745` | Success states |
| `WhiteInfo` | `#3F76E4` | Info/network states |
| `WhiteProgressTrack` | `#E2E5EA` | Progress bar track |
| `WhiteProgressFill` | `#5D8C3E` | Progress bar fill |

### Screen Inventory (9 Stitch Screens)

| # | Screen | File | Theme | Status |
|---|--------|------|-------|--------|
| 1 | Minecraft Launcher Home | `HomeScreen.kt` | Dark | Redesigned |
| 2 | Resource Management | `ResourceManagementScreen.kt` | Dark | New |
| 3 | Account Profile | `AccountsScreen.kt` | Dark | Enhanced |
| 4 | Download Manager | `VersionsScreenWhite.kt` | White | New |
| 5 | Multiplayer | `LanScreenWhite.kt` | White | New |
| 6 | Settings | `SettingsScreenWhite.kt` | White | New |
| 7 | Terminal Console | `ConsoleScreenWhite.kt` | White | New |
| 8 | Steve AI Chat | `SteveChatScreen.kt` | Dark | New |
| 9 | Design System | `DESIGN.md` | — | Updated |

### Home Screen Layout (Stitch Design)

The home screen follows the Minecraft Launcher pattern:
1. **Hero Section** — Gradient banner with "MINECRAFT" + "LAUNCHER" branding, version selector dropdown, prominent PLAY button
2. **Quick Actions** — Row of 4 action buttons: Forge, Fabric, Mods folder, Browse
3. **Account Summary** — Compact card showing current account with avatar, name, type badge
4. **News Updates** — Two cards showing latest release and snapshot version info
5. **Installed Versions** — List of locally installed versions with select/delete/settings actions
6. **Server Status** — Quick access to LAN/server management
7. **Launch Modal** — Detailed launch progress with step tracking

### Component Patterns

#### Stitch Hero Section
- Height: 300dp
- Background: Vertical gradient from deep green to transparent
- Content: MINECRAFT/LAUNCHER branding, version selector, PLAY button
- Version selector: Dropdown within a translucent card overlay

#### Quick Action Buttons
- 36dp icon containers with rounded corners
- Colored icon backgrounds with 12% alpha
- Labels below icons (10sp)

#### News Cards
- Rounded rectangle cards (12dp radius)
- Icon + label header, version number, description
- McCardBg background

#### White Theme Screens
All white theme screens follow these conventions:
- Background: `WhiteBg` (#FAFAFA)
- Cards: `WhiteBgCard` (#FFFFFF) with `WhiteBorder` (#E2E5EA)
- Primary text: `WhiteTextPrimary` (#1A1A2E)
- Accent: `WhiteAccent` (#5D8C3E) — same green for consistency
- Input fields: `WhiteBgInput` (#F0F2F5) with accent focus border
- All cards use subtle borders instead of shadows for definition

### Navigation Updates

The sidebar navigation now includes:
- **Steve** — AI chat assistant (new entry after Home)
- All existing routes preserved (Versions, Mods, Modpacks, Resource Packs, Shaders, LAN, Accounts, Settings, Java, Console)

### Design Tokens Added

| Category | Tokens |
|----------|--------|
| Fabric/Forge | `FabricGreen` (#30B25A), `ForgeColor` (#EA580C) |
| NeoForge/Quilt | `NeoForgeRed` (#EF4444), `QuiltPurple` (#A855F7) |
| Console | `ConsoleBg` (#1E1E2E), `ConsoleText` (#CDD6F4) |
| Button states | `McButtonBg`, `McButtonHover`, `McButtonDisabled` |
| Tab system | `McTabBg`, `McTabActive`, `McTabInactive` |
| Switch/Overlay | `McSwitchTrackOff/On`, `McOverlayBg`, `McOverlayContentBg` |

---

## Changelog

### v2.0.0 (2026-06-03) — Stitch Minecraft Launcher
- Added Minecraft dark theme color tokens (McGreen, McGold, McWater, etc.)
- Added white theme color tokens (WhiteBg, WhiteAccent, WhiteBorder, etc.)
- Redesigned HomeScreen with Stitch Minecraft Launcher layout
- Created ResourceManagementScreen for unified resource management
- Enhanced AccountsScreen with profile header
- Added SteveChatScreen (AI assistant) with chat UI
- Created 4 white theme screens: Download Manager, Multiplayer, Settings, Console
- Added SteveChat navigation route to sidebar
- Documented all new components and patterns

### v1.0.0 (2026-05-11)
- Initial design system based on Stripe
- Core components: Button, Card, Input
- Navigation components: BottomNav, Status, Progress
- Typography system with 14 text styles
- Complete color palette with light/dark themes
