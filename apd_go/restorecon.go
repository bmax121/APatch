package main

import (
	"fmt"
	"os"
	"path/filepath"
	"unsafe"

	"golang.org/x/sys/unix"
)

func lsetFileCon(path string, con string) error {
	// 忽略空上下文，因为 SELinux 上下文设置为空通常会失败
	if con == "" {
		return nil
	}

	// 1. 将 Go 字符串转换为 C 风格的指针 (以空字节结尾)
	pathBytes, err := unix.BytePtrFromString(path)
	if err != nil {
		return err
	}
	nameBytes, err := unix.BytePtrFromString("security.selinux")
	if err != nil {
		return err
	}

	// SELinux 上下文需要包含尾部的空字节，作为 C 字符串传递
	conBytes := append([]byte(con), 0)

	// 2. 调用原始系统调用 lsetxattr(2)
	// 关键点: 使用 unix.SYS_LSETXATTR 确保不对符号链接进行追随。
	_, _, errno := unix.Syscall6(
		unix.SYS_LSETXATTR,
		uintptr(unsafe.Pointer(pathBytes)),
		uintptr(unsafe.Pointer(nameBytes)),
		uintptr(unsafe.Pointer(&conBytes[0])), // value pointer
		uintptr(len(conBytes)),                // size (必须包含空字节)
		0,                                     // flags
		0,                                     // unused
	)

	if errno != 0 {
		// 捕获系统调用返回的底层错误
		return fmt.Errorf("failed to change SELinux context for %s via lsetxattr: %s", path, errno.Error())
	}
	return nil
}

// lgetFileCon gets the SELinux context for the specified path
func lgetFileCon(path string) (string, error) {
	con := make([]byte, 256) // Allocate a buffer for the SELinux context
	_, err := unix.Getxattr(path, SELINUX_XATTR, con)
	if err != nil {
		return "", err
	}
	return string(con), nil
}

// setSysCon sets the SELinux context to SYSTEM_CON
func setSysCon(path string) error {
	return lsetFileCon(path, SYSTEM_CON)
}

// restoreSysCon restores SELinux context for all files in the directory
func restoreSysCon(dir string) error {
	return filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		return setSysCon(path)
	})
}

// restoreSysConIfUnlabeled restores SELinux context for unlabeled files in the directory
func restoreSysConIfUnlabeled(dir string) error {
	return filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		con, err := lgetFileCon(path)
		if err != nil {
			return err
		}
		if con == UNLABEL_CON || con == "" {
			return lsetFileCon(path, SYSTEM_CON)
		}
		return nil
	})
}

// RestoreCon restores the SELinux context for specified paths
func RestoreCon() error {

	if err := lsetFileCon(apd, ADB_CON); err != nil {
		return err
	}
	return restoreSysConIfUnlabeled(moduleDir)
}
