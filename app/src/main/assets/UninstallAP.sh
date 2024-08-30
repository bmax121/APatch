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

function apatchNote(){
	ui_print "- APatch Unpatch Done"
	exit
}

function failed(){
	ui_print "- APatch Unpatch Failed."
	ui_print "- Please feedback to the developer with the screenshots."
	exit
}

function boot_execute_ab(){
	./lib/arm64-v8a/libmagiskboot.so unpack boot.img
	mv kernel kernel-origin
	./lib/arm64-v8a/libkptools.so -u --image kernel-origin  --out ./kernel
	if [[ ! "$?" == 0 ]]; then
		failed
	fi
	./lib/arm64-v8a/libmagiskboot.so repack boot.img
	dd if=/dev/tmp/install/new-boot.img of=/dev/block/by-name/boot$slot
	apatchNote
}

function boot_execute(){
	./lib/arm64-v8a/libmagiskboot.so unpack boot.img
	mv kernel kernel-origin
	./lib/arm64-v8a/libkptools.so -u --image kernel-origin  --out ./kernel
	if [[ ! "$?" == 0 ]]; then
		failed
	fi
	./lib/arm64-v8a/libmagiskboot.so repack boot.img
	dd if=/dev/tmp/install/new-boot.img of=/dev/block/by-name/boot
	apatchNote
}

function main(){

cd /dev/tmp/install

chmod a+x ./lib/arm64-v8a/libkptools.so
chmod a+x ./lib/arm64-v8a/libmagiskboot.so

slot=$(getprop ro.boot.slot_suffix)

if [[ ! "$slot" == "" ]]; then

	ui_print ""
	ui_print "- You are using A/B device."

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