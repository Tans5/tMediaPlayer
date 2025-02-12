package com.tans.tmediaplayer

import java.io.File

class FFmpegSimpleFmtConverter {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val sourceFile = File("./tmediaplayer/src/main/cpp/ffmpeg/header/libavutil/samplefmt.h")

            val simpleFmtLines = sourceFile.readLines(Charsets.UTF_8)
                .map { it.trim() }
                .filter { it.startsWith("AV_SAMPLE_FMT_") }
                .map { s ->
                    val endIndex = s.indexOf(",").let {
                        if (it == -1) {
                            s.indexOf(" ")
                        } else {
                            val end2 = s.indexOf("//")
                            if (end2 != -1 && end2 < it) {
                                end2
                            } else {
                                it
                            }
                        }
                    }
                    s.removeRange(endIndex, s.length).trim()
                }
            var nextCode = 0
            for (l in simpleFmtLines) {
                val enumName = if (!l.contains("=")) {
                    "${l.replace("AV_SAMPLE_FMT_", "")}(${nextCode++}),"
                } else {
                    val keyAndValue = l.split("=").let { it.map { it.trim() } }
                    val key = keyAndValue[0].replace("AV_SAMPLE_FMT_", "")

                    val valueInt = if (keyAndValue[1].startsWith("0x")) {
                        val v = keyAndValue[1].replace("0x", "")
                        Integer.parseInt(v, 16)
                    } else {
                        try {
                            keyAndValue[1].toInt()
                        } catch (e: Throwable) {
                            continue
                        }
                    }
                    nextCode = valueInt + 1
                    "${key}($valueInt),"
                }
                val firstChar = enumName[0]
                if (firstChar in '0'..'9') {
                    println("_$enumName")
                } else {
                    println(enumName)
                }
            }
        }
    }
}