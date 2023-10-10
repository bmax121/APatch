cmake_minimum_required(VERSION 3.15)
project("akpatch")

add_subdirectory(bootimgtools)

add_subdirectory(tools)

add_subdirectory(user)

include_directories(user)

add_subdirectory(akpatch)