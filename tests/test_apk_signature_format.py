"""Check KP's APK container restrictions without an Android device or private keys."""
import io
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest
import zipfile


class ApkSignatureFormatTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        java_home = Path(os.environ.get("JAVA_HOME", ""))
        cls.javac = shutil.which("javac") or str(java_home / "bin/javac.exe")
        cls.java = shutil.which("java") or str(java_home / "bin/java.exe")
        if not Path(cls.javac).is_file():
            raise unittest.SkipTest("JDK required for APK signature format regressions")
        cls.temp = tempfile.TemporaryDirectory(prefix="apatch-apk-signatures-")
        cls.directory = Path(cls.temp.name)
        runner = cls.directory / "CheckFormat.java"
        runner.write_text(
            "import java.io.File; import me.bmax.apatch.util.ApkSignatureFormat;"
            "public class CheckFormat { public static void main(String[] args) {"
            "System.out.println(ApkSignatureFormat.isV2Only(new File(args[0]))); }}",
            encoding="utf-8",
        )
        source = Path(__file__).resolve().parents[1] / "app/src/main/java/me/bmax/apatch/util/ApkSignatureFormat.java"
        subprocess.run([cls.javac, "-d", str(cls.directory), str(source), str(runner)], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def apk(self, ids=(0x7109871A,), v1=False, comment=b"", corrupt_size=False):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as apk:
            apk.writestr("AndroidManifest.xml", b"fixture")
            if v1:
                apk.writestr("META-INF/CERT.RSA", b"fixture")
            apk.comment = comment
        data = stream.getvalue()
        eocd = len(data) - 22 - len(comment)
        cd = struct.unpack_from("<I", data, eocd + 16)[0]
        pairs = b"".join(struct.pack("<QI", 8, ident) + b"test" for ident in ids)
        size = len(pairs) + 24
        block = struct.pack("<Q", size) + pairs + struct.pack("<Q", size) + b"APK Sig Block 42"
        result = bytearray(data[:cd] + block + data[cd:])
        struct.pack_into("<I", result, eocd + len(block) + 16, cd + len(block))
        if corrupt_size:
            struct.pack_into("<Q", result, cd + len(block) - 24, 0xFFFFFFFFFFFFFFFF)
        return result

    def check(self, data, expected):
        path = self.directory / "fixture.apk"
        path.write_bytes(data)
        result = subprocess.run([self.java, "-cp", str(self.directory), "CheckFormat", str(path)],
                                check=True, capture_output=True, text=True, timeout=10)
        self.assertEqual(result.stdout.strip(), str(expected).lower())

    def test_v2_with_padding_block_is_supported(self):
        self.check(self.apk(ids=(0x7109871A, 0x42726577)), True)

    def test_official_release_v2_and_v3_shape_requires_superkey(self):
        self.check(self.apk(ids=(0x7109871A, 0xF05368C0)), False)

    def test_v31_requires_superkey(self):
        self.check(self.apk(ids=(0x7109871A, 0x1B93AD61)), False)

    def test_jar_signature_requires_superkey(self):
        self.check(self.apk(v1=True), False)

    def test_duplicate_or_missing_v2_is_rejected(self):
        self.check(self.apk(ids=(0x7109871A, 0x7109871A)), False)
        self.check(self.apk(ids=(0x42726577,)), False)

    def test_bad_lengths_are_rejected(self):
        self.check(self.apk(corrupt_size=True), False)
        self.check(self.apk()[:-15], False)
        self.check(b"", False)

    def test_zip_comment_with_embedded_eocd_is_handled(self):
        self.check(self.apk(comment=b"comment PK\x05\x06" + b"\0" * 32), True)


if __name__ == "__main__":
    unittest.main()
