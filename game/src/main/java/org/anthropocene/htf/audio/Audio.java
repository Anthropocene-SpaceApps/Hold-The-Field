package org.anthropocene.htf.audio;

import org.anthropocene.htf.core.Settings;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ShortBuffer;
import java.util.Random;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Procedural sound: every effect is synthesised at start-up, so there are no audio files to ship.
 * If no audio device exists the engine disables itself and every call becomes a no-op.
 */
public final class Audio {
    private static final int RATE = 22050;

    private long device, context;
    private boolean enabled;
    private final int[] sources = new int[10];
    private int next;
    private int bufClick, bufPlace, bufHarvest, bufError, bufWarning, bufThunder, bufFlood, bufAchieve, bufSplash;
    private int srcRain, srcWind, srcMusic;
    private Settings settings = new Settings();
    private double rainTarget, rainGain;

    public void init(Settings s) {
        this.settings = s;
        try {
            device = alcOpenDevice((java.nio.ByteBuffer) null);
            if (device == NULL) { System.err.println("Audio: no output device, sound disabled"); return; }
            ALCCapabilities caps = ALC.createCapabilities(device);
            context = alcCreateContext(device, (int[]) null);
            alcMakeContextCurrent(context);
            AL.createCapabilities(caps);
            for (int i = 0; i < sources.length; i++) sources[i] = alGenSources();
            synthesise();
            srcRain = loop(noiseLoop(3.0, 0.55, 6, 31), 0f);
            srcWind = loop(noiseLoop(4.0, 0.25, 40, 32), 0f);
            srcMusic = loop(padLoop(), 0f);
            enabled = true;
            applyVolumes();
            alSourcePlay(srcRain); alSourcePlay(srcWind); alSourcePlay(srcMusic);
        } catch (Throwable t) {
            System.err.println("Audio: disabled (" + t.getMessage() + ")");
            enabled = false;
        }
    }

    public boolean isEnabled() { return enabled; }

    // ------------------------------------------------------------------ playback

    private void play(int buffer, float gain, float pitch) {
        if (!enabled) return;
        int src = sources[next++ % sources.length];
        alSourceStop(src);
        alSourcei(src, AL_BUFFER, buffer);
        alSourcef(src, AL_GAIN, (float) (gain * settings.effectsVolume * settings.masterVolume));
        alSourcef(src, AL_PITCH, pitch);
        alSourcePlay(src);
    }

    public void click() { play(bufClick, 0.5f, 1f); }
    public void place() { play(bufPlace, 0.9f, 0.95f + (float) Math.random() * 0.1f); }
    public void harvest() { play(bufHarvest, 0.8f, 1f); }
    public void error() { play(bufError, 0.6f, 1f); }
    public void warning() { play(bufWarning, 0.8f, 1f); }
    public void thunder() { play(bufThunder, 1f, 0.85f + (float) Math.random() * 0.3f); }
    public void flood() { play(bufFlood, 1f, 1f); }
    public void achieve() { play(bufAchieve, 0.8f, 1f); }
    public void splash() { play(bufSplash, 0.7f, 1f); }

    /** 0..1 how hard it rains right now; drives the rain and wind loops. */
    public void setWeather(double intensity) { rainTarget = Math.max(0, Math.min(1, intensity)); }

    public void tick(double dt) {
        if (!enabled) return;
        rainGain += (rainTarget - rainGain) * Math.min(1, dt * 1.5);
        float amb = (float) (settings.ambientVolume * settings.masterVolume);
        alSourcef(srcRain, AL_GAIN, (float) (rainGain * 0.55) * amb);
        alSourcef(srcWind, AL_GAIN, (float) (0.05 + rainGain * 0.25) * amb);
    }

    public void applyVolumes() {
        if (!enabled) return;
        alSourcef(srcMusic, AL_GAIN, (float) (0.22 * settings.ambientVolume * settings.masterVolume));
    }

    public void shutdown() {
        if (!enabled) return;
        enabled = false;
        for (int s : sources) { alSourceStop(s); alDeleteSources(s); }
        alDeleteSources(srcRain); alDeleteSources(srcWind); alDeleteSources(srcMusic);
        alcMakeContextCurrent(NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }

    // ------------------------------------------------------------------ synthesis

    private int buffer(short[] pcm) {
        int b = alGenBuffers();
        ShortBuffer sb = org.lwjgl.BufferUtils.createShortBuffer(pcm.length);
        sb.put(pcm).flip();
        alBufferData(b, AL_FORMAT_MONO16, sb, RATE);
        return b;
    }

    private int loop(int buf, float gain) {
        int src = alGenSources();
        alSourcei(src, AL_BUFFER, buf);
        alSourcei(src, AL_LOOPING, AL_TRUE);
        alSourcef(src, AL_GAIN, gain);
        return src;
    }

    private static short s16(double v) { return (short) Math.max(-32767, Math.min(32767, Math.round(v * 32767))); }

    private void synthesise() {
        Random rnd = new Random(5);
        bufClick = buffer(tone(0.05, t -> Math.sin(2 * Math.PI * 920 * t) * Math.exp(-t * 70) * 0.7));
        bufError = buffer(tone(0.16, t -> (Math.sin(2 * Math.PI * 130 * t) + Math.sin(2 * Math.PI * 137 * t)) * 0.3 * Math.exp(-t * 14)));
        bufPlace = buffer(tone(0.22, t -> (Math.sin(2 * Math.PI * (120 - 240 * t) * t) * 0.8 + (rnd.nextDouble() - .5) * 0.5 * Math.exp(-t * 60)) * Math.exp(-t * 16)));
        bufHarvest = buffer(tone(0.42, t -> (rnd.nextDouble() - .5) * 0.9 * Math.exp(-t * 7) * (0.5 + 0.5 * Math.sin(t * 90)) + Math.sin(2 * Math.PI * 1320 * t) * Math.exp(-t * 11) * 0.25));
        bufWarning = buffer(tone(1.1, t -> {
            double beat = (t % 0.55) < 0.28 ? 1 : 0;
            double f = (t % 0.55) < 0.14 ? 660 : 880;
            return Math.sin(2 * Math.PI * f * t) * 0.45 * beat * Math.min(1, (0.28 - (t % 0.55)) * 40);
        }));
        double[] lp = {0};
        bufThunder = buffer(tone(2.2, t -> { lp[0] += ((rnd.nextDouble() - .5) * 2 - lp[0]) * 0.025; return lp[0] * 5.5 * Math.exp(-t * 1.5) * Math.min(1, t * 30); }));
        double[] lp2 = {0};
        bufFlood = buffer(tone(1.4, t -> { lp2[0] += ((rnd.nextDouble() - .5) * 2 - lp2[0]) * 0.05; return lp2[0] * 4.0 * Math.sin(Math.PI * Math.min(1, t / 1.4)); }));
        bufAchieve = buffer(tone(0.9, t -> {
            double f = t < 0.15 ? 523 : t < 0.3 ? 659 : t < 0.45 ? 784 : 1047;
            return (Math.sin(2 * Math.PI * f * t) * 0.4 + Math.sin(4 * Math.PI * f * t) * 0.12) * Math.exp(-(t % 0.15) * 6) * (t < 0.45 ? 1 : Math.exp(-(t - 0.45) * 4));
        }));
        double[] lp3 = {0};
        bufSplash = buffer(tone(0.5, t -> { lp3[0] += ((rnd.nextDouble() - .5) * 2 - lp3[0]) * 0.3; return lp3[0] * 0.9 * Math.exp(-t * 9); }));
    }

    private interface Wave { double at(double t); }

    private static short[] tone(double seconds, Wave w) {
        short[] pcm = new short[(int) (seconds * RATE)];
        for (int i = 0; i < pcm.length; i++) pcm[i] = s16(w.at(i / (double) RATE));
        return pcm;
    }

    /** Looping filtered noise (rain, wind) with a cross-faded seam. */
    private int noiseLoop(double seconds, double amp, int smooth, long seed) {
        Random rnd = new Random(seed);
        int n = (int) (seconds * RATE);
        double[] raw = new double[n];
        double lp = 0;
        for (int i = 0; i < n; i++) { lp += ((rnd.nextDouble() - .5) * 2 - lp) / smooth; raw[i] = smooth > 10 ? lp * 6 : (rnd.nextDouble() - .5) * 2 - lp * 0.6; }
        int fade = RATE / 4;
        for (int i = 0; i < fade; i++) {
            double k = i / (double) fade;
            raw[i] = raw[i] * k + raw[n - fade + i] * (1 - k);
        }
        short[] pcm = new short[n - fade];
        for (int i = 0; i < pcm.length; i++) pcm[i] = s16(raw[i] * amp);
        return buffer(pcm);
    }

    /** A slow Cmaj7 / Am7 pad that loops, for a calm Minecraft-ish backdrop. */
    private int padLoop() {
        double len = 24;
        double[][] chords = {{130.8, 196.0, 246.9, 329.6}, {110.0, 164.8, 261.6, 329.6}, {174.6, 220.0, 261.6, 349.2}, {146.8, 220.0, 293.7, 369.99}};
        int n = (int) (len * RATE);
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE, seg = t / 6.0;
            int c = ((int) seg) % 4;
            double local = (t % 6.0) / 6.0, env = Math.sin(Math.PI * local);
            double v = 0;
            for (double f : chords[c]) v += Math.sin(2 * Math.PI * f * t + Math.sin(t * 0.7) * 0.4) * 0.5 + Math.sin(2 * Math.PI * f * 2 * t) * 0.08;
            pcm[i] = s16(v * 0.16 * env * env);
        }
        return buffer(pcm);
    }
}
