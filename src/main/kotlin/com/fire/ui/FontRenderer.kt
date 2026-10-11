package com.fire.ui

import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_QUADS
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.glBegin
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glColor4f
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glEnd
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glTexCoord2f
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL11.glVertex2f
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.system.MemoryUtil
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.HashMap

data class TextTex(val tex: Int, val w: Int, val h: Int)

object FontRenderer {

    private val cache = HashMap<String, TextTex>()

    @JvmStatic
    fun getText(text: String, size: Int): TextTex {
        val key = "$size:$text"
        cache[key]?.let { return it }
        val t = buildTex(text, size)
        cache[key] = t
        return t
    }

    private fun buildTex(text: String, size: Int): TextTex {
        val font = Font("SansSerif", Font.BOLD, size)

        val tmp = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val g0 = tmp.createGraphics()
        g0.font = font
        val fm = g0.fontMetrics
        val textW = fm.stringWidth(text).coerceAtLeast(1)
        val textH = fm.height.coerceAtLeast(1)
        g0.dispose()

        val img = BufferedImage(textW + 4, textH + 4, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
        g.font = font
        g.color = Color(255, 255, 255, 255)
        g.drawString(text, 2, fm.ascent + 2)
        g.dispose()

        val pixels = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, pixels, 0, img.width)

        val buf = MemoryUtil.memAlloc(pixels.size * 4)
        for (p in pixels) {
            buf.put(((p shr 16) and 0xFF).toByte())
            buf.put(((p shr 8) and 0xFF).toByte())
            buf.put((p and 0xFF).toByte())
            buf.put(((p shr 24) and 0xFF).toByte())
        }
        buf.flip()

        val tex = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, tex)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, img.width, img.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        MemoryUtil.memFree(buf)

        return TextTex(tex, img.width, img.height)
    }

    @JvmStatic
    fun draw(text: String, x: Float, y: Float, size: Int, r: Float, g: Float, b: Float, a: Float) {
        val t = getText(text, size)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        glColor4f(r, g, b, a)
        glBindTexture(GL_TEXTURE_2D, t.tex)
        glBegin(GL_QUADS)
        glTexCoord2f(0f, 0f); glVertex2f(x, y)
        glTexCoord2f(0f, 1f); glVertex2f(x, y + t.h)
        glTexCoord2f(1f, 1f); glVertex2f(x + t.w, y + t.h)
        glTexCoord2f(1f, 0f); glVertex2f(x + t.w, y)
        glEnd()
        glColor4f(1f, 1f, 1f, 1f)

        glDisable(GL_BLEND)
    }

    @JvmStatic
    fun drawCentered(text: String, cx: Float, y: Float, size: Int, r: Float, g: Float, b: Float, a: Float) {
        val t = getText(text, size)
        draw(text, cx - t.w / 2f, y, size, r, g, b, a)
    }

    @JvmStatic
    fun width(text: String, size: Int): Int = getText(text, size).w

    @JvmStatic
    fun height(text: String, size: Int): Int = getText(text, size).h
}