# Just Enough Hiding (JEH)

一个 Minecraft Forge 1.20.1 的**客户端**模组：记录「是谁、出于什么原因隐藏了 JEI / EMI 的内容」，把被隐藏的东西揭示出来，并允许你用一份本地列表把内容从 recipe viewer 里隐藏掉——方便整合包作者统一管理 recipe viewer 内容的隐藏情况。

- Recipe viewer（可选项，二选一）：[JEI](https://www.curseforge.com/minecraft/mc-mods/jei) 或 [EMI](https://www.curseforge.com/minecraft/mc-mods/emi)。两者都装时 **EMI 优先**（JEH 的界面/图标用 EMI，同时 JEI 侧的揭示与 intent 记录照常运行）；都不装时模组不做事。
- 可选联动：[KubeJS](https://kubejs.com/)（用脚本读写隐藏列表）
- 许可：MIT

## 功能

- **Reveal（揭示）**：JEI 启动时，把被标签 / JEI 编辑模式 / 黑名单隐藏的 ingredient 重新显示；把被移除或缺失的 ingredient 补回；把被隐藏的配方与配方类别取消隐藏。
- **Intent 记录**：记录其它模组、JEI 自身、服务端等对 JEI 内容的隐藏 / 显示操作，并提供查看界面（含来源、次数、时间等）。
- **ListEHiding（隐藏列表）**：你自己维护的一份列表，JEHide 会按它把匹配内容从 JEI 中隐藏。支持批量选择 / 开关 / 删除。
- **类型与匹配**：物品、流体、化学物等所有 JEI ingredient 类型；配方；配方类别；标签；以及通配符 / 正则。
- **统一管理**：JEHide 同时也会读取记录下来的 hide 类 intent，等效于「先揭示、再按列表 + intent 重新隐藏」。

## EMI 适配

- **界面/图标**：EMI 作为 viewer 时，intent 查看器与隐藏列表使用 EMI 的索引与图标。JEI 生成的条目（type uid 拼写不同）也能正常显示图标。
- **揭示**：EMI 启动/重载时，被标签（`c:hidden_from_recipe_viewers`）、插件移除（`removeEmiStacks`/`removeRecipes`）、EMI 编辑模式、以及数据包 `assets/emi/index/stacks` 与 `assets/emi/recipe/filters` 隐藏的内容都会被揭示（受 `[reveal] enabled` 控制）。
- **隐藏**：`[jehide]` 的列表 + intent 以 EMI 谓词形式在 bake 时生效，优先于揭示。
- **记录**：EMI 插件移除（来源=调用 mod）、EMI 编辑模式（来源 `EMI edit mode`）、`emi:index_stacks` / `emi:recipe_filters` 数据（来源=提供该文件的**资源包**）。
- **测试/编辑 EMI 数据**：这些文件是**客户端资源** `assets/emi/...`。改完后请用 **F3+T**（重载资源）或切换资源包使其生效——`/reload` 只重载服务端数据（tags/recipes），不会重载 `assets/`。
- **已知限制**：模组自带的 EMI 数据来源显示为 `mod_resources`（Forge 合并所致，无法细分到具体 modid）；EMI 没有类别隐藏 API（`recipe_category` 只能隐藏其配方）；EMI 侧不支持 JEI 书签键。

## 配置文件（`config/jeh/`）

| 文件 | 说明 |
| --- | --- |
| `client.toml` | 模组设置（见下），首次运行由 Forge 生成 |
| `listehiding.json` | 隐藏列表，**首次运行自动生成默认列表**（见下），用 GUI 或外部编辑 |
| `intentoverrides.json` | 每条 intent 是否参与隐藏（由 GUI 里 Enable/Disable 写入） |

> 首次生成的 `listehiding.json` 自带几条 `note="default"` 的默认项，同时充当格式示例：
> - 隐藏纹饰锻造配方（`pattern` 作用域 `recipe`，`*_armor_trim_smithing_template`），**启用**；
> - 伪装板（AE2 `ae2:facade`、Create `create:copycat_panel`、Refined Storage `refinedstorage:cover`），默认**禁用**，需要时把 `enabled` 改为 `true`。
>
> 整合包作者可把仓库里的 `defaultconfigs/jeh/client.toml` 放到实例的 `defaultconfigs/jeh/`，Forge 会在新实例首次运行时套用。

`client.toml` 主要项：

```toml
[intentRecording]
enabled = true      # 是否记录 intent

[reveal]
enabled = true      # 启动时是否揭示被隐藏内容

[jehide]
enabled = true      # 是否按 listehiding（+ intent）隐藏
applyIntents = true # 是否把记录的 hide 类 intent 也当作隐藏规则

[intentView]
sortModes = ["source,kind,target", "kind,source,target", "target", "count:desc", "sequence"]
bookmarkTarget = "ICON" # 书签键作用于 ICON 还是 ROW
```

## `listehiding.json` 格式

根对象是 `{ "entries": [ ... ] }`。每条至少要有 `kind`，`enabled`（默认 `true`）、`note`、`priority`（默认 `0`）可选。

```json
{
  "entries": [
    { "kind": "ingredient", "typeUid": "minecraft:item_stack", "uid": "minecraft:stone", "enabled": true, "note": "", "priority": 0 },
    { "kind": "tag", "tag": "minecraft:logs", "enabled": true },
    { "kind": "recipe", "recipeType": "minecraft:crafting", "recipeId": "minecraft:stick", "enabled": true },
    { "kind": "recipe_category", "recipeType": "minecraft:crafting", "enabled": true },
    { "kind": "pattern", "scope": "recipe_category", "pattern": "minecraft:*", "mode": "glob", "enabled": true },
    { "kind": "pattern", "scope": "", "pattern": "^minecraft:.*_ore$", "mode": "regex", "enabled": true },
    { "kind": "unset", "enabled": true }
  ]
}
```

- `kind = "ingredient"`：`typeUid` 是 JEI ingredient 类型 uid（如 `minecraft:item_stack`、`fluid_stack`、化学物类型），`uid` 是该类型的唯一 id。
- `kind = "tag"`：`tag` 为标签 id（不含 `#`）。
- `kind = "recipe"` / `"recipe_category"`：`recipeType` 为配方类型，`recipeId` 为配方 id。
- `kind = "pattern"`：`scope` 取值同编辑器类型标签（`""` 表示任意类型、`ingredient|<typeUid>`、`recipe`、`recipe_category`、`tag`），`pattern` 为正文，`mode` 为 `glob` 或 `regex`。
- `kind = "unset"`：空占位（编辑框留空即此）。

## 游戏内用法

命令：

- `/jeh intents`：打开 intent 查看器
- `/jeh list`：打开隐藏列表编辑器

按键（默认未绑定）：`key.justenoughhiding.open_intents`、`key.justenoughhiding.open_list`。

**隐藏列表编辑器**（`/jeh list`）：

- `New Entry` 新建空白条目；`Save` 写盘并立即重应用；`Refresh` 需点两次（丢弃内存改动并重读文件）。
- 搜索框 + 类型 / 状态 / 排序下拉，`Desc` 反向排序。
- 双击某个单元格直接编辑：**目标** / **优先级** / **备注**。
- 右键菜单：Enable/Disable、Edit target / note / priority、Delete（二次确认）。
- 多选：`Ctrl` 逐条切换、`Shift` 选择区间；多选后右键可批量 Enable / Disable / Delete（删除需点三次确认）。
- 灰色的行是来自 intent 的条目，只读、只能 Enable/Disable；备注会标注来源（如 `intent + malum`）。开启 JEI 编辑模式时，来自编辑模式的 intent 会暂停应用，并在备注标注 `(edit mode paused)`。

**编辑目标**：编辑框只填 id，用顶部标签选择类型（`Auto` 会自动识别物品 / 标签 / 通配符 / 配方 / 类别）。

- `Auto` 下输入 `minecraft:*` → 通配符（glob），`?` 匹配单字符。
- `~` 开头为正则，如 `~^minecraft:.*_ore$`。
- `#` 开头为标签，如 `#minecraft:logs`。
- 也可显式用 `item` / `recipe` / `category` 前缀覆盖类型。

## KubeJS 联动

把脚本放到 `kubejs/client_scripts/`，使用全局对象 `JEH`：

```js
// 自动识别（物品 / #标签 / 通配符 / 配方 / 类别）
JEH.add('minecraft:stone')
JEH.add('#minecraft:logs')
JEH.add('minecraft:*')

// 显式指定
JEH.addItem('minecraft:stone')
JEH.addTag('minecraft:logs')
JEH.addPattern('minecraft:*')
JEH.addRecipe('minecraft:crafting', 'minecraft:stick')
JEH.addCategory('minecraft:crafting')

// 备注 / 优先级（可选参数）
JEH.add('minecraft:diamond', '我的备注', 10)

// 编辑
JEH.remove('minecraft:stone')      // 返回删除数量
JEH.setEnabled('minecraft:stone', false)
JEH.clear()
JEH.size()
JEH.list()

// 持久化与生效
JEH.save()    // 写入 listehiding.json；不调用则只在本局内存生效
JEH.reload()
JEH.apply()
```

脚本添加的条目，备注会标注 `kubejs(unsaved)`（未写盘）或 `kubejs(saved)`（已写盘）。脚本每次启动都会重跑；若想让脚本完全接管列表，先 `JEH.clear()` 再添加，避免跨启动累积。

## 从源码构建

需要 JDK 17。Windows：

```powershell
.\gradlew.bat build        # 产物：build\libs\justenoughhiding-<version>.jar
.\gradlew.bat runData      # 冒烟测试
```

可选参数：`-PnoJei`、`-PnoKubeJS`、`-PnoEmi` 跳过对应依赖。
