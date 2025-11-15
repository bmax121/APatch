package main

import (
	"fmt"
	"os"
	"os/exec"
	"os/user"
	"strconv"
	"syscall"
)

func setIdentity(uid, gid int) error {
	if err := syscall.Setgid(int(gid)); err != nil {
		return fmt.Errorf("failed to set GID: %v", err)
	}
	if err := syscall.Setuid(int(uid)); err != nil {
		return fmt.Errorf("failed to set UID: %v", err)
	}

	return nil
}
func create_root_shell() error {
	args := os.Args[1:]

	var command []string
	for i, arg := range args {
		if arg == "-c" && i+1 < len(args) {
			command = append(command, args[i:]...)
			break
		}
	}

	if len(command) == 0 {
		command = args
	}

	for _, arg := range command {
		if arg == "-h" {
			printUsage()
			return nil
		}
		if arg == "-v" {
			fmt.Printf("%s:APatch\n", Version)
			return nil
		}
		if arg == "-V" {
			fmt.Printf("%s\n", Version)
			return nil
		}
	}

	var uid, gid int
	if len(args) > 0 {
		userInfo, err := user.Lookup(args[0])
		if err == nil {
			uid, _ = strconv.Atoi(userInfo.Uid)
			gid, _ = strconv.Atoi(userInfo.Gid)
		}
	}

	path := os.Getenv("PATH")
	newPath := "/data/adb/ap/bin:" + path
	if err := os.Setenv("PATH", newPath); err != nil {
		return err
	}

	if err := os.Setenv("HOME", os.Getenv("HOME")); err != nil {
		return err
	}

	shell := "/bin/sh"
	if len(command) > 0 {
		shell = command[0]
		command = command[1:]
	}
	if err := setIdentity(uid, gid); err != nil {
		return err
	}

	cmd := exec.Command(shell, command...)
	cmd.SysProcAttr = &syscall.SysProcAttr{
		Setpgid: true,
	}
	return syscall.Exec(shell, append([]string{shell}, command...), os.Environ())
}
