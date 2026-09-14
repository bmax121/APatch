# Pixel 11 boot image compatibility

## Reproduced on cubs

The inspected device runs `CD1A.260905.001.B1` (Android 17) with
`6.12.69-android16-6-g5c5f2fea42dd-ab15835541-4k` and 4096-byte pages.
Its failed image contains KernelPatch 0.13.3. The saved stock image came from
`CD1A.260714.001.A9`, with the August security patch; the running system is
the September build. The two stock kernels are identical, but their signed
boot metadata is different. Always start with `boot.img` from the installed
build. APatch patches the kernel in `boot.img`; `init_boot.img` has no kernel.

## Confirmed repacker defect

Pixel 11 uses AVB metadata requiring libavb 1.4 and the ML-DSA-65 algorithm.
KernelPatch 0.13.8's `repack_bootimg_mem()` searches the boot trailer for
`AVB0` plus a hard-coded version prefix. It only recognizes minor versions
0, 1 and 2. It therefore misses the outer ML-DSA metadata and points the
output footer at an embedded GKI RSA certificate instead.

On the matching September image, the original outer metadata starts at
20,254,720. After patching, it moves to 20,267,008. The uncorrected repacker
instead points at 20,252,864; an independent AVB parser then reports RSA,
rollback index zero and GKI properties instead of the device build properties.

`repair_boot_avb_footer()` uses the original `AVBf` footer and the change in
padded kernel size to locate the moved metadata. It checks bounds and compares
the complete metadata bytes before updating the footer. This preserves the
algorithm, rollback index, fingerprint and security patch without recognizing
specific signature algorithms or searching for embedded certificates. A
missing or changed metadata block aborts installation. Images without an AVB
footer are left unchanged.

The repair applies to manager patching, recovery installation and repacking
during uninstall. It preserves existing signatures; it does not make a
modified kernel pass Google's original hash or enable use on a locked device.

## Kernel compatibility and packaging

Google's exact kernel revision maps kernel text read-only in the early mapper
and creates the linear mapping inside `paging_init()`. KernelPatch 0.13.3's
older early-paging path is another compatibility concern. This checkout
already selects 0.13.8, which includes upstream paging and map-area fixes.
The download tasks now track their release URL as an input, replace files only
after a complete download, and refresh kernel modules on version changes.
They cannot retain an older payload merely because its local timestamp is newer.

The patch script also propagates failures, discards incomplete output, always
unpacks the selected image, and excludes its flash-control argument from
kptools options. These changes prevent a failed patch or write from being
reported as a successful install.

## Validation

Run host regressions with `python3 -m unittest discover -s tests -v`.
Tests cover patch failures, stale files, argument handling, flash failures,
legacy/v3/v4 headers, growing and shrinking kernels, embedded certificates,
future AVB versions, invalid offsets and corrupted metadata.

The matching Google stock boot image has SHA-256
`8e4c29a524adfe1e0ee7ae554bc04e90d36a2c79f69b5c88c0be836dbdc6d972`.
Its ML-DSA signature and boot hash were verified with avbroot. The corrected
candidate retains the original outer AVB metadata byte-for-byte and round-trips
the patched kernel through LZ4 decompression. Running the packaged Android
kptools and BusyBox with the corrected scripts on the connected cubs device
produced the exact same image as the host run (patch-only mode).

Candidate SHA-256:
`7afdbcf5f57a7705374dc2ad51578a63b8331a6735b4cdc559bffcf7191e0ad5`.

The user temporarily booted this exact image as
`apatch_patched_11274_0.13.8_qwwj.img`. Android completed boot and a direct
KernelPatch query returned `0xd08`; an authorized root request returned
`uid=0`. The debug manager initially reported that KernelPatch was not
installed, despite the working kernel.

## Manager authentication after boot

KernelPatch 0.13.8 accepts automatic manager authentication only for its
listed package/certificate pairs and a v2-only APK signing container. The
local debug APK has a different certificate. The official APatch 11224 APK
has the expected certificate, but includes both v2 and v3 signatures, which
this KernelPatch version rejects. Removing v3 is not a valid workaround:
Android's verifier detects the missing signing scheme.

The manager now checks both its installed signing certificate and APK
container before permitting a patch without a SuperKey. Other builds require
a valid custom key. Successful patches retain that key in encrypted pending
storage without replacing the running kernel's key. The next successful
authentication promotes it. The home screen also provides SuperKey login;
an unsuccessful query is described as unavailable root rather than proof that
the kernel is unpatched. Patch command arguments are no longer logged.

On this device, KernelPatch's existing authorized ADB shell was able to grant
the installed manager UID 10336 access through `truncate su sumgr grant`.
APatch then displayed **Working, 0.13.8 (11274) - Full**. Its Android daemon
reported version 11274, the manager UID could obtain `uid=0`, and SELinux
remained Enforcing. The manager grant was saved in APatch's normal package
configuration and successfully reloaded. The new manager APK was installed
as an update, retaining app data. All 34 host regressions and `assembleDebug`
passed.

This was a temporary boot. The active `boot_b` partition and APatch's stock
backup still match the verified stock SHA-256 above; no boot partition was
flashed. Permission persistence has not been tested across another boot.

A separate SuperKey image was prepared with SHA-256
`08932f6068876b507eadc4f1fa04666bca43fa1c00ae08f6dbd80ffc8b4be648`.
It preserves the same AVB metadata and contains a key hash, not the plaintext
key. It has **not** been boot-tested and is not needed for the successful
current session. The key was encrypted into the device's app storage through
APatch's existing migration path; a private local recovery copy is retained
outside the source tree's tracked files.

## References

- [Google factory images](https://developers.google.com/android/images#cubs)
- [Google's exact early kernel mapper](https://android.googlesource.com/kernel/common/+/5c5f2fea42dd/arch/arm64/kernel/pi/map_kernel.c)
- [Google's exact paging initialization](https://android.googlesource.com/kernel/common/+/5c5f2fea42dd/arch/arm64/mm/mmu.c)
- [KernelPatch 0.13.8 repacker](https://github.com/bmax121/KernelPatch/blob/0.13.8/tools/bootimg.c)
- [KernelPatch 0.13.8 release](https://github.com/bmax121/KernelPatch/releases/tag/0.13.8)
- [Pixel 11 AVB investigation](https://github.com/chenxiaolong/avbroot/issues/643)
