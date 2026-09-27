// ============================================================================
//  woke.wtf — tools/woketool/png_writer.h
//
//  Minimal PNG writer (RGB8, no compression — stored deflate blocks).
//  Exists only for the --screenshot verification mode so the tool has zero
//  image dependencies. ~120 lines; correctness over size.
// ============================================================================

#pragma once

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <vector>

namespace woke::png {

inline uint32_t crc32_update(uint32_t crc, const uint8_t *data, size_t len) {
    static uint32_t table[256];
    static bool table_ready = false;
    if (!table_ready) {
        for (uint32_t n = 0; n < 256; ++n) {
            uint32_t c = n;
            for (int k = 0; k < 8; ++k)
                c = (c & 1) ? (0xEDB88320u ^ (c >> 1)) : (c >> 1);
            table[n] = c;
        }
        table_ready = true;
    }
    crc ^= 0xFFFFFFFFu;
    for (size_t i = 0; i < len; ++i)
        crc = table[(crc ^ data[i]) & 0xFF] ^ (crc >> 8);
    return crc ^ 0xFFFFFFFFu;
}

inline void put_u32be(std::vector<uint8_t> &v, uint32_t x) {
    v.push_back(static_cast<uint8_t>(x >> 24));
    v.push_back(static_cast<uint8_t>(x >> 16));
    v.push_back(static_cast<uint8_t>(x >> 8));
    v.push_back(static_cast<uint8_t>(x));
}

inline void chunk(std::vector<uint8_t> &out, const char *type,
                  const std::vector<uint8_t> &payload) {
    put_u32be(out, static_cast<uint32_t>(payload.size()));
    const size_t crc_start = out.size();
    for (int i = 0; i < 4; ++i) out.push_back(static_cast<uint8_t>(type[i]));
    out.insert(out.end(), payload.begin(), payload.end());
    put_u32be(out, crc32_update(0, out.data() + crc_start,
                                out.size() - crc_start));
}

/// Write an RGB8 image (row-major, top-down) as PNG. Returns true on success.
inline bool write_rgb(const char *path, int w, int h,
                      const uint8_t *rgb /* w*h*3 */) {
    if (w <= 0 || h <= 0 || rgb == nullptr || path == nullptr) return false;

    // -- raw scanlines with filter byte 0 (None) --
    const size_t stride = static_cast<size_t>(w) * 3 + 1;
    std::vector<uint8_t> raw;
    raw.reserve(stride * static_cast<size_t>(h));
    for (int y = 0; y < h; ++y) {
        raw.push_back(0); // filter: None
        raw.insert(raw.end(), rgb + static_cast<size_t>(y) * w * 3,
                   rgb + (static_cast<size_t>(y) + 1) * w * 3);
    }

    // -- zlib stream with stored (uncompressed) deflate blocks --
    std::vector<uint8_t> zdata;
    zdata.push_back(0x78);
    zdata.push_back(0x01);
    size_t off = 0;
    while (off < raw.size()) {
        const size_t n = (raw.size() - off > 65535) ? 65535
                                                    : raw.size() - off;
        const bool last = (off + n == raw.size());
        zdata.push_back(last ? 1 : 0);
        zdata.push_back(static_cast<uint8_t>(n & 0xFF));
        zdata.push_back(static_cast<uint8_t>(n >> 8));
        zdata.push_back(static_cast<uint8_t>(~n & 0xFF));
        zdata.push_back(static_cast<uint8_t>((~n >> 8) & 0xFF));
        zdata.insert(zdata.end(), raw.begin() + static_cast<long>(off),
                     raw.begin() + static_cast<long>(off + n));
        off += n;
    }
    uint32_t a = 1, b = 0; // adler32
    for (uint8_t byte : raw) {
        a = (a + byte) % 65521;
        b = (b + a) % 65521;
    }
    put_u32be(zdata, (b << 16) | a);

    // -- assemble the file --
    std::vector<uint8_t> out;
    const uint8_t sig[8] = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    out.insert(out.end(), sig, sig + 8);

    std::vector<uint8_t> ihdr;
    put_u32be(ihdr, static_cast<uint32_t>(w));
    put_u32be(ihdr, static_cast<uint32_t>(h));
    ihdr.push_back(8);  // bit depth
    ihdr.push_back(2);  // color type: RGB
    ihdr.push_back(0);  // compression
    ihdr.push_back(0);  // filter
    ihdr.push_back(0);  // interlace
    chunk(out, "IHDR", ihdr);
    chunk(out, "IDAT", zdata);
    chunk(out, "IEND", {});

    std::FILE *f = std::fopen(path, "wb");
    if (f == nullptr) return false;
    const bool ok = std::fwrite(out.data(), 1, out.size(), f) == out.size();
    std::fclose(f);
    return ok;
}

} // namespace woke::png
