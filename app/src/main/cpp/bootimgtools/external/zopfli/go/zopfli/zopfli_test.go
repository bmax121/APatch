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
package zopfli

import (
	"bytes"
	"compress/gzip"
	"io/ioutil"
	"math/rand"
	"strings"
	"testing"
)

func getRandomBytes(length uint64) []byte {
	rng := rand.New(rand.NewSource(1)) // Make test repeatable.
	data := make([]byte, length)
	for i := uint64(0); i < length; i++ {
		data[i] = (byte)(rng.Int())
	}
	return data
}

// TestGzip verifies that Gzip compresses data correctly.
func TestGzip(t *testing.T) {
	compressibleString := "compressthis" + strings.Repeat("_foobar", 1000) + "$"

	for _, test := range []struct {
		name    string
		data    []byte
		maxSize int
	}{
		{"compressible string", []byte(compressibleString), 500},
		{"random binary data", getRandomBytes(3000), 3100},
		{"empty string", []byte(""), 20},
	} {
		compressed := Gzip(test.data)
		gzipReader, err := gzip.NewReader(bytes.NewReader(compressed))
		if err != nil {
			t.Errorf("%s: gzip.NewReader: got error %v, expected no error",
				test.name, err)
			continue
		}
		decompressed, err := ioutil.ReadAll(gzipReader)
		if err != nil {
			t.Errorf("%s: reading gzip stream: got error %v, expected no error",
				test.name, err)
			continue
		}
		if bytes.Compare(test.data, decompressed) != 0 {
			t.Errorf("%s: mismatch between input and decompressed data", test.name)
			continue
		}
		if test.maxSize > 0 && len(compressed) > test.maxSize {
			t.Errorf("%s: compressed data is %d bytes, expected %d or less",
				test.name, len(compressed), test.maxSize)
		}
	}
}
