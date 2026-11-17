package com.example.event.util;

import java.nio.charset.StandardCharsets;

/**
 * Triển khai thuật toán MurmurHash3 32-bit (Austin Appleby) chuẩn hóa cho Sparse Vector Qdrant/FastEmbed.
 * Trả về số nguyên dương 31-bit [0, Integer.MAX_VALUE] tương thích với FastEmbed mmh3.hash(token).
 */
public final class MurmurHash3 {

    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;
    private static final int DEFAULT_SEED = 0;

    private MurmurHash3() {
        // Utility class
    }

    /**
     * Băm chuỗi UTF-8 thành số nguyên dương 31-bit [0, Integer.MAX_VALUE].
     * Tương thích với `abs(mmh3.hash(token))` của Python FastEmbed Qdrant/bm25.
     *
     * @param text Chuỗi token cần băm
     * @return Số nguyên không âm đại diện cho sparse vector index
     */
    public static int hashToken(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int rawHash = hash32(data, DEFAULT_SEED);
        // abs(Integer.MIN_VALUE) sẽ bị tràn số và trả về số âm, do đó xử lý an toàn
        return rawHash == Integer.MIN_VALUE ? 0 : Math.abs(rawHash);
    }

    /**
     * MurmurHash3 32-bit x86.
     */
    public static int hash32(byte[] data, int seed) {
        int length = data.length;
        int nblocks = length / 4;
        int h1 = seed;

        // Body
        for (int i = 0; i < nblocks; i++) {
            int i4 = i * 4;
            int k1 = (data[i4] & 0xff)
                    | ((data[i4 + 1] & 0xff) << 8)
                    | ((data[i4 + 2] & 0xff) << 16)
                    | ((data[i4 + 3] & 0xff) << 24);

            k1 *= C1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= C2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        // Tail
        int k1 = 0;
        int tail = nblocks * 4;
        switch (length & 3) {
            case 3:
                k1 ^= (data[tail + 2] & 0xff) << 16;
            case 2:
                k1 ^= (data[tail + 1] & 0xff) << 8;
            case 1:
                k1 ^= (data[tail] & 0xff);
                k1 *= C1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= C2;
                h1 ^= k1;
            default:
                break;
        }

        // Finalization
        h1 ^= length;
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
    }
}

