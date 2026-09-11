"""Pixel 11 AVB footer regressions; no device or firmware download required.

Run: python3 -B -m unittest discover -s tests -v
Set APATCH_TEST_SHELL to a bash/busybox ash-compatible shell if needed.
"""
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest

ASSETS = Path(__file__).resolve().parents[1] / "app/src/main/assets"
SHELL = os.environ.get("APATCH_TEST_SHELL") or shutil.which("bash")


@unittest.skipUnless(SHELL, "bash or APATCH_TEST_SHELL is required")
class AvbFooterTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="apatch-avb-test-")
        self.addCleanup(self.temp.cleanup)
        self.work = Path(self.temp.name)
        for name in ("boot_patch.sh", "boot_unpatch.sh", "util_functions.sh"):
            self.write(name, (ASSETS / name).read_text())
        self.write("boot.img", "stock boot")
        self.write("kpimg", "test payload")
        self.write("kptools", """#!/bin/sh
case "$1" in
  unpack) echo kernel > kernel ;;
  repack) cp repacked.img new-boot.img ;;
  -p|-u) echo kernel > kernel ;;
  -i)
    case "$3" in
      -f) echo CONFIG_KALLSYMS=y; echo CONFIG_KALLSYMS_ALL=y ;;
      -l) echo "patched=${PATCHED:-false}" ;;
    esac ;;
esac
""")
        self.write("getprop", "#!/bin/sh\necho arm64-v8a\n")
        self.write("driver.sh", """#!/bin/sh
export PATH="$PWD:/usr/bin:/bin:$PATH"
mode="$1"
shift
case "$mode" in
  patch) exec sh ./boot_patch.sh "$@" ;;
  unpatch) exec sh ./boot_unpatch.sh "$@" ;;
  util) . ./util_functions.sh; eval "$1" ;;
esac
""")

    def write(self, name, content):
        path = self.work / name
        path.write_text(content, encoding="utf-8", newline="\n")
        path.chmod(0o755)

    def run_shell(self, *args, **overrides):
        env = os.environ.copy()
        env.update(overrides)
        return subprocess.run([SHELL, "driver.sh", *args], cwd=self.work, env=env,
                              text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              timeout=15)

    def avb_fixture(self, old_kernel=5000, new_kernel=10000, minor=4, version=4):
        page = 4096 if version >= 3 else 2048
        align = lambda size: (size + page - 1) // page * page
        old_end, new_end = page + align(old_kernel), page + align(new_kernel)
        delta = new_end - old_end
        metadata_offset = old_end + page
        old = bytearray(65536)
        old[:8] = b"ANDROID!"
        struct.pack_into("<I", old, 8, old_kernel)
        struct.pack_into("<I", old, 36, page if version < 3 else 0)
        struct.pack_into("<I", old, 40, version)
        # An earlier GKI certificate is deliberately different from the outer
        # AVB metadata. Searching for AVB0 can select this wrong certificate.
        old[old_end:old_end + 16] = b"AVB0" + struct.pack(">III", 1, 0, 0)
        metadata = b"AVB0" + struct.pack(">II", 1, minor) + bytes(range(244))
        old[metadata_offset:metadata_offset + len(metadata)] = metadata
        footer = struct.pack(">4sIIQQQ28s", b"AVBf", 1, 0, metadata_offset,
                             metadata_offset, len(metadata), bytes(28))
        old[-64:] = footer
        new = bytearray(65536)
        new[:page] = old[:page]
        struct.pack_into("<I", new, 8, new_kernel)
        used_end = metadata_offset + len(metadata)
        new[new_end:used_end + delta] = old[old_end:used_end]
        # Reproduce kptools' bug: a footer pointing at the GKI certificate.
        new[-64:] = struct.pack(">4sIIQQQ28s", b"AVBf", 1, 0, new_end,
                               new_end, len(metadata), bytes(28))
        (self.work / "original.img").write_bytes(old)
        (self.work / "repacked.img").write_bytes(new)
        expected = bytearray(new)
        expected[-64:] = footer
        struct.pack_into(">QQ", expected, len(expected) - 52,
                         metadata_offset + delta, metadata_offset + delta)
        return bytes(expected), metadata_offset + delta

    def test_avb_footer_follows_original_metadata_not_embedded_certificate(self):
        for minor in (0, 2, 4, 99):
            with self.subTest(libavb_minor=minor):
                expected, _ = self.avb_fixture(minor=minor)
                result = self.run_shell("util", "repair_boot_avb_footer original.img repacked.img")
                self.assertEqual(result.returncode, 0, result.stdout)
                self.assertEqual((self.work / "repacked.img").read_bytes(), expected)

    def test_avb_footer_handles_shrinking_and_unchanged_kernel_alignment(self):
        for old_size, new_size in ((10000, 5000), (5000, 5100)):
            with self.subTest(old_size=old_size, new_size=new_size):
                expected, _ = self.avb_fixture(old_size, new_size)
                result = self.run_shell("util", "repair_boot_avb_footer original.img repacked.img")
                self.assertEqual(result.returncode, 0, result.stdout)
                self.assertEqual((self.work / "repacked.img").read_bytes(), expected)

    def test_avb_footer_supports_legacy_and_modern_boot_headers(self):
        for version in range(5):
            with self.subTest(header_version=version):
                expected, _ = self.avb_fixture(version=version)
                result = self.run_shell("util", "repair_boot_avb_footer original.img repacked.img")
                self.assertEqual(result.returncode, 0, result.stdout)
                self.assertEqual((self.work / "repacked.img").read_bytes(), expected)

    def test_avb_metadata_corruption_is_rejected_without_writing_footer(self):
        _, offset = self.avb_fixture()
        data = bytearray((self.work / "repacked.img").read_bytes())
        data[offset + 30] ^= 255
        (self.work / "repacked.img").write_bytes(data)
        result = self.run_shell("util", "repair_boot_avb_footer original.img repacked.img")
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertEqual((self.work / "repacked.img").read_bytes(), data)

    def test_invalid_avb_offsets_and_lengths_are_rejected(self):
        for offset, value in ((20, 65536), (28, 65536), (28, 1 << 63)):
            with self.subTest(field=offset, value=value):
                self.avb_fixture()
                data = bytearray((self.work / "original.img").read_bytes())
                struct.pack_into(">Q", data, len(data) - 64 + offset, value)
                (self.work / "original.img").write_bytes(data)
                before = (self.work / "repacked.img").read_bytes()
                result = self.run_shell("util", "repair_boot_avb_footer original.img repacked.img")
                self.assertNotEqual(result.returncode, 0, result.stdout)
                self.assertEqual((self.work / "repacked.img").read_bytes(), before)

    def test_image_without_avb_footer_is_unchanged(self):
        self.write("repacked.img", "no AVB")
        result = self.run_shell("util", "repair_boot_avb_footer boot.img repacked.img")
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual((self.work / "repacked.img").read_text(), "no AVB")

    def test_patch_repairs_footer_before_reporting_success(self):
        expected, _ = self.avb_fixture()
        shutil.copyfile(self.work / "original.img", self.work / "boot.img")
        result = self.run_shell("patch", "su", "boot.img")
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual((self.work / "new-boot.img").read_bytes(), expected)

    def test_missing_metadata_aborts_patch_and_unpatch(self):
        for mode in ("patch", "unpatch"):
            with self.subTest(mode=mode):
                for name in ("kernel", "kernel.ori", "new-boot.img"):
                    (self.work / name).unlink(missing_ok=True)
                self.avb_fixture()
                original = (self.work / "original.img").read_bytes()
                (self.work / "boot.img").write_bytes(original)
                self.write("repacked.img", "lost AVB metadata")
                args = ("su", "boot.img") if mode == "patch" else ("boot.img",)
                result = self.run_shell(mode, *args, PATCHED="true" if mode == "unpatch" else "false")
                self.assertNotEqual(result.returncode, 0, result.stdout)
                self.assertFalse((self.work / "new-boot.img").exists())
                self.assertEqual((self.work / "boot.img").read_bytes(), original)
                self.assertNotIn("Successfully", result.stdout)
                self.assertNotIn("Flash successful", result.stdout)


if __name__ == "__main__":
    unittest.main()
