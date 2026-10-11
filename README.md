# MinecraftEdi

用 Java + LWJGL 从零复刻的经典 Minecraft 早期版本。超平坦 / 无限世界、多种方块、生存与创造模式、自由挖掘与放置，并带有 **EdiPack** Mod 加载器和资源包系统。

- 当前版本：**0.21**
- 需要：**JDK 17+**
- 渲染：OpenGL 2.1（兼容模式）
- 输入：GLFW 3
- 许可证：**MIT**

---

## 功能一览

| 功能 | 说明 |
|---|---|
| 世界 | 128 × 64 × 128，超平坦或无限随机地形 |
| 游戏模式 | 创造（无限方块）· 生存（撸树挖矿，背包管理） |
| 方块 | 草、石头、泥土、木板、原木、树叶、圆石、煤矿、铁矿、金矿、钻石矿、沙子 |
| 群系 | 平原、森林、沙漠 |
| 结构文件 | JSON 定义（`data/minecraft/structures/*.json`） |
| 挖掘 / 放置 | 左键破坏，右键放置，射线精确命中准星方块 |
| 方块高亮 | 准星对准方块时画黑色线框 |
| 重力 / 跳跃 / 碰撞 | AABB 碰撞，空格起跳 |
| 快捷栏 | 9 格，按 `1`~`9` 切换，生存模式带堆叠数量 |
| 背包 | 27 格，按 `E` 打开 |
| 开始菜单 | 游戏按钮 → 存档列表 → 新建 / 加载，新建时可选创造 / 生存 |
| 存档 | `saves/` 目录，记住模式、世界类型、玩家位置 |
| EdiPack Mod | 启动时扫描 `mods/`，加载所有 jar |
| 资源包 | 启动时扫描 `resourcepacks/`，读取 `package.edimeta` |
| 音效 | 菜单点击、放置、破坏、脚步声（OGG） |

---

## 目录结构

```
MinecraftEdi/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── README.md
├── LICENSE
└── src/main/
    ├── java/com/fire/
    │   ├── Main.java
    │   ├── block/
    │   ├── mod/
    │   ├── pack/
    │   ├── sound/
    │   └── world/
    ├── kotlin/com/fire/ui/
    │   ├── FontRenderer.kt
    │   └── MenuRenderer.kt
    └── resources/
        ├── assets/minecraft/
        │   ├── textures/block/
        │   ├── textures/gui/
        │   └── sounds/
        └── data/minecraft/
            ├── biome/
            │   ├── plains.json
            │   ├── forest.json
            │   └── desert.json
            └── structures/
                └── oak_tree.json
```

首次运行会在**当前工作目录**生成：

```
mods/             ← 放 mod jar
resourcepacks/    ← 放资源包文件夹
saves/            ← 存档
```

---

## 构建与运行

```bash
gradle clean build
java -jar build/libs/MinecraftEdi.jar
```

开发模式：

```bash
gradle run
```

> Windows 控制台若出现中文乱码，执行一次 `chcp 65001` 把代码页切到 UTF-8。

---

## 游戏模式

新建存档时点击中间的模式按钮，可在 **创造** 和 **生存** 之间切换。

| 模式 | 说明 |
|---|---|
| 创造 | 物品栏显示所有方块，点击即可无限放置 |
| 生存 | 空背包开局，破坏方块掉落进背包，放置扣数量；草方块掉泥土 |

**想致富，先撸树**：进入生存模式 → 找一棵树 → 左键原木 → 按 `E` 打开背包把原木拖到快捷栏 → `1`~`9` 选中 → 右键放置。

---

## 操作

| 按键 | 作用 |
|---|---|
| `W A S D` | 前后左右移动 |
| 鼠标 | 转动视角 |
| `空格` | 跳跃 |
| `左键` | 破坏方块 |
| `右键` | 放置方块 |
| `1`~`9` | 切换快捷栏 |
| `E` | 打开 / 关闭物品栏 |
| `ESC` | 保存并返回菜单 |

---

## 结构文件格式

结构文件放在 `src/main/resources/data/minecraft/structures/`，文件名 `<structure_name>.json`。

```json
{
    "size": [5, 7, 5],
    "palette": {
        "L": "log",
        "G": "leaves"
    },
    "layers": [
        ["     ", "     ", "  L  ", "     ", "     "],
        ["     ", "     ", "  L  ", "     ", "     "],
        ["     ", "     ", "  L  ", "     ", "     "],
        ["     ", " GGG ", "GLGLG", " GGG ", "     "],
        ["     ", " GGG ", "GLGLG", " GGG ", "     "],
        ["     ", " GGG ", " GGG ", " GGG ", "     "],
        ["     ", "  G  ", "  G  ", "  G  ", "     "]
    ]
}
```

- `size`：结构的 X / Y / Z 尺寸
- `palette`：单字符到方块名（与 `BlockRegistry` 中的 name 对应）
- `layers`：从下往上的 Y 层，每层是 Z 行，每行是 X 个字符；空格表示空气

---

## 群系文件格式

群系文件放在 `src/main/resources/data/minecraft/biome/`。

```json
{
    "plant": [
        "oak_tree"
    ],
    "density": {
        "oak_tree": 5
    }
}
```

- `plant`：该群系可生成的结构名列表，`null` 表示不生成任何结构
- `density`：每个结构的密度值，概率 = 值 / 5000

沙漠群系示例：

```json
{
    "plant": null,
    "density": null
}
```

---

# EdiPack Mod 制作教程

## 目录结构

```
MyFirstMod/
├── settings.gradle
├── gradle.properties
├── build.gradle
├── gradle/
│   └── lib/
│       └── MinecraftEdi-0.21.jar      ← 手动放入
└── src/main/
    ├── java/com/example/
    │   └── ModBlock.java
    └── resources/
        ├── edimod.json
        └── assets/myfirstmod/textures/block/ruby.png
```

## `settings.gradle`

```gradle
rootProject.name = 'MyFirstMod'
```

## `gradle.properties`

```properties
modName=MyFirstMod
modVersion=1.0.0
mainClass=com.example.ModBlock
```

## `build.gradle`

```gradle
plugins {
    id 'java'
}

def modName    = project.property('modName')
def modVersion = project.property('modVersion')
def mainClass  = project.property('mainClass')

repositories {
    // 不联网，只从 gradle/lib 拿依赖
}

dependencies {
    implementation fileTree(dir: 'gradle/lib', include: ['*.jar'])
}

processResources {
    def modNameVal    = modName
    def modVersionVal = modVersion
    def mainClassVal  = mainClass
    inputs.property 'modName',    modNameVal
    inputs.property 'modVersion', modVersionVal
    inputs.property 'mainClass',  mainClassVal
    filesMatching('edimod.json') {
        expand(
            modName:    modNameVal,
            modVersion: modVersionVal,
            mainClass:  mainClassVal
        )
    }
}

jar {
    archiveBaseName = modName
    archiveVersion  = modVersion
}
```

## `src/main/java/com/example/ModBlock.java`

```java
package com.example;

import com.fire.block.BlockRegistry;
import com.fire.mod.EdiMod;

public class ModBlock implements EdiMod {

    @Override
    public void onInit(BlockRegistry registry) {
        // 注册一个名为 ruby 的方块，纹理来自本 mod jar 内：
        //   assets/myfirstmod/textures/block/ruby.png
        BlockRegistry.register(
                "ruby",
                "/assets/myfirstmod/textures/block/ruby.png"
        );
    }
}
```

## `src/main/resources/edimod.json`

```json
{
    "name":    "${modName}",
    "version": "${modVersion}",
    "main":    "${mainClass}"
}
```

## 编译与安装

```bash
gradlew build
```

把 `build/libs/MyFirstMod-1.0.0.jar` 复制到 MinecraftEdi 运行目录的 `mods/` 下，重新启动游戏。

---

# 资源包格式

## 目录结构

```
resourcepacks/BlueGrass/
├── package.edimeta
└── assets/
    └── minecraft/
        └── textures/
            └── block/
                ├── grass.png
                ├── grass_gradient.png
                ├── dirt.png
                ├── stone.png
                ├── cobblestone.png
                ├── coal_ore.png
                ├── iron_ore.png
                ├── gold_ore.png
                ├── diamond_ore.png
                ├── sand.png
                ├── planks.png
                ├── log.png
                └── leaves.png
```

## `package.edimeta`

结构与原版 `pack.mcmeta` 完全一样，只是文件名改成 `package.edimeta`。

```json
{
    "pack": {
        "pack_format": 1,
        "description": "我的资源包 - 把草方块改成蓝的"
    }
}
```

## 规则

- 启动时扫描 `resourcepacks/` 下所有**子文件夹**。
- 只有包含 `package.edimeta` 的文件夹才会被识别。
- 覆盖路径与内置方块贴图路径一致即可，如 `assets/minecraft/textures/block/grass.png`。
- Mod 方块的贴图同样可以被覆盖，路径要和 Mod 里声明的路径一致。

---

# 版本历史

| 版本 | 内容 |
|---|---|
| 0.18 | 超平坦世界、挖掘放置、方块高亮、区块显示列表渲染 |
| 0.19 | EdiPack Mod 加载器、资源包系统、快捷栏、放置不受 y 轴限制 |
| 0.20 | 新方块、开始菜单、存档、无限地形、树木、群系、音效、Kotlin UI |
| 0.21 | 生存模式、9 格快捷栏 + 背包、矿石、沙子、沙漠群系、结构 JSON、草方块多面纹理 |

---

# 许可证

本项目采用 **MIT License**。

完整许可证文本见仓库根目录的 [LICENSE](LICENSE) 文件。

MinecraftEdi 与 Mojang / Microsoft 无关，是一个独立的学习项目。