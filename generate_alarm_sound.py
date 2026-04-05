"""
Generates a simple alarm WAV sound file.
Run this script to create the default alarm.wav bundled with the application.
Requires Python 3.x with no additional dependencies (uses only stdlib wave module).
"""

import wave
import struct
import math
import os

# Output path: src/main/resources/sounds/alarm.wav
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "sounds")
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "alarm.wav")

# WAV parameters
SAMPLE_RATE = 44100  # Hz
CHANNELS = 1         # Mono
SAMPLE_WIDTH = 2     # 16-bit
DURATION = 1.0       # seconds

# Create output directory
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Generate a two-tone alternating alarm pattern (similar to a classic alarm bell)
with wave.open(OUTPUT_FILE, 'w') as wav_file:
    wav_file.setnchannels(CHANNELS)
    wav_file.setsampwidth(SAMPLE_WIDTH)
    wav_file.setframerate(SAMPLE_RATE)

    total_samples = int(SAMPLE_RATE * DURATION)
    
    for i in range(total_samples):
        t = i / SAMPLE_RATE
        
        # Alternate between two frequencies every 0.125 seconds (creating a pulsing alarm)
        cycle_time = t % 0.25
        if cycle_time < 0.125:
            frequency = 880.0   # A5
        else:
            frequency = 660.0   # E5
        
        # Generate sine wave
        sample = math.sin(2 * math.pi * frequency * t)
        
        # Add amplitude envelope for each pulse (attack-decay)
        pulse_position = cycle_time if cycle_time < 0.125 else cycle_time - 0.125
        envelope = math.exp(-pulse_position * 15) * 0.8 + 0.2
        
        # Apply envelope
        sample *= envelope
        
        # Convert to 16-bit integer
        sample_int = int(sample * 32767)
        
        # Pack as signed 16-bit little-endian
        wav_file.writeframes(struct.pack('<h', sample_int))

print(f"Generated alarm sound: {OUTPUT_FILE}")
print(f"  Duration: {DURATION}s")
print(f"  Sample rate: {SAMPLE_RATE} Hz")
print(f"  Channels: {CHANNELS}")
print(f"  Sample width: {SAMPLE_WIDTH * 8}-bit")
