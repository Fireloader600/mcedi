package com.fire;

import com.fire.block.Block;
import com.fire.block.BlockRegistry;
import com.fire.mod.ModLoader;
import com.fire.pack.ResourcePackLoader;
import com.fire.sound.SoundManager;
import com.fire.ui.FontRenderer;
import com.fire.ui.MenuRenderer;
import com.fire.world.SaveManager;
import com.fire.world.WorldGen;
import com.fire.world.WorldTag;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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

    private enum State { MENU, WORLD_LIST, NEW_WORLD, INVENTORY, GAME }
    private State state = State.MENU;

    private byte[][][] world;
    private int[][][] chunkLists;
    private boolean[][][] chunkDirty;
    private SaveManager.WorldInfo currentWorld;

    private float playerX = 64.5f, playerY = 64.0f, playerZ = 64.5f;
    private float velY = 0;
    private boolean onGround = false;
    private float yaw = 0, pitch = 0;

    private static final float PLAYER_WIDTH = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float EYE_HEIGHT = 1.62f;
    private static final float MOVE_SPEED = 4.3f;
    private static final float GRAVITY = 32f;
    private static final float JUMP_SPEED = 9.0f;
    private static final float RENDER_DISTANCE = 32f;
    private static final int MAX_CHUNK_REBUILDS_PER_FRAME = 2;
    private static final int STACK_SIZE = 64;

    private double mouseX = 0, mouseY = 0;
    private boolean mousePressed = false;
    private boolean leftClicked = false, rightClicked = false;
    private double lastMouseX = 0, lastMouseY = 0;
    private boolean firstMouse = true;

    private double lastActionTime = 0;
    private static final double ACTION_COOLDOWN = 0.15;

    private double stepTimer = 0;
    private static final double STEP_INTERVAL = 0.35;

    private boolean hasHitBlock = false;
    private int hitBlockX, hitBlockY, hitBlockZ;

    // 游戏模式：creative 或 survival
    private String gameMode = "creative";
    // 新建世界时选择的模式
    private String pendingGameMode = "creative";

    // 快捷栏 + 背包（生存模式）
    private final byte[] hotbarBlock = new byte[9];
    private final int[]  hotbarCount = new int[9];
    private final byte[] invBlock = new byte[27];
    private final int[]  invCount = new int[27];
    private int selectedSlot = 0;

    // 创造模式可选的方块
    private static final byte[] AVAILABLE_BLOCKS = {
            Block.GRASS, Block.STONE, Block.DIRT, Block.PLANKS, Block.LOG, Block.LEAVES,
            Block.COBBLESTONE, Block.COAL_ORE, Block.IRON_ORE, Block.GOLD_ORE,
            Block.DIAMOND_ORE, Block.SAND
    };

    private List<ResourcePackLoader.Pack> packs;

    private int texDirt = 0;
    private int texLogo = 0;
    private int texIcon = 0;
    private int texButton = 0;
    private int texButtonHighlighted = 0;

    private final List<SaveManager.WorldInfo> worldList = new ArrayList<>();
    private String newWorldName = "新世界";

    private static final String PATH_DIRT  = "/assets/minecraft/textures/block/dirt.png";
    private static final String PATH_ICON  = "/assets/minecraft/textures/gui/msg/icon.png";
    private static final String PATH_LOGO  = "/assets/minecraft/textures/gui/title/logo.png";
    private static final String PATH_BUTTON             = "/assets/minecraft/textures/gui/widget/button.png";
    private static final String PATH_BUTTON_HIGHLIGHTED = "/assets/minecraft/textures/gui/widget/button_highlighted.png";

    public static void main(String[] args) {
        setupNatives();
        new Main().run();
    }

    private static void setupNatives() {
        String[][] NATIVES = {
                {"windows/x64/org/lwjgl/lwjgl.dll",        "lwjgl.dll"},
                {"windows/x64/org/lwjgl/lwjgl_opengl.dll", "lwjgl_opengl.dll"},
                {"windows/x64/org/lwjgl/lwjgl_glfw.dll",   "lwjgl_glfw.dll"},
                {"windows/x64/org/lwjgl/lwjgl_stb.dll",    "lwjgl_stb.dll"},
                {"windows/x64/org/lwjgl/lwjgl_openal.dll", "lwjgl_openal.dll"},
                {"windows/x64/org/lwjgl/glfw.dll",         "glfw.dll"},
                {"windows/x64/org/lwjgl/OpenAL.dll",       "OpenAL.dll"},
                {"windows/x64/org/lwjgl/jemalloc.dll",     "jemalloc.dll"},
                {"lwjgl.dll",        "lwjgl.dll"},
                {"lwjgl_opengl.dll", "lwjgl_opengl.dll"},
                {"lwjgl_glfw.dll",   "lwjgl_glfw.dll"},
                {"lwjgl_stb.dll",    "lwjgl_stb.dll"},
                {"lwjgl_openal.dll", "lwjgl_openal.dll"},
                {"glfw.dll",         "glfw.dll"},
                {"OpenAL.dll",       "OpenAL.dll"}
        };
        Path tmp = null;
        int extracted = 0;
        try {
            tmp = Files.createTempDirectory("mcedi-natives-");
            for (String[] pair : NATIVES) {
                try (InputStream in = Main.class.getResourceAsStream("/" + pair[0])) {
                    if (in == null) continue;
                    Path out = tmp.resolve(pair[1]);
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    out.toFile().deleteOnExit();
                    extracted++;
                }
            }
        } catch (Exception ignored) {}
        if (extracted > 0 && tmp != null) {
            System.setProperty("org.lwjgl.librarypath", tmp.toAbsolutePath().toString());
            tmp.toFile().deleteOnExit();
        }
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
        window = glfwCreateWindow(width, height, "MinecraftEdi 0.21", 0, 0);
        if (window == 0) throw new RuntimeException("无法创建窗口");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();

        setWindowIcon(PATH_ICON);
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        setupCallbacks();

        BlockRegistry.initBuiltin();
        WorldTag.initBuiltin();
        packs = ResourcePackLoader.loadAll();
        ModLoader.loadAll(new BlockRegistry(), new WorldTag());
        SoundManager.loadAll();

        for (BlockRegistry.BlockType bt : BlockRegistry.all()) {
            if (bt.topTexturePath == null && bt.sideTexturePath == null) continue;
            bt.topTexture = loadTexture(bt.topTexturePath, bt.classLoader, packs);
            bt.sideTexture = loadTexture(bt.sideTexturePath, bt.classLoader, packs);
            bt.bottomTexture = loadTexture(bt.bottomTexturePath, bt.classLoader, packs);
        }

        texDirt = loadTexture(PATH_DIRT, Main.class.getClassLoader(), packs);
        texIcon = loadTexture(PATH_ICON, Main.class.getClassLoader(), packs);
        texLogo = loadTexture(PATH_LOGO, Main.class.getClassLoader(), packs);
        texButton            = loadTexture(PATH_BUTTON, Main.class.getClassLoader(), packs);
        texButtonHighlighted = loadTexture(PATH_BUTTON_HIGHLIGHTED, Main.class.getClassLoader(), packs);

        if (texDirt > 0) {
            glBindTexture(GL_TEXTURE_2D, texDirt);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        }

        SaveManager.getSavesDir();
        initInventory();

        chunkLists = new int[CHUNKS_X][CHUNKS_Y][CHUNKS_Z];
        chunkDirty = new boolean[CHUNKS_X][CHUNKS_Y][CHUNKS_Z];
        for (int cx = 0; cx < CHUNKS_X; cx++)
            for (int cy = 0; cy < CHUNKS_Y; cy++)
                for (int cz = 0; cz < CHUNKS_Z; cz++)
                    chunkLists[cx][cy][cz] = glGenLists(1);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glClearColor(0.5f, 0.7f, 1.0f, 1.0f);
    }

    private void setupCallbacks() {
        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            width = Math.max(w, 1);
            height = Math.max(h, 1);
            glViewport(0, 0, width, height);
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (state == State.GAME) {
                if (action == GLFW_PRESS) {
                    if (button == GLFW_MOUSE_BUTTON_LEFT) leftClicked = true;
                    else if (button == GLFW_MOUSE_BUTTON_RIGHT) rightClicked = true;
                }
            } else {
                if (action == GLFW_PRESS) {
                    mousePressed = true;
                    SoundManager.play("pop");
                }
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer ww = stack.mallocInt(1);
                IntBuffer wh = stack.mallocInt(1);
                glfwGetWindowSize(window, ww, wh);
                double sx = ww.get(0) > 0 ? (double) width / ww.get(0) : 1.0;
                double sy = wh.get(0) > 0 ? (double) height / wh.get(0) : 1.0;
                double mx = xpos * sx;
                double my = ypos * sy;

                if (state == State.GAME) {
                    if (firstMouse) {
                        lastMouseX = mx;
                        lastMouseY = my;
                        firstMouse = false;
                        return;
                    }
                    float dx = (float) (mx - lastMouseX);
                    float dy = (float) (my - lastMouseY);
                    lastMouseX = mx;
                    lastMouseY = my;
                    yaw += dx * 0.15f;
                    pitch += dy * 0.15f;
                    if (pitch > 89f) pitch = 89f;
                    if (pitch < -89f) pitch = -89f;
                } else {
                    mouseX = mx;
                    mouseY = my;
                }
            }
        });

        glfwSetCharCallback(window, (win, codepoint) -> {
            if (state == State.NEW_WORLD) {
                char c = (char) codepoint;
                if (newWorldName.length() < 20 && !Character.isISOControl(c)) {
                    newWorldName += c;
                }
            }
        });

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action != GLFW_PRESS) return;

            if (state == State.NEW_WORLD && key == GLFW_KEY_BACKSPACE) {
                if (newWorldName.length() > 0) {
                    newWorldName = newWorldName.substring(0, newWorldName.length() - 1);
                }
                return;
            }

            if (key == GLFW_KEY_E) {
                if (state == State.GAME) {
                    enterState(State.INVENTORY);
                } else if (state == State.INVENTORY) {
                    enterState(State.GAME);
                }
                return;
            }

            if (state == State.INVENTORY && key == GLFW_KEY_ESCAPE) {
                enterState(State.GAME);
                return;
            }

            if (state == State.GAME && key == GLFW_KEY_ESCAPE) {
                saveCurrentWorld();
                worldList.clear();
                worldList.addAll(SaveManager.listWorlds());
                enterState(State.MENU);
            }
        });
    }

    private void enterState(State s) {
        if (s == State.GAME) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            if (glfwRawMouseMotionSupported())
                glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
            firstMouse = true;
            stepTimer = 0;
        } else {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        }
        state = s;
        mousePressed = false;
    }

    // ---------------- 物品栏数据 ----------------

    private boolean isSurvival() {
        return "survival".equals(gameMode);
    }

    private void initInventory() {
        for (int i = 0; i < 9; i++) { hotbarBlock[i] = Block.AIR; hotbarCount[i] = 0; }
        for (int i = 0; i < 27; i++) { invBlock[i] = Block.AIR; invCount[i] = 0; }
        selectedSlot = 0;

        if (!isSurvival()) {
            // 创造模式：默认给 9 个方块
            byte[] defaults = {
                    Block.GRASS, Block.STONE, Block.DIRT, Block.PLANKS,
                    Block.LOG, Block.LEAVES, Block.COBBLESTONE, Block.SAND, Block.COAL_ORE
            };
            for (int i = 0; i < 9; i++) {
                hotbarBlock[i] = defaults[i];
                hotbarCount[i] = 0;
            }
        }
    }

    /** 生存模式：把物品加入背包（先堆叠，再找空位） */
    private void giveItem(byte block, int count) {
        if (block == Block.AIR || count <= 0) return;

        // 堆叠到已有
        for (int i = 0; i < 9; i++) {
            if (hotbarBlock[i] == block && hotbarCount[i] < STACK_SIZE) {
                hotbarCount[i] = Math.min(STACK_SIZE, hotbarCount[i] + count);
                return;
            }
        }
        for (int i = 0; i < 27; i++) {
            if (invBlock[i] == block && invCount[i] < STACK_SIZE) {
                invCount[i] = Math.min(STACK_SIZE, invCount[i] + count);
                return;
            }
        }
        // 找空位，优先快捷栏
        for (int i = 0; i < 9; i++) {
            if (hotbarBlock[i] == Block.AIR) {
                hotbarBlock[i] = block;
                hotbarCount[i] = count;
                return;
            }
        }
        for (int i = 0; i < 27; i++) {
            if (invBlock[i] == Block.AIR) {
                invBlock[i] = block;
                invCount[i] = count;
                return;
            }
        }
    }

    /** 草方块掉泥土，其他方块掉自身 */
    private byte getDrop(byte block) {
        if (block == Block.GRASS) return Block.DIRT;
        return block;
    }

    // ---------------- 资源加载 ----------------

    private static ByteBuffer loadResource(String path, ClassLoader cl) {
        if (path == null) return null;
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
        if (fileBuf == null) return;
        IntBuffer w = memAllocInt(1);
        IntBuffer h = memAllocInt(1);
        IntBuffer comp = memAllocInt(1);
        try {
            stbi_set_flip_vertically_on_load(false);
            ByteBuffer image = stbi_load_from_memory(fileBuf, w, h, comp, 4);
            if (image == null) return;
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
        if (path == null) return 0;
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
                if (fileBuf == null) return 0;
            }
            stbi_set_flip_vertically_on_load(false);
            ByteBuffer image = stbi_load_from_memory(fileBuf, w, h, comp, 4);
            if (image == null) return 0;
            int tex = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, tex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            stbi_image_free(image);
            return tex;
        } catch (Throwable t) {
            return 0;
        } finally {
            memFree(w); memFree(h); memFree(comp);
            if (fileBuf != null) memFree(fileBuf);
        }
    }

    // ---------------- 主循环 ----------------

    private void loop() {
        double lastTime = glfwGetTime();
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float delta = (float) (now - lastTime);
            lastTime = now;
            if (delta > 0.05f) delta = 0.05f;

            switch (state) {
                case MENU:
                    handleMenu();
                    MenuRenderer.render(width, height, mouseX, mouseY,
                            texDirt, texLogo, texIcon, texButton, texButtonHighlighted);
                    break;
                case WORLD_LIST:
                    handleWorldList();
                    renderWorldList();
                    break;
                case NEW_WORLD:
                    handleNewWorld();
                    renderNewWorld();
                    break;
                case INVENTORY:
                    handleInventory();
                    renderInventory();
                    break;
                case GAME:
                    handleGame(delta);
                    updateGame(delta);
                    renderGame();
                    break;
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    // ---------------- 2D 通用 ----------------

    private void begin2D() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1, 1, 1, 1);
    }

    private void end2D() {
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    private void drawRect(float x, float y, float w, float h, float r, float g, float b, float a) {
        glDisable(GL_TEXTURE_2D);
        glColor4f(r, g, b, a);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x, y + h);
        glVertex2f(x + w, y + h);
        glVertex2f(x + w, y);
        glEnd();
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
    }

    private void drawText(String text, float x, float y, int size, float r, float g, float b, float a) {
        FontRenderer.draw(text, x, y, size, r, g, b, a);
    }

    private void drawTextCentered(String text, float cx, float y, int size, float r, float g, float b, float a) {
        FontRenderer.drawCentered(text, cx, y, size, r, g, b, a);
    }

    private boolean inRect(float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void drawSlot(float x, float y, float size, boolean selected, byte block, int count, boolean showCount) {
        drawRect(x, y, size, size, selected ? 0.9f : 0.3f, selected ? 0.9f : 0.3f, selected ? 0.9f : 0.3f, 0.6f);
        drawRect(x, y, size, 2, 0, 0, 0, 1);
        drawRect(x, y + size - 2, size, 2, 0, 0, 0, 1);
        drawRect(x, y, 2, size, 0, 0, 0, 1);
        drawRect(x + size - 2, y, 2, size, 0, 0, 0, 1);

        BlockRegistry.BlockType bt = BlockRegistry.get(block);
        if (bt != null && bt.getSideTexture() > 0) {
            glColor4f(1, 1, 1, 1);
            glBindTexture(GL_TEXTURE_2D, bt.getSideTexture());
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(x + 6, y + 6);
            glTexCoord2f(0, 1); glVertex2f(x + 6, y + size - 6);
            glTexCoord2f(1, 1); glVertex2f(x + size - 6, y + size - 6);
            glTexCoord2f(1, 0); glVertex2f(x + size - 6, y + 6);
            glEnd();
        }

        if (showCount && count > 1) {
            drawText(String.valueOf(count), x + size - 22, y + size - 22, 14, 1, 1, 1, 1);
        }
    }

    private boolean drawMcButton(String label, float x, float y, float w, float h, int fontSize) {
        boolean hovered = inRect(x, y, w, h);

        int tex = hovered ? texButtonHighlighted : texButton;
        if (tex <= 0) tex = texButton;

        if (tex > 0) {
            glColor4f(1, 1, 1, 1);
            glBindTexture(GL_TEXTURE_2D, tex);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(x, y);
            glTexCoord2f(0, 1); glVertex2f(x, y + h);
            glTexCoord2f(1, 1); glVertex2f(x + w, y + h);
            glTexCoord2f(1, 0); glVertex2f(x + w, y);
            glEnd();

            int tw = FontRenderer.width(label, fontSize);
            int th = FontRenderer.height(label, fontSize);
            float tx = x + (w - tw) / 2f;
            float ty = y + (h - th) / 2f;
            drawText(label, tx + 1, ty + 1, fontSize, 0, 0, 0, 0.75f);
            drawText(label, tx, ty, fontSize, 1, 1, 1, 1);
            return hovered;
        }

        drawRect(x, y, w, h, 0, 0, 0, 1);
        float r, g, b;
        if (hovered) { r = 0.42f; g = 0.54f; b = 0.85f; }
        else         { r = 0.60f; g = 0.60f; b = 0.60f; }
        drawRect(x + 1, y + 1, w - 2, h - 2, r, g, b, 1);
        drawRect(x + 1, y + 1, w - 2, 1, 1, 1, 1, 0.45f);
        drawRect(x + 1, y + 1, 1, h - 2, 1, 1, 1, 0.45f);
        drawRect(x + 1, y + h - 2, w - 2, 1, 0, 0, 0, 0.45f);
        drawRect(x + w - 2, y + 1, 1, h - 2, 0, 0, 0, 0.45f);

        int tw = FontRenderer.width(label, fontSize);
        int th = FontRenderer.height(label, fontSize);
        float tx = x + (w - tw) / 2f;
        float ty = y + (h - th) / 2f;
        drawText(label, tx + 1, ty + 1, fontSize, 0, 0, 0, 0.75f);
        drawText(label, tx, ty, fontSize, 1, 1, 1, 1);
        return hovered;
    }

    private void drawDirtBackground() {
        if (texDirt > 0) {
            glEnable(GL_TEXTURE_2D);
            glColor4f(1, 1, 1, 1);
            glBindTexture(GL_TEXTURE_2D, texDirt);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            float tu = width / 32f;
            float tv = height / 32f;
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0);   glVertex2f(0, 0);
            glTexCoord2f(0, tv);  glVertex2f(0, height);
            glTexCoord2f(tu, tv); glVertex2f(width, height);
            glTexCoord2f(tu, 0);  glVertex2f(width, 0);
            glEnd();
        } else {
            drawRect(0, 0, width, height, 0.32f, 0.22f, 0.14f, 1);
        }
        drawRect(0, 0, width, height, 0, 0, 0, 0.45f);
    }

    // ---------------- 菜单 ----------------

    private void handleMenu() {
        if (!mousePressed) return;
        mousePressed = false;
        if (MenuRenderer.isButtonHit(width, height, mouseX, mouseY)) {
            worldList.clear();
            worldList.addAll(SaveManager.listWorlds());
            enterState(State.WORLD_LIST);
        }
    }

    // ---------------- 世界列表 ----------------

    private void handleWorldList() {
        if (!mousePressed) return;
        mousePressed = false;

        float itemW = 400, itemH = 40;
        float startX = width / 2f - itemW / 2f;
        float startY = 120;

        for (int i = 0; i < worldList.size(); i++) {
            float y = startY + i * (itemH + 8);
            if (inRect(startX, y, itemW, itemH)) {
                loadOrCreate(worldList.get(i));
                return;
            }
        }

        float newY = startY + worldList.size() * (itemH + 8) + 20;
        if (inRect(startX, newY, itemW, itemH)) {
            newWorldName = "新世界";
            pendingGameMode = "creative";
            enterState(State.NEW_WORLD);
            return;
        }

        float backY = newY + itemH + 8;
        if (inRect(startX, backY, itemW, itemH)) {
            enterState(State.MENU);
        }
    }

    private void renderWorldList() {
        begin2D();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        drawDirtBackground();

        drawTextCentered("选择存档", width / 2f, 50, 28, 1, 1, 1, 1);

        float itemW = 400, itemH = 40;
        float startX = width / 2f - itemW / 2f;
        float startY = 120;

        for (int i = 0; i < worldList.size(); i++) {
            SaveManager.WorldInfo info = worldList.get(i);
            float y = startY + i * (itemH + 8);
            String mode = "survival".equals(info.gameMode) ? "生存" : "创造";
            String label = info.name + "  [" + info.type + " · " + mode + "]";
            drawMcButton(label, startX, y, itemW, itemH, 16);
        }

        float newY = startY + worldList.size() * (itemH + 8) + 20;
        drawMcButton("+ 新建存档", startX, newY, itemW, itemH, 16);

        float backY = newY + itemH + 8;
        drawMcButton("返回", startX, backY, itemW, itemH, 16);

        end2D();
    }

    // ---------------- 新建世界 ----------------

    private void handleNewWorld() {
        if (!mousePressed) return;
        mousePressed = false;

        float bw = 200, bh = 40;
        float by = height / 2f + 30;

        // 模式切换按钮
        float modeX = width / 2f - 130;
        if (inRect(modeX, height / 2f - 20, 260, 36)) {
            pendingGameMode = pendingGameMode.equals("survival") ? "creative" : "survival";
            return;
        }

        // 超平坦
        float bx1 = width / 2f - bw - 10;
        if (inRect(bx1, by, bw, bh)) {
            createAndLoad("flat");
            return;
        }
        // 无限
        if (inRect(bx1 + bw + 20, by, bw, bh)) {
            createAndLoad("infinite");
            return;
        }

        float ry = by + bh + 12;
        if (inRect(width / 2f - 100, ry, 200, 40)) {
            enterState(State.WORLD_LIST);
        }
    }

    private void renderNewWorld() {
        begin2D();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        drawDirtBackground();

        drawTextCentered("新建存档", width / 2f, 60, 28, 1, 1, 1, 1);
        drawTextCentered("名称:", width / 2f, height / 2f - 110, 16, 0.85f, 0.85f, 0.85f, 1);
        drawTextCentered(newWorldName + "_", width / 2f, height / 2f - 80, 22, 1, 1, 1, 1);

        // 模式切换按钮
        String modeLabel = pendingGameMode.equals("survival")
                ? "模式: 生存（点击切换）"
                : "模式: 创造（点击切换）";
        drawMcButton(modeLabel, width / 2f - 130, height / 2f - 20, 260, 36, 16);

        float bw = 200, bh = 40;
        float by = height / 2f + 30;
        float bx1 = width / 2f - bw - 10;
        drawMcButton("超平坦", bx1, by, bw, bh, 18);
        drawMcButton("无限", bx1 + bw + 20, by, bw, bh, 18);

        float ry = by + bh + 12;
        drawMcButton("返回", width / 2f - 100, ry, 200, 40, 16);

        end2D();
    }

    private void createAndLoad(String type) {
        try {
            SaveManager.WorldInfo info = SaveManager.createWorld(newWorldName, type, pendingGameMode);
            loadOrCreate(info);
        } catch (IOException e) {
        }
    }

    private void loadOrCreate(SaveManager.WorldInfo info) {
        byte[][][] loaded = null;
        try { loaded = SaveManager.load(info); } catch (Exception ignored) {}

        if (loaded == null) {
            loaded = WorldGen.generate(info.seed, info.type);
            int sy = WorldGen.getSurfaceY(loaded, 64, 64);
            info.playerY = sy + 0.1f;
        }

        world = loaded;
        currentWorld = info;
        gameMode = info.gameMode != null ? info.gameMode : "creative";
        playerX = info.playerX;
        playerY = info.playerY;
        playerZ = info.playerZ;
        yaw = info.yaw;
        pitch = info.pitch;
        velY = 0;
        onGround = false;
        initInventory();

        for (int cx = 0; cx < CHUNKS_X; cx++)
            for (int cy = 0; cy < CHUNKS_Y; cy++)
                for (int cz = 0; cz < CHUNKS_Z; cz++) {
                    compileChunk(cx, cy, cz);
                    chunkDirty[cx][cy][cz] = false;
                }

        enterState(State.GAME);
    }

    private void saveCurrentWorld() {
        if (currentWorld == null || world == null) return;
        currentWorld.playerX = playerX;
        currentWorld.playerY = playerY;
        currentWorld.playerZ = playerZ;
        currentWorld.yaw = yaw;
        currentWorld.pitch = pitch;
        currentWorld.gameMode = gameMode;
        try {
            SaveManager.save(currentWorld, world);
        } catch (Exception e) {
        }
    }

    // ---------------- 物品栏界面 ----------------

    private void handleInventory() {
        if (!mousePressed) return;
        mousePressed = false;

        if (isSurvival()) {
            handleSurvivalInventory();
        } else {
            handleCreativeInventory();
        }
    }

    private void handleCreativeInventory() {
        int cols = 6;
        int slotSize = 60;
        int gap = 10;
        int rows = (AVAILABLE_BLOCKS.length + cols - 1) / cols;
        float totalW = cols * slotSize + (cols - 1) * gap;
        float startX = width / 2f - totalW / 2f;
        float startY = height / 2f - (rows * slotSize + (rows - 1) * gap) / 2f;

        for (int i = 0; i < AVAILABLE_BLOCKS.length; i++) {
            int col = i % cols;
            int row = i / cols;
            float x = startX + col * (slotSize + gap);
            float y = startY + row * (slotSize + gap);
            if (inRect(x, y, slotSize, slotSize)) {
                hotbarBlock[selectedSlot] = AVAILABLE_BLOCKS[i];
                hotbarCount[selectedSlot] = 0;
                enterState(State.GAME);
                return;
            }
        }
    }

    /** 生存：底部快捷栏 9 格 + 上方背包 27 格 */
    private void handleSurvivalInventory() {
        int cols = 9;
        int slotSize = 50;
        int gap = 4;
        float totalW = cols * slotSize + (cols - 1) * gap;
        float startX = width / 2f - totalW / 2f;

        // 背包 27 格（3 行）在上方
        int invRows = 3;
        float invTotalH = invRows * slotSize + (invRows - 1) * gap;
        float invStartY = height / 2f - invTotalH / 2f - 30;

        for (int i = 0; i < 27; i++) {
            int col = i % cols;
            int row = i / cols;
            float x = startX + col * (slotSize + gap);
            float y = invStartY + row * (slotSize + gap);
            if (inRect(x, y, slotSize, slotSize)) {
                swapWithHotbar(i);
                return;
            }
        }

        // 快捷栏 9 格在下方
        float hotbarY = invStartY + invTotalH + 40;
        for (int i = 0; i < 9; i++) {
            float x = startX + i * (slotSize + gap);
            if (inRect(x, hotbarY, slotSize, slotSize)) {
                selectedSlot = i;
                return;
            }
        }
    }

    private void swapWithHotbar(int invIndex) {
        // 与当前选中的快捷栏格子交换
        byte oldBlock = hotbarBlock[selectedSlot];
        int oldCount = hotbarCount[selectedSlot];

        hotbarBlock[selectedSlot] = invBlock[invIndex];
        hotbarCount[selectedSlot] = invCount[invIndex];

        invBlock[invIndex] = oldBlock;
        invCount[invIndex] = oldCount;
    }

    private void renderInventory() {
        begin2D();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        drawDirtBackground();

        if (isSurvival()) {
            drawTextCentered("背包", width / 2f, 40, 28, 1, 1, 1, 1);
            drawTextCentered("点击背包中的物品 → 与当前快捷栏格子交换 · E / ESC 返回",
                    width / 2f, 76, 14, 0.85f, 0.85f, 0.85f, 1);

            int cols = 9;
            int slotSize = 50;
            int gap = 4;
            float totalW = cols * slotSize + (cols - 1) * gap;
            float startX = width / 2f - totalW / 2f;

            int invRows = 3;
            float invTotalH = invRows * slotSize + (invRows - 1) * gap;
            float invStartY = height / 2f - invTotalH / 2f - 30;

            for (int i = 0; i < 27; i++) {
                int col = i % cols;
                int row = i / cols;
                float x = startX + col * (slotSize + gap);
                float y = invStartY + row * (slotSize + gap);
                drawSlot(x, y, slotSize, false, invBlock[i], invCount[i], true);
            }

            float hotbarY = invStartY + invTotalH + 40;
            for (int i = 0; i < 9; i++) {
                float x = startX + i * (slotSize + gap);
                drawSlot(x, hotbarY, slotSize, i == selectedSlot,
                        hotbarBlock[i], hotbarCount[i], true);
            }
        } else {
            drawTextCentered("物品栏", width / 2f, 60, 32, 1, 1, 1, 1);
            drawTextCentered("点击方块放入当前格子 · E / ESC 返回", width / 2f, 110, 16, 0.85f, 0.85f, 0.85f, 1);
            drawTextCentered("当前选中格子: " + (selectedSlot + 1), width / 2f, 140, 16, 1, 1, 0.5f, 1);

            int cols = 6;
            int slotSize = 60;
            int gap = 10;
            int rows = (AVAILABLE_BLOCKS.length + cols - 1) / cols;
            float totalW = cols * slotSize + (cols - 1) * gap;
            float startX = width / 2f - totalW / 2f;
            float startY = height / 2f - (rows * slotSize + (rows - 1) * gap) / 2f;

            for (int i = 0; i < AVAILABLE_BLOCKS.length; i++) {
                int col = i % cols;
                int row = i / cols;
                float x = startX + col * (slotSize + gap);
                float y = startY + row * (slotSize + gap);
                drawSlot(x, y, slotSize, false, AVAILABLE_BLOCKS[i], 0, false);
            }
        }

        end2D();
    }

    // ---------------- 游戏 ----------------

    private void handleGame(float delta) {
        for (int i = 0; i < 9; i++) {
            if (glfwGetKey(window, GLFW_KEY_1 + i) == GLFW_PRESS) selectedSlot = i;
        }

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
        boolean moving = mdx != 0 || mdz != 0;
        if (moving) movePlayer(mdx, 0, mdz);

        if (moving && onGround) {
            stepTimer += delta;
            if (stepTimer >= STEP_INTERVAL) {
                stepTimer = 0;
                playStepSound();
            }
        } else {
            stepTimer = STEP_INTERVAL;
        }

        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS && onGround) {
            velY = JUMP_SPEED;
            onGround = false;
        }

        float radPitch = (float) Math.toRadians(pitch);
        float lx = (float) (Math.sin(radYaw) * Math.cos(radPitch));
        float ly = (float) (-Math.sin(radPitch));
        float lz = (float) (-Math.cos(radYaw) * Math.cos(radPitch));

        double now = glfwGetTime();
        boolean leftDown = leftClicked || glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
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

    private void playStepSound() {
        int by = (int) Math.floor(playerY - 0.1f);
        if (by < 0 || by >= WORLD_Y) return;
        int bx = (int) Math.floor(playerX);
        int bz = (int) Math.floor(playerZ);
        if (bx < 0 || bx >= WORLD_X || bz < 0 || bz >= WORLD_Z) return;
        byte below = world[bx][by][bz];
        SoundManager.play(stepSoundFor(below));
    }

    private String stepSoundFor(byte block) {
        if (block == Block.GRASS || block == Block.LEAVES) return "step_grass";
        if (block == Block.DIRT || block == Block.SAND) return "step_dirt";
        if (block == Block.PLANKS || block == Block.LOG) return "step_wood";
        return "step_stone";
    }

    private String breakSoundFor(byte block) {
        if (block == Block.GRASS || block == Block.LEAVES) return "break_grass";
        if (block == Block.DIRT || block == Block.SAND) return "break_dirt";
        if (block == Block.PLANKS || block == Block.LOG) return "break_wood";
        return "break_stone";
    }

    private void updateGame(float delta) {
        velY -= GRAVITY * delta;
        if (velY < -50f) velY = -50f;
        onGround = false;
        movePlayer(0, velY * delta, 0);
        updateBlockHighlight();
    }

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
        if (dx != 0) if (!collides(playerX + dx, playerY, playerZ)) playerX += dx;
        if (dz != 0) if (!collides(playerX, playerY, playerZ + dz)) playerZ += dz;
        if (dy != 0) {
            if (!collides(playerX, playerY + dy, playerZ)) playerY += dy;
            else {
                if (dy < 0) onGround = true;
                velY = 0;
            }
        }
    }

    private void updateBlockHighlight() {
        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);
        float lx = (float) (Math.sin(radYaw) * Math.cos(radPitch));
        float ly = (float) (-Math.sin(radPitch));
        float lz = (float) (-Math.cos(radYaw) * Math.cos(radPitch));

        float ex = playerX, ey = playerY + EYE_HEIGHT, ez = playerZ;
        hasHitBlock = false;

        for (float t = 0f; t < 6.0f; t += 0.02f) {
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

    private boolean raycastAction(float lx, float ly, float lz, boolean destroy) {
        float ex = playerX, ey = playerY + EYE_HEIGHT, ez = playerZ;
        int lastX = Integer.MIN_VALUE, lastY = 0, lastZ = 0;

        for (float t = 0f; t < 6.0f; t += 0.02f) {
            int bx = (int) Math.floor(ex + lx * t);
            int by = (int) Math.floor(ey + ly * t);
            int bz = (int) Math.floor(ez + lz * t);
            if (bx < 0 || bx >= WORLD_X || bz < 0 || bz >= WORLD_Z) return false;
            if (by < 0 || by >= WORLD_Y) continue;

            if (world[bx][by][bz] != Block.AIR) {
                if (destroy) {
                    byte old = world[bx][by][bz];
                    SoundManager.play(breakSoundFor(old));
                    world[bx][by][bz] = Block.AIR;
                    markDirtyAround(bx, by, bz);
                    if (isSurvival()) {
                        byte drop = getDrop(old);
                        if (drop != Block.AIR) giveItem(drop, 1);
                    }
                    return true;
                } else {
                    if (lastX != Integer.MIN_VALUE
                            && lastY >= 0 && lastY < WORLD_Y
                            && !blockOverlapsPlayer(lastX, lastY, lastZ)) {

                        byte placed = hotbarBlock[selectedSlot];
                        if (placed == Block.AIR) return false;

                        if (isSurvival()) {
                            if (hotbarCount[selectedSlot] <= 0) return false;
                            hotbarCount[selectedSlot]--;
                            if (hotbarCount[selectedSlot] <= 0) {
                                hotbarBlock[selectedSlot] = Block.AIR;
                                hotbarCount[selectedSlot] = 0;
                            }
                        }

                        world[lastX][lastY][lastZ] = placed;
                        SoundManager.play(breakSoundFor(placed));
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

    private void renderGame() {
        int rebuilt = 0;
        outer:
        for (int cx = 0; cx < CHUNKS_X; cx++) {
            for (int cy = 0; cy < CHUNKS_Y; cy++) {
                for (int cz = 0; cz < CHUNKS_Z; cz++) {
                    if (chunkDirty[cx][cy][cz]) {
                        compileChunk(cx, cy, cz);
                        chunkDirty[cx][cy][cz] = false;
                        if (++rebuilt >= MAX_CHUNK_REBUILDS_PER_FRAME) break outer;
                    }
                }
            }
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
        float vx = (float) (Math.sin(radYaw) * Math.cos(radPitch));
        float vy = (float) (-Math.sin(radPitch));
        float vz = (float) (-Math.cos(radYaw) * Math.cos(radPitch));

        float eyeX = playerX, eyeY = playerY + EYE_HEIGHT, eyeZ = playerZ;
        float rangeSq = RENDER_DISTANCE * RENDER_DISTANCE;
        float chunkR = (float) (Math.sqrt(3) * CHUNK_SIZE * 0.5);

        for (int cx = 0; cx < CHUNKS_X; cx++) {
            for (int cy = 0; cy < CHUNKS_Y; cy++) {
                for (int cz = 0; cz < CHUNKS_Z; cz++) {
                    float ccx = cx * CHUNK_SIZE + CHUNK_SIZE / 2f;
                    float ccy = cy * CHUNK_SIZE + CHUNK_SIZE / 2f;
                    float ccz = cz * CHUNK_SIZE + CHUNK_SIZE / 2f;
                    float dx = ccx - eyeX, dy = ccy - eyeY, dz = ccz - eyeZ;
                    if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
                    float dot = dx * vx + dy * vy + dz * vz;
                    if (dot < -chunkR) continue;
                    glCallList(chunkLists[cx][cy][cz]);
                }
            }
        }

        if (hasHitBlock) drawBlockHighlight(hitBlockX, hitBlockY, hitBlockZ);

        begin2D();
        int slots = 9;
        float slotSize = 50;
        float total = slots * slotSize + (slots - 1) * 6;
        float sx = width / 2f - total / 2f;
        float sy = height - 70;
        for (int i = 0; i < slots; i++) {
            float x = sx + i * (slotSize + 6);
            boolean sel = i == selectedSlot;
            drawSlot(x, sy, slotSize, sel, hotbarBlock[i], hotbarCount[i], isSurvival());
            drawText(String.valueOf(i + 1), x + 4, sy + 2, 14, 1, 1, 1, 1);
        }
        end2D();
    }

    private boolean isSolid(int x, int y, int z) {
        if (x < 0 || x >= WORLD_X || y < 0 || y >= WORLD_Y || z < 0 || z >= WORLD_Z) return true;
        return world[x][y][z] != Block.AIR;
    }

    private void compileChunk(int cx, int cy, int cz) {
        int x0 = cx * CHUNK_SIZE;
        int y0 = cy * CHUNK_SIZE;
        int z0 = cz * CHUNK_SIZE;

        glNewList(chunkLists[cx][cy][cz], GL_COMPILE);
        for (int x = x0; x < x0 + CHUNK_SIZE; x++) {
            for (int y = y0; y < y0 + CHUNK_SIZE; y++) {
                for (int z = z0; z < z0 + CHUNK_SIZE; z++) {
                    byte block = world[x][y][z];
                    if (block == Block.AIR) continue;
                    BlockRegistry.BlockType bt = BlockRegistry.get(block);
                    if (bt == null) continue;
                    if (bt.getSideTexture() <= 0 && bt.getTopTexture() <= 0) continue;

                    if (isSolid(x - 1, y, z) && isSolid(x + 1, y, z)
                            && isSolid(x, y - 1, z) && isSolid(x, y + 1, z)
                            && isSolid(x, y, z - 1) && isSolid(x, y, z + 1)) {
                        continue;
                    }

                    drawBlockFaces(x, y, z, bt);
                }
            }
        }
        glEndList();
    }

    private void drawBlockFaces(int x, int y, int z, BlockRegistry.BlockType bt) {
        // 上
        if (y == WORLD_Y - 1 || world[x][y + 1][z] == Block.AIR) {
            glBindTexture(GL_TEXTURE_2D, bt.getTopTexture());
            glBegin(GL_QUADS);
            glNormal3f(0, 1, 0);
            glTexCoord2f(0, 0); glVertex3f(x, y + 1, z);
            glTexCoord2f(0, 1); glVertex3f(x, y + 1, z + 1);
            glTexCoord2f(1, 1); glVertex3f(x + 1, y + 1, z + 1);
            glTexCoord2f(1, 0); glVertex3f(x + 1, y + 1, z);
            glEnd();
        }
        // 下
        if (y == 0 || world[x][y - 1][z] == Block.AIR) {
            glBindTexture(GL_TEXTURE_2D, bt.getBottomTexture());
            glBegin(GL_QUADS);
            glNormal3f(0, -1, 0);
            glTexCoord2f(0, 0); glVertex3f(x, y, z + 1);
            glTexCoord2f(0, 1); glVertex3f(x, y, z);
            glTexCoord2f(1, 1); glVertex3f(x + 1, y, z);
            glTexCoord2f(1, 0); glVertex3f(x + 1, y, z + 1);
            glEnd();
        }
        // 北
        if (z == 0 || world[x][y][z - 1] == Block.AIR) {
            glBindTexture(GL_TEXTURE_2D, bt.getSideTexture());
            glBegin(GL_QUADS);
            glNormal3f(0, 0, -1);
            glTexCoord2f(1, 1); glVertex3f(x, y, z);
            glTexCoord2f(1, 0); glVertex3f(x, y + 1, z);
            glTexCoord2f(0, 0); glVertex3f(x + 1, y + 1, z);
            glTexCoord2f(0, 1); glVertex3f(x + 1, y, z);
            glEnd();
        }
        // 南
        if (z == WORLD_Z - 1 || world[x][y][z + 1] == Block.AIR) {
            glBindTexture(GL_TEXTURE_2D, bt.getSideTexture());
            glBegin(GL_QUADS);
            glNormal3f(0, 0, 1);
            glTexCoord2f(1, 1); glVertex3f(x + 1, y, z + 1);
            glTexCoord2f(1, 0); glVertex3f(x + 1, y + 1, z + 1);
            glTexCoord2f(0, 0); glVertex3f(x, y + 1, z + 1);
            glTexCoord2f(0, 1); glVertex3f(x, y, z + 1);
            glEnd();
        }
        // 西
        if (x == 0 || world[x - 1][y][z] == Block.AIR) {
            glBindTexture(GL_TEXTURE_2D, bt.getSideTexture());
            glBegin(GL_QUADS);
            glNormal3f(-1, 0, 0);
            glTexCoord2f(1, 1); glVertex3f(x, y, z + 1);
            glTexCoord2f(1, 0); glVertex3f(x, y + 1, z + 1);
            glTexCoord2f(0, 0); glVertex3f(x, y + 1, z);
            glTexCoord2f(0, 1); glVertex3f(x, y, z);
            glEnd();
        }
        // 东
        if (x == WORLD_X - 1 || world[x + 1][y][z] == Block.AIR) {
            glBindTexture(GL_TEXTURE_2D, bt.getSideTexture());
            glBegin(GL_QUADS);
            glNormal3f(1, 0, 0);
            glTexCoord2f(1, 1); glVertex3f(x + 1, y, z);
            glTexCoord2f(1, 0); glVertex3f(x + 1, y + 1, z);
            glTexCoord2f(0, 0); glVertex3f(x + 1, y + 1, z + 1);
            glTexCoord2f(0, 1); glVertex3f(x + 1, y, z + 1);
            glEnd();
        }
    }

    private void cleanup() {
        SoundManager.cleanup();
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}