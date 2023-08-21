// Copyright 2013 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Author: lode.vandevenne@gmail.com (Lode Vandevenne)
// Author: jyrki.alakuijala@gmail.com (Jyrki Alakuijala)

// Command line tool to recompress and optimize PNG images, using zopflipng_lib.

#include <stdlib.h>
#include <stdio.h>

#include "lodepng/lodepng.h"
#include "lodepng/lodepng_util.h"
#include "zopflipng_lib.h"

// Returns directory path (including last slash) in dir, filename without
// extension in file, extension (including the dot) in ext
void GetFileNameParts(const std::string& filename,
    std::string* dir, std::string* file, std::string* ext) {
  size_t npos = (size_t)(-1);
  size_t slashpos = filename.find_last_of("/\\");
  std::string nodir;
  if (slashpos == npos) {
    *dir = "";
    nodir = filename;
  } else {
    *dir = filename.substr(0, slashpos + 1);
    nodir = filename.substr(slashpos + 1);
  }
  size_t dotpos = nodir.find_last_of('.');
  if (dotpos == (size_t)(-1)) {
    *file = nodir;
    *ext = "";
  } else {
    *file = nodir.substr(0, dotpos);
    *ext = nodir.substr(dotpos);
  }
}

// Returns whether the file exists and we have read permissions.
bool FileExists(const std::string& filename) {
  FILE* file = fopen(filename.c_str(), "rb");
  if (file) {
    fclose(file);
    return true;
  }
  return false;
}

// Returns the size of the file, if it exists and we have read permissions.
size_t GetFileSize(const std::string& filename) {
  size_t size;
  FILE* file = fopen(filename.c_str(), "rb");
  if (!file) return 0;
  fseek(file , 0 , SEEK_END);
  size = static_cast<size_t>(ftell(file));
  fclose(file);
  return size;
}

void ShowHelp() {
  printf("ZopfliPNG, a Portable Network Graphics (PNG) image optimizer.\n"
         "\n"
         "Usage: zopflipng [options]... infile.png outfile.png\n"
         "       zopflipng [options]... --prefix=[fileprefix] [files.png]...\n"
         "\n"
         "If the output file exists, it is considered a result from a"
         " previous run and not overwritten if its filesize is smaller.\n"
         "\n"
         "Options:\n"
         "-m: compress more: use more iterations (depending on file size)\n"
         "--prefix=[fileprefix]: Adds a prefix to output filenames. May also"
         " contain a directory path. When using a prefix, multiple input files"
         " can be given and the output filenames are generated with the"
         " prefix\n"
         " If --prefix is specified without value, 'zopfli_' is used.\n"
         " If input file names contain the prefix, they are not processed but"
         " considered as output from previous runs. This is handy when using"
         " *.png wildcard expansion with multiple runs.\n"
         "-y: do not ask about overwriting files.\n"
         "--lossy_transparent: remove colors behind alpha channel 0. No visual"
         " difference, removes hidden information.\n"
         "--lossy_8bit: convert 16-bit per channel image to 8-bit per"
         " channel.\n"
         "-d: dry run: don't save any files, just see the console output"
         " (e.g. for benchmarking)\n"
         "--always_zopflify: always output the image encoded by Zopfli, even if"
         " it's bigger than the original, for benchmarking the algorithm. Not"
         " good for real optimization.\n"
         "-q: use quick, but not very good, compression"
         " (e.g. for only trying the PNG filter and color types)\n"
         "--iterations=[number]: number of iterations, more iterations makes it"
         " slower but provides slightly better compression. Default: 15 for"
         " small files, 5 for large files.\n"
         "--splitting=[0-3]: ignored, left for backwards compatibility\n"
         "--filters=[types]: filter strategies to try:\n"
         " 0-4: give all scanlines PNG filter type 0-4\n"
         " m: minimum sum\n"
         " e: entropy\n"
         " p: predefined (keep from input, this likely overlaps another"
         " strategy)\n"
         " b: brute force (experimental)\n"
         " By default, if this argument is not given, one that is most likely"
         " the best for this image is chosen by trying faster compression with"
         " each type.\n"
         " If this argument is used, all given filter types"
         " are tried with slow compression and the best result retained. A good"
         " set of filters to try is --filters=0me.\n"
         "--keepchunks=nAME,nAME,...: keep metadata chunks with these names"
         " that would normally be removed, e.g. tEXt,zTXt,iTXt,gAMA, ... \n"
         " Due to adding extra data, this increases the result size. Keeping"
         " bKGD or sBIT chunks may cause additional worse compression due to"
         " forcing a certain color type, it is advised to not keep these for"
         " web images because web browsers do not use these chunks. By default"
         " ZopfliPNG only keeps (and losslessly modifies) the following chunks"
         " because they are essential: IHDR, PLTE, tRNS, IDAT and IEND.\n"
         "--keepcolortype: Keep original color type (RGB, RGBA, gray,"
         " gray+alpha or palette) and bit depth of the PNG.\n"
         " This results in a loss of compression opportunities, e.g. it will no"
         " longer convert a 4-channel RGBA image to 2-channel gray+alpha if the"
         " image only had translucent gray pixels.\n"
         " May be useful if a device does not support decoding PNGs of a"
         " particular color type.\n"
         "\n"
         "Usage examples:\n"
         "Optimize a file and overwrite if smaller: zopflipng infile.png"
         " outfile.png\n"
         "Compress more: zopflipng -m infile.png outfile.png\n"
         "Optimize multiple files: zopflipng --prefix a.png b.png c.png\n"
         "Compress really good and trying all filter strategies: zopflipng"
         " --iterations=500 --filters=01234mepb --lossy_8bit"
         " --lossy_transparent infile.png outfile.png\n");
}

void PrintSize(const char* label, size_t size) {
  printf("%s: %d (%dK)\n", label, (int) size, (int) size / 1024);
}

void PrintResultSize(const char* label, size_t oldsize, size_t newsize) {
  printf("%s: %d (%dK). Percentage of original: %.3f%%\n",
         label, (int) newsize, (int) newsize / 1024, newsize * 100.0 / oldsize);
}

int main(int argc, char *argv[]) {
  if (argc < 2) {
    ShowHelp();
    return 0;
  }

  ZopfliPNGOptions png_options;

  // cmd line options
  bool always_zopflify = false;  // overwrite file even if we have bigger result
  bool yes = false;  // do not ask to overwrite files
  bool dryrun = false;  // never save anything

  std::string user_out_filename;  // output filename if no prefix is used
  bool use_prefix = false;
  std::string prefix = "zopfli_";  // prefix for output filenames

  std::vector<std::string> files;
  for (int i = 1; i < argc; i++) {
    std::string arg = argv[i];
    if (arg[0] == '-' && arg.size() > 1 && arg[1] != '-') {
      for (size_t pos = 1; pos < arg.size(); pos++) {
        char c = arg[pos];
        if (c == 'y') {
          yes = true;
        } else if (c == 'd') {
          dryrun = true;
        } else if (c == 'm') {
          png_options.num_iterations *= 4;
          png_options.num_iterations_large *= 4;
        } else if (c == 'q') {
          png_options.use_zopfli = false;
        } else if (c == 'h') {
          ShowHelp();
          return 0;
        } else {
          printf("Unknown flag: %c\n", c);
          return 0;
        }
      }
    } else if (arg[0] == '-' && arg.size() > 1 && arg[1] == '-') {
      size_t eq = arg.find('=');
      std::string name = arg.substr(0, eq);
      std::string value = eq >= arg.size() - 1 ? "" : arg.substr(eq + 1);
      int num = atoi(value.c_str());
      if (name == "--always_zopflify") {
        always_zopflify = true;
      } else if (name == "--verbose") {
        png_options.verbose = true;
      } else if (name == "--lossy_transparent") {
        png_options.lossy_transparent = true;
      } else if (name == "--lossy_8bit") {
        png_options.lossy_8bit = true;
      } else if (name == "--iterations") {
        if (num < 1) num = 1;
        png_options.num_iterations = num;
        png_options.num_iterations_large = num;
      } else if (name == "--splitting") {
        // ignored
      } else if (name == "--filters") {
        for (size_t j = 0; j < value.size(); j++) {
          ZopfliPNGFilterStrategy strategy = kStrategyZero;
          char f = value[j];
          switch (f) {
            case '0': strategy = kStrategyZero; break;
            case '1': strategy = kStrategyOne; break;
            case '2': strategy = kStrategyTwo; break;
            case '3': strategy = kStrategyThree; break;
            case '4': strategy = kStrategyFour; break;
            case 'm': strategy = kStrategyMinSum; break;
            case 'e': strategy = kStrategyEntropy; break;
            case 'p': strategy = kStrategyPredefined; break;
            case 'b': strategy = kStrategyBruteForce; break;
            default:
              printf("Unknown filter strategy: %c\n", f);
              return 1;
          }
          png_options.filter_strategies.push_back(strategy);
          // Enable auto filter strategy only if no user-specified filter is
          // given.
          png_options.auto_filter_strategy = false;
        }
      } else if (name == "--keepchunks") {
        bool correct = true;
        if ((value.size() + 1) % 5 != 0) correct = false;
        for (size_t i = 0; i + 4 <= value.size() && correct; i += 5) {
          png_options.keepchunks.push_back(value.substr(i, 4));
          if (i > 4 && value[i - 1] != ',') correct = false;
        }
        if (!correct) {
          printf("Error: keepchunks format must be like for example:\n"
                 " --keepchunks=gAMA,cHRM,sRGB,iCCP\n");
          return 0;
        }
      } else if (name == "--keepcolortype") {
        png_options.keep_colortype = true;
      } else if (name == "--prefix") {
        use_prefix = true;
        if (!value.empty()) prefix = value;
      } else if (name == "--help") {
        ShowHelp();
        return 0;
      } else {
        printf("Unknown flag: %s\n", name.c_str());
        return 0;
      }
    } else {
      files.push_back(argv[i]);
    }
  }

  if (!use_prefix) {
    if (files.size() == 2) {
      // The second filename is the output instead of an input if no prefix is
      // given.
      user_out_filename = files[1];
      files.resize(1);
    } else {
      printf("Please provide one input and output filename\n\n");
      ShowHelp();
      return 0;
    }
  }

  size_t total_in_size = 0;
  // Total output size, taking input size if the input file was smaller
  size_t total_out_size = 0;
  // Total output size that zopfli produced, even if input was smaller, for
  // benchmark information
  size_t total_out_size_zopfli = 0;
  size_t total_errors = 0;
  size_t total_files = 0;
  size_t total_files_smaller = 0;
  size_t total_files_saved = 0;
  size_t total_files_equal = 0;

  for (size_t i = 0; i < files.size(); i++) {
    if (use_prefix && files.size() > 1) {
      std::string dir, file, ext;
      GetFileNameParts(files[i], &dir, &file, &ext);
      // avoid doing filenames which were already output by this so that you
      // don't get zopfli_zopfli_zopfli_... files after multiple runs.
      if (file.find(prefix) == 0) continue;
    }

    total_files++;

    printf("Optimizing %s\n", files[i].c_str());
    std::vector<unsigned char> image;
    unsigned w, h;
    std::vector<unsigned char> origpng;
    unsigned error;
    lodepng::State inputstate;
    std::vector<unsigned char> resultpng;

    error = lodepng::load_file(origpng, files[i]);
    if (!error) {
      error = ZopfliPNGOptimize(origpng, png_options,
                                png_options.verbose, &resultpng);
    }

    if (error) {
      if (error == 1) {
        printf("Decoding error\n");
      } else {
        printf("Decoding error %u: %s\n", error, lodepng_error_text(error));
      }
    }

    // Verify result, check that the result causes no decoding errors
    if (!error) {
      error = lodepng::decode(image, w, h, resultpng);
      if (!error) {
        std::vector<unsigned char> origimage;
        unsigned origw, origh;
        lodepng::decode(origimage, origw, origh, origpng);
        if (origw != w || origh != h || origimage.size() != image.size()) {
          error = 1;
        } else {
          for (size_t i = 0; i < image.size(); i += 4) {
            bool same_alpha = image[i + 3] == origimage[i + 3];
            bool same_rgb =
                (png_options.lossy_transparent && image[i + 3] == 0) ||
                (image[i + 0] == origimage[i + 0] &&
                 image[i + 1] == origimage[i + 1] &&
                 image[i + 2] == origimage[i + 2]);
            if (!same_alpha || !same_rgb) {
              error = 1;
              break;
            }
          }
        }
      }
      if (error) {
        printf("Error: verification of result failed, keeping original."
               " Error: %u.\n", error);
        // Reset the error to 0, instead set output back to the original. The
        // input PNG is valid, zopfli failed on it so treat as if it could not
        // make it smaller.
        error = 0;
        resultpng = origpng;
      }
    }

    if (error) {
      total_errors++;
    } else {
      size_t origsize = origpng.size();
      size_t resultsize = resultpng.size();

      if (!png_options.keepchunks.empty()) {
        std::vector<std::string> names;
        std::vector<size_t> sizes;
        lodepng::getChunkInfo(names, sizes, resultpng);
        for (size_t i = 0; i < names.size(); i++) {
          if (names[i] == "bKGD" || names[i] == "sBIT") {
            printf("Forced to keep original color type due to keeping bKGD or"
                   " sBIT chunk. Try without --keepchunks for better"
                   " compression.\n");
            break;
          }
        }
      }

      PrintSize("Input size", origsize);
      PrintResultSize("Result size", origsize, resultsize);
      if (resultsize < origsize) {
        printf("Result is smaller\n");
      } else if (resultsize == origsize) {
        printf("Result has exact same size\n");
      } else {
        printf(always_zopflify
            ? "Original was smaller\n"
            : "Preserving original PNG since it was smaller\n");
      }

      std::string out_filename = user_out_filename;
      if (use_prefix) {
        std::string dir, file, ext;
        GetFileNameParts(files[i], &dir, &file, &ext);
        out_filename = dir + prefix + file + ext;
      }
      bool different_output_name = out_filename != files[i];

      total_in_size += origsize;
      total_out_size_zopfli += resultpng.size();
      if (resultpng.size() < origsize) total_files_smaller++;
      else if (resultpng.size() == origsize) total_files_equal++;

      if (!always_zopflify && resultpng.size() >= origsize) {
        // Set output file to input since zopfli didn't improve it.
        resultpng = origpng;
      }

      bool already_exists = FileExists(out_filename);
      size_t origoutfilesize = GetFileSize(out_filename);

      // When using a prefix, and the output file already exist, assume it's
      // from a previous run. If that file is smaller, it may represent a
      // previous run with different parameters that gave a smaller PNG image.
      // This also applies when not using prefix but same input as output file.
      // In that case, do not overwrite it. This behaviour can be removed by
      // adding the always_zopflify flag.
      bool keep_earlier_output_file = already_exists &&
          resultpng.size() >= origoutfilesize && !always_zopflify &&
          (use_prefix || !different_output_name);

      if (keep_earlier_output_file) {
        // An output file from a previous run is kept, add that files' size
        // to the output size statistics.
        total_out_size += origoutfilesize;
        if (use_prefix) {
          printf(resultpng.size() == origoutfilesize
              ? "File not written because a previous run was as good.\n"
              : "File not written because a previous run was better.\n");
        }
      } else {
        bool confirmed = true;
        if (!yes && !dryrun && already_exists) {
          printf("File %s exists, overwrite? (y/N) ", out_filename.c_str());
          char answer = 0;
          // Read the first character, the others and enter with getchar.
          while (int input = getchar()) {
            if (input == '\n' || input == EOF) break;
            else if (!answer) answer = input;
          }
          confirmed = answer == 'y' || answer == 'Y';
        }
        if (confirmed) {
          if (!dryrun) {
            if (lodepng::save_file(resultpng, out_filename) != 0) {
              printf("Failed to write to file %s\n", out_filename.c_str());
            } else {
              total_files_saved++;
            }
          }
          total_out_size += resultpng.size();
        } else {
          // An output file from a previous run is kept, add that files' size
          // to the output size statistics.
          total_out_size += origoutfilesize;
        }
      }
    }
    printf("\n");
  }

  if (total_files > 1) {
    printf("Summary for all files:\n");
    printf("Files tried: %d\n", (int) total_files);
    printf("Files smaller: %d\n", (int) total_files_smaller);
    if (total_files_equal) {
      printf("Files equal: %d\n", (int) total_files_equal);
    }
    printf("Files saved: %d\n", (int) total_files_saved);
    if (total_errors) printf("Errors: %d\n", (int) total_errors);
    PrintSize("Total input size", total_in_size);
    PrintResultSize("Total output size", total_in_size, total_out_size);
    PrintResultSize("Benchmark result size",
                    total_in_size, total_out_size_zopfli);
  }

  if (dryrun) printf("No files were written because dry run was specified\n");

  return total_errors;
}
