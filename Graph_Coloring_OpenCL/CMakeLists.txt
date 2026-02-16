cmake_minimum_required(VERSION 3.20)
project(GraphColoring_OpenCL C)

set(CMAKE_C_STANDARD 99)

find_package(OpenCL REQUIRED)

add_executable(GraphColoring_OpenCL GraphColoring_OpenCL.c)

target_link_libraries(GraphColoring_OpenCL OpenCL::OpenCL)
