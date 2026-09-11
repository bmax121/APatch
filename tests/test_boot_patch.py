"""Host regressions for the boot patch/flash scripts; no Android device needed.

Run: python3 -m unittest discover -s tests -v
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
class BootPatchTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="apatch-test-")
        self.addCleanup(self.temp.cleanup)
        self.work = Path(self.temp.name)
        for name in ("boot_patch.sh", "boot_unpatch.sh", "util_functions.sh"):
            self.write(name, (ASSETS / name).read_text())
        self.write("boot.img", "stock boot")
        self.write("kpimg", "test payload")
        self.write("kptools", """#!/bin/sh
printf '%s\\n' "$*" >> calls
case "$1" in
  unpack)
    [ "${FAIL_AT:-}" = unpack ] && exit 17
    [ "${EMPTY_AT:-}" = unpack ] || printf 'fresh kernel' > kernel
    ;;
  repack)
    # Simulate an interrupted writer leaving a partial image.
    if [ "${FAIL_AT:-}" = repack ]; then echo partial > new-boot.img; exit 23; fi
    [ "${EMPTY_AT:-}" = repack ] || printf 'patched boot' > new-boot.img
    ;;
  -p)
    printf '%s\\n' "$@" > patch-args
    [ "${FAIL_AT:-}" = patch ] && exit 19
    [ "${EMPTY_AT:-}" = patch ] || printf 'patched kernel' > kernel
    ;;
  -u)
    [ "${FAIL_AT:-}" = unpatch ] && exit 41
    printf 'unpatched kernel' > kernel
    ;;
  -i)
    case "$3" in
      -f)
        [ "${FAIL_AT:-}" = config ] && exit 29
        [ "${NO_KALLSYMS:-}" = 1 ] || echo CONFIG_KALLSYMS=y
        [ "${NO_KALLSYMS_ALL:-}" = 1 ] || echo CONFIG_KALLSYMS_ALL=y
        ;;
      -l)
        [ "${FAIL_AT:-}" = info ] && exit 31
        echo '[kernel]'
        echo "patched=${PATCHED:-false}"
        ;;
    esac
    ;;
esac
exit 0
""")
        self.write("getprop", "#!/bin/sh\necho arm64-v8a\n")
        self.write("driver.sh", """#!/bin/sh
# Git for Windows does not always add its Unix commands to PATH.
export PATH="$PWD:/usr/bin:/bin:$PATH"
if [ "$1" = patch ]; then
  shift
  exec sh ./boot_patch.sh "$@"
fi
if [ "$1" = unpatch ]; then
  shift
  exec sh ./boot_unpatch.sh "$@"
fi
. ./util_functions.sh
shift
eval "$1"
""")

    def write(self, name, content):
        path = self.work / name
        path.write_text(content, encoding="utf-8", newline="\n")
        path.chmod(0o755)

    def run_shell(self, *args, **overrides):
        env = os.environ.copy()
        env.update(overrides)
        return subprocess.run(
            [SHELL, "driver.sh", *args], cwd=self.work, env=env,
            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            timeout=15,
        )

    def patch(self, *extra, **env):
        return self.run_shell("patch", "TestKey123", "boot.img", *extra, **env)

    def assert_failed(self, result, code=None):
        if code is None:
            self.assertNotEqual(result.returncode, 0, result.stdout)
        else:
            self.assertEqual(result.returncode, code, result.stdout)
        self.assertNotIn("Successfully", result.stdout)
        self.assertFalse((self.work / "new-boot.img").exists())

    def test_success_keeps_stock_backup(self):
        result = self.patch()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("Successfully Patched!", result.stdout)
        self.assertEqual((self.work / "ori.img").read_text(), "stock boot")
        self.assertEqual((self.work / "kernel.ori").read_text(), "fresh kernel")
        self.assertEqual((self.work / "new-boot.img").read_text(), "patched boot")

    def test_each_failure_stops_and_discards_partial_output(self):
        for stage, code in (("unpack", 17), ("config", 29), ("info", 31),
                            ("patch", 19), ("repack", 23)):
            with self.subTest(stage=stage):
                self.write("new-boot.img", "stale boot")
                result = self.patch(FAIL_AT=stage)
                self.assert_failed(result, code)

    def test_empty_output_is_failure_even_with_zero_exit_status(self):
        for stage in ("unpack", "patch", "repack"):
            with self.subTest(stage=stage):
                self.write("kernel", "stale kernel")
                self.write("new-boot.img", "stale boot")
                self.assert_failed(self.patch(EMPTY_AT=stage))

    def test_required_kallsyms_missing_aborts(self):
        self.assert_failed(self.patch(NO_KALLSYMS="1"))
        self.assertNotIn("-p ", (self.work / "calls").read_text())

    def test_optional_kallsyms_all_warning_does_not_hide_repack_failure(self):
        self.assert_failed(self.patch(FAIL_AT="repack", NO_KALLSYMS_ALL="1"), 23)

    def test_optional_kallsyms_all_missing_still_allows_patch(self):
        result = self.patch(NO_KALLSYMS_ALL="1")
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("CONFIG_KALLSYMS_ALL is not set", result.stdout)

    def test_retry_unpacks_selected_image_again(self):
        self.write("kernel", "previously patched kernel")
        result = self.patch()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual((self.work / "kernel.ori").read_text(), "fresh kernel")

    def test_control_flag_is_not_passed_to_kptools(self):
        result = self.patch("false", "-M", "module with spaces.kpm", "-A", "two words")
        self.assertEqual(result.returncode, 0, result.stdout)
        args = (self.work / "patch-args").read_text().splitlines()
        self.assertNotIn("false", args)
        self.assertEqual(args[-4:], ["-M", "module with spaces.kpm", "-A", "two words"])

    def test_options_without_control_flag_are_preserved(self):
        result = self.patch("-M", "module.kpm")
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual((self.work / "patch-args").read_text().splitlines()[-2:],
                         ["-M", "module.kpm"])

    def test_key_remains_one_argument_and_is_not_traced(self):
        key = "Test Key123 ' $literal"
        result = self.run_shell("patch", key, "boot.img")
        self.assertEqual(result.returncode, 0, result.stdout)
        args = (self.work / "patch-args").read_text().splitlines()
        self.assertEqual(args[args.index("-S") + 1], key)
        self.assertNotIn(key, result.stdout)

    def test_rejects_flash_to_regular_file(self):
        self.assert_failed(self.patch("true"))
        self.assertEqual((self.work / "boot.img").read_text(), "stock boot")

    def test_existing_patched_image_does_not_replace_stock_backup(self):
        self.write("ori.img", "original backup")
        result = self.patch(PATCHED="true")
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual((self.work / "ori.img").read_text(), "original backup")

    def test_unknown_patch_state_aborts(self):
        self.assert_failed(self.patch(PATCHED="unknown"))

    def test_copy_failure_is_not_reported_as_flash_success(self):
        (self.work / "destination").mkdir()
        result = self.run_shell("util", "flash_image boot.img destination")
        self.assertNotEqual(result.returncode, 0, result.stdout)

    def test_image_path_can_contain_single_quote(self):
        self.write("boot's image.img", "boot data")
        result = self.run_shell("util", 'flash_image "boot\'s image.img" copied.img')
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual((self.work / "copied.img").read_text(), "boot data")

    def test_failed_decompression_is_not_reported_as_success(self):
        self.write("broken.img.gz", "not gzip")
        result = self.run_shell("util", "flash_image broken.img.gz copied.img")
        self.assertNotEqual(result.returncode, 0, result.stdout)

    def test_flash_pipeline_propagates_writer_failure(self):
        result = self.run_shell("util", """
# /dev/null selects the character-device branch; both device writers are mocked.
flash_eraseall() { return 0; }
nandwrite() { cat >/dev/null; return 37; }
flash_image boot.img /dev/null
""")
        self.assertEqual(result.returncode, 37, result.stdout)

    def test_flash_pipeline_propagates_reader_failure(self):
        self.write("broken.img.gz", "not gzip")
        result = self.run_shell("util", """
flash_eraseall() { return 0; }
nandwrite() { cat >/dev/null; return 0; }
flash_image broken.img.gz /dev/null
""")
        self.assertNotEqual(result.returncode, 0, result.stdout)

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

    def test_patch_discards_output_if_repacker_loses_avb_metadata(self):
        self.avb_fixture()
        shutil.copyfile(self.work / "original.img", self.work / "boot.img")
        self.assert_failed(self.patch())

    def test_unpatch_and_repack_failures_leave_boot_untouched(self):
        for stage, code in (("unpatch", 41), ("repack", 23)):
            with self.subTest(stage=stage):
                for name in ("kernel", "kernel.ori", "new-boot.img"):
                    (self.work / name).unlink(missing_ok=True)
                result = self.run_shell("unpatch", "boot.img", FAIL_AT=stage, PATCHED="true")
                self.assertEqual(result.returncode, code, result.stdout)
                self.assertNotIn("Flash successful", result.stdout)
                self.assertEqual((self.work / "boot.img").read_text(), "stock boot")

    def test_unpatch_does_not_flash_if_avb_metadata_was_lost(self):
        self.avb_fixture()
        original = (self.work / "original.img").read_bytes()
        (self.work / "boot.img").write_bytes(original)
        result = self.run_shell("unpatch", "boot.img", PATCHED="true")
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertNotIn("Flash successful", result.stdout)
        self.assertEqual((self.work / "boot.img").read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
