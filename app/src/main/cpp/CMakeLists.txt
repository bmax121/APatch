cmake_minimum_required(VERSION 3.4.1)
project(apjni)

find_program(CCACHE ccache)

if (CCACHE)
        set(CMAKE_CXX_COMPILER_LAUNCHER ${CCACHE})
        set(CMAKE_C_COMPILER_LAUNCHER ${CCACHE})
endif ()

find_package(cxx REQUIRED CONFIG)
link_libraries(cxx::cxx)

macro(SET_OPTION option value)
        set(${option} ${value} CACHE INTERNAL "" FORCE)
endmacro()

set(CHERISH_POLLY_FLAGS "-mllvm -polly -mllvm -polly-run-dce -mllvm -polly-run-inliner -mllvm -polly-reschedule=1 -mllvm -polly-loopfusion-greedy=1 -mllvm -polly-postopts=1 -mllvm -polly-num-threads=0 -mllvm -polly-omp-backend=LLVM -mllvm -polly-scheduling=dynamic -mllvm -polly-scheduling-chunksize=1 -mllvm -polly-isl-arg=--no-schedule-serialize-sccs -mllvm -polly-ast-use-context -mllvm -polly-detect-keep-going -mllvm -polly-position=before-vectorizer -mllvm -polly-vectorizer=stripmine -mllvm -polly-detect-profitability-min-per-loop-insts=40 -mllvm -polly-invariant-load-hoisting")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${CHERISH_POLLY_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden -fno-rtti -fno-exceptions -O3 -flto")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${CHERISH_POLLY_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden -fno-rtti -fno-exceptions -O3 -flto")
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(${PROJECT_NAME} SHARED apjni.cpp)
add_custom_command(TARGET ${PROJECT_NAME} POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-all --remove-section=.note.gnu.build-id --remove-section=.note.android.ident $<TARGET_FILE:${PROJECT_NAME}>)

target_link_libraries(${PROJECT_NAME} PRIVATE log)
target_compile_options(${PROJECT_NAME} PRIVATE -flto)
target_link_options(${PROJECT_NAME} PRIVATE "-Wl,--build-id=none" "-Wl,-icf=all,--lto-O3" "-Wl,-s,-x,--gc-sections" "-Wl,--no-undefined")
