package com.fire.ui

import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_MODELVIEW
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_PROJECTION
import org.lwjgl.opengl.GL11.GL_QUADS
import org.lwjgl.opengl.GL11.GL_REPEAT
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.glBegin
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glColor4f
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glEnd
import org.lwjgl.opengl.GL11.glGetTexLevelParameteri
import org.lwjgl.opengl.GL11.glLoadIdentity
import org.lwjgl.opengl.GL11.glMatrixMode
import org.lwjgl.opengl.GL11.glOrtho
import org.lwjgl.opengl.GL11.glTexCoord2f
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL11.glVertex2f
import org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT
import org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH
import java.util.HashMap

object MenuRenderer {

    private val texSizeCache = HashMap<Int, Pair<Int, Int>>()

    // 布局尺寸（放大版）
    private const val LOGO_HEIGHT = 160f
    private const val LOGO_TOP_OFFSET = 220f     // 屏幕中线以上多少像素

    private const val BUTTON_W = 500f
    private const val BUTTON_H = 60f
    private const val BUTTON_FONT_SIZE = 26
    private const val BUTTON_Y_OFFSET = 40f      // 屏幕中线以下多少像素

    private const val TIP_FONT_SIZE = 16
    private const val VERSION_FONT_SIZE = 14

    @JvmStatic
    fun render(
        width: Int,
        height: Int,
        mouseX: Double,
        mouseY: Double,
        texDirt: Int,
        texLogo: Int,
        texIcon: Int,
        texButton: Int,
        texButtonHighlighted: Int
    ) {
        begin2D(width, height)

        drawDirtBackground(width, height, texDirt)
        drawLogo(width / 2f, height / 2f - LOGO_TOP_OFFSET, LOGO_HEIGHT, texLogo, texIcon)

        val bx = width / 2f - BUTTON_W / 2f
        val by = height / 2f - BUTTON_H / 2f + BUTTON_Y_OFFSET
        drawMcButton("游戏", bx, by, BUTTON_W, BUTTON_H, BUTTON_FONT_SIZE,
            mouseX, mouseY, texButton, texButtonHighlighted)

        FontRenderer.drawCentered(
            "WASD 移动 · 空格跳跃 · 1-6 选方块 · 左键破坏 · 右键放置 · ESC 返回菜单",
            width / 2f, height - 50f, TIP_FONT_SIZE, 0.85f, 0.85f, 0.85f, 1f
        )
        FontRenderer.drawCentered(
            "MinecraftEdi 0.20",
            width / 2f, height - 24f, VERSION_FONT_SIZE, 0.6f, 0.6f, 0.6f, 1f
        )

        end2D()
    }

    @JvmStatic
    fun isButtonHit(width: Int, height: Int, mouseX: Double, mouseY: Double): Boolean {
        val bx = width / 2f - BUTTON_W / 2f
        val by = height / 2f - BUTTON_H / 2f + BUTTON_Y_OFFSET
        return mouseX >= bx && mouseX <= bx + BUTTON_W &&
                mouseY >= by && mouseY <= by + BUTTON_H
    }

    // ---------------- 内部 ----------------

    private fun begin2D(width: Int, height: Int) {
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        glOrtho(0.0, width.toDouble(), height.toDouble(), 0.0, -1.0, 1.0)
        glMatrixMode(GL_MODELVIEW)
        glLoadIdentity()
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        glEnable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glColor4f(1f, 1f, 1f, 1f)
    }

    private fun end2D() {
        glDisable(GL_BLEND)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
    }

    private fun drawRect(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float, a: Float) {
        glDisable(GL_TEXTURE_2D)
        glColor4f(r, g, b, a)
        glBegin(GL_QUADS)
        glVertex2f(x, y)
        glVertex2f(x, y + h)
        glVertex2f(x + w, y + h)
        glVertex2f(x + w, y)
        glEnd()
        glEnable(GL_TEXTURE_2D)
        glColor4f(1f, 1f, 1f, 1f)
    }

    private fun drawDirtBackground(width: Int, height: Int, texDirt: Int) {
        if (texDirt > 0) {
            glEnable(GL_TEXTURE_2D)
            glColor4f(1f, 1f, 1f, 1f)
            glBindTexture(GL_TEXTURE_2D, texDirt)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
            val tu = width / 32f
            val tv = height / 32f
            glBegin(GL_QUADS)
            glTexCoord2f(0f, 0f);   glVertex2f(0f, 0f)
            glTexCoord2f(0f, tv);   glVertex2f(0f, height.toFloat())
            glTexCoord2f(tu, tv);   glVertex2f(width.toFloat(), height.toFloat())
            glTexCoord2f(tu, 0f);   glVertex2f(width.toFloat(), 0f)
            glEnd()
        } else {
            drawRect(0f, 0f, width.toFloat(), height.toFloat(), 0.32f, 0.22f, 0.14f, 1f)
        }
        drawRect(0f, 0f, width.toFloat(), height.toFloat(), 0f, 0f, 0f, 0.45f)
    }

    private fun drawMcButton(
        label: String, x: Float, y: Float, w: Float, h: Float, fontSize: Int,
        mouseX: Double, mouseY: Double,
        texButton: Int, texButtonHighlighted: Int
    ) {
        val hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h

        var tex = if (hovered) texButtonHighlighted else texButton
        if (tex <= 0) tex = texButton

        if (tex > 0) {
            glColor4f(1f, 1f, 1f, 1f)
            glBindTexture(GL_TEXTURE_2D, tex)
            glBegin(GL_QUADS)
            glTexCoord2f(0f, 0f); glVertex2f(x, y)
            glTexCoord2f(0f, 1f); glVertex2f(x, y + h)
            glTexCoord2f(1f, 1f); glVertex2f(x + w, y + h)
            glTexCoord2f(1f, 0f); glVertex2f(x + w, y)
            glEnd()

            val tw = FontRenderer.width(label, fontSize).toFloat()
            val th = FontRenderer.height(label, fontSize).toFloat()
            val tx = x + (w - tw) / 2f
            val ty = y + (h - th) / 2f
            FontRenderer.draw(label, tx + 2f, ty + 2f, fontSize, 0f, 0f, 0f, 0.75f)
            FontRenderer.draw(label, tx, ty, fontSize, 1f, 1f, 1f, 1f)
            return
        }

        // 程序化兜底
        drawRect(x, y, w, h, 0f, 0f, 0f, 1f)
        val r: Float
        val g: Float
        val b: Float
        if (hovered) { r = 0.42f; g = 0.54f; b = 0.85f }
        else         { r = 0.60f; g = 0.60f; b = 0.60f }
        drawRect(x + 1, y + 1, w - 2, h - 2, r, g, b, 1f)

        drawRect(x + 1, y + 1, w - 2, 1f, 1f, 1f, 1f, 0.45f)
        drawRect(x + 1, y + 1, 1f, h - 2, 1f, 1f, 1f, 0.45f)
        drawRect(x + 1, y + h - 2, w - 2, 1f, 0f, 0f, 0f, 0.45f)
        drawRect(x + w - 2, y + 1, 1f, h - 2, 0f, 0f, 0f, 0.45f)

        val tw = FontRenderer.width(label, fontSize).toFloat()
        val th = FontRenderer.height(label, fontSize).toFloat()
        val tx = x + (w - tw) / 2f
        val ty = y + (h - th) / 2f
        FontRenderer.draw(label, tx + 2f, ty + 2f, fontSize, 0f, 0f, 0f, 0.75f)
        FontRenderer.draw(label, tx, ty, fontSize, 1f, 1f, 1f, 1f)
    }

    private fun drawLogo(cx: Float, topY: Float, targetH: Float, texLogo: Int, texIcon: Int) {
        if (texLogo > 0) {
            val lw = texWidth(texLogo).toFloat()
            val lh = texHeight(texLogo).toFloat()
            if (lw > 0f && lh > 0f) {
                val scale = targetH / lh
                val w = lw * scale
                glColor4f(1f, 1f, 1f, 1f)
                glBindTexture(GL_TEXTURE_2D, texLogo)
                glBegin(GL_QUADS)
                glTexCoord2f(0f, 0f); glVertex2f(cx - w / 2f, topY)
                glTexCoord2f(0f, 1f); glVertex2f(cx - w / 2f, topY + targetH)
                glTexCoord2f(1f, 1f); glVertex2f(cx + w / 2f, topY + targetH)
                glTexCoord2f(1f, 0f); glVertex2f(cx + w / 2f, topY)
                glEnd()
                return
            }
        }
        if (texIcon > 0) {
            val iw = texWidth(texIcon).toFloat()
            val ih = texHeight(texIcon).toFloat()
            if (iw > 0f && ih > 0f) {
                val scale = targetH / ih
                val w = iw * scale
                glColor4f(1f, 1f, 1f, 1f)
                glBindTexture(GL_TEXTURE_2D, texIcon)
                glBegin(GL_QUADS)
                glTexCoord2f(0f, 0f); glVertex2f(cx - w / 2f, topY)
                glTexCoord2f(0f, 1f); glVertex2f(cx - w / 2f, topY + targetH)
                glTexCoord2f(1f, 1f); glVertex2f(cx + w / 2f, topY + targetH)
                glTexCoord2f(1f, 0f); glVertex2f(cx + w / 2f, topY)
                glEnd()
                return
            }
        }
        val t = FontRenderer.getText("MinecraftEdi", 96)
        FontRenderer.draw("MinecraftEdi", cx - t.w / 2f + 4f, topY + 4f, 96, 0f, 0f, 0f, 0.6f)
        FontRenderer.draw("MinecraftEdi", cx - t.w / 2f, topY, 96, 1f, 1f, 1f, 1f)
    }

    private fun texWidth(tex: Int): Int = texSize(tex).first
    private fun texHeight(tex: Int): Int = texSize(tex).second

    private fun texSize(tex: Int): Pair<Int, Int> {
        texSizeCache[tex]?.let { return it }
        glBindTexture(GL_TEXTURE_2D, tex)
        val w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH)
        val h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT)
        val p = Pair(w, h)
        texSizeCache[tex] = p
        return p
    }
}