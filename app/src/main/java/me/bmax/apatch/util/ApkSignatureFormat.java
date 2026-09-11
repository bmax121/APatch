package me.bmax.apatch.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

/** Container compatibility with KernelPatch 0.13.8; Android verifies the signer. */
public final class ApkSignatureFormat {
    private ApkSignatureFormat() {}

    private static long uint32(RandomAccessFile file) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
    }

    private static long int64(RandomAccessFile file) throws IOException {
        return Long.reverseBytes(file.readLong());
    }

    public static boolean isV2Only(File apk) {
        try (RandomAccessFile file = new RandomAccessFile(apk, "r");
             ZipFile zip = new ZipFile(apk)) {
            // KP rejects JAR signing, even when a v2 block is also present.
            boolean hasV1 = zip.stream().anyMatch(entry -> {
                String name = entry.getName();
                return name.startsWith("META-INF/") &&
                        (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"));
            });
            if (hasV1) return false;

            long length = file.length();
            long eocd = -1;
            for (long pos = length - 22; pos >= Math.max(0, length - 22 - 65535); pos--) {
                file.seek(pos);
                if (uint32(file) != 0x06054b50L) continue;
                file.seek(pos + 20);
                int commentLength = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
                if (pos + 22 + commentLength == length) {
                    eocd = pos;
                    break;
                }
            }
            if (eocd < 0) return false;
            file.seek(eocd + 12);
            long cdSize = uint32(file);
            long cd = uint32(file);
            if (cd < 32 || cd > eocd || cdSize != eocd - cd) return false;
            file.seek(cd - 24);
            long size = int64(file);
            byte[] magic = new byte[16];
            file.readFully(magic);
            if (!new String(magic, StandardCharsets.US_ASCII).equals("APK Sig Block 42") ||
                    size < 24 || size > cd - 8) return false;
            long start = cd - size - 8;
            file.seek(start);
            if (int64(file) != size) return false;

            int v2 = 0;
            long end = cd - 24;
            while (file.getFilePointer() < end) {
                long pos = file.getFilePointer();
                if (end - pos < 12) return false;
                long pairSize = int64(file);
                if (pairSize < 4 || pairSize > end - pos - 8) return false;
                long id = uint32(file);
                if (id == 0xf05368c0L || id == 0x1b93ad61L) return false;
                if (id == 0x7109871aL) v2++;
                file.seek(pos + 8 + pairSize);
            }
            return v2 == 1;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }
}
