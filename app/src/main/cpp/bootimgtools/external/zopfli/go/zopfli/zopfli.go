// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package zopfli provides a simple Go interface for Zopfli compression.
package zopfli

/*
#cgo LDFLAGS: -lzopfli -lm
#include <limits.h> // for INT_MAX
#include <stdlib.h> // for free()
#include <string.h> // for memmove()
#include "zopfli.h"
*/
import "C"
import "unsafe"

// Zopfli can't handle empty input, so we use a static result.
const emptyGzip = "\x1f\x8b\x08\x00\x00\x00\x00\x00\x00\xff\x03\x00\x00\x00\x00\x00\x00\x00\x00\x00"

// Gzip compresses data with Zopfli using default settings and gzip format.
// The Zopfli library does not return errors, and there are no (detectable)
// failure cases, hence no error return.
func Gzip(inputSlice []byte) []byte {
	var options C.struct_ZopfliOptions
	C.ZopfliInitOptions(&options)

	inputSize := (C.size_t)(len(inputSlice))
	if inputSize == 0 {
		return []byte(emptyGzip)
	}
	input := (*C.uchar)(unsafe.Pointer(&inputSlice[0]))
	var compressed *C.uchar
	var compressedLength C.size_t

	C.ZopfliCompress(&options, C.ZOPFLI_FORMAT_GZIP,
		input, inputSize,
		&compressed, &compressedLength)
	defer C.free(unsafe.Pointer(compressed))

	// GoBytes only accepts int, not C.size_t. The code below does the same minus
	// protection against zero-length values, but compressedLength is never 0 due
	// to headers.
	result := make([]byte, compressedLength)
	C.memmove(unsafe.Pointer(&result[0]), unsafe.Pointer(compressed),
		compressedLength)
	return result
}
