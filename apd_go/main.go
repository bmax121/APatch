package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

func printbanner() {
	banner := `
    _    ____       _       _     
   / \  |  _ \ __ _| |_ ___| |__  
  / _ \ | |_) / _` + "`" + ` | __/ __| '_ \ 
 / ___ \|  __/ (_| | || (__| | | |
/_/   \_\_|   \__,_|\__\___|_| |_|
   `
	fmt.Println(banner)
}

func printUsage() {

	fmt.Fprintf(os.Stderr, "Usage: %s [global options] <command> [arguments]\n\n", filepath.Base(os.Args[0]))

	fmt.Fprintf(os.Stderr, "Commands:\n")
	fmt.Fprintf(os.Stderr, "  module install <path>      Install a module from the given path.\n")
	fmt.Fprintf(os.Stderr, "  module test <func>         Run a test function.\n")
	fmt.Fprintf(os.Stderr, "  module list                List all installed modules.\n")
	fmt.Fprintf(os.Stderr, "  module enable <name>       Enable a specific module.\n")
	fmt.Fprintf(os.Stderr, "  module disable <name>      Disable a specific module.\n")
	fmt.Fprintf(os.Stderr, "  module disable_all_modules Disable all modules.\n")
	fmt.Fprintf(os.Stderr, "  post-fs-data               Trigger the post-fs-data event.\n")
	fmt.Fprintf(os.Stderr, "  services                   Trigger the services event.\n")
	fmt.Fprintf(os.Stderr, "  boot-completed             Trigger the boot-completed event.\n")
	fmt.Fprintf(os.Stderr, "  getprop <key>              Get a system property value.\n")

	fmt.Fprintf(os.Stderr, "\nGlobal Options:\n")
	flag.PrintDefaults()
}

func main() {
	flag.Usage = printUsage
	programName := filepath.Base(os.Args[0])
	if strings.HasSuffix(programName, "su") || strings.HasSuffix(programName, "kp") {
		if err := create_root_shell(); err != nil {
			fmt.Printf("Error: %v\n", err)
			os.Exit(1)
		}
		os.Exit(0)
	}
	for _, arg := range os.Args[1:] {
		if arg == "-V" {
			fmt.Printf("apd %s\n", Version)
			return
		}
	}
	var superkey string
	flag.StringVar(&superkey, "s", "none", "Superkey for privileged operations.")

	flag.Parse()

	if len(flag.Args()) == 0 {
		flag.Usage()
		return
	}
	args := flag.Args()

	if len(args) < 1 {
		flag.Usage()
		return
	}

	switch args[0] {
	case "module":
		if len(args) < 2 {
			fmt.Fprintf(os.Stderr, "Error: missing module subcommand.\n")
			printUsage()
			return
		}
		moduleCmd := args[1]
		switch moduleCmd {
		case "test":

			magicMount()
			fmt.Printf("test function")
			return
		case "install":
			if len(args) < 3 {
				break
			}
			modulepath := args[2]
			fmt.Printf("Installing module: %s\n", modulepath)
			installModule(modulepath)
			return
		case "list":
			modules, err := listModules()
			if err != nil {
				fmt.Printf("Error: %v\n", err)
				return
			}
			jsonOutput, err := json.MarshalIndent(modules, "", "  ")
			if err != nil {
				fmt.Printf("Error: %v\n", err)
				return
			}
			fmt.Println(string(jsonOutput))
			return
		case "enable":
			if len(args) < 3 {
				break
			}
			if err := enableModule(args[2], true); err != nil {
				fmt.Printf("Error: %v\n", err)
			}
			return
		case "disable":
			if len(args) < 3 {
				break
			}
			if err := enableModule(args[2], false); err != nil {
				fmt.Printf("Error: %v\n", err)
			}
			return
		case "disable_all_modules":
			if err := disableAllModulesUpdate(); err != nil {
				fmt.Printf("Error: %v\n", err)
			}
			return
		}

		fmt.Fprintf(os.Stderr, "Usage: apd module %s <argument>\n", moduleCmd)
		return

	case "post-fs-data":
		on_post_fs_data(superkey)
	case "services":
		on_services(superkey)
	case "boot-completed":
		on_boot_completed(superkey)
	case "supercall":
		test(superkey)
	case "getprop":
		if len(args) < 2 {
			fmt.Fprintf(os.Stderr, "Usage: apd getprop <key>\n")
			return
		}
		value, err := getprop(args[1])
		if err != nil {
			fmt.Printf("Error: %v\n", err)
			return
		}
		fmt.Printf("%s: %s\n", args[1], value)

	default:
		fmt.Fprintf(os.Stderr, "Error: Unknown command \"%s\"\n", args[0])
		flag.Usage()
	}
}
