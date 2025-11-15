package main

//static
const (
	disableFileName      = "disable"
	updateFileName       = "update"
	removeFileName       = "remove"
	moduleWebDir         = "webroot"
	moduleActionSh       = "action.sh"
	adbDir               = "/data/adb/"
	workingDir           = "/data/adb/ap/"
	binaryDir            = "/data/adb/ap/bin/"
	moduleDir            = "/data/adb/modules/"
	moduleupdateDir      = "/data/adb/modules_update/"
	busybox              = "/data/adb/ap/bin/busybox"
	magiskpolicy         = "/data/adb/ap/bin/magiskpolicy"
	resetprop            = "/data/adb/ap/bin/resetprop"
	apd                  = "/data/adb/apd"
	ap_log               = "/data/adb/ap/log/"
	tmp_img              = "/data/adb/ap/tmp_img.img"
	force_overlayfs_file = "/data/adb/.overlayfs_enable"
	temp_dir_legacy      = "/sbin"
	temp_dir             = "/debug_ramdisk"
)

// restorecon Constants
const (
	SYSTEM_CON    = "u:object_r:system_file:s0"
	ADB_CON       = "u:object_r:adb_data_file:s0"
	UNLABEL_CON   = "u:object_r:unlabeled:s0"
	SELINUX_XATTR = "security.selinux"
)

// ver_and_cmd
const (
	MAJOR = 0
	MINOR = 12
	PATCH = 0
)

// super_call defer
const (
	__NR_SUPERCALL              = 45
	SUPERCALL_SCONTEXT_LEN      = 256
	KSTORAGE_EXCLUDE_LIST_GROUP = 1

	SUPERCALL_KLOG            = 0x1004
	SUPERCALL_KERNELPATCH_VER = 0x1008
	SUPERCALL_KERNEL_VER      = 0x1009
	SUPERCALL_SU              = 0x1010
	SUPERCALL_KSTORAGE_WRITE  = 0x1041
	SUPERCALL_SU_GRANT_UID    = 0x1100
	SUPERCALL_SU_REVOKE_UID   = 0x1101
	SUPERCALL_SU_NUMS         = 0x1102
	SUPERCALL_SU_LIST         = 0x1103
	SUPERCALL_SU_RESET_PATH   = 0x1111
	SUPERCALL_SU_GET_SAFEMODE = 0x1112
	SUPERCALL_SU_GET_PATH     = 0x1110
)

//apd version
var (
	// go build -ldflags "-X main.Version=..."
	Version        string = "11100"
	Overlay_Source string = "APatch"
	GitCommit      string = "APatch"
)
