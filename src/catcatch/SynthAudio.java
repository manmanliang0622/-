package catcatch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

final class SynthAudio {
    private static final float SAMPLE_RATE = 22_050f;
    private volatile boolean enabled = true;
    private volatile float volume = 0.7f;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "catcatch-audio");
        t.setDaemon(true);
        return t;
    });

    void setEnabled(boolean enabled) { this.enabled = enabled; }
    void setVolume(float v) { this.volume = Math.max(0f, Math.min(1f, v)); }
    boolean isEnabled() { return enabled; }
    float getVolume() { return volume; }

    void playMeow()    { if (enabled) play(this::buildMeow); }
    void playBark()    { if (enabled) play(this::buildBark); }
    void playStart()   { if (enabled) play(() -> sequence(note(660,0.10,0.24),silence(0.03),note(880,0.12,0.26),silence(0.03),note(1050,0.18,0.28))); }
    void playSuccess() { if (enabled) play(() -> sequence(note(720,0.06,0.20),note(920,0.08,0.26))); }
    void playWrong()   { if (enabled) play(() -> sequence(note(300,0.09,0.18),note(220,0.12,0.18))); }
    void playDog()     { if (enabled) play(() -> sequence(note(200,0.07,0.30),note(160,0.10,0.25))); }
    void playFinish()  { if (enabled) play(() -> sequence(note(523,0.09,0.22),silence(0.02),note(659,0.10,0.24),silence(0.02),note(784,0.16,0.27))); }
    void shutdown()    { executor.shutdownNow(); }

    private void play(SampleProducer producer) {
        executor.submit(() -> writeSamples(producer.produce()));
    }

    private byte[] buildMeow() {
        int n = (int)(SAMPLE_RATE * 0.42);
        byte[] data = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / SAMPLE_RATE;
            double glide = 1.0 - (t / 0.42);
            double freq = 860 - 360 * glide + 40 * Math.sin(2 * Math.PI * 3 * t);
            double env = Math.sin(Math.PI * Math.min(1.0, t / 0.09)) * Math.exp(-2.2 * t);
            double s = (Math.sin(2*Math.PI*freq*t) + 0.45*Math.sin(2*Math.PI*freq*2.02*t)) * env;
            short pcm = (short)Math.max(Math.min(s * 12000 * volume, Short.MAX_VALUE), Short.MIN_VALUE);
            data[i*2] = (byte)(pcm & 0xff); data[i*2+1] = (byte)((pcm>>8) & 0xff);
        }
        return data;
    }

    private byte[] buildBark() {
        int n = (int)(SAMPLE_RATE * 0.24);
        byte[] data = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / SAMPLE_RATE;
            double env = Math.exp(-12 * t);
            double s = (Math.random()*2-1)*env*0.7 + Math.sin(2*Math.PI*180*t)*0.45*env;
            short pcm = (short)Math.max(Math.min(s * 15000 * volume, Short.MAX_VALUE), Short.MIN_VALUE);
            data[i*2] = (byte)(pcm & 0xff); data[i*2+1] = (byte)((pcm>>8) & 0xff);
        }
        return data;
    }

    private byte[] note(double freq, double sec, double amp) {
        int n = (int)(SAMPLE_RATE * sec);
        byte[] data = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / SAMPLE_RATE;
            double env = Math.sin(Math.PI * Math.min(1.0, t/sec)) * Math.exp(-4*t);
            double s = Math.sin(2*Math.PI*freq*t) * env * amp;
            short pcm = (short)Math.max(Math.min(s * 32000 * volume, Short.MAX_VALUE), Short.MIN_VALUE);
            data[i*2] = (byte)(pcm & 0xff); data[i*2+1] = (byte)((pcm>>8) & 0xff);
        }
        return data;
    }

    private byte[] silence(double sec) { return new byte[(int)(SAMPLE_RATE*sec)*2]; }

    private byte[] sequence(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] data = new byte[total];
        int off = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, data, off, p.length); off += p.length; }
        return data;
    }

    private void writeSamples(byte[] data) {
        AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
            line.open(fmt, data.length);
            line.start();
            line.write(data, 0, data.length);
            line.drain();
        } catch (LineUnavailableException ignored) {}
    }

    @FunctionalInterface
    private interface SampleProducer { byte[] produce(); }
}
