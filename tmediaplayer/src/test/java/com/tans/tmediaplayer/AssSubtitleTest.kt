package com.tans.tmediaplayer

class AssSubtitleTest {

    companion object {

        private val aasSubtitlePrefixRegex = "^(([^,]*,){8})".toRegex()

        private val assCommandRegex = "\\{.*\\}".toRegex()

        private fun String.fixAssSubtitle(): String {
            return if (this.contains(aasSubtitlePrefixRegex)) {
                this.replace(aasSubtitlePrefixRegex, "")
                    .replace(assCommandRegex, "")
                    .replace("\\N", "\n")
                    .replace("\\h", "\t")
            } else {
                this
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val input1 = "9,0,Default,,0,0,0,,快点走了"
            val input2 = "466,0,白中黄英,,0,0,0,,不幸的是 \"何时\"离我仍然\\N{\\r原文字幕}Unfortunately, the \"When\" Of it was still"
            println(input1.fixAssSubtitle())
            println(input2.fixAssSubtitle())
        }
    }
}