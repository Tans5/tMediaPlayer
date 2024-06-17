package com.tans.tmediaplayer

class FFmpegSimpleFmtConverter {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val ffmpegSimpleFmt = """
                    AV_SAMPLE_FMT_NONE,
                    AV_SAMPLE_FMT_U8,          ///< unsigned 8 bits
                    AV_SAMPLE_FMT_S16,         ///< signed 16 bits
                    AV_SAMPLE_FMT_S32,         ///< signed 32 bits
                    AV_SAMPLE_FMT_FLT,         ///< float
                    AV_SAMPLE_FMT_DBL,         ///< double

                    AV_SAMPLE_FMT_U8P,         ///< unsigned 8 bits, planar
                    AV_SAMPLE_FMT_S16P,        ///< signed 16 bits, planar
                    AV_SAMPLE_FMT_S32P,        ///< signed 32 bits, planar
                    AV_SAMPLE_FMT_FLTP,        ///< float, planar
                    AV_SAMPLE_FMT_DBLP,        ///< double, planar
                    AV_SAMPLE_FMT_S64,         ///< signed 64 bits
                    AV_SAMPLE_FMT_S64P,        ///< signed 64 bits, planar

                    AV_SAMPLE_FMT_NB,           ///< Number of sample formats. DO NOT USE if linking dynamically
            """.trimIndent()

            val simpleFmtLines = ffmpegSimpleFmt.lines()
                .map { it.trim() }
                .filter { it.startsWith("AV_SAMPLE_FMT_") }
                .map {
                    val endIndex = it.indexOf(",")
                    it.removeRange(endIndex, it.length)
                }
            var nextCode = -1
            for (l in simpleFmtLines) {
                if (!l.contains("=")) {
                    println("${l.replace("AV_SAMPLE_FMT_", "")}(${nextCode++}),")
                }
            }
        }
    }
}