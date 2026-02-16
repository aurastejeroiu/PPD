cmake_minimum_required(VERSION 3.20)
project(GraphColoring_CUDA CUDA)

set(CMAKE_CUDA_STANDARD 14)

add_executable(GraphColoring_CUDA GraphColoring_CUDA.cu)

# Optional: target GPU architecture (safe default)
# If you know your GTX architecture, we can set it.
# set_property(TARGET GraphColoring_CUDA PROPERTY CUDA_ARCHITECTURES 61)
