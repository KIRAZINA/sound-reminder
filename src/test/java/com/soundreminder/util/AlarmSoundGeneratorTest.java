package com.soundreminder.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for AlarmSoundGenerator.
 */
class AlarmSoundGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("generateAlarmWav produces non-empty byte array")
    void testGenerateProducesBytes() {
        byte[] wavData = AlarmSoundGenerator.generateAlarmWav();

        assertNotNull(wavData);
        assertTrue(wavData.length > 0, "WAV data should not be empty");
    }

    @Test
    @DisplayName("Generated WAV starts with correct RIFF header")
    void testWavHeader() {
        byte[] wavData = AlarmSoundGenerator.generateAlarmWav();

        // WAV files start with "RIFF"
        assertEquals('R', (char) wavData[0]);
        assertEquals('I', (char) wavData[1]);
        assertEquals('F', (char) wavData[2]);
        assertEquals('F', (char) wavData[3]);
    }

    @Test
    @DisplayName("Generated WAV contains WAVE format marker")
    void testWaveFormatMarker() {
        byte[] wavData = AlarmSoundGenerator.generateAlarmWav();

        // "WAVE" should appear in the header (bytes 8-11)
        assertEquals('W', (char) wavData[8]);
        assertEquals('A', (char) wavData[9]);
        assertEquals('V', (char) wavData[10]);
        assertEquals('E', (char) wavData[11]);
    }

    @Test
    @DisplayName("Generated WAV contains data chunk marker")
    void testDataChunkMarker() {
        byte[] wavData = AlarmSoundGenerator.generateAlarmWav();

        // Find "data" chunk
        boolean foundData = false;
        for (int i = 0; i < wavData.length - 4; i++) {
            if (wavData[i] == 'd' && wavData[i + 1] == 'a' &&
                    wavData[i + 2] == 't' && wavData[i + 3] == 'a') {
                foundData = true;
                break;
            }
        }
        assertTrue(foundData, "WAV should contain 'data' chunk");
    }

    @Test
    @DisplayName("Generated WAV has reasonable file size (44100 samples * 2 bytes + 44 header)")
    void testWavSize() {
        byte[] wavData = AlarmSoundGenerator.generateAlarmWav();

        // 1 second at 44100 Hz, 16-bit mono = 44100 * 2 = 88200 bytes of audio data
        // Plus 44 bytes WAV header = 88244
        int expectedSize = 44 + (44100 * 1 * 2); // header + (sampleRate * channels * bytesPerSample)
        assertEquals(expectedSize, wavData.length,
                "WAV file size should match expected size for 1s 44.1kHz 16-bit mono");
    }

    @Test
    @DisplayName("generateAndSave creates a valid WAV file on disk")
    void testGenerateAndSave() throws IOException {
        Path outputPath = tempDir.resolve("test_alarm.wav");

        AlarmSoundGenerator.generateAndSave(outputPath);

        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 44, "Saved file should be larger than WAV header");

        // Verify the file starts with RIFF
        byte[] header = Files.readAllBytes(outputPath);
        assertEquals('R', (char) header[0]);
        assertEquals('I', (char) header[1]);
        assertEquals('F', (char) header[2]);
        assertEquals('F', (char) header[3]);
    }

    @Test
    @DisplayName("Generated audio data contains non-zero samples")
    void testNonZeroSamples() {
        byte[] wavData = AlarmSoundGenerator.generateAlarmWav();

        // Skip 44-byte header and check audio data
        boolean hasNonZero = false;
        for (int i = 44; i < wavData.length; i++) {
            if (wavData[i] != 0) {
                hasNonZero = true;
                break;
            }
        }

        assertTrue(hasNonZero, "Audio data should contain non-zero samples");
    }
}
