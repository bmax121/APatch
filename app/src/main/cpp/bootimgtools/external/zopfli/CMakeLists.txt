cmake_minimum_required(VERSION 2.8.11)

project(Zopfli)

# Check if Zopfli is the top-level project (standalone), or a subproject
set(zopfli_standalone FALSE)
get_directory_property(zopfli_parent_directory PARENT_DIRECTORY)
if(zopfli_parent_directory STREQUAL "")
  set(zopfli_standalone TRUE)
endif()
unset(zopfli_parent_directory)

#
# Options
#

# ZOPFLI_BUILD_SHARED controls if Zopfli libraries are built as shared or
# static
#
# It defaults to the value of BUILD_SHARED_LIBS if set, and in most cases
# that should be used instead. The purpose of ZOPFLI_BUILD_SHARED is to allow
# overriding it when built as a subproject.
set(zopfli_shared_default OFF)
if(DEFINED BUILD_SHARED_LIBS)
  set(zopfli_shared_default ${BUILD_SHARED_LIBS})
endif()
option(ZOPFLI_BUILD_SHARED "Build Zopfli with shared libraries" ${zopfli_shared_default})
unset(zopfli_shared_default)

# ZOPFLI_BUILD_INSTALL controls if Zopfli adds an install target to the build
#
# When built standalone or as a shared library subproject, the default is ON,
# and for static library subproject the default is OFF.
if(zopfli_standalone OR ZOPFLI_BUILD_SHARED)
  option(ZOPFLI_BUILD_INSTALL "Add Zopfli install target" ON)
else()
  option(ZOPFLI_BUILD_INSTALL "Add Zopfli install target" OFF)
endif()

# ZOPFLI_DEFAULT_RELEASE enables changing empty build type to Release
#
# Make based single-configuration generators default to an empty build type,
# which might be surprising, but could be useful if you want full control over
# compiler and linker flags. When ZOPFLI_DEFAULT_RELEASE is ON, change an
# empty default build type to Release.
option(ZOPFLI_DEFAULT_RELEASE "If CMAKE_BUILD_TYPE is empty, default to Release" ON)

if(zopfli_standalone AND ZOPFLI_DEFAULT_RELEASE)
  if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    message(STATUS "CMAKE_BUILD_TYPE empty, defaulting to Release")
    set(CMAKE_BUILD_TYPE Release)
  endif()
endif()

#
# Library version
#
set(ZOPFLI_VERSION_MAJOR 1)
set(ZOPFLI_VERSION_MINOR 0)
set(ZOPFLI_VERSION_PATCH 3)
set(ZOPFLI_VERSION ${ZOPFLI_VERSION_MAJOR}.${ZOPFLI_VERSION_MINOR}.${ZOPFLI_VERSION_PATCH})

if(ZOPFLI_BUILD_SHARED)
  set(zopfli_library_type SHARED)
else()
  set(zopfli_library_type STATIC)
endif()

include(GNUInstallDirs)

#
# libzopfli
#
add_library(libzopfli ${zopfli_library_type}
  src/zopfli/blocksplitter.c
  src/zopfli/cache.c
  src/zopfli/deflate.c
  src/zopfli/gzip_container.c
  src/zopfli/hash.c
  src/zopfli/katajainen.c
  src/zopfli/lz77.c
  src/zopfli/squeeze.c
  src/zopfli/tree.c
  src/zopfli/util.c
  src/zopfli/zlib_container.c
  src/zopfli/zopfli_lib.c
)
target_include_directories(libzopfli
  INTERFACE
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/src/zopfli>
    $<INSTALL_INTERFACE:${CMAKE_INSTALL_INCLUDEDIR}>
)
set_target_properties(libzopfli PROPERTIES
  OUTPUT_NAME zopfli
  VERSION ${ZOPFLI_VERSION}
  SOVERSION ${ZOPFLI_VERSION_MAJOR}
)
if(UNIX AND NOT (BEOS OR HAIKU))
  target_link_libraries(libzopfli m)
endif()

#
# libzopflipng
#
add_library(libzopflipng ${zopfli_library_type}
  src/zopflipng/zopflipng_lib.cc
  src/zopflipng/lodepng/lodepng.cpp
  src/zopflipng/lodepng/lodepng_util.cpp
)
target_link_libraries(libzopflipng libzopfli)
target_include_directories(libzopflipng
  INTERFACE
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/src/zopflipng>
    $<INSTALL_INTERFACE:${CMAKE_INSTALL_INCLUDEDIR}>
)
set_target_properties(libzopflipng PROPERTIES
  OUTPUT_NAME zopflipng
  VERSION ${ZOPFLI_VERSION}
  SOVERSION ${ZOPFLI_VERSION_MAJOR}
)

# MSVC does not export symbols by default when building a DLL, this is a
# workaround for recent versions of CMake
if(MSVC AND ZOPFLI_BUILD_SHARED)
  if(CMAKE_VERSION VERSION_LESS 3.4)
    message(WARNING "Automatic export of all symbols to DLL not supported until CMake 3.4")
  else()
    set_target_properties(libzopfli PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS ON)
    set_target_properties(libzopflipng PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS ON)
  endif()
endif()

#
# zopfli
#
add_executable(zopfli src/zopfli/zopfli_bin.c)
target_link_libraries(zopfli libzopfli)
if(MSVC)
  target_compile_definitions(zopfli PRIVATE _CRT_SECURE_NO_WARNINGS)
endif()

#
# zopflipng
#
add_executable(zopflipng src/zopflipng/zopflipng_bin.cc)
target_link_libraries(zopflipng libzopflipng)
if(MSVC)
  target_compile_definitions(zopflipng PRIVATE _CRT_SECURE_NO_WARNINGS)
endif()

# Create aliases
#
# Makes targets available to projects using Zopfli as a subproject using the
# same names as in the config file package.
if(NOT CMAKE_VERSION VERSION_LESS 3.0)
  add_library(Zopfli::libzopfli ALIAS libzopfli)
  add_library(Zopfli::libzopflipng ALIAS libzopflipng)
  add_executable(Zopfli::zopfli ALIAS zopfli)
  add_executable(Zopfli::zopflipng ALIAS zopflipng)
endif()

#
# Install
#
if(ZOPFLI_BUILD_INSTALL)
  # Install binaries, libraries, and headers
  install(TARGETS libzopfli libzopflipng zopfli zopflipng
    EXPORT ZopfliTargets
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
  )
  install(FILES src/zopfli/zopfli.h src/zopflipng/zopflipng_lib.h
    DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}
  )

  # Install config file package
  #
  # This allows CMake based projects to use the installed libraries with
  # find_package(Zopfli).
  if(NOT CMAKE_VERSION VERSION_LESS 3.0)
    include(CMakePackageConfigHelpers)
    write_basic_package_version_file(${CMAKE_CURRENT_BINARY_DIR}/ZopfliConfigVersion.cmake
      VERSION ${ZOPFLI_VERSION}
      COMPATIBILITY SameMajorVersion
    )
    # Since we have no dependencies, use export file directly as config file
    install(EXPORT ZopfliTargets
      DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/Zopfli
      NAMESPACE Zopfli::
      FILE ZopfliConfig.cmake
    )
    install(FILES ${CMAKE_CURRENT_BINARY_DIR}/ZopfliConfigVersion.cmake
      DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/Zopfli
    )
  endif()
endif()
