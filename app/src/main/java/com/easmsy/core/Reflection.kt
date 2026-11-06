package com.easmsy.core

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * The original project used the large "deagle" reflection library.  The rewrite needs only
 * a handful of calls, so this intentionally tiny helper replaces it.
 */
object Reflection {

    fun method(type: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? =
        try {
            type.getMethod(name, *parameterTypes).apply { isAccessible = true }
        } catch (_: Exception) {
            null
        }

    fun declaredMethod(type: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? =
        try {
            type.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
        } catch (_: Exception) {
            null
        }

    fun field(type: Class<*>, name: String): Field? =
        try {
            type.getDeclaredField(name).apply { isAccessible = true }
        } catch (_: Exception) {
            null
        }

    @Suppress("UNCHECKED_CAST")
    fun <T> invoke(target: Any?, method: Method?, vararg args: Any?): T? =
        try {
            method?.invoke(target, *args) as? T
        } catch (_: Exception) {
            null
        }

    fun invokeBoolean(target: Any?, method: Method?, vararg args: Any?): Boolean =
        try {
            method?.invoke(target, *args) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
}
