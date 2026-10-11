package com.fire.sound;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.system.MemoryUtil.memAllocShort;
import static org.lwjgl.system.MemoryUtil.memFree;

public class SoundManager {

    private static final Map<String, Integer> BUFFERS = new HashMap<>();
    private static final List<Integer> ACTIVE = Collections.synchronizedList(new ArrayList<>());
    private static long device = MemoryUtil.NULL;
    private static long context = MemoryUtil.NULL;
    private static float volume = 0.9f;
    private static boolean enabled = true;
    private static boolean ok = false;

    public static void loadAll() {
        if (!init()) return;
        ok = true;

        load("pop",         "/assets/minecraft/sounds/random/pop.ogg");
        load("break_grass", "/assets/minecraft/sounds/dig/grass.ogg");
        load("break_dirt",  "/assets/minecraft/sounds/dig/dirt.ogg");
        load("break_stone", "/assets/minecraft/sounds/dig/stone.ogg");
        load("break_wood",  "/assets/minecraft/sounds/dig/wood.ogg");
        load("step_grass",  "/assets/minecraft/sounds/step/grass.ogg");
        load("step_dirt",   "/assets/minecraft/sounds/step/dirt.ogg");
        load("step_stone",  "/assets/minecraft/sounds/step/stone.ogg");
        load("step_wood",   "/assets/minecraft/sounds/step/wood.ogg");
    }

    private static boolean init() {
        device = alcOpenDevice((ByteBuffer) null);
        if (device == MemoryUtil.NULL) return false;

        context = alcCreateContext(device, (IntBuffer) null);
        if (context == MemoryUtil.NULL) {
            alcCloseDevice(device);
            device = MemoryUtil.NULL;
            return false;
        }

        alcMakeContextCurrent(context);
        ALCCapabilities alcCaps = ALC.createCapabilities(device);
        AL.createCapabilities(alcCaps);
        return true;
    }

    private static void load(String name, String path) {
        ByteBuffer fileBuf = null;
        IntBuffer error = null;
        STBVorbisInfo info = null;
        ShortBuffer pcm = null;
        long decoder = MemoryUtil.NULL;
        try {
            byte[] data;
            try (InputStream in = SoundManager.class.getResourceAsStream(path)) {
                if (in == null) return;
                data = in.readAllBytes();
            }
            if (data.length == 0) return;

            fileBuf = memAlloc(data.length);
            fileBuf.put(data).flip();

            error = memAllocInt(1);
            decoder = STBVorbis.stb_vorbis_open_memory(fileBuf, error, null);
            if (decoder == MemoryUtil.NULL) return;

            info = STBVorbisInfo.malloc();
            STBVorbis.stb_vorbis_get_info(decoder, info);
            int channels = info.channels();
            int sampleRate = info.sample_rate();
            if (channels <= 0 || sampleRate <= 0) return;

            int estimate = STBVorbis.stb_vorbis_stream_length_in_samples(decoder) * channels;
            if (estimate <= 0) estimate = sampleRate * channels;
            int capacity = estimate + sampleRate * channels * 2;

            pcm = memAllocShort(capacity);
            int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
            int count = frames * channels;
            if (count <= 0) return;

            pcm.position(0);
            pcm.limit(count);

            int format = channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
            int buf = alGenBuffers();
            alBufferData(buf, format, pcm, sampleRate);
            if (alGetError() != AL_NO_ERROR) {
                alDeleteBuffers(buf);
                return;
            }
            BUFFERS.put(name, buf);
        } catch (Throwable ignored) {
        } finally {
            if (decoder != MemoryUtil.NULL) STBVorbis.stb_vorbis_close(decoder);
            if (info != null) info.free();
            if (pcm != null) memFree(pcm);
            if (error != null) memFree(error);
            if (fileBuf != null) memFree(fileBuf);
        }
    }

    public static void play(String name) {
        if (!enabled || !ok) return;
        Integer buffer = BUFFERS.get(name);
        if (buffer == null) return;

        synchronized (ACTIVE) {
            for (int i = ACTIVE.size() - 1; i >= 0; i--) {
                int src = ACTIVE.get(i);
                if (alGetSourcei(src, AL_SOURCE_STATE) != AL_PLAYING) {
                    alDeleteSources(src);
                    ACTIVE.remove(i);
                }
            }
        }

        float gain = volume;
        if (name.startsWith("step_")) {
            gain = Math.min(1.0f, volume * 1.5f);
        }

        int source = alGenSources();
        alSourcei(source, AL_BUFFER, buffer);
        alSourcef(source, AL_GAIN, gain);
        alSourcePlay(source);
        ACTIVE.add(source);
    }

    public static void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
    }

    public static void setEnabled(boolean b) {
        enabled = b;
    }

    public static void cleanup() {
        if (!ok) return;
        synchronized (ACTIVE) {
            for (int src : ACTIVE) alDeleteSources(src);
            ACTIVE.clear();
        }
        for (int buf : BUFFERS.values()) alDeleteBuffers(buf);
        BUFFERS.clear();

        if (context != MemoryUtil.NULL) {
            alcMakeContextCurrent(MemoryUtil.NULL);
            alcDestroyContext(context);
            context = MemoryUtil.NULL;
        }
        if (device != MemoryUtil.NULL) {
            alcCloseDevice(device);
            device = MemoryUtil.NULL;
        }
        ok = false;
    }
}