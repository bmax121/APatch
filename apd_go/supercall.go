package main

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"strings"
	"syscall"
	"unsafe"
)

type SuProfile struct {
	Uid      int32
	ToUid    int32
	Scontext [SUPERCALL_SCONTEXT_LEN]byte
}

func verAndCmd(key string, cmd int64) int64 {
	versionCode := (MAJOR << 16) | (MINOR << 8) | PATCH
	return (int64(versionCode) << 32) | (0x1158 << 16) | (cmd & 0xFFFF)
}
func indexNullByte(b []byte) int {
	for i, v := range b {
		if v == 0 {
			return i
		}
	}
	return -1
}
func convertStringToSctx(s string) [256]byte {
	var b [256]byte
	copy(b[:], s)
	return b
}
func test(key string) {
	buf := make([]byte, 256)     // 路径缓冲区
	ret := scSuGetPath(key, buf) // 调用超级调用

	if ret < 0 {
		fmt.Printf("scSuGetPath failed: %d\n", ret)
		return
	}
	path := string(buf)
	if i := indexNullByte(buf); i >= 0 {
		path = string(buf[:i])
	}
	fmt.Printf("Path: %s\n", path)
}

// test function
func test1(key string) {
	var profile SuProfile
	profile.Uid = 1000                         // 当前用户 UID
	profile.ToUid = 2000                       // 目标用户 UID
	copy(profile.Scontext[:], "untrusted_app") // 填充安全上下文

	result := supercall(key, unsafe.Pointer(&profile))
	fmt.Printf("Result: %d\n", result)
}

func supercall(key string, profilePtr unsafe.Pointer) int64 {
	if len(key) == 0 {
		return -int64(syscall.EINVAL)
	}

	cKey := []byte(key + "\x00")
	keyPtr := unsafe.Pointer(&cKey[0])

	ret, _, errno := syscall.RawSyscall(
		uintptr(__NR_SUPERCALL),
		uintptr(keyPtr),
		uintptr(SUPERCALL_SU),
		uintptr(profilePtr),
	)

	if errno != 0 {
		return int64(-errno)
	}

	return int64(ret)
}

func scSuGrantUid(key string, profile *SuProfile) int64 {
	if len(key) == 0 {
		return -int64(syscall.EINVAL)
	}

	cKey := append([]byte(key), 0)
	keyPtr := unsafe.Pointer(&cKey[0])

	ret, _, errno := syscall.RawSyscall(
		uintptr(__NR_SUPERCALL),
		uintptr(keyPtr),
		uintptr(verAndCmd(key, SUPERCALL_SU_GRANT_UID)),
		uintptr(unsafe.Pointer(profile)),
	)

	if errno != 0 {
		return -int64(errno)
	}
	return int64(ret)
}

func scSuGetPath(key string, outPath []byte) int64 {
	pathLen := len(outPath)

	if len(key) == 0 {
		return -int64(syscall.EINVAL)
	}
	if outPath == nil || pathLen <= 0 {
		return -int64(syscall.EINVAL)
	}

	cKey := append([]byte(key), 0)
	keyPtr := unsafe.Pointer(&cKey[0])

	outPtr := unsafe.Pointer(&outPath[0])

	ret, _, errno := syscall.RawSyscall6(
		uintptr(__NR_SUPERCALL),
		uintptr(keyPtr),
		uintptr(verAndCmd(key, SUPERCALL_SU_GET_PATH)),
		uintptr(outPtr),
		uintptr(pathLen),
		0,
		0,
	)

	if errno != 0 {
		return -int64(errno)
	}
	return int64(ret)
}

func scKstorageWrite(key string, gid int32, did int64, data unsafe.Pointer, offset int32, dlen int32) int64 {
	if len(key) == 0 {
		return -int64(syscall.EINVAL)
	}

	cKey := append([]byte(key), 0)
	keyPtr := unsafe.Pointer(&cKey[0])

	offsetDlen := int64(offset)<<32 | int64(dlen)

	ret, _, errno := syscall.RawSyscall6(
		uintptr(__NR_SUPERCALL),
		uintptr(keyPtr),
		uintptr(verAndCmd(key, SUPERCALL_KSTORAGE_WRITE)),
		uintptr(gid),
		uintptr(did),
		uintptr(data),
		uintptr(offsetDlen),
	)

	if errno != 0 {
		return -int64(errno)
	}
	return int64(ret)
}

func InitLoadPackageUidConfig(superkey string) {
	packageConfigs := ReadApPackageConfig()

	for _, config := range packageConfigs {
		if config.Allow == 1 && config.Exclude == 0 {
			if len(superkey) > 0 {
				profile := SuProfile{
					Uid:      int32(config.Uid),
					ToUid:    int32(config.ToUid),
					Scontext: convertStringToSctx(config.Sctx),
				}
				result := scSuGrantUid(superkey, &profile)
				Info("Processed %s: result = %d\n", config.Pkg, result)
			} else {
				Error("Superkey is None, skipping config: %s\n", config.Pkg)
			}
		}

		if config.Allow == 0 && config.Exclude == 1 {
			if len(superkey) > 0 {
				result := scSetApModExclude(superkey, int64(config.Uid), 1)
				Info("Processed exclude %s: result = %d\n", config.Pkg, result)
			} else {
				Error("Superkey is None, skipping config: %s\n", config.Pkg)
			}
		}
	}
}

func scSetApModExclude(key string, uid int64, exclude int32) int64 {
	return scKstorageWrite(
		key,
		KSTORAGE_EXCLUDE_LIST_GROUP,   // gid
		uid,                           // did
		unsafe.Pointer(&exclude),      // data
		0,                             // offset
		int32(unsafe.Sizeof(exclude)), // dlen
	)
}

func scSuResetPath(key string, suPath string) int64 {
	if len(key) == 0 || len(suPath) == 0 {
		return -1
	}

	cKey := append([]byte(key), 0)
	keyPtr := unsafe.Pointer(&cKey[0])

	cPath := append([]byte(suPath), 0)
	pathPtr := unsafe.Pointer(&cPath[0])

	ret, _, errno := syscall.RawSyscall6(
		uintptr(__NR_SUPERCALL),
		uintptr(keyPtr),
		uintptr(verAndCmd(key, SUPERCALL_SU_RESET_PATH)),
		uintptr(pathPtr),
		0, 0, 0,
	)
	if errno != 0 {
		return -int64(errno)
	}
	return int64(ret)
}
func InitLoadSuPath(superkey string) {
	suPathFile := "/data/adb/ap/su_path"

	file, err := os.Open(suPathFile)
	if err != nil {
		Error("Failed to read su_path file: %v\n", err)
		return
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	var suPath string
	if scanner.Scan() {
		suPath = strings.TrimSpace(scanner.Text())
	}
	if err := scanner.Err(); err != nil {
		Error("Failed to read su_path file: %v\n", err)
		return
	}

	if len(superkey) == 0 {
		Error("Superkey is None, skipping...")
		return
	}

	if len(suPath) == 0 {
		Error("su_path is empty, skipping...")
		return
	}

	result := scSuResetPath(superkey, suPath)
	if result == 0 {
		Info("suPath load successfully")
	} else {
		Info("Failed to load su path, error code: %d\n", result)
	}
}
func scSu(key string, profile *SuProfile) int64 {
	if len(key) == 0 {
		return -int64(syscall.EINVAL)
	}

	cKey := append([]byte(key), 0)
	keyPtr := unsafe.Pointer(&cKey[0])

	ret, _, errno := syscall.RawSyscall(
		uintptr(__NR_SUPERCALL),
		uintptr(keyPtr),
		uintptr(verAndCmd(key, SUPERCALL_SU)),
		uintptr(unsafe.Pointer(profile)),
	)

	if errno != 0 {
		return -int64(errno)
	}
	return int64(ret)
}

func PrivilegeApdProfile(key string) {
	if key == "" {
		log.Println("[PrivilegeApdProfile] empty key, skipping privilege")
		return
	}
	var sctx [256]byte
	copy(sctx[:], []byte("u:r:magisk:s0"))
	profile := SuProfile{
		Uid:      int32(os.Getpid()),
		ToUid:    0,
		Scontext: sctx,
	}

	result := scSu(key, &profile)
	Info("[PrivilegeApdProfile] result = %d\n", result)
}
func scSuGetSafemode(key string) int64 {
	if len(key) == 0 {
		return 0
	}

	cKey := append([]byte(key), 0)
	keyPtr := unsafe.Pointer(&cKey[0])

	ret, _, errno := syscall.RawSyscall(
		uintptr(__NR_SUPERCALL),
		uintptr(keyPtr),
		uintptr(verAndCmd(key, SUPERCALL_SU_GET_SAFEMODE)),
		0,
	)

	if errno != 0 {
		return -int64(errno)
	}
	return int64(ret)
}
