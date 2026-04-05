"""
Generates a simple 16x16 PNG icon for the system tray.
A blue bell icon on transparent background.
"""

import struct
import zlib
import os

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "icons")
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "tray-icon.png")

os.makedirs(OUTPUT_DIR, exist_ok=True)

SIZE = 16

def create_png(width, height):
    """Create a simple blue circular icon as PNG."""
    def make_chunk(chunk_type, data):
        chunk = chunk_type + data
        return struct.pack('>I', len(data)) + chunk + struct.pack('>I', zlib.crc32(chunk) & 0xFFFFFFFF)

    # PNG signature
    signature = b'\x89PNG\r\n\x1a\n'
    
    # IHDR chunk
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)  # 8-bit RGBA
    ihdr = make_chunk(b'IHDR', ihdr_data)
    
    # IDAT chunk (image data)
    raw_data = b''
    cx, cy = width // 2, height // 2
    radius = width // 2 - 1
    
    for y in range(height):
        raw_data += b'\x00'  # filter byte
        for x in range(width):
            dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if dist <= radius:
                # Blue circle with slight gradient
                intensity = int(200 - (dist / radius) * 40)
                r, g, b, a = 0, 120, 212, 255
                raw_data += struct.pack('BBBB', r, g, b, a)
            else:
                # Transparent
                raw_data += struct.pack('BBBB', 0, 0, 0, 0)
    
    compressed = zlib.compress(raw_data, 9)
    idat = make_chunk(b'IDAT', compressed)
    
    # IEND chunk
    iend = make_chunk(b'IEND', b'')
    
    return signature + ihdr + idat + iend

png_data = create_png(SIZE, SIZE)
with open(OUTPUT_FILE, 'wb') as f:
    f.write(png_data)

print(f"Generated tray icon: {OUTPUT_FILE}")
print(f"  Size: {SIZE}x{SIZE} pixels")
print(f"  Format: PNG with alpha channel")
