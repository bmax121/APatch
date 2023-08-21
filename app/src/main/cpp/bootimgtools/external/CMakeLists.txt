
cmake_minimum_required(VERSION 3.9)
project(ext)

add_library(
	libfdt
	STATIC
	dtc/libfdt/fdt.c
	dtc/libfdt/fdt_addresses.c
	dtc/libfdt/fdt_empty_tree.c
	dtc/libfdt/fdt_overlay.c
	dtc/libfdt/fdt_ro.c
	dtc/libfdt/fdt_rw.c
	dtc/libfdt/fdt_strerror.c
	dtc/libfdt/fdt_sw.c
	dtc/libfdt/fdt_wip.c
)
target_include_directories(libfdt PUBLIC dtc/libfdt)

add_library(
	liblz4
	STATIC
	lz4/lib/lz4.c
	lz4/lib/lz4frame.c
	lz4/lib/lz4hc.c
	lz4/lib/xxhash.c
)
target_include_directories(liblz4 PUBLIC lz4/lib)

add_library(
	libbz2
	STATIC
	bzip2/blocksort.c
	bzip2/huffman.c
	bzip2/crctable.c
	bzip2/randtable.c
	bzip2/compress.c
	bzip2/decompress.c
	bzip2/bzlib.c
)
target_include_directories(libbz2 PUBLIC bzip2)

# liblzma.a
add_subdirectory(xz)

add_library(
	libz
	STATIC
	zlib/adler32.c
	zlib/compress.c
	zlib/cpu_features.c
	zlib/crc32.c
	zlib/deflate.c
	zlib/gzclose.c
	zlib/gzlib.c
	zlib/gzread.c
	zlib/gzwrite.c
	zlib/infback.c
	zlib/inffast.c
	zlib/inflate.c
	zlib/inftrees.c
	zlib/trees.c
	zlib/uncompr.c
	zlib/zutil.c
)
target_include_directories(libz PUBLIC zlib)
target_compile_options(libz PRIVATE -DHAVE_HIDDEN -DZLIB_CONST -Wall)

add_library(
	libzopfli
	STATIC
	zopfli/src/zopfli/blocksplitter.c
	zopfli/src/zopfli/cache.c
	zopfli/src/zopfli/deflate.c
	zopfli/src/zopfli/gzip_container.c
	zopfli/src/zopfli/hash.c
	zopfli/src/zopfli/katajainen.c
	zopfli/src/zopfli/lz77.c
	zopfli/src/zopfli/squeeze.c
	zopfli/src/zopfli/tree.c
	zopfli/src/zopfli/util.c
	zopfli/src/zopfli/zlib_container.c
	zopfli/src/zopfli/zopfli_lib.c
)
target_include_directories(libzopfli PUBLIC zopfli/src)
target_compile_options(libzopfli PRIVATE -Wall -Werror -Wno-unused -Wno-unused-parameter)

add_library(
	libmincrypt
	STATIC
	mincrypt/dsa_sig.c
	mincrypt/p256.c
	mincrypt/p256_ec.c
	mincrypt/p256_ecdsa.c
	mincrypt/rsa.c
	mincrypt/sha.c
	mincrypt/sha256.c
)
target_include_directories(libmincrypt PUBLIC mincrypt/include)
