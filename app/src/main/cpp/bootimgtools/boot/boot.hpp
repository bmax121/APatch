#pragma once

#include <sys/types.h>

#define HEADER_FILE     "header"
#define KERNEL_FILE     "kernel"
#define RAMDISK_FILE    "ramdisk.cpio"
#define SECOND_FILE     "second"
#define EXTRA_FILE      "extra"
#define KER_DTB_FILE    "kernel_dtb"
#define RECV_DTBO_FILE  "recovery_dtbo"
#define DTB_FILE        "dtb"
#define BOOT            "boot.img"
#define NEW_BOOT        "new-boot.img"

extern char HEADER_FILE_PATH[FILENAME_MAX];
extern char KERNEL_FILE_PATH[FILENAME_MAX];
extern char RAMDISK_FILE_PATH[FILENAME_MAX];
extern char SECOND_FILE_PATH[FILENAME_MAX];
extern char EXTRA_FILE_PATH[FILENAME_MAX];
extern char KER_DTB_FILE_PATH[FILENAME_MAX];
extern char RECV_DTBO_FILE_PATH[FILENAME_MAX];
extern char DTB_FILE_PATH[FILENAME_MAX];
extern char BOOT_PATH[FILENAME_MAX];
extern char NEW_BOOT_PATH[FILENAME_MAX];

int unpack(const char *work_dir, bool skip_decomp = false, bool hdr = false);
void repack(const char *work_dir, const char *out_img, bool skip_comp = false);
int split_image_dtb(const char *filename);
int hexpatch(const char *file, const char *from, const char *to);
int cpio_commands(int argc, char *argv[]);
int dtb_commands(int argc, char *argv[]);

uint32_t patch_verity(void *buf, uint32_t size);
uint32_t patch_encryption(void *buf, uint32_t size);
bool check_env(const char *name);
