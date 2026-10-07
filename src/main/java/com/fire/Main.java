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
        // 1) classpath
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

        // 2) 文件系统
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