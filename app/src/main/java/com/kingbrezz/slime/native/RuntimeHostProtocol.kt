package com.kingbrezz.slime.native

internal object RuntimeHostProtocol {
    const val START = 1
    const val SHUTDOWN = 2
    const val INSTALL_HOOKS = 3
    const val RESULT = 100

    const val VERSION = "version"
    const val MODULE_IDS = "module_ids"
    const val MODULE_FLAGS = "module_flags"
    const val VALUE_IDS = "value_ids"
    const val VALUE_NUMBERS = "value_numbers"
    const val SIGNATURE_NAMES = "signature_names"
    const val SIGNATURE_PATTERNS = "signature_patterns"
    const val OFFSET_NAMES = "offset_names"
    const val OFFSET_VALUES = "offset_values"
}