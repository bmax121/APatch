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
package zopflipng

import (
	"fmt"
)

/*
#cgo LDFLAGS: -lzopflipng -lzopfli -lstdc++ -lm
#include <stdlib.h>
#include <string.h>
#include "zopflipng_lib.h"
*/
import "C"
import "unsafe"

// Options allows overriding of some internal parameters.
type Options struct {
	LossyTransparent   bool
	Lossy8bit          bool
	NumIterations      int
	NumIterationsLarge int
}

// NewOptions creates an options struct with the default parameters.
func NewOptions() *Options {
	ret := &Options{
		LossyTransparent:   false,
		Lossy8bit:          false,
		NumIterations:      15,
		NumIterationsLarge: 5,
	}
	return ret
}

// Compress recompresses a PNG using Zopfli.
func Compress(inputSlice []byte) ([]byte, error) {
	return CompressWithOptions(inputSlice, NewOptions())
}

// CompressWithOptions allows overriding some internal parameters.
func CompressWithOptions(inputSlice []byte, options *Options) ([]byte, error) {
	cOptions := createCOptions(options)
	input := (*C.uchar)(unsafe.Pointer(&inputSlice[0]))
	inputSize := (C.size_t)(len(inputSlice))
	var compressed *C.uchar
	var compressedLength C.size_t
	errCode := int(C.CZopfliPNGOptimize(input, inputSize, &cOptions, 0, &compressed, &compressedLength))
	defer C.free(unsafe.Pointer(compressed))
	if errCode != 0 {
		return nil, fmt.Errorf("ZopfliPng failed with code: %d", errCode)
	}

	result := make([]byte, compressedLength)
	C.memmove(unsafe.Pointer(&result[0]), unsafe.Pointer(compressed), compressedLength)
	return result, nil
}

func createCOptions(options *Options) C.struct_CZopfliPNGOptions {
	var cOptions C.struct_CZopfliPNGOptions
	C.CZopfliPNGSetDefaults(&cOptions)
	cOptions.lossy_transparent = boolToInt(options.LossyTransparent)
	cOptions.lossy_8bit = boolToInt(options.Lossy8bit)
	cOptions.num_iterations = C.int(options.NumIterations)
	cOptions.num_iterations_large = C.int(options.NumIterationsLarge)
	return cOptions
}

func boolToInt(b bool) C.int {
	if b {
		return C.int(1)
	}
	return C.int(0)
}
