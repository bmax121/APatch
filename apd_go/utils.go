//go:build !android || !cgo
// +build !android !cgo

package main

import (
	"bufio"
	"errors"
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"golang.org/x/sys/unix"
)

// getprop sys.boot_completed

func ensureBootCompleted() error {
	value, err := getprop("sys.boot_completed")

	if err != nil {
		fmt.Printf("Error: %v\n", err)
		return err
	}
	if value == "1" {
		return nil
	} else {
		return errors.New("System is loading")
	}

}
func getEnv(key string) (string, bool) {
	value, exists := os.LookupEnv(key)
	return value, exists
}
func getprop(prop string) (string, error) {
	cmd := exec.Command("getprop", prop)
	output, err := cmd.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("error running getprop: %w, output: %s", err, string(output))
	}
	return strings.TrimSpace(string(output)), nil
}
func ensureCleanDir(dir string) error {
	if _, err := os.Stat(dir); err == nil {
		log.Printf("ensureCleanDir: %s exists, removing it", dir)
		if err := os.RemoveAll(dir); err != nil {
			return err
		}
	}
	return os.MkdirAll(dir, 0755)
}

func ensureFileExists(filename string) error {
	file, err := os.OpenFile(filename, os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}
	defer file.Close()
	return nil
}
func ensureDirExists(dir string) error {
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		return os.MkdirAll(dir, 0700)
	}
	return nil
}
func ensureBinary(path string) error {
	return os.Chmod(path, 0755)
}
func isSafeMode(superkey *string) bool {
	safemode, err := getprop("persist.sys.safemode")
	if err == nil && safemode == "1" {
		Info("safemode: true")
		return true
	}
	Info("safemode: false")
	if superkey != nil {
		ret := scSuGetSafemode(*superkey)
		if ret == 1 {
			Info("scSuGetSafemode: safemode active")
			return true
		} else if ret == 0 {
			Info("scSuGetSafemode: normal mode")
			return false
		} else {
			Error("scSuGetSafemode failed: %d", ret)
			return false
		}
	}
	return false
}
func isOverlayFSSupported() (bool, error) {
	file, err := os.Open("/proc/filesystems")
	if err != nil {
		return false, fmt.Errorf("failed to open /proc/filesystems: %v", err)
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		if scanner.Text() == "overlay" {
			return true, nil
		}
	}
	return false, scanner.Err()
}
func shouldEnableOverlay() bool {
	forceOverlayExists := fileExists(force_overlayfs_file)
	overlaySupported, _ := isOverlayFSSupported()
	return forceOverlayExists && overlaySupported
}
func fileExists(filename string) bool {
	if _, err := os.Stat(filename); os.IsNotExist(err) {
		return false
	}
	return true
}
func getTmpPath() string {
	if fileExists(temp_dir_legacy) {
		return temp_dir_legacy
	}
	if fileExists(temp_dir) {
		return temp_dir
	}
	return ""
}
func getWorkDir() string {
	return getTmpPath() + "/workdir/"
}
func switchCgroups(pid int) error {
	//pid := os.Getpid()
	if err := switchCgroup("/acct", pid); err != nil {
		return err
	}
	if err := switchCgroup("/dev/cg2_bpf", pid); err != nil {
		return err
	}
	if err := switchCgroup("/sys/fs/cgroup", pid); err != nil {
		return err
	}
	if prop, _ := getprop("ro.config.per_app_memcg"); prop == "false" {
		return switchCgroup("/dev/memcg/apps", pid)
	}

	return nil

}
func switchCgroup(grp string, pid int) error {
	path := filepath.Join(grp, "cgroup.procs")
	if _, err := os.Stat(path); os.IsNotExist(err) {
		return nil
	}
	fp, err := os.OpenFile(path, os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		return fmt.Errorf("failed to open cgroup.procs: %w", err)
	}
	defer fp.Close()

	if _, err := fmt.Fprintf(fp, "%d", pid); err != nil {
		return fmt.Errorf("failed to write pid to cgroup.procs: %w", err)
	}

	return nil
}
func switchMntNs(pid int) error {
	path := fmt.Sprintf("/proc/%d/ns/mnt", pid)

	if _, err := os.Stat(path); os.IsNotExist(err) {
		return fmt.Errorf("mount namespace for PID %d does not exist", pid)
	}

	cmd := exec.Command("setns", path, "0")
	if err := cmd.Run(); err != nil {
		return fmt.Errorf("failed to switch mount namespace: %w", err)
	}

	currentDir, err := os.Getwd()
	if err != nil {
		return fmt.Errorf("failed to get current directory: %w", err)
	}
	if err := os.Chdir(currentDir); err != nil {
		return fmt.Errorf("failed to change directory: %w", err)
	}

	return nil
}
func unshareMntNs() error {

	if err := unix.Unshare(unix.CLONE_NEWNS); err != nil {
		return fmt.Errorf("unshare mount namespace failed: %w", err)
	}
	return nil
}
func Umask(mask uint32) {
	unix.Umask(int(mask))
}

func HasMagisk() bool {
	cmd := exec.Command("which", "magisk")
	err := cmd.Run()
	return err == nil
}
func mountImage(imagePath string, mountPoint string) error {
	cmd := exec.Command("mount", imagePath, mountPoint)
	if err := cmd.Run(); err != nil {
		return fmt.Errorf("failed to mount image: %w", err)
	}
	return nil
}

func Logcat(level string, format string, a ...interface{}) error {
	msg := fmt.Sprintf(format, a...)
	return Logcat_inter("APatchD", level, msg)
}
func Logcat_inter(tag, priority, message string) error {
	cmd := exec.Command("log", "-t", tag, "-p", priority, message)
	return cmd.Run()
}

func Info(format string, a ...interface{})  { Logcat("I", format, a...) }
func Warn(format string, a ...interface{})  { Logcat("W", format, a...) }
func Error(format string, a ...interface{}) { Logcat("E", format, a...) }
