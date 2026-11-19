#pragma once
#include <android/log.h>
#define SLIME_TAG "SlimePreloader"
#define SLOG_I(...) __android_log_print(ANDROID_LOG_INFO,  SLIME_TAG, __VA_ARGS__)
#define SLOG_W(...) __android_log_print(ANDROID_LOG_WARN,  SLIME_TAG, __VA_ARGS__)
#define SLOG_E(...) __android_log_print(ANDROID_LOG_ERROR, SLIME_TAG, __VA_ARGS__)
