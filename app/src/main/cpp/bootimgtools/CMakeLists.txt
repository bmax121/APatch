
cmake_minimum_required(VERSION 3.9)
project(bootimgtools)


if(NOT CMAKE_BUILD_TYPE)
	set(CMAKE_BUILD_TYPE "Debug")
endif()

option(DEBUG "Build Debug" ON)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_FLAGS  "${CMAKE_C_FLAGS} -std=c11")
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")

# file(MAKE_DIRECTORY "build" "out/bin" "out/lib")

set(PROJ_ROOT "${CMAKE_CURRENT_LIST_DIR}")

add_subdirectory(external)

include_directories(base/include)

add_library(
	base
	STATIC
	base/files.cpp
	base/misc.cpp
	base/logging.cpp
	base/stream.cpp
	base/xwrap.cpp
	base/new.cpp
)

set(SOURCE
	boot/bootimg.cpp
	boot/compress.cpp
	boot/cpio.cpp
	boot/dtb.cpp
	boot/format.cpp
	boot/hexpatch.cpp
	boot/main.cpp
	boot/pattern.cpp
	boot/ramdisk.cpp
)

add_executable(bootimgtools ${SOURCE})

target_link_libraries(bootimgtools
	base 
	libmincrypt 
	liblzma 
	liblz4 
	libbz2 
	libfdt 
	libz 
	libzopfli 
)

if(${CMAKE_SYSTEM_NAME} STREQUAL "Android")
	set_target_properties(bootimgtools PROPERTIES OUTPUT_NAME "bootimgtools.exe.so")
	set_target_properties(bootimgtools PROPERTIES RUNTIME_OUTPUT_DIRECTORY ${PROJECT_SOURCE_DIR}/../../jniLibs/arm64-v8a)
endif()