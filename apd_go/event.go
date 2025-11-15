package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
)

func execCommand(command string, args []string) error {
	cmd := exec.Command(command, args...)
	return cmd.Run()
}
func on_post_fs_data(superkey string) {
	Umask(0)

	InitLoadPackageUidConfig(superkey)
	InitLoadSuPath(superkey)

	args := []string{"--magisk", "--live"}
	if err := execCommand(magiskpolicy, args); err != nil {
		Error("Load magiskpolicy failed")
	}

	Info("Re-privilege apd profile after injecting sepolicy")
	PrivilegeApdProfile(superkey)

	if HasMagisk() {
		Error("Magisk detected, skip post-fs-data!")
		return
	}

	// Create log environment
	if _, err := os.Stat(ap_log); os.IsNotExist(err) {
		if err := os.Mkdir(ap_log, 0700); err != nil {
			Error("failed to create log folder: %w", err)
		}
	}

	// Remove old log files
	commandString := fmt.Sprintf("rm -rf %s*.old; for file in %s*; do mv \"$file\" \"$file.old\"; done", ap_log, ap_log)

	if err := execCommand("sh", []string{"-c", commandString}); err != nil {
		Warn("failed to delect old log")
	}

	logcatPath := filepath.Join(ap_log, "logcat.log")
	dmesgLog := filepath.Join(ap_log, "dmesg.log")
	cmdStr := fmt.Sprintf("nohup timeout -s 9 120s logcat -b main,system,crash -f %s logcatcher-bootlog:S &", logcatPath)
	cmd := exec.Command("/bin/sh", "-c", cmdStr)
	if err := cmd.Run(); err != nil {
		Error("failed to start logcat: %v\n", err)
	}

	cmdStr = fmt.Sprintf("nohup timeout -s 9 120s dmesg -w > %s 2>&1 &", dmesgLog)
	cmd = exec.Command("/bin/sh", "-c", cmdStr)
	if err := cmd.Run(); err != nil {
		Error("failed to start dmesg: %v\n", err)
	}
	safeMode := isSafeMode(&superkey)

	if safeMode {
		if err := disableAllModulesUpdate(); err != nil {
			Error("disable all modules failed: %v", err)
		}
	} else {
		if err := execCommonScripts("post-fs-data.d", true); err != nil {
			//warn(fmt.Sprintf("exec common post-fs-data scripts failed: %v", err))
			Error("exec common post-fs-data scripts failed: %v", err)
		}
	}

	moduleUpdateFlag := filepath.Join(workingDir, updateFileName)
	if err := ensureBinary(binaryDir); err != nil {
		Error("binary missing: %w", err)
		return
	}
	if _, err := os.Stat(moduleupdateDir); err == nil {
		if err := moveFile(moduleupdateDir, moduleDir); err != nil {

		}
		if err := os.RemoveAll(moduleupdateDir); err != nil {

		}
	}
	tmpModuleImg := tmp_img
	tmpModulePath := filepath.Join(tmpModuleImg)

	if (fileExists(moduleUpdateFlag) || !fileExists(tmpModulePath)) && shouldEnableOverlay() {
		//todo

	} else {
		//info("donothing")
	}

	if safeMode {
		//warn("safe mode, skip post-fs-data scripts and disable all modules!")
		if err := disableAllModulesUpdate(); err != nil {
			//warn(fmt.Sprintf("disable all modules failed: %v", err))
			Error("disable all modules failed: %v", err)
		}
		return
	}

	if err := pruneModules(); err != nil {
		Error("prune modules failed: %v", err)
	}

	if err := RestoreCon(); err != nil {
		Error("restorecon failed: %v", err)
	}

	if err := loadSEPolicyRule(); err != nil {
		Error("load sepolicy.rule failed")
	}

	if err := mountTmpfs(getTmpPath()); err != nil {
		Error("do temp dir mount failed: %v", err)
	}

	// Execute modules post-fs-data scripts
	if err := ExecStageScript("post-fs-data", true); err != nil {
		Error("exec post-fs-data scripts failed: %v", err)
	}

	// Load system.prop
	if err := loadSystemProp(); err != nil {
		Error("load system.prop failed: %v", err)
	}
	//magicMount()
	//if shouldEnableOverlay() {
	//	if err := mountSystemlessly(moduleDir); err != nil {
	//		warn(fmt.Sprintf("do systemless mount failed: %v", err))
	//	}
	//} else {
	//	if err := systemlessBindMount(moduleDir); err != nil {
	//		warn(fmt.Sprintf("do systemless bind_mount failed: %v", err))
	//	}
	//}

	runStage("post-mount", &superkey, true)

	if err := os.Chdir("/"); err != nil {
		Error("failed to chdir to /: %w", err)
	}

	return
}
func on_services(superkey string) {
	Info("on_services triggered!")
	runStage("service", &superkey, false)
}
func on_boot_completed(superkey string) {
	Info("on_boot_completed triggered!")
	runStage("boot-completed", &superkey, false)
}
func runStage(stage string, superkey *string, block bool) {
	Umask(0)

	if HasMagisk() {
		Info("Magisk detected, skip %s", stage)
		return
	}

	if isSafeMode(superkey) {
		Info("safe mode, skip %s scripts", stage)
		if err := disableAllModulesUpdate(); err != nil {
			Error("disable all modules failed: %v", err)
		}
		return
	}

	if err := execScript(fmt.Sprintf("%s.d", stage), block); err != nil {
		Error("Failed to exec common %s scripts: %v", stage, err)
	}
	if err := ExecStageScript(stage, block); err != nil {
		Error("Failed to exec %s scripts: %v", stage, err)
	}
}

func moveFile(moduleUpdateDir, moduleDir string) error {
	entries, err := os.ReadDir(moduleUpdateDir)
	if err != nil {
		return err
	}

	for _, entry := range entries {
		fileName := entry.Name()
		sourcePath := filepath.Join(moduleUpdateDir, fileName)
		targetPath := filepath.Join(moduleDir, fileName)

		if entry.IsDir() {
			updateFilePath := filepath.Join(targetPath, updateFileName)
			if _, err := os.Stat(updateFilePath); err == nil {
				if _, err := os.Stat(targetPath); err == nil {
					Info("Removing existing folder in target directory: %s\n", fileName)
					if err := os.RemoveAll(targetPath); err != nil {
						return err
					}
				}

				Info("Moving %s to target directory\n", fileName)
				if err := os.Rename(sourcePath, targetPath); err != nil {
					return err
				}
			}
		}
	}

	return nil
}
