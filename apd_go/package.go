package main

import (
	"bufio"
	"encoding/csv"
	"fmt"
	"io"
	"os"
	"strings"
	"time"
)

type PackageConfig struct {
	Pkg     string `json:"pkg"`
	Exclude int    `json:"exclude"`
	Allow   int    `json:"allow"`
	Uid     int    `json:"uid"`
	ToUid   int    `json:"to_uid"`
	Sctx    string `json:"sctx"`
}

// 读取 /data/adb/ap/package_config CSV 文件
func ReadApPackageConfig() []PackageConfig {
	maxRetry := 5
	path := "/data/adb/ap/package_config"

	for i := 0; i < maxRetry; i++ {
		file, err := os.Open(path)
		if err != nil {
			fmt.Printf("WARN: Error opening file: %v\n", err)
			time.Sleep(time.Second)
			continue
		}
		defer file.Close()

		reader := csv.NewReader(file)
		var configs []PackageConfig
		success := true

		for {
			record, err := reader.Read()
			if err == io.EOF {
				break
			} else if err != nil {
				fmt.Printf("WARN: Error reading CSV record: %v\n", err)
				success = false
				break
			}

			if len(record) < 6 {
				fmt.Printf("WARN: Invalid record: %v\n", record)
				success = false
				break
			}

			cfg := PackageConfig{}
			cfg.Pkg = record[0]
			fmt.Sscanf(record[1], "%d", &cfg.Exclude)
			fmt.Sscanf(record[2], "%d", &cfg.Allow)
			fmt.Sscanf(record[3], "%d", &cfg.Uid)
			fmt.Sscanf(record[4], "%d", &cfg.ToUid)
			cfg.Sctx = record[5]

			configs = append(configs, cfg)
		}

		if success {
			return configs
		}
		time.Sleep(time.Second)
	}
	return []PackageConfig{}
}

// 写 CSV 文件
func WriteApPackageConfig(configs []PackageConfig) error {
	maxRetry := 5
	tempPath := "/data/adb/ap/package_config.tmp"
	finalPath := "/data/adb/ap/package_config"

	for i := 0; i < maxRetry; i++ {
		file, err := os.Create(tempPath)
		if err != nil {
			fmt.Printf("WARN: Error creating temp file: %v\n", err)
			time.Sleep(time.Second)
			continue
		}
		defer file.Close()

		writer := csv.NewWriter(file)
		success := true

		for _, cfg := range configs {
			record := []string{
				cfg.Pkg,
				fmt.Sprintf("%d", cfg.Exclude),
				fmt.Sprintf("%d", cfg.Allow),
				fmt.Sprintf("%d", cfg.Uid),
				fmt.Sprintf("%d", cfg.ToUid),
				cfg.Sctx,
			}
			if err := writer.Write(record); err != nil {
				fmt.Printf("WARN: Error writing CSV record: %v\n", err)
				success = false
				break
			}
		}

		writer.Flush()
		if err := writer.Error(); err != nil {
			fmt.Printf("WARN: Error flushing writer: %v\n", err)
			time.Sleep(time.Second)
			continue
		}

		if !success {
			time.Sleep(time.Second)
			continue
		}

		if err := os.Rename(tempPath, finalPath); err != nil {
			fmt.Printf("WARN: Error renaming temp file: %v\n", err)
			time.Sleep(time.Second)
			continue
		}
		return nil
	}
	return fmt.Errorf("Failed after max retries")
}

// 读取 /data/system/packages.list
func ReadSystemPackages() ([]string, error) {
	file, err := os.Open("/data/system/packages.list")
	if err != nil {
		return nil, err
	}
	defer file.Close()

	var packages []string
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		parts := strings.Fields(line)
		if len(parts) >= 2 {
			packages = append(packages, parts[0])
		}
	}
	return packages, scanner.Err()
}

// 同步 package UID 配置
func SynchronizePackageUid() error {
	fmt.Println("[INFO] Start synchronizing root list with system packages...")

	maxRetry := 5
	for i := 0; i < maxRetry; i++ {
		systemPackages, err := ReadSystemPackages()
		if err != nil {
			fmt.Printf("WARN: Error reading packages.list: %v\n", err)
			time.Sleep(time.Second)
			continue
		}

		configs := ReadApPackageConfig()
		originalLen := len(configs)
		var updatedConfigs []PackageConfig

		// 保留仍然安装的 package
		for _, cfg := range configs {
			for _, pkg := range systemPackages {
				if cfg.Pkg == pkg {
					updatedConfigs = append(updatedConfigs, cfg)
					break
				}
			}
		}
		removedCount := originalLen - len(updatedConfigs)
		if removedCount > 0 {
			fmt.Printf("[INFO] Removed %d uninstalled package configurations\n", removedCount)
		}

		updated := false
		// 更新 UID
		file, _ := os.Open("/data/system/packages.list")
		scanner := bufio.NewScanner(file)
		for scanner.Scan() {
			line := scanner.Text()
			words := strings.Fields(line)
			if len(words) >= 2 {
				pkgName := words[0]
				var uid int
				if _, err := fmt.Sscanf(words[1], "%d", &uid); err != nil {
					fmt.Printf("WARN: Error parsing uid: %v\n", words[1])
					continue
				}

				for i := range updatedConfigs {
					if updatedConfigs[i].Pkg == pkgName && updatedConfigs[i].Uid != uid {
						fmt.Printf("[INFO] Updating uid for package %s: %d -> %d\n",
							pkgName, updatedConfigs[i].Uid, uid)
						updatedConfigs[i].Uid = uid
						updated = true
						break
					}
				}
			}
		}
		file.Close()

		if updated || removedCount > 0 {
			if err := WriteApPackageConfig(updatedConfigs); err != nil {
				fmt.Printf("WARN: Error writing package config: %v\n", err)
			}
		}
		return nil
	}

	return fmt.Errorf("Failed after max retries")
}
