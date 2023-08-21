#!/bin/sh
#
#############################################################################
#
# Script meant to be used for Continuous Integration automation for POSIX
# systems. On GitHub, this is used by Ubuntu and MacOS builds.
#
#############################################################################
#
# Author: Jia Tan
#
# This file has been put into the public domain.
# You can do whatever you want with this file.
#
#############################################################################

set -e

USAGE="Usage: $0 -b [autotools|cmake] -c [crc32|crc64|sha256] -d [encoders,decoders,bcj,delta,threads] -l [destdir] -s [srcdir]"

# Absolute path of script directory
ABS_DIR=$(cd -- "$(dirname -- "$0")" && pwd)

# Default CLI option values
BUILD_SYSTEM="autotools"
CHECK_TYPE="crc32,crc64,sha256"
BCJ="y"
DELTA="y"
ENCODERS="y"
DECODERS="y"
THREADS="y"
SRC_DIR="$ABS_DIR/../"
DEST_DIR="$SRC_DIR/../xz_build"

# Parse arguments
while getopts b:c:d:l:s: opt; do
	# b option can have either value "autotools" OR "cmake"
	case ${opt} in
	b)
		case "$OPTARG" in
			autotools) ;;
			cmake) ;;
			*) echo "Invalid build system: $OPTARG"; exit 1;;
		esac
		BUILD_SYSTEM="$OPTARG"
	;;
	# c options can be a comma separated list of check types to support
	c)
	for crc in $(echo $OPTARG | sed "s/,/ /g"); do 
		case "$crc" in
		crc32) ;;
		crc64) ;;
		sha256) ;;
		*) echo "Invalid check type: $crc"; exit 1 ;;
		esac
	done
	CHECK_TYPE="$OPTARG"
	;;
	# d options can be a comma separated list of things to disable at
	# configure time
	d)
	for disable_arg in $(echo $OPTARG | sed "s/,/ /g"); do 
		case "$disable_arg" in
		encoders) ENCODERS="n" ;;
		decoders) DECODERS="n" ;;
		bcj) BCJ="n" ;;
		delta) DELTA="n" ;;
		threads) THREADS="n" ;;
		*) echo "Invalid disable value: $disable_arg"; exit 1 ;;
		esac
	done	
	;;
	l) DEST_DIR="$OPTARG"
	;;
	s) SRC_DIR="$OPTARG"
	;;
	esac
done

# Build based on arguments
mkdir -p "$DEST_DIR"
case $BUILD_SYSTEM in
	autotools)
	# Run autogen.sh script
	cd "$SRC_DIR"
	"./autogen.sh"
	cd "$DEST_DIR"
	# Generate configure option values

	EXTRA_OPTIONS=""
	FILTER_LIST="lzma1,lzma2"

	if [ "$BCJ" = "y" ]
	then
		FILTER_LIST="$FILTER_LIST,x86,powerpc,ia64,arm,armthumb,arm64,sparc"
	fi

	if [ "$DELTA" = "y" ]
	then
		FILTER_LIST="$FILTER_LIST,delta"
	fi

	if [ "$ENCODERS" = "y" ]
	then
		EXTRA_OPTIONS="$EXTRA_OPTIONS --enable-encoders=$FILTER_LIST"
	else
		EXTRA_OPTIONS="$EXTRA_OPTIONS --disable-encoders"
	fi

	if [ "$DECODERS" = "y" ]
	then
		EXTRA_OPTIONS="$EXTRA_OPTIONS --enable-decoders=$FILTER_LIST"
	else
		EXTRA_OPTIONS="$EXTRA_OPTIONS --disable-decoders"
	fi

	if [ "$THREADS" = "n" ]
	then
		EXTRA_OPTIONS="$EXTRA_OPTIONS --disable-threads"
	fi

	# Run configure script
	"$SRC_DIR"/configure --enable-checks=$CHECK_TYPE $EXTRA_OPTIONS

	# Build the project
	make

	# Run the tests
	make check
	;;

	cmake)
	# CMake currently does not support disabling encoders, decoders,
	# threading, or check types. For now, just run the full build.
	cd "$DEST_DIR"
	cmake "$SRC_DIR/CMakeLists.txt" -B "$DEST_DIR"
	make
	make test
	;;

esac
