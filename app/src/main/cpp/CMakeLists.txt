cmake_minimum_required(VERSION 3.22.1)
project(slime_preloader)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(ANDROID)
    add_link_options("-Wl,-z,max-page-size=16384")
endif()

add_library(slime_preloader SHARED
    src/preloader.cpp
    src/signature_manager.cpp
    src/offset_tables.cpp
    src/hook_registry.cpp
    src/module_bridge.cpp
    src/module_scanner.cpp
    src/effects.cpp
    src/selftest.cpp
    jni/jni_bridge.cpp
)

target_include_directories(slime_preloader PRIVATE include)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(slime_preloader ${log-lib} ${android-lib})

target_compile_options(slime_preloader PRIVATE
    -fvisibility=hidden
    -Wall
    -Wextra
    -fno-exceptions
    -fno-rtti
)

# LeviLauncher Android Preloader. It is packaged separately as libpreloader.so;
# MainActivity's Activity manifest metadata will select it at launch time.
add_subdirectory(
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/preloader-android"
    "${CMAKE_CURRENT_BINARY_DIR}/levi-preloader"
)
