cmake_minimum_required(VERSION 3.22.1)
project(qqmusic LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---- Android 15 / API 35 16KB page size 兼容 ----
# 16KB 页大小设备要求原生库 ELF 段按 16KB 对齐，否则 System.loadLibrary 直接崩溃。
# 该 flag 兼容 4KB 设备，同时满足 16KB 设备加载要求。
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# ---- mbedtls static crypto library ----
set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ENABLE_ZLIB_SUPPORT ON CACHE BOOL "" FORCE)
set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)

add_subdirectory(third_party/mbedtls)

# ---- Our native library bundling all QQMusic API logic ----
add_library(qqmusicapi SHARED
    qqapi/alg/hash33.c
    qqapi/alg/zzc_sign.c
    qqapi/alg/tripledes.c
    qqapi/alg/qrc.c
    qqapi/alg/qimei.c
    qqapi/alg/device.c
    qqapi/alg/base64.c
    qqapi/alg/hex.c
    qqapi/alg/random.c
    qqapi/comm.c
    qqapi/jni_bridge.cpp
)

target_include_directories(qqmusicapi PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    third_party/mbedtls/include
    qqapi
)

target_link_libraries(qqmusicapi
    mbedcrypto
    z
)

# 强制 16KB 对齐（Android 15 / API 35 16KB page size 设备兼容）
# 全局 link flag 可能被 NDK toolchain 覆盖，这里直接作用于目标最可靠。
target_link_options(qqmusicapi PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

find_library(log-lib log)
target_link_libraries(qqmusicapi ${log-lib})