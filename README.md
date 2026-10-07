# MinecraftEdi

用 Java + LWJGL 从零复刻的经典 Minecraft 早期版本。超平坦世界、草方块与石头、自由挖掘与放置、方块高亮，并带有 **EdiPack** Mod 加载器和资源包系统。

- 当前版本：**0.19**
- 需要：**JDK 17+**
- 渲染：OpenGL 2.1（兼容模式）
- 输入：GLFW 3
- 许可证：**MIT**

---

## 功能一览

| 功能 | 说明 |
|---|---|
| 超平坦世界 | 128 × 64 × 128，`y=0..61` 石头（62 层），`y=62` 草（1 层） |
| 挖掘 / 放置 | 左键破坏，右键放置，射线精确命中准星方块 |
| 方块高亮 | 准星对准方块时画黑色线框 |
| 重力 / 跳跃 / 碰撞 | AABB 碰撞，脚部 + 眼高，空格起跳 |
| 快捷栏 | `1` = 草方块，`2` = 石头 |
| 区块渲染 | 16×16×16 区块显示列表 + 视锥剔除 + 距离剔除 |
| EdiPack Mod | 启动时扫描 `mods/`，加载所有 jar |
| 资源包 | 启动时扫描 `resourcepacks/`，读取 `package.edimeta` |

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
    │   │   ├── Block.java
    │   │   └── BlockRegistry.java
    │   ├── mod/
    │   │   ├── EdiMod.java
    │   │   └── ModLoader.java
    │   ├── pack/
    │   │   └── ResourcePackLoader.java
    │   └── world/
    │       └── WorldGen.java
    └── resources/assets/minecraft/textures/
        ├── block/grass.png
        ├── block/stone.png
        └── gui/msg/icon.png
```

首次运行会在**当前工作目录**生成：

```
mods/             ← 放 mod jar
resourcepacks/    ← 放资源包文件夹
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
> 若控制台提示"未找到窗口图标"，只是 `icon.png` 不在 `src/main/resources/assets/minecraft/textures/gui/msg/`，不影响启动。

---

## 操作

| 按键 | 作用 |
|---|---|
| `W A S D` | 前后左右移动 |
| 鼠标 | 转动视角 |
| `空格` | 跳跃 |
| `左键` | 破坏方块 |
| `右键` | 放置方块 |
| `1` | 切换到草方块 |
| `2` | 切换到石头 |
| `ESC` | 关闭窗口 |

---

# 源码

## `settings.gradle`

```gradle
rootProject.name = 'MinecraftEdi'
```

## `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.caching=true

lwjglVersion=3.3.3
lwjglNatives=natives-windows
```

> 换平台时只改 `lwjglNatives`：`natives-linux` / `natives-macos` / `natives-macos-arm64`。

## `build.gradle`

```gradle
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

def lwjglVersion = project.property('lwjglVersion')
def lwjglNatives = project.property('lwjglNatives')

dependencies {
    implementation "org.lwjgl:lwjgl:$lwjglVersion"
    implementation "org.lwjgl:lwjgl-glfw:$lwjglVersion"
    implementation "org.lwjgl:lwjgl-opengl:$lwjglVersion"
    implementation "org.lwjgl:lwjgl-stb:$lwjglVersion"

    runtimeOnly "org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives"
    runtimeOnly "org.lwjgl:lwjgl-stb:$lwjglVersion:$lwjglNatives"
}

application {
    mainClass = 'com.fire.Main'
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

tasks.named('run') {
    jvmArgs = [
        '-Dfile.encoding=UTF-8',
        '-Dstdout.encoding=UTF-8',
        '-Dstderr.encoding=UTF-8'
    ]
}

jar {
    manifest {
        attributes 'Main-Class': 'com.fire.Main'
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from {
        configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) }
    }
    exclude 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'module-info.class'
}
```

## `src/main/java/com/fire/block/Block.java`

```java
package com.fire.block;

public class Block {
    public static final byte AIR = 0;
    public static final byte GRASS = 1;
    public static final byte STONE = 2;
}
```

## `src/main/java/com/fire/block/BlockRegistry.java`

```java
package com.fire.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockRegistry {

    public static class BlockType {
        public final byte id;
        public final String name;
        public final String texturePath;   // classpath 路径，air 为 null
        public final ClassLoader classLoader;
        public int texture = 0;            // 运行时 GL 纹理 id

        public BlockType(byte id, String name, String texturePath, ClassLoader cl) {
            this.id = id;
            this.name = name;
            this.texturePath = texturePath;
            this.classLoader = cl;
        }
    }

    private static final Map<Byte, BlockType> REGISTRY = new HashMap<>();
    private static byte nextId = 3;
    private static boolean builtinInit = false;

    public static synchronized void initBuiltin() {
        if (builtinInit) return;
        builtinInit = true;
        ClassLoader cl = BlockRegistry.class.getClassLoader();
        REGISTRY.put(Block.AIR,   new BlockType(Block.AIR,   "air",   null, cl));
        REGISTRY.put(Block.GRASS, new BlockType(Block.GRASS, "grass", "/assets/minecraft/textures/block/grass.png", cl));
        REGISTRY.put(Block.STONE, new BlockType(Block.STONE, "stone", "/assets/minecraft/textures/block/stone.png", cl));
    }

    public static synchronized int register(String name, String texturePath) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return register(name, texturePath, cl);
    }

    public static synchronized int register(String name, String texturePath, ClassLoader cl) {
        if (nextId > 127 || nextId < 0) throw new RuntimeException("方块 id 用尽");
        byte id = nextId++;
        REGISTRY.put(id, new BlockType(id, name, texturePath, cl));
        System.out.println("[EdiPack] 注册方块: " + name + " (id=" + id + ")");
        return id;
    }

    public static BlockType get(byte id) {
        return REGISTRY.get(id);
    }

    public static List<BlockType> all() {
        return new ArrayList<>(REGISTRY.values());
    }
}
```

## `src/main/java/com/fire/mod/EdiMod.java`

```java
package com.fire.mod;

import com.fire.block.BlockRegistry;

public interface EdiMod {
    void onInit(BlockRegistry registry);
}
```

## `src/main/java/com/fire/mod/ModLoader.java`

```java
package com.fire.mod;

import com.fire.block.BlockRegistry;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ModLoader {

    public static void loadAll(BlockRegistry registry) {
        File modsDir = new File("mods");
        if (!modsDir.exists()) {
            if (modsDir.mkdirs()) System.out.println("[EdiPack] 已创建 mods/ 文件夹");
            return;
        }

        File[] jars = modsDir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            System.out.println("[EdiPack] mods/ 为空，跳过加载");
            return;
        }

        for (File jar : jars) {
            try {
                loadJar(jar, registry);
            } catch (Throwable t) {
                System.err.println("[EdiPack] 加载失败: " + jar.getName() + " → " + t);
            }
        }
    }

    private static void loadJar(File jar, BlockRegistry registry) throws Exception {
        try (JarFile jf = new JarFile(jar)) {
            JarEntry meta = jf.getJarEntry("edimod.json");
            if (meta == null) {
                System.out.println("[EdiPack] 跳过（无 edimod.json）: " + jar.getName());
                return;
            }
            String json;
            try (InputStream in = jf.getInputStream(meta)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            String name      = parseField(json, "name");
            String version   = parseField(json, "version");
            String mainClass = parseField(json, "main");

            if (mainClass == null || mainClass.isEmpty()) {
                throw new RuntimeException("edimod.json 缺少 main 字段");
            }

            URLClassLoader loader = new URLClassLoader(
                    new URL[]{ jar.toURI().toURL() },
                    ModLoader.class.getClassLoader()
            );

            Class<?> clazz = loader.loadClass(mainClass);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            if (!(instance instanceof EdiMod)) {
                throw new RuntimeException(mainClass + " 未实现 EdiMod 接口");
            }

            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try {
                ((EdiMod) instance).onInit(registry);
            } finally {
                Thread.currentThread().setContextClassLoader(old);
            }

            System.out.println("[EdiPack] 已加载: " + name + " v" + version + " (" + jar.getName() + ")");
        }
    }

    private static String parseField(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return null;
        int colon = json.indexOf(':', k);
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }
}
```

## `src/main/java/com/fire/pack/ResourcePackLoader.java`

```java
package com.fire.pack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ResourcePackLoader {

    public static class Pack {
        public final File root;
        public final String name;
        public final int packFormat;

        public Pack(File root, String name, int packFormat) {
            this.root = root;
            this.name = name;
            this.packFormat = packFormat;
        }
    }

    public static List<Pack> loadAll() {
        List<Pack> packs = new ArrayList<>();
        File dir = new File("resourcepacks");
        if (!dir.exists()) {
            if (dir.mkdirs()) System.out.println("[EdiPack] 已创建 resourcepacks/ 文件夹");
            return packs;
        }

        File[] items = dir.listFiles(File::isDirectory);
        if (items == null) return packs;

        for (File item : items) {
            File metaFile = new File(item, "package.edimeta");
            if (!metaFile.exists()) continue;

            try {
                String json = new String(Files.readAllBytes(metaFile.toPath()), StandardCharsets.UTF_8);
                String desc = parseField(json, "description");
                String fmt  = parseField(json, "pack_format");
                int format = 1;
                try { if (fmt != null) format = Integer.parseInt(fmt.trim()); } catch (Exception ignored) {}

                Pack p = new Pack(item, desc != null ? desc : item.getName(), format);
                packs.add(p);
                System.out.println("[EdiPack] 资源包: " + item.getName() + " - " + p.name);
            } catch (Exception e) {
                System.err.println("[EdiPack] 资源包读取失败: " + item.getName() + " → " + e.getMessage());
            }
        }
        return packs;
    }

    /**
     * 按加载顺序反向查找纹理文件。返回 null 表示全部未命中。
     * relPath 形如 "assets/minecraft/textures/block/grass.png"
     */
    public static byte[] findTexture(List<Pack> packs, String relPath) {
        for (int i = packs.size() - 1; i >= 0; i--) {
            File f = new File(packs.get(i).root, relPath);
            if (f.exists() && f.isFile()) {
                try {
                    return Files.readAllBytes(f.toPath());
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private static String parseField(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return null;
        int colon = json.indexOf(':', k);
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) return null;
        if (json.charAt(i) == '"') {
            int q2 = json.indexOf('"', i + 1);
            if (q2 < 0) return null;
            return json.substring(i + 1, q2);
        } else {
            int end = i;
            while (end < json.length() && ",} \n\r\t".indexOf(json.charAt(end)) < 0) end++;
            return json.substring(i, end);
        }
    }
}
```

## `src/main/java/com/fire/world/WorldGen.java`

```java
package com.fire.world;

import com.fire.block.Block;

public class WorldGen {
    public static final int SIZE_X = 128;
    public static final int SIZE_Y = 144;   // 9 × 16，索引 0..143
    public static final int SIZE_Z = 128;

    // 超平坦：y=0..61 石头（62 层），y=62 草（1 层），y>62 空气
    public static final int STONE_TOP_Y = 61;
    public static final int GRASS_Y = 62;

    public static byte[][][] generate() {
        byte[][][] world = new byte[SIZE_X][SIZE_Y][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int y = 0; y <= STONE_TOP_Y; y++) {
                    world[x][y][z] = Block.STONE;
                }
                world[x][GRASS_Y][z] = Block.GRASS;
            }
        }
        return world;
    }
}
```

## `src/main/java/com/fire/Main.java`

```java
package com.fire;

import com.fire.block.Block;
import com.fire.block.BlockRegistry;
import com.fire.mod.ModLoader;
import com.fire.pack.ResourcePackLoader;
import com.fire.world.WorldGen;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {
    private long window;
    private int width = 800, height = 600;

    private static final int WORLD_X = WorldGen.SIZE_X;
    private static final int WORLD_Y = WorldGen.SIZE_Y;
    private static final int WORLD_Z = WorldGen.SIZE_Z;

    private static final int CHUNK_SIZE = 16;
    private static final int CHUNKS_X = WORLD_X / CHUNK_SIZE;
    private static final int CHUNKS_Y = WORLD_Y / CHUNK_SIZE;
    private static final int CHUNKS_Z = WORLD_Z / CHUNK_SIZE;

    private byte[][][] world;
    private int[][][] chunkLists;
    private boolean[][][] chunkDirty;

    // 玩家
    private float playerX = 64.5f;
    private float playerY = 63.0f;
    private float playerZ = 64.5f;
    private float velY = 0f;
    private boolean onGround = false;
    private float yaw = 0f, pitch = 0f;

    private static final float PLAYER_WIDTH = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float EYE_HEIGHT = 1.62f;
    private static final float MOVE_SPEED = 4.3f;
    private static final float GRAVITY = 32f;
    private static final float JUMP_SPEED = 9.0f;

    // 鼠标
    private double lastMouseX = 0, lastMouseY = 0;
    private boolean firstMouse = true;
    private boolean leftClicked = false;
    private boolean rightClicked = false;

    private double lastActionTime = 0;
    private static final double ACTION_COOLDOWN = 0.15;

    private static final float RENDER_DISTANCE = 64f;

    // 方块高亮
    private boolean hasHitBlock = false;
    private int hitBlockX, hitBlockY, hitBlockZ;

    // 快捷栏
    private final int[] hotbar = new int[]{ Block.GRASS, Block.STONE };
    private int selectedSlot = 0;

    private List<ResourcePackLoader.Pack> packs;

    private static final String PATH_ICON = "/assets/minecraft/textures/gui/msg/icon.png";

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        if (!glfwInit()) throw new RuntimeException("无法初始化 GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        window = glfwCreateWindow(width, height, "MinecraftEdi 0.19", 0, 0);
        if (window == 0) throw new RuntimeException("无法创建窗口");

        glfwMakeContextCurrent(window);
        GL.createCapabilities();

        setWindowIcon(PATH_ICON);

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        if (glfwRawMouseMotionSupported())
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (button == GLFW_MOUSE_BUTTON_LEFT) leftClicked = true;
                else if (button == GLFW_MOUSE_BUTTON_RIGHT) rightClicked = true;
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
                return;
            }
            float dx = (float) (xpos - lastMouseX);
            float dy = (float) (ypos - lastMouseY);
            lastMouseX = xpos;
            lastMouseY = ypos;

            yaw += dx * 0.15f;
            pitch += dy * 0.15f;
            if (pitch > 89f) pitch = 89f;
            if (pitch < -89f) pitch = -89f;
        });

        BlockRegistry.initBuiltin();
        packs = ResourcePackLoader.loadAll();

        System.out.println("[EdiPack] 正在加载 mods/ ...");
        ModLoader.loadAll(BlockRegistryInstance.get());

        for (BlockRegistry.BlockType bt : BlockRegistry.all()) {
            if (bt.texturePath == null) continue;
            bt.texture = loadTexture(bt.texturePath, bt.classLoader, packs);
        }

        world = WorldGen.generate();

        chunkLists = new int[CHUNKS_X][CHUNKS_Y][CHUNKS_Z];
        chunkDirty = new boolean[CHUNKS_X][CHUNKS_Y][CHUNKS_Z];
        for (int cx = 0; cx < CHUNKS_X; cx++)
            for (int cy = 0; cy < CHUNKS_Y; cy++)
                for (int cz = 0; cz < CHUNKS_Z; cz++) {
                    chunkLists[cx][cy][cz] = glGenLists(1);
                    compileChunk(cx, cy, cz);
                }

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glClearColor(0.5f, 0.7f, 1.0f, 1.0f);
    }

    private static class BlockRegistryInstance {
        static BlockRegistry get() { return BlockRegistryHolder.INSTANCE; }
    }
    private static class BlockRegistryHolder {
        static final BlockRegistry INSTANCE = new BlockRegistry();
    }

    /**
     * 统一的资源读取：先查 classpath，再查文件系统。
     * 都找不到返回 null，不抛异常。
     */
    private static ByteBuffer loadResource(String path, ClassLoader cl) {
        ClassLoader[] loaders = { cl, Main.class.getClassLoader(), ClassLoader.getSystemClassLoader() };
        for (ClassLoader l : loaders) {
            if (l == null) continue;
            try (InputStream in = l.getResourceAsStream(path)) {
                if (in != null) {
                    byte[] bytes = in.readAllBytes();
                    ByteBuffer buf = memAlloc(bytes.length);
                    buf.put(bytes).flip();
                    return buf;
                }
            } catch (IOException ignored) {}
        }

        String rel = path.startsWith("/") ? path.substring(1) : path;
        File[] candidates = {
                new File("src/main/resources/" + rel),
                new File("resources/" + rel),
                new File(rel)
        };
        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                try {
                    byte[] bytes = Files.readAllBytes(f.toPath());
                    ByteBuffer buf = memAlloc(bytes.length);
                    buf.put(bytes).flip();
                    return buf;
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private void setWindowIcon(String path) {
        ByteBuffer fileBuf = loadResource(path, Main.class.getClassLoader());
        if (fileBuf == null) {
            System.out.println("[警告] 未找到窗口图标，使用默认图标: " + path);
            return;
        }

        IntBuffer w = memAllocInt(1);
        IntBuffer h = memAllocInt(1);
        IntBuffer comp = memAllocInt(1);
        try {
            stbi_set_flip_vertically_on_load(false);
            ByteBuffer image = stbi_load_from_memory(fileBuf, w, h, comp, 4);
            if (image == null) {
                System.out.println("[警告] 窗口图标解码失败: " + path);
                return;
            }
            GLFWImage.Buffer icon = GLFWImage.malloc(1);
            icon.width(w.get(0));
            icon.height(h.get(0));
            icon.pixels(image);
            glfwSetWindowIcon(window, icon);
            icon.free();
            stbi_image_free(image);
        } finally {
            memFree(w); memFree(h); memFree(comp);
            memFree(fileBuf);
        }
    }

    private int loadTexture(String path, ClassLoader cl, List<ResourcePackLoader.Pack> packs) {
        String rel = path.startsWith("/") ? path.substring(1) : path;
        byte[] override = (packs != null) ? ResourcePackLoader.findTexture(packs, rel) : null;

        ByteBuffer fileBuf = null;
        IntBuffer w = memAllocInt(1);
        IntBuffer h = memAllocInt(1);
        IntBuffer comp = memAllocInt(1);
        try {
            if (override != null) {
                fileBuf = memAlloc(override.length);
                fileBuf.put(override).flip();
            } else {
                fileBuf = loadResource(path, cl);
                if (fileBuf == null) {
                    System.err.println("[纹理] 找不到资源: " + path);
                    return 0;
                }
            }

            stbi_set_flip_vertically_on_load(false);
            ByteBuffer image = stbi_load_from_memory(fileBuf, w, h, comp, 4);
            if (image == null) {
                System.err.println("[纹理] 解码失败: " + path);
                return 0;
            }

            int tex = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, tex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            stbi_image_free(image);
            return tex;
        } catch (Throwable t) {
            System.err.println("[纹理] 读取失败: " + path + " → " + t.getMessage());
            return 0;
        } finally {
            memFree(w); memFree(h); memFree(comp);
            if (fileBuf != null) memFree(fileBuf);
        }
    }

    private void loop() {
        double lastTime = glfwGetTime();
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float delta = (float) (now - lastTime);
            lastTime = now;
            if (delta > 0.05f) delta = 0.05f;

            handleInput(delta);
            updatePhysics(delta);
            updateBlockHighlight();
            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    // ---------------- 碰撞 ----------------

    private boolean collides(float x, float y, float z) {
        float hw = PLAYER_WIDTH / 2f;
        int x0 = (int) Math.floor(x - hw);
        int x1 = (int) Math.floor(x + hw);
        int y0 = (int) Math.floor(y);
        int y1 = (int) Math.floor(y + PLAYER_HEIGHT - 0.0001f);
        int z0 = (int) Math.floor(z - hw);
        int z1 = (int) Math.floor(z + hw);

        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    if (by < 0 || by >= WORLD_Y) continue;
                    if (bx < 0 || bx >= WORLD_X || bz < 0 || bz >= WORLD_Z) return true;
                    if (world[bx][by][bz] != Block.AIR) return true;
                }
            }
        }
        return false;
    }

    private boolean blockOverlapsPlayer(int bx, int by, int bz) {
        float hw = PLAYER_WIDTH / 2f;
        float px0 = playerX - hw, px1 = playerX + hw;
        float py0 = playerY, py1 = playerY + PLAYER_HEIGHT;
        float pz0 = playerZ - hw, pz1 = playerZ + hw;
        return bx + 1 > px0 && bx < px1 &&
               by + 1 > py0 && by < py1 &&
               bz + 1 > pz0 && bz < pz1;
    }

    private void movePlayer(float dx, float dy, float dz) {
        if (dx != 0) {
            if (!collides(playerX + dx, playerY, playerZ)) playerX += dx;
        }
        if (dz != 0) {
            if (!collides(playerX, playerY, playerZ + dz)) playerZ += dz;
        }
        if (dy != 0) {
            if (!collides(playerX, playerY + dy, playerZ)) {
                playerY += dy;
            } else {
                if (dy < 0) onGround = true;
                velY = 0;
            }
        }
    }

    private void updatePhysics(float delta) {
        velY -= GRAVITY * delta;
        if (velY < -50f) velY = -50f;
        onGround = false;
        movePlayer(0, velY * delta, 0);
    }

    // ---------------- 方块高亮 ----------------

    private void updateBlockHighlight() {
        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);
        float lx = (float) ( Math.sin(radYaw) * Math.cos(radPitch));
        float ly = (float) (-Math.sin(radPitch));
        float lz = (float) (-Math.cos(radYaw) * Math.cos(radPitch));

        float ex = playerX;
        float ey = playerY + EYE_HEIGHT;
        float ez = playerZ;

        hasHitBlock = false;
        float step = 0.02f;
        float maxDist = 6.0f;

        for (float t = 0f; t < maxDist; t += step) {
            int bx = (int) Math.floor(ex + lx * t);
            int by = (int) Math.floor(ey + ly * t);
            int bz = (int) Math.floor(ez + lz * t);

            if (bx < 0 || bx >= WORLD_X || bz < 0 || bz >= WORLD_Z) return;
            if (by < 0 || by >= WORLD_Y) continue;

            if (world[bx][by][bz] != Block.AIR) {
                hasHitBlock = true;
                hitBlockX = bx; hitBlockY = by; hitBlockZ = bz;
                return;
            }
        }
    }

    private void drawBlockHighlight(int x, int y, int z) {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);
        glLineWidth(2.0f);
        glColor3f(0f, 0f, 0f);

        float e = 0.002f;
        float x0 = x - e, y0 = y - e, z0 = z - e;
        float x1 = x + 1 + e, y1 = y + 1 + e, z1 = z + 1 + e;

        glBegin(GL_LINES);
        glVertex3f(x0, y0, z0); glVertex3f(x1, y0, z0);
        glVertex3f(x1, y0, z0); glVertex3f(x1, y0, z1);
        glVertex3f(x1, y0, z1); glVertex3f(x0, y0, z1);
        glVertex3f(x0, y0, z1); glVertex3f(x0, y0, z0);

        glVertex3f(x0, y1, z0); glVertex3f(x1, y1, z0);
        glVertex3f(x1, y1, z0); glVertex3f(x1, y1, z1);
        glVertex3f(x1, y1, z1); glVertex3f(x0, y1, z1);
        glVertex3f(x0, y1, z1); glVertex3f(x0, y1, z0);

        glVertex3f(x0, y0, z0); glVertex3f(x0, y1, z0);
        glVertex3f(x1, y0, z0); glVertex3f(x1, y1, z0);
        glVertex3f(x1, y0, z1); glVertex3f(x1, y1, z1);
        glVertex3f(x0, y0, z1); glVertex3f(x0, y1, z1);
        glEnd();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_TEXTURE_2D);
        glColor3f(1f, 1f, 1f);
    }

    // ---------------- 输入 ----------------

    private void handleInput(float delta) {
        if (glfwGetKey(window, GLFW_KEY_1) == GLFW_PRESS) selectedSlot = 0;
        if (glfwGetKey(window, GLFW_KEY_2) == GLFW_PRESS) selectedSlot = 1;

        float forward = 0f, strafe = 0f;
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) forward += 1f;
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) forward -= 1f;
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) strafe -= 1f;
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) strafe += 1f;

        float radYaw = (float) Math.toRadians(yaw);
        float sinY = (float) Math.sin(radYaw);
        float cosY = (float) Math.cos(radYaw);
        float fx = sinY, fz = -cosY;
        float sx = cosY, sz = sinY;

        float mdx = (fx * forward + sx * strafe) * MOVE_SPEED * delta;
        float mdz = (fz * forward + sz * strafe) * MOVE_SPEED * delta;
        if (mdx != 0 || mdz != 0) movePlayer(mdx, 0, mdz);

        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS && onGround) {
            velY = JUMP_SPEED;
            onGround = false;
        }

        float radPitch = (float) Math.toRadians(pitch);
        float lx = (float) ( Math.sin(radYaw) * Math.cos(radPitch));
        float ly = (float) (-Math.sin(radPitch));
        float lz = (float) (-Math.cos(radYaw) * Math.cos(radPitch));

        double now = glfwGetTime();
        boolean leftDown  = leftClicked  || glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT)  == GLFW_PRESS;
        boolean rightDown = rightClicked || glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
        leftClicked = false;
        rightClicked = false;

        if (now - lastActionTime > ACTION_COOLDOWN) {
            if (leftDown) {
                if (raycastAction(lx, ly, lz, true)) lastActionTime = now;
            } else if (rightDown) {
                if (raycastAction(lx, ly, lz, false)) lastActionTime = now;
            }
        }
    }

    private byte getSelectedBlock() {
        int idx = Math.max(0, Math.min(selectedSlot, hotbar.length - 1));
        return (byte) hotbar[idx];
    }

    private boolean raycastAction(float lx, float ly, float lz, boolean destroy) {
        float ex = playerX;
        float ey = playerY + EYE_HEIGHT;
        float ez = playerZ;

        int lastX = Integer.MIN_VALUE, lastY = 0, lastZ = 0;
        float step = 0.02f;
        float maxDist = 6.0f;

        for (float t = 0f; t < maxDist; t += step) {
            int bx = (int) Math.floor(ex + lx * t);
            int by = (int) Math.floor(ey + ly * t);
            int bz = (int) Math.floor(ez + lz * t);

            if (bx < 0 || bx >= WORLD_X || bz < 0 || bz >= WORLD_Z) return false;
            if (by < 0 || by >= WORLD_Y) continue;

            if (world[bx][by][bz] != Block.AIR) {
                if (destroy) {
                    world[bx][by][bz] = Block.AIR;
                    markDirtyAround(bx, by, bz);
                    return true;
                } else {
                    if (lastX != Integer.MIN_VALUE
                            && lastY >= 0 && lastY < WORLD_Y
                            && !blockOverlapsPlayer(lastX, lastY, lastZ)) {
                        world[lastX][lastY][lastZ] = getSelectedBlock();
                        markDirtyAround(lastX, lastY, lastZ);
                        return true;
                    }
                    return false;
                }
            }
            lastX = bx; lastY = by; lastZ = bz;
        }
        return false;
    }

    private void markDirtyAround(int bx, int by, int bz) {
        markDirty(bx, by, bz);
        markDirty(bx - 1, by, bz);
        markDirty(bx + 1, by, bz);
        markDirty(bx, by - 1, bz);
        markDirty(bx, by + 1, bz);
        markDirty(bx, by, bz - 1);
        markDirty(bx, by, bz + 1);
    }

    private void markDirty(int bx, int by, int bz) {
        if (bx < 0 || bx >= WORLD_X || by < 0 || by >= WORLD_Y || bz < 0 || bz >= WORLD_Z) return;
        chunkDirty[bx >> 4][by >> 4][bz >> 4] = true;
    }

    // ---------------- 渲染 ----------------

    private void render() {
        for (int cx = 0; cx < CHUNKS_X; cx++)
            for (int cy = 0; cy < CHUNKS_Y; cy++)
                for (int cz = 0; cz < CHUNKS_Z; cz++)
                    if (chunkDirty[cx][cy][cz]) {
                        compileChunk(cx, cy, cz);
                        chunkDirty[cx][cy][cz] = false;
                    }

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) width / height;
        float fov = 70f, near = 0.1f, far = 1000f;
        float f = 1.0f / (float) Math.tan(Math.toRadians(fov) / 2);
        float[] proj = {
                f / aspect, 0, 0, 0,
                0, f, 0, 0,
                0, 0, (far + near) / (near - far), -1,
                0, 0, (2 * far * near) / (near - far), 0
        };
        glLoadMatrixf(proj);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glRotatef(pitch, 1, 0, 0);
        glRotatef(yaw, 0, 1, 0);
        glTranslatef(-playerX, -(playerY + EYE_HEIGHT), -playerZ);

        glEnable(GL_TEXTURE_2D);

        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);
        float vx = (float) ( Math.sin(radYaw) * Math.cos(radPitch));
        float vy = (float) (-Math.sin(radPitch));
        float vz = (float) (-Math.cos(radYaw) * Math.cos(radPitch));

        float eyeX = playerX;
        float eyeY = playerY + EYE_HEIGHT;
        float eyeZ = playerZ;

        float rangeSq = RENDER_DISTANCE * RENDER_DISTANCE;
        float chunkR = (float) (Math.sqrt(3) * CHUNK_SIZE * 0.5);

        for (int cx = 0; cx < CHUNKS_X; cx++) {
            for (int cy = 0; cy < CHUNKS_Y; cy++) {
                for (int cz = 0; cz < CHUNKS_Z; cz++) {
                    float ccx = cx * CHUNK_SIZE + CHUNK_SIZE / 2f;
                    float ccy = cy * CHUNK_SIZE + CHUNK_SIZE / 2f;
                    float ccz = cz * CHUNK_SIZE + CHUNK_SIZE / 2f;
                    float dx = ccx - eyeX;
                    float dy = ccy - eyeY;
                    float dz = ccz - eyeZ;

                    if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

                    float dot = dx * vx + dy * vy + dz * vz;
                    if (dot < -chunkR) continue;

                    glCallList(chunkLists[cx][cy][cz]);
                }
            }
        }

        if (hasHitBlock) {
            drawBlockHighlight(hitBlockX, hitBlockY, hitBlockZ);
        }
    }

    private void compileChunk(int cx, int cy, int cz) {
        int x0 = cx * CHUNK_SIZE;
        int y0 = cy * CHUNK_SIZE;
        int z0 = cz * CHUNK_SIZE;

        glNewList(chunkLists[cx][cy][cz], GL_COMPILE);

        int currentTex = -1;
        for (int x = x0; x < x0 + CHUNK_SIZE; x++) {
            for (int y = y0; y < y0 + CHUNK_SIZE; y++) {
                for (int z = z0; z < z0 + CHUNK_SIZE; z++) {
                    byte block = world[x][y][z];
                    if (block == Block.AIR) continue;

                    BlockRegistry.BlockType bt = BlockRegistry.get(block);
                    if (bt == null || bt.texture <= 0) continue;

                    if (bt.texture != currentTex) {
                        glBindTexture(GL_TEXTURE_2D, bt.texture);
                        currentTex = bt.texture;
                    }
                    drawBlockFaces(x, y, z);
                }
            }
        }

        glEndList();
    }

    private void drawBlockFaces(int x, int y, int z) {
        glBegin(GL_QUADS);

        if (y == WORLD_Y - 1 || world[x][y + 1][z] == Block.AIR) {
            glNormal3f(0, 1, 0);
            glTexCoord2f(0, 0); glVertex3f(x, y + 1, z);
            glTexCoord2f(0, 1); glVertex3f(x, y + 1, z + 1);
            glTexCoord2f(1, 1); glVertex3f(x + 1, y + 1, z + 1);
            glTexCoord2f(1, 0); glVertex3f(x + 1, y + 1, z);
        }
        if (y == 0 || world[x][y - 1][z] == Block.AIR) {
            glNormal3f(0, -1, 0);
            glTexCoord2f(0, 0); glVertex3f(x, y, z + 1);
            glTexCoord2f(0, 1); glVertex3f(x, y, z);
            glTexCoord2f(1, 1); glVertex3f(x + 1, y, z);
            glTexCoord2f(1, 0); glVertex3f(x + 1, y, z + 1);
        }
        if (z == 0 || world[x][y][z - 1] == Block.AIR) {
            glNormal3f(0, 0, -1);
            glTexCoord2f(0, 1); glVertex3f(x, y, z);
            glTexCoord2f(0, 0); glVertex3f(x, y + 1, z);
            glTexCoord2f(1, 0); glVertex3f(x + 1, y + 1, z);
            glTexCoord2f(1, 1); glVertex3f(x + 1, y, z);
        }
        if (z == WORLD_Z - 1 || world[x][y][z + 1] == Block.AIR) {
            glNormal3f(0, 0, 1);
            glTexCoord2f(0, 1); glVertex3f(x + 1, y, z + 1);
            glTexCoord2f(0, 0); glVertex3f(x + 1, y + 1, z + 1);
            glTexCoord2f(1, 0); glVertex3f(x, y + 1, z + 1);
            glTexCoord2f(1, 1); glVertex3f(x, y, z + 1);
        }
        if (x == 0 || world[x - 1][y][z] == Block.AIR) {
            glNormal3f(-1, 0, 0);
            glTexCoord2f(0, 1); glVertex3f(x, y, z + 1);
            glTexCoord2f(0, 0); glVertex3f(x, y + 1, z + 1);
            glTexCoord2f(1, 0); glVertex3f(x, y + 1, z);
            glTexCoord2f(1, 1); glVertex3f(x, y, z);
        }
        if (x == WORLD_X - 1 || world[x + 1][y][z] == Block.AIR) {
            glNormal3f(1, 0, 0);
            glTexCoord2f(0, 1); glVertex3f(x + 1, y, z);
            glTexCoord2f(0, 0); glVertex3f(x + 1, y + 1, z);
            glTexCoord2f(1, 0); glVertex3f(x + 1, y + 1, z + 1);
            glTexCoord2f(1, 1); glVertex3f(x + 1, y, z + 1);
        }

        glEnd();
    }

    private void cleanup() {
        glfwDestroyWindow(window);
        glfwTerminate();
    }
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
│       └── MinecraftEdi-0.19.jar      ← 手动放入
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
        int id = BlockRegistry.register(
                "ruby",
                "/assets/myfirstmod/textures/block/ruby.png"
        );
        System.out.println("[MyFirstMod] ruby 方块已注册, id = " + id);
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
                └── stone.png
```

## `package.edimeta`

```json
{
    "pack": {
        "pack_format": 1,
        "description": "我的资源包 - 把草方块改成蓝的"
    }
}
```

结构与原版 `pack.mcmeta` 完全一样，只是文件名改成 `package.edimeta`。

## 规则

- 启动时扫描 `resourcepacks/` 下所有**子文件夹**。
- 只有包含 `package.edimeta` 的文件夹才会被识别。
- 覆盖路径与内置方块贴图路径一致即可，如 `assets/minecraft/textures/block/grass.png`。
- 后加载的资源包覆盖先加载的。

---

# 版本历史

| 版本 | 内容 |
|---|---|
| 0.18 | 超平坦世界、挖掘放置、方块高亮、区块显示列表渲染 |
| 0.19 | EdiPack Mod 加载器、资源包系统、快捷栏、放置不受 y 轴限制 |

---

# 许可证

本项目采用 **MIT License** 授权。

```
MIT License

Copyright (c) 2026 fire

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

MinecraftEdi 与 Mojang / Microsoft 无关，是一个独立的学习项目。