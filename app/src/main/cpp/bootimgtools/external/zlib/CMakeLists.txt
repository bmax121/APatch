# Copyright 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Build zlib for Windows x86_64 using MSVC.
#

project(libz LANGUAGES C)
cmake_minimum_required(VERSION 3.18.1)

add_library(libz STATIC
  # Optimizations for x86-64
  adler32_simd.c
  crc32_simd.c
  crc_folding.c

  # Common sources
  adler32.c
  compress.c
  cpu_features.c
  crc32.c
  deflate.c
  gzclose.c
  gzlib.c
  gzread.c
  gzwrite.c
  infback.c
  inffast.c
  inflate.c
  inftrees.c
  trees.c
  uncompr.c
  zutil.c
)

target_compile_definitions(libz PRIVATE
  # Enable optimizations: cpu_features.c will enable them at runtime using __cpuid.
  ADLER32_SIMD_SSSE3
  CRC32_SIMD_SSE42_PCLMUL
  INFLATE_CHUNK_READ_64LE

  X86_WINDOWS
  ZLIB_CONST
)

set(out ${CMAKE_CURRENT_BINARY_DIR})
configure_file(zconf.h  ${out}/dist/include/zconf.h   COPYONLY)
configure_file(zlib.h   ${out}/dist/include/zlib.h    COPYONLY)
