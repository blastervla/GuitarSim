package com.example.guitarsim.utils

import android.annotation.SuppressLint
import android.text.TextUtils
import java.net.URLEncoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.sign

/**
 * Created by blastervla on 12/9/17.
 */

fun <K, V> Map<K, V>.doubleForKey(key: K): Double {
    return this[key] as? Double ?: 0.0
}

fun <K, V> Map<K, V>.longForKey(key: K): Long {
    return this.doubleForKey(key).toLong()
}

fun <K, V> Map<K, V>.stringForKey(key: K): String {
    return this[key] as? String ?: ""
}

fun <K, V> Map<K, V>.boolForKey(key: K): Boolean {
    return this[key] as? Boolean == true
}

fun <K, V, T> Map<K, V>.arrayForKey(key: K): List<T> {
    return this[key] as? List<T> ?: ArrayList()
}

fun <K, V> Map<K, V>.objectForKey(key: K): Map<String, Any> {
    return this[key] as? Map<String, Any> ?: HashMap()
}

fun Int.signedString() = (if (sign == 1) "+" else "") + this.toString()

@SuppressLint("SimpleDateFormat")
fun Date.fromString(string: String): Date? {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    return try {
        format.parse(string)
    } catch (e: ParseException) {
        Date()
    }
}

@SuppressLint("SimpleDateFormat")
fun String.Companion.fromDate(date: Date): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    return try {
        dateFormat.format(date)
    } catch (e: ParseException) {
        ""
    }
}

fun Int.toMinuteSecondString() = String.format(
    "%02d:%02d",
    TimeUnit.MILLISECONDS.toMinutes(this.toLong()) % TimeUnit.HOURS.toMinutes(1),
    TimeUnit.MILLISECONDS.toSeconds(this.toLong()) % TimeUnit.MINUTES.toSeconds(1)
)

class ParsingUtils {
    companion object {
        fun emptyStringIfCero(value: Long): String = if (value != 0L) value.toString() else ""

        fun ceroIfEmptyString(value: String): Long = defaultIfEmptyString(value, 0)

        fun defaultIfEmptyString(value: String, default: Long): Long = try {
            if (!TextUtils.isEmpty(value) && value != "-" && value != "+") value.toLong() else default
        } catch (_: Exception) {
            default
        }

        fun nullIfEmptyString(value: String): Long? = try {
            if (!TextUtils.isEmpty(value)) value.toLong() else null
        } catch (_: Exception) {
            null
        }

        fun setIfNotNull(key: String, value: Any?, hashMap: HashMap<String, Any>): HashMap<String, Any> {
            if (value != null) {
                hashMap.set(key, value)
            }

            return hashMap
        }

        fun addAsQueryIfNotNull(key: String, value: String?, list: ArrayList<String>): ArrayList<String> {
            if (value != null) {
                list.add(parseAsQuery(key, value))
            }

            return list
        }

        fun addAsQueryIfNotNull(key: String, value: List<String>?, list: ArrayList<String>): ArrayList<String> {
            if (value != null) {
                list.add(parseListAsQueryParams(key, value))
            }

            return list
        }

        fun parseListAsQueryParams(key: String, values: List<String>): String {
            val parsedValues = ArrayList<String>()

            values.forEach {
                parsedValues.add("$key[]=${URLEncoder.encode(it, "utf-8")}")
            }

            return TextUtils.join("&", parsedValues)
        }

        fun parseAsQuery(key: String, value: String): String =
            "$key=${URLEncoder.encode(value, "utf-8")}"

        fun parseURL(endpoint: String, queryParams: List<String>): String =
            "$endpoint?${TextUtils.join("&", queryParams)}"

        fun <T, R> getOrDefault(hashMap: HashMap<T, R>, key: T, default: R): R {
            return hashMap[key] ?: default
        }

    }
}