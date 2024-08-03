#!/bin/sh
# By SakuraKyuo

OUTFD=/proc/self/fd/$2

function ui_print() {
  echo -e "ui_print $1\nui_print" >> $OUTFD
}

function ui_printfile() {
  while IFS='' read -r line || $BB [[ -n "$line" ]]; do
    ui_print "$line";
  done < $1;
}

function kernelFlagsErr(){
	ui_print "- Installation has Aborted!"
	ui_print "- APatch requires CONFIG_KALLSYMS to be Enabled."
	ui_print "- But your kernel seems NOT enable it."
	exit
}

function apatchNote(){
	ui_print "- APatch Patch Done"
	ui_print "- APatch Key is $skey"
	ui_print "- We do have saved Origin Boot image to /data"
	ui_print "- If you encounter bootloop, reboot into Recovery and flash it"
	exit
}

function failed(){
	ui_printfile /dev/tmp/install/log
	ui_print "- APatch Patch Failed."
	ui_print "- Please feedback to the developer with the screenshots."
	exit
}

function boot_execute_ab(){
	./lib/arm64-v8a/libmagiskboot.so unpack boot.img
	if [[ ! "$(./assets/extract-ikconfig ./kernel | grep CONFIG_KALLSYMS= | cut -d = -f 2)" == "y" ]]; then
		kernelFlagsErr
	fi
	mv kernel kernel-origin
	./lib/arm64-v8a/libkptools.so -p --image kernel-origin --skey "$skey" --kpimg ./assets/kpimg --out ./kernel 2>&1 | tee /dev/tmp/install/log
	if [[ ! $(cat /dev/tmp/install/log | grep "patch done") ]]; then
		failed
	fi
	ui_printfile /dev/tmp/install/log
	./lib/arm64-v8a/libmagiskboot.so repack boot.img
	dd if=/dev/tmp/install/new-boot.img of=/dev/block/by-name/boot$slot
	mv boot.img /data/boot.img
	apatchNote
}

function boot_execute(){
	./lib/arm64-v8a/libmagiskboot.so unpack boot.img
	if [[ ! "$(./assets/extract-ikconfig ./kernel | grep CONFIG_KALLSYMS= | cut -d = -f 2)" == "y" ]]; then
		kernelFlagsErr
	fi
	mv kernel kernel-origin
	./lib/arm64-v8a/libkptools.so -p --image kernel-origin --skey "$skey" --kpimg ./assets/kpimg --out ./kernel 2>&1 | tee /dev/tmp/install/log
	if [[ ! $(cat /dev/tmp/install/log | grep "patch done") ]]; then
		failed
	fi
	ui_printfile /dev/tmp/install/log
	./lib/arm64-v8a/libmagiskboot.so repack boot.img
	dd if=/dev/tmp/install/new-boot.img of=/dev/block/by-name/boot$slot
	mv boot.img /data/boot.img
	apatchNote
}

function main(){

cd /dev/tmp/install

chmod a+x ./assets/kpimg
chmod a+x ./assets/extract-ikconfig
chmod a+x ./lib/arm64-v8a/libkptools.so
chmod a+x ./lib/arm64-v8a/libmagiskboot.so

slot=$(getprop ro.boot.slot_suffix)

skey=$(cat /proc/sys/kernel/random/uuid | cut -d \- -f1)

if [[ ! "$slot" == "" ]]; then

	ui_print ""
	ui_print "- You are using A/B device."

	# Script author
	ui_print "- Install Script by SakuraKyuo"

	# Get kernel
	ui_print ""
	dd if=/dev/block/by-name/boot$slot of=/dev/tmp/install/boot.img
	if [[ "$?" == 0 ]]; then
		ui_print "- Detected boot partition."
		boot_execute_ab
	fi

else

	ui_print "You are using A Only device."

	# Get kernel
	ui_print ""
	dd if=/dev/block/by-name/boot of=/dev/tmp/install/boot.img
	if [[ "$?" == 0 ]]; then
		ui_print "- Detected boot partition."
		boot_execute
	fi

fi

}

main