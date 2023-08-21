/*
Copyright 2011 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Author: lode.vandevenne@gmail.com (Lode Vandevenne)
Author: jyrki.alakuijala@gmail.com (Jyrki Alakuijala)
*/

/*
Zopfli compressor program. It can output gzip-, zlib- or deflate-compatible
data. By default it creates a .gz file. This tool can only compress, not
decompress. Decompression can be done by any standard gzip, zlib or deflate
decompressor.
*/

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "deflate.h"
#include "gzip_container.h"
#include "zlib_container.h"

/* Windows workaround for stdout output. */
#if _WIN32
#include <fcntl.h>
#endif

/*
Loads a file into a memory array. Returns 1 on success, 0 if file doesn't exist
or couldn't be opened.
*/
static int LoadFile(const char* filename,
                    unsigned char** out, size_t* outsize) {
  FILE* file;

  *out = 0;
  *outsize = 0;
  file = fopen(filename, "rb");
  if (!file) return 0;

  fseek(file , 0 , SEEK_END);
  *outsize = ftell(file);
  if(*outsize > 2147483647) {
    fprintf(stderr,"Files larger than 2GB are not supported.\n");
    exit(EXIT_FAILURE);
  }
  rewind(file);

  *out = (unsigned char*)malloc(*outsize);

  if (*outsize && (*out)) {
    size_t testsize = fread(*out, 1, *outsize, file);
    if (testsize != *outsize) {
      /* It could be a directory */
      free(*out);
      *out = 0;
      *outsize = 0;
      fclose(file);
      return 0;
    }
  }

  assert(!(*outsize) || out);  /* If size is not zero, out must be allocated. */
  fclose(file);
  return 1;
}

/*
Saves a file from a memory array, overwriting the file if it existed.
*/
static void SaveFile(const char* filename,
                     const unsigned char* in, size_t insize) {
  FILE* file = fopen(filename, "wb" );
  if (file == NULL) {
      fprintf(stderr,"Error: Cannot write to output file, terminating.\n");
      exit (EXIT_FAILURE);
  }
  assert(file);
  fwrite((char*)in, 1, insize, file);
  fclose(file);
}

/*
outfilename: filename to write output to, or 0 to write to stdout instead
*/
static void CompressFile(const ZopfliOptions* options,
                         ZopfliFormat output_type,
                         const char* infilename,
                         const char* outfilename) {
  unsigned char* in;
  size_t insize;
  unsigned char* out = 0;
  size_t outsize = 0;
  if (!LoadFile(infilename, &in, &insize)) {
    fprintf(stderr, "Invalid filename: %s\n", infilename);
    return;
  }

  ZopfliCompress(options, output_type, in, insize, &out, &outsize);

  if (outfilename) {
    SaveFile(outfilename, out, outsize);
  } else {
#if _WIN32
    /* Windows workaround for stdout output. */
    _setmode(_fileno(stdout), _O_BINARY);
#endif
    fwrite(out, 1, outsize, stdout);
  }

  free(out);
  free(in);
}

/*
Add two strings together. Size does not matter. Result must be freed.
*/
static char* AddStrings(const char* str1, const char* str2) {
  size_t len = strlen(str1) + strlen(str2);
  char* result = (char*)malloc(len + 1);
  if (!result) exit(-1); /* Allocation failed. */
  strcpy(result, str1);
  strcat(result, str2);
  return result;
}

static char StringsEqual(const char* str1, const char* str2) {
  return strcmp(str1, str2) == 0;
}

int main(int argc, char* argv[]) {
  ZopfliOptions options;
  ZopfliFormat output_type = ZOPFLI_FORMAT_GZIP;
  const char* filename = 0;
  int output_to_stdout = 0;
  int i;

  ZopfliInitOptions(&options);

  for (i = 1; i < argc; i++) {
    const char* arg = argv[i];
    if (StringsEqual(arg, "-v")) options.verbose = 1;
    else if (StringsEqual(arg, "-c")) output_to_stdout = 1;
    else if (StringsEqual(arg, "--deflate")) {
      output_type = ZOPFLI_FORMAT_DEFLATE;
    }
    else if (StringsEqual(arg, "--zlib")) output_type = ZOPFLI_FORMAT_ZLIB;
    else if (StringsEqual(arg, "--gzip")) output_type = ZOPFLI_FORMAT_GZIP;
    else if (StringsEqual(arg, "--splitlast"))  /* Ignore */;
    else if (arg[0] == '-' && arg[1] == '-' && arg[2] == 'i'
        && arg[3] >= '0' && arg[3] <= '9') {
      options.numiterations = atoi(arg + 3);
    }
    else if (StringsEqual(arg, "-h")) {
      fprintf(stderr,
          "Usage: zopfli [OPTION]... FILE...\n"
          "  -h    gives this help\n"
          "  -c    write the result on standard output, instead of disk"
          " filename + '.gz'\n"
          "  -v    verbose mode\n"
          "  --i#  perform # iterations (default 15). More gives"
          " more compression but is slower."
          " Examples: --i10, --i50, --i1000\n");
      fprintf(stderr,
          "  --gzip        output to gzip format (default)\n"
          "  --zlib        output to zlib format instead of gzip\n"
          "  --deflate     output to deflate format instead of gzip\n"
          "  --splitlast   ignored, left for backwards compatibility\n");
      return 0;
    }
  }

  if (options.numiterations < 1) {
    fprintf(stderr, "Error: must have 1 or more iterations\n");
    return 0;
  }

  for (i = 1; i < argc; i++) {
    if (argv[i][0] != '-') {
      char* outfilename;
      filename = argv[i];
      if (output_to_stdout) {
        outfilename = 0;
      } else if (output_type == ZOPFLI_FORMAT_GZIP) {
        outfilename = AddStrings(filename, ".gz");
      } else if (output_type == ZOPFLI_FORMAT_ZLIB) {
        outfilename = AddStrings(filename, ".zlib");
      } else {
        assert(output_type == ZOPFLI_FORMAT_DEFLATE);
        outfilename = AddStrings(filename, ".deflate");
      }
      if (options.verbose && outfilename) {
        fprintf(stderr, "Saving to: %s\n", outfilename);
      }
      CompressFile(&options, output_type, filename, outfilename);
      free(outfilename);
    }
  }

  if (!filename) {
    fprintf(stderr,
            "Please provide filename\nFor help, type: %s -h\n", argv[0]);
  }

  return 0;
}
