# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

set(TARGET hprofclipper)
project(${TARGET})

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.22.1)

add_compile_options(-Wall -Wextra #[[-Werror -Wno-unused-command-line-argument]] -Oz)


include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/include/
#        D:\\AndroidStudioProjects\\lab\\KOOM\\koom-common\\third-party\\xhook\\src\\main\\cpp\\xhook\\src
)

#link_directories(
#    D:\\AndroidStudioProjects\\lab\\KOOM\\koom-common\\third-party\\xhook\\src\\main\\libs\\${ANDROID_ABI}
#)


#add_library(xhook_lib SHARED IMPORTED)
#set_target_properties(xhook_lib PROPERTIES IMPORTED_LOCATION
#        D:\\AndroidStudioProjects\\lab\\KOOM\\koom-common\\third-party\\xhook\\src\\main\\libs\\${ANDROID_ABI}\\libxhook_lib.so)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
add_library( # Sets the name of the library.
        ${TARGET}

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        native_bridge.cpp hprof_strip.cpp)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

#find_package(bytehook REQUIRED CONFIG)
find_package(xhook REQUIRED CONFIG)

target_link_libraries( # Specifies the target library.
        ${TARGET}
#        bytehook::bytehook
        xhook::xhook
#        xhook_lib
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})

