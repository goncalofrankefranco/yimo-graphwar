//  Copyright (C) 2011 Lucas Catabriga Rocha <catabriga90@gmail.com>
//
//  This file is part of Graphwar and contains YIMO modifications.
//  Graphwar is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.

package Graphwar;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/** Small dependency-free cues for the animated campaign shot. */
final class SoundEffects {
    private static final int SAMPLE_RATE = 8000;

    private SoundEffects() {
    }

    static int durationMillis(String name) {
        if ("shot".equals(name)) {
            return 140;
        }
        if ("impact".equals(name)) {
            return 260;
        }
        return 0;
    }

    static void playShot() {
        play("shot", new double[] { 660.0, 990.0 });
    }

    static void playImpact() {
        play("impact", new double[] { 190.0, 120.0 });
    }

    private static void play(final String name, final double[] frequencies) {
        final int duration = durationMillis(name);
        if (duration == 0) {
            return;
        }
        Thread soundThread = new Thread(new Runnable() {
            @Override
            public void run() {
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine line = null;
                try {
                    line = (SourceDataLine) AudioSystem.getLine(info);
                    line.open(format);
                    line.start();
                    byte[] samples = new byte[SAMPLE_RATE * duration / 1000];
                    for (int i = 0; i < samples.length; i++) {
                        double time = (double) i / SAMPLE_RATE;
                        double envelope = 1.0 - (double) i / samples.length;
                        double value = 0.0;
                        for (double frequency : frequencies) {
                            value += Math.sin(2.0 * Math.PI * frequency * time);
                        }
                        samples[i] = (byte) (value * 28.0 * envelope / frequencies.length);
                    }
                    line.write(samples, 0, samples.length);
                    line.drain();
                } catch (Exception ignored) {
                    // ponytail: sound is an optional device capability; visuals must remain usable without it.
                } finally {
                    if (line != null) {
                        line.stop();
                        line.close();
                    }
                }
            }
        }, "YIMO-" + name + "-sound");
        soundThread.setDaemon(true);
        soundThread.start();
    }
}
