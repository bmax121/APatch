cmake_minimum_required(VERSION 3.15)
project("native")

set(SOURCE
        akpatch.cpp
        )

add_library(
        akp
        SHARED
        ${SOURCE}
        )

target_link_libraries(
        akp
        kp
        log
        )