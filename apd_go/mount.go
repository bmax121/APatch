package main

import (
	"fmt"
	"os"
	"path/filepath"
	"syscall"
	"unsafe"

	"golang.org/x/sys/unix"
)

const (
	MODULE_DIR           = "/data/adb/modules"
	DISABLE_FILE_NAME    = "disable"
	SKIP_MOUNT_FILE_NAME = "skip_mount"
	REPLACE_DIR_XATTR    = "trusted.overlay.opaque"
)

// NodeFileType 结构体 (从 Rust 转换)
type NodeFileType int

const (
	RegularFile NodeFileType = iota
	Directory
	Symlink
	Whiteout
)

type Node struct {
	Name       string
	FileType   NodeFileType
	Children   map[string]*Node
	ModulePath string
	Replace    bool
	Skip       bool
}

func fileTypeFromOS(info os.FileInfo) NodeFileType {
	mode := info.Mode()
	if mode.IsRegular() {
		return RegularFile
	}
	if mode.IsDir() {
		return Directory
	}
	if mode&os.ModeSymlink != 0 {
		return Symlink
	}

	return RegularFile
}

func newNodeRoot(name string) *Node {
	return &Node{
		Name:     name,
		FileType: Directory,
		Children: make(map[string]*Node),
	}
}
func newNodeModule(name string, path string, info os.FileInfo) *Node {
	ft := fileTypeFromOS(info)
	replace := false

	if ft == Directory {
		if attrVal, err := lgetFileCon(path); err == nil {
			if string(attrVal) == "y" {
				replace = true
			}
		}
	}

	return &Node{
		Name:       name,
		FileType:   ft,
		Children:   make(map[string]*Node),
		ModulePath: path,
		Replace:    replace,
		Skip:       false,
	}
}
func (n *Node) collectModuleFiles(moduleDir string) (bool, error) {
	var hasFile bool

	entries, err := os.ReadDir(moduleDir)
	if err != nil {
		return false, fmt.Errorf("failed to read module directory %s: %w", moduleDir, err)
	}

	for _, entry := range entries {
		name := entry.Name()
		entryPath := filepath.Join(moduleDir, name)

		info, err := os.Lstat(entryPath)
		if err != nil {
			continue
		}

		node := n.Children[name]
		if node == nil {
			newNode := newNodeModule(name, entryPath, info)
			if newNode == nil {
				continue
			}
			n.Children[name] = newNode
			node = newNode
		}

		if node.FileType == Directory {

			childHasFile, err := node.collectModuleFiles(filepath.Join(moduleDir, name))
			if err != nil {
				return false, err
			}
			if childHasFile || node.Replace {
				hasFile = true
			}
		} else {

			hasFile = true
		}
	}

	return hasFile, nil
}

func collectAllModuleFiles() (*Node, error) {
	root := newNodeRoot("")
	system := newNodeRoot("system")
	var hasFile bool

	moduleRoot := filepath.Clean(MODULE_DIR)
	entries, err := os.ReadDir(moduleRoot)
	if err != nil {
		return nil, fmt.Errorf("failed to read modules directory: %w", err)
	}

	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}

		modPath := filepath.Join(moduleRoot, entry.Name())

		if exists(filepath.Join(modPath, DISABLE_FILE_NAME)) || exists(filepath.Join(modPath, SKIP_MOUNT_FILE_NAME)) {
			continue
		}

		modSystem := filepath.Join(modPath, "system")
		if info, err := os.Stat(modSystem); err != nil || !info.IsDir() {
			continue
		}

		modHasFile, err := system.collectModuleFiles(modSystem)
		if err != nil {
			return nil, err
		}
		if modHasFile {
			hasFile = true
		}
	}

	if !hasFile {
		// log.Printf("no modules to mount")
		return nil, nil
	}

	partitions := map[string]bool{
		"vendor":     true,  // require_symlink=true
		"system_ext": true,  // require_symlink=true
		"product":    true,  // require_symlink=true
		"odm":        false, // require_symlink=false
		"oem":        false, // require_symlink=false
	}

	for partition, requireSymlink := range partitions {
		pathOfRoot := filepath.Join("/", partition)
		pathOfSystem := filepath.Join("/system", partition)

		rootInfo, err := os.Stat(pathOfRoot)
		if err != nil || !rootInfo.IsDir() {
			continue
		}

		if requireSymlink {
			if _, err := os.Lstat(pathOfSystem); err != nil || os.IsNotExist(err) {
				continue
			}

			symlinkInfo, _ := os.Lstat(pathOfSystem)
			if symlinkInfo == nil || symlinkInfo.Mode()&os.ModeSymlink == 0 {
				continue
			}
		}

		if node, ok := system.Children[partition]; ok {
			delete(system.Children, partition)
			root.Children[partition] = node
		}
	}

	root.Children["system"] = system
	return root, nil
}

func exists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}
func fsopen(fstype string, flags int) (int, error) {
	fd, err := unix.Open(fstype, unix.O_RDONLY, 0)
	if err != nil {
		return -1, err
	}
	return fd, nil
}

func fsconfigCreate(fd int) error {
	_, _, errno := unix.Syscall(unix.SYS_FSCONFIG, uintptr(fd), 0, 0)
	if errno != 0 {
		return errno
	}
	return nil
}
func moveMount(src, dest string) error {

	if _, err := os.Stat(src); os.IsNotExist(err) {
		return fmt.Errorf("source %s is not found: %w", src, err)
	}

	err := unix.Mount(src, dest, "", unix.MS_MOVE, "")
	if err != nil {
		return fmt.Errorf("connot %s move to %s: %w", src, dest, err)
	}
	return nil
}
func mountDevpts(dest string) error {
	err := unix.Mkdir(dest, 0755)
	if err != nil && !os.IsExist(err) {
		return err
	}

	_, _, errno := unix.Syscall(unix.SYS_MOUNT, uintptr(unsafe.Pointer(&[]byte("devpts")[0])), uintptr(unsafe.Pointer(&[]byte(dest)[0])), uintptr(0))
	if errno != 0 {
		return errno
	}
	return nil
}
func mountTmpfs(dest string) error {

	if err := os.MkdirAll(dest, 0755); err != nil {
		return fmt.Errorf("failed to create directory %s: %w", dest, err)
	}

	if err := unix.Mount("tmpfs", dest, "tmpfs", 0, ""); err != nil {
		return fmt.Errorf("failed to mount tmpfs on %s: %w", dest, err)
	}

	ptsDir := fmt.Sprintf("%s/pts", dest)
	if err := os.MkdirAll(ptsDir, 0755); err != nil {
		return fmt.Errorf("failed to create directory %s: %w", ptsDir, err)
	}

	if err := unix.Mount("devpts", ptsDir, "devpts", 0, ""); err != nil {
		return fmt.Errorf("failed to mount devpts on %s: %w", ptsDir, err)
	}

	return nil
}

func bindMount(source, target string) error {

	return unix.Mount(source, target, "", unix.MS_BIND, "")
}
func cloneSymlink(src, dst string) error {
	srcSymlink, err := os.Readlink(src)
	if err != nil {
		return fmt.Errorf("read link %s failed: %w", src, err)
	}
	if err := os.Symlink(srcSymlink, dst); err != nil {
		return fmt.Errorf("create symlink %s -> %s failed: %w", src, srcSymlink, err)
	}

	con, err := lgetFileCon(src)
	if err != nil {
		return fmt.Errorf("get file context %s failed: %w", src, dst)
	}
	if err := lsetFileCon(dst, con); err != nil {
		fmt.Printf("create symlink %s -> %s ", src, srcSymlink)
		return fmt.Errorf("set file context %s failed: %w", dst, err)
	}

	return nil
}

func magicMount() error {
	rootNode, err := collectAllModuleFiles()
	if err != nil {
		return err
	}

	if rootNode == nil {
		return nil
	}
	tmpDir := filepath.Join(getWorkDir(), "overlay_tmp")

	if err := os.MkdirAll(tmpDir, 0755); err != nil {
		return fmt.Errorf("ensure tmp dir exists: %w", err)
	}

	if err := unix.Mount(Overlay_Source, tmpDir, "tmpfs", 0, ""); err != nil {
		return fmt.Errorf("mount tmpfs failed: %w", err)
	}

	if err := unix.Mount(tmpDir, tmpDir, "none", syscall.MS_PRIVATE, ""); err != nil {
		return fmt.Errorf("make tmpfs private failed: %w", err)
	}

	resultErr := doMagicMount("/", tmpDir, rootNode, false)

	//if umountErr := unix.Unmount(tmpDir, unix.MNT_DETACH); umountErr != nil {
	//	fmt.Printf("Error: failed to unmount tmp %v\n", umountErr)
	//}

	os.RemoveAll(tmpDir)

	return resultErr
}
func doMagicMount(
	path string,
	workDirPath string,
	current *Node,
	hasTmpfs bool,
) error {
	path = filepath.Join(path, current.Name)
	workDirPath = filepath.Join(workDirPath, current.Name)

	switch current.FileType {
	case RegularFile:
		targetPath := path
		if hasTmpfs {
			if _, err := os.Create(workDirPath); err != nil {
				return fmt.Errorf("create file %s in tmpfs failed: %w", workDirPath, err)
			}
			targetPath = workDirPath
		}

		if current.ModulePath == "" {
			return fmt.Errorf("cannot mount root file %s! module path is missing", path)
		}

		if err := bindMount(current.ModulePath, targetPath); err != nil {
			return fmt.Errorf("mount module file %s -> %s failed: %w", current.ModulePath, targetPath, err)
		}
		// log::debug!("mount module file {} -> {}", current.ModulePath, targetPath)

	case Symlink:

		if current.ModulePath == "" {
			return fmt.Errorf("cannot mount root symlink %s! module path is missing", path)
		}

		if err := cloneSymlink(current.ModulePath, workDirPath); err != nil {
			return fmt.Errorf("create module symlink %s -> %s failed: %w", current.ModulePath, workDirPath, err)
		}
		// log::debug!("create module symlink {} -> {}", current.ModulePath, workDirPath)

	case Directory:

		createTmpfs := !hasTmpfs && current.Replace && current.ModulePath != ""

		if !hasTmpfs && !createTmpfs {

			for name, node := range current.Children {
				realPath := filepath.Join(path, name)
				needTmpfs := false

				switch node.FileType {
				case Symlink:

					needTmpfs = true
				case Whiteout:
					if exists(realPath) {
						needTmpfs = true
					}
				default:
					if metadata, err := os.Lstat(realPath); err == nil {
						realFileType := fileTypeFromOS(metadata)
						if realFileType != node.FileType || realFileType == Symlink {
							needTmpfs = true
						}
					} else {
						needTmpfs = true
					}
				}

				if needTmpfs {
					if current.ModulePath == "" {

						fmt.Printf("Error: cannot create tmpfs on %s, ignoring: %s\n", path, name)
						node.Skip = true
						continue
					}
					createTmpfs = true
					break
				}
			}
		}

		hasTmpfs = hasTmpfs || createTmpfs

		if hasTmpfs {
			// log::debug!("creating tmpfs skeleton for {} at {}", path, workDirPath)

			if err := os.MkdirAll(workDirPath, 0755); err != nil {
				return fmt.Errorf("create work dir %s failed: %w", workDirPath, err)
			}

			var metadata os.FileInfo
			var sourcePath string

			if exists(path) {
				metadata, _ = os.Stat(path)
				sourcePath = path
			} else if current.ModulePath != "" && exists(current.ModulePath) {
				metadata, _ = os.Stat(current.ModulePath)
				sourcePath = current.ModulePath
			} else {

				return fmt.Errorf("cannot get metadata for dir %s", path)
			}

			sysStat := metadata.Sys().(*syscall.Stat_t)

			if err := os.Chmod(workDirPath, metadata.Mode().Perm()); err != nil {
				return fmt.Errorf("chmod %s failed: %w", workDirPath, err)
			}
			if err := os.Chown(workDirPath, int(sysStat.Uid), int(sysStat.Gid)); err != nil {
				return fmt.Errorf("chown %s failed: %w", workDirPath, err)
			}
			if con, err := lgetFileCon(sourcePath); err == nil {
				if err := lsetFileCon(workDirPath, con); err != nil {
					return fmt.Errorf("lsetfilecon %s failed: %w", workDirPath, err)
				}
			}
		}

		if createTmpfs {
			// log::debug!("creating tmpfs for {} at {}", path, workDirPath)
			if err := bindMount(workDirPath, workDirPath); err != nil {
				return fmt.Errorf("bind self mount on %s failed: %w", workDirPath, err)
			}
		}

		if exists(path) && !current.Replace {
			dirEntries, err := os.ReadDir(path)
			if err != nil {
				return fmt.Errorf("read target dir %s failed: %w", path, err)
			}

			for _, entry := range dirEntries {
				name := entry.Name()

				if node, ok := current.Children[name]; ok {
					if node.Skip {
						continue
					}

					if err := doMagicMount(path, workDirPath, node, hasTmpfs); err != nil {

						if hasTmpfs {
							return fmt.Errorf("magic mount %s/%s failed: %w", path, name, err)
						} else {
							fmt.Printf("Error: mount child %s/%s failed: %v\n", path, name, err)
						}
					}
					delete(current.Children, name)

				} else if hasTmpfs {

					realEntryPath := filepath.Join(path, name)
					if info, err := os.Lstat(realEntryPath); err == nil {
						if err := mountMirror(path, workDirPath, realEntryPath, info); err != nil {

							return fmt.Errorf("mount mirror %s/%s failed: %w", path, name, err)
						}
					}
				}
			}
		}

		if current.Replace {
			if current.ModulePath == "" {
				return fmt.Errorf("dir %s is declared as replaced but it is root!", path)
			}
			// log::debug!("dir {} is replaced", path)
		}

		for name, node := range current.Children {
			if node.Skip {
				continue
			}
			if err := doMagicMount(path, workDirPath, node, hasTmpfs); err != nil {
				if hasTmpfs {
					return fmt.Errorf("magic mount remaining %s/%s failed: %w", path, name, err)
				} else {
					fmt.Printf("Error: mount child %s/%s failed: %v\n", path, name, err)
				}
			}
		}

		if createTmpfs {
			// log::debug!("moving tmpfs {} -> {}", workDirPath, path)
			if err := moveMount(workDirPath, path); err != nil {
				return fmt.Errorf("move mount %s -> %s failed: %w", workDirPath, path, err)
			}

			if err := unix.Mount(path, path, "none", unix.MS_PRIVATE|unix.MS_REC, ""); err != nil {
				return fmt.Errorf("make mount %s private failed: %w", path, err)
			}
		}

	case Whiteout:

		// log::debug!("file {} is removed", path)

	default:
		// do nothing here
	}

	return nil
}
func mountMirror(path, workDirPath, entryPath string, info os.FileInfo) error {
	name := info.Name()
	targetPath := filepath.Join(path, name)
	workTargetDir := filepath.Join(workDirPath, name)

	if info.Mode().IsRegular() {

		if _, err := os.Create(workTargetDir); err != nil {
			return fmt.Errorf("create mirror file %s failed: %w", workTargetDir, err)
		}
		if err := bindMount(targetPath, workTargetDir); err != nil {
			return fmt.Errorf("bind mount mirror file %s -> %s failed: %w", targetPath, workTargetDir, err)
		}
	} else if info.IsDir() {
		if err := os.Mkdir(workTargetDir, 0755); err != nil {
			return fmt.Errorf("create mirror dir %s failed: %w", workTargetDir, err)
		}

		sysStat := info.Sys().(*syscall.Stat_t)
		if err := os.Chmod(workTargetDir, info.Mode().Perm()); err != nil {
			return fmt.Errorf("chmod mirror dir %s failed: %w", workTargetDir, err)
		}
		if err := os.Chown(workTargetDir, int(sysStat.Uid), int(sysStat.Gid)); err != nil {
			return fmt.Errorf("chown mirror dir %s failed: %w", workTargetDir, err)
		}
		if con, err := lgetFileCon(targetPath); err == nil {
			if err := lsetFileCon(workTargetDir, con); err != nil {
				return fmt.Errorf("lsetfilecon mirror dir %s failed: %w", workTargetDir, err)
			}
		}

		entries, err := os.ReadDir(targetPath)
		if err != nil {
			return fmt.Errorf("read mirror source dir %s failed: %w", targetPath, err)
		}
		for _, entry := range entries {
			childPath := filepath.Join(targetPath, entry.Name())
			childInfo, _ := os.Lstat(childPath)
			if childInfo != nil {
				if err := mountMirror(targetPath, workTargetDir, childPath, childInfo); err != nil {
					return err
				}
			}
		}

	} else if info.Mode()&os.ModeSymlink != 0 {
		if err := cloneSymlink(targetPath, workTargetDir); err != nil {
			return fmt.Errorf("clone mirror symlink %s -> %s failed: %w", targetPath, workTargetDir, err)
		}
	}
	return nil
}
