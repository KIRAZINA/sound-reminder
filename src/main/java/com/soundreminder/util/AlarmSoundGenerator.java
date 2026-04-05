package com.soundreminder.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class that generates the default alarm WAV sound programmatically.
 * The sound is a two-tone alternating alarm pattern (880Hz / 660Hz).
 * Generated at runtime if no bundled sound is found.
 */
public final class AlarmSoundGenerator {

    private static final int SAMPLE_RATE = 44100;
    private static final short CHANNELS = 1; // Mono
    private static final short BITS_PER_SAMPLE = 16;
    private static final double DURATION = 1.0; // seconds

    /** High tone frequency (A5). */
    private static final double FREQ_HIGH = 880.0;

    /** Low tone frequency (E5). */
    private static final double FREQ_LOW = 660.0;

    /** Duration of each tone segment in seconds. */
    private static final double SEGMENT_DURATION = 0.125;

    /** Full cycle duration (high + low). */
    private static final double CYCLE_DURATION = SEGMENT_DURATION * 2;

    private AlarmSoundGenerator() {
        // Utility class - prevent instantiation
    }

    /**
     * Generates the alarm WAV sound as a byte array.
     *
     * @return WAV file data as bytes
     */
    public static byte[] generateAlarmWav() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int totalSamples = (int) (SAMPLE_RATE * DURATION);

            // Generate audio samples
            short[] samples = new short[totalSamples];
            for (int i = 0; i < totalSamples; i++) {
                double t = (double) i / SAMPLE_RATE;

                // Determine current frequency based on position in cycle
                double cycleTime = t % CYCLE_DURATION;
                double frequency = cycleTime < SEGMENT_DURATION ? FREQ_HIGH : FREQ_LOW;

                // Generate sine wave
                double sample = Math.sin(2 * Math.PI * frequency * t);

                // Apply amplitude envelope (attack-decay per pulse)
                double pulsePosition = cycleTime < SEGMENT_DURATION ? cycleTime : cycleTime - SEGMENT_DURATION;
                double envelope = Math.exp(-pulsePosition * 15) * 0.8 + 0.2;
                sample *= envelope;

                // Convert to 16-bit
                samples[i] = (short) (sample * Short.MAX_VALUE);
            }

            // Write WAV file
            writeWavFile(baos, samples, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE);

            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate alarm sound", e);
        }
    }

    /**
     * Generates the alarm sound and saves it to the specified path.
     * Useful for pre-generating the resource during build.
     *
     * @param outputPath the path to save the WAV file
     * @throws IOException if the file cannot be written
     */
    public static void generateAndSave(Path outputPath) throws IOException {
        byte[] wavData = generateAlarmWav();
        Files.write(outputPath, wavData);
        System.out.println("Alarm sound saved to: " + outputPath);
    }

    /**
     * Writes PCM audio samples to a WAV file format.
     *
     * @param out            the output stream
     * @param samples        the PCM audio samples
     * @param sampleRate     sample rate in Hz
     * @param channels       number of audio channels
     * @param bitsPerSample  bits per sample
     * @throws IOException if writing fails
     */
    private static void writeWavFile(ByteArrayOutputStream out, short[] samples,
                                      int sampleRate, short channels, short bitsPerSample) throws IOException {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataLength = samples.length * blockAlign;
        int totalLength = 36 + dataLength;

        // WAV header (44 bytes)
        writeString(out, "RIFF");                           // ChunkID
        writeInt(out, totalLength);                         // ChunkSize
        writeString(out, "WAVE");                           // Format
        writeString(out, "fmt ");                           // Subchunk1ID
        writeInt(out, 16);                                  // Subchunk1Size (PCM)
        writeShort(out, (short) 1);                        // AudioFormat (PCM = 1)
        writeShort(out, channels);                         // NumChannels
        writeInt(out, sampleRate);                         // SampleRate
        writeInt(out, byteRate);                           // ByteRate
        writeShort(out, (short) blockAlign);                       // BlockAlign
        writeShort(out, bitsPerSample);                    // BitsPerSample
        writeString(out, "data");                           // Subchunk2ID
        writeInt(out, dataLength);                         // Subchunk2Size

        // Write audio data
        for (short sample : samples) {
            writeShort(out, sample);
        }
    }

    /** Writes a 4-byte ASCII string. */
    private static void writeString(ByteArrayOutputStream out, String s) throws IOException {
        for (int i = 0; i < 4; i++) {
            out.write(s.charAt(i));
        }
    }

    /** Writes a 32-bit little-endian integer. */
    private static void writeInt(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    /** Writes a 16-bit little-endian short. */
    private static void writeShort(ByteArrayOutputStream out, short value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    /**
     * Main method for pre-generating the alarm sound file.
     * Usage: java AlarmSoundGenerator [output-path]
     */
    public static void main(String[] args) {
        try {
            Path outputPath;
            if (args.length > 0) {
                outputPath = Path.of(args[0]);
            } else {
                outputPath = Path.of("alarm.wav");
            }
            generateAndSave(outputPath);
        } catch (Exception e) {
            System.err.println("Failed to generate alarm sound: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
