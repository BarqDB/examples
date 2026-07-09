cmake_minimum_required(VERSION 3.20)

project(barq_multitenant_e2e LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# The example only needs the SDK client; do not build the SDK's own tests.
set(BARQ_NATIVE_NO_TESTS ON CACHE BOOL "Do not build Barq Native tests from the example" FORCE)

include(FetchContent)

set(BARQ_NATIVE_GIT_REPOSITORY "https://github.com/BarqDB/barq-native.git" CACHE STRING "Barq Native git repository")
set(BARQ_NATIVE_GIT_TAG "main" CACHE STRING "Barq Native git ref")
set(BARQ_NATIVE_SOURCE_DIR "" CACHE PATH "Optional local Barq Native checkout")

set(BARQ_CORE_GIT_REPOSITORY "https://github.com/BarqDB/barq-core.git" CACHE STRING "Barq Core git repository")
set(BARQ_CORE_GIT_TAG "main" CACHE STRING "Barq Core git ref")
set(BARQ_CORE_SOURCE_DIR "" CACHE PATH "Optional local Barq Core checkout")

if(BARQ_NATIVE_SOURCE_DIR)
    FetchContent_Declare(barq_native SOURCE_DIR "${BARQ_NATIVE_SOURCE_DIR}")
else()
    FetchContent_Declare(
        barq_native
        GIT_REPOSITORY "${BARQ_NATIVE_GIT_REPOSITORY}"
        GIT_TAG "${BARQ_NATIVE_GIT_TAG}"
    )
endif()

FetchContent_MakeAvailable(barq_native)

add_executable(barq_mt_e2e src/main.cpp)
target_link_libraries(barq_mt_e2e PRIVATE Barq::Native)
