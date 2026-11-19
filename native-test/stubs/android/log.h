#pragma once
#include <cstdarg>
#include <cstdio>
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_WARN 5
#define ANDROID_LOG_ERROR 6
inline int __android_log_print(int, const char* tag, const char* fmt, ...) {
    va_list a; va_start(a, fmt); std::fprintf(stderr, "[%s] ", tag); std::vfprintf(stderr, fmt, a); std::fprintf(stderr, "\n"); va_end(a); return 0;
}
