#include <libavcodec/avcodec.h>
#include <cstdint>
#include <vector>

// 假设 av_subtitle 是一个已经解码并填充好的 AVSubtitle 结构体指针

void convert_subtitle_rect_to_rgba(const AVSubtitleRect* rect, std::vector<uint8_t>& rgba_bitmap) {
    if (!rect || rect->type != SUBTITLE_BITMAP) {
        return;
    }

    int width = rect->w;
    int height = rect->h;

    // 1. 获取像素索引数据和调色板数据
    const uint8_t* pixel_indexes = rect->data[0];
    const uint32_t* palette = reinterpret_cast<const uint32_t*>(rect->data[1]);

    if (!pixel_indexes || !palette) {
        return;
    }

    // 2. 分配 RGBA 位图的内存
    rgba_bitmap.resize(width * height * 4);

    // 3. 遍历每个像素并进行转换
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            // 获取当前像素的调色板索引
            uint8_t index = pixel_indexes[y * rect->linesize[0] + x];

            // 从调色板中获取 RGBA 颜色
            uint32_t rgba_color = palette[index];

            // FFMpeg 的调色板通常是 ABRG (小端序) 或 RGBA (大端序)
            // 这里我们假设是标准 RGBA，并分别提取 R, G, B, A
            // 注意：FFMpeg 的颜色格式可能是 A, B, G, R 排列，需要根据实际情况调整
            uint8_t r = (rgba_color >> 24) & 0xFF;
            uint8_t g = (rgba_color >> 16) & 0xFF;
            uint8_t b = (rgba_color >> 8) & 0xFF;
            uint8_t a = rgba_color & 0xFF;

            // 将 RGBA 值写入到我们的位图缓冲区
            int rgba_index = (y * width + x) * 4;
            rgba_bitmap[rgba_index + 0] = r;
            rgba_bitmap[rgba_index + 1] = g;
            rgba_bitmap[rgba_index + 2] = b;
            rgba_bitmap[rgba_index + 3] = a;
        }
    }
}

//好的，根据您提供的 FFMpeg AV_CODEC_ID 列表，以下是属于位图格式（Bitmap-based） 的字幕编码，以及它们的调色板（Palette）格式说明。
//
//位图格式的字幕编码及其调色板
//        这些格式的字幕不是由文本和样式指令构成的，而是由像素图像组成，通常会附带一个调色板来定义图像中每个索引所代表的具体颜色。
//
//AV_CODEC_ID	字幕格式名称	调色板格式说明
//AV_CODEC_ID_DVD_SUBTITLE	DVD Subtitles	通常称为 VobSub (.sub/.idx)。这是一种基于调色板的图像格式。解码后，FFmpeg 通常会提供一个包含 16 种颜色 的调色板，格式为 RGBA。调色板本身可以由字幕流中的 YCrCb 值计算得出。
//AV_CODEC_ID_DVB_SUBTITLE	DVB Subtitles	用于数字视频广播 (DVB) 的标准。这也是一种基于调色板的位图格式。它可以支持 4位（16色） 或 8位（256色） 的调色板。解码后，FFmpeg 将其表示为 RGBA 调色板。
//AV_CODEC_ID_HDMV_PGS_SUBTITLE	PGS Subtitles	用于蓝光光盘 (Blu-ray Disc) 的高清图形字幕格式。这是一种复杂的位图格式，每个图像段都有其自己的调色板，最多包含 256 种颜色。解码后，FFmpeg 将其调色板转换为 RGBA 格式。
//AV_CODEC_ID_XSUB	XSUB	DivX/Xvid 使用的位图字幕格式，常见于 .avi 文件中。它也是基于调色板的。解码后，其调色板同样以 RGBA 格式提供。
//AV_CODEC_ID_ARIB_CAPTION	ARIB Caption	日本数字电视标准 (ARIB) 中使用的字幕格式。它是一种混合格式，既可以包含图形元素（位图），也可以包含文本。当作为位图处理时，它依赖于一组预定义的或动态加载的调色板 (CLUTs)，FFmpeg 会将其处理为 RGBA 格式。
//
//导出到 Google 表格
//        总结
//当您使用 FFMpeg 解码上述几种格式的字幕时，AVSubtitleRect 结构中的 type 字段会是 SUBTITLE_BITMAP。其像素数据 (rect->data[0]) 是调色板的索引，而调色板本身 (rect->data[1]) 已经被 FFMpeg 统一处理成一个 32位的 RGBA 颜色数组。每个 uint32_t 值代表一个颜色，其字节顺序通常是 [R, G, B, A]。
//
//因此，无论原始字幕流中的调色板是 YCrCb 还是其他格式，FFmpeg 的解码器已经帮您完成了转换工作，您在 AVSubtitleRect 层面接触到的调色板基本都是标准的 RGBA 格式，可以直接用于渲染。
//
//文本及其他格式
//        为了完整性，您列表中的其他格式主要是基于文本（Text-based） 或矢量图形（Vector-based） 的，它们不直接提供位图和调色板，而是提供文本内容和样式信息（如字体、颜色、位置、时间轴等）。渲染这些字幕需要一个文本渲染引擎。
//
//纯文本类: AV_CODEC_ID_TEXT, AV_CODEC_ID_SRT, AV_CODEC_ID_SUBRIP, AV_CODEC_ID_WEBVTT, AV_CODEC_ID_MICRODVD, AV_CODEC_ID_SUBVIEWER1, AV_CODEC_ID_SUBVIEWER, AV_CODEC_ID_MPL2, AV_CODEC_ID_VPLAYER, AV_CODEC_ID_PJS, AV_CODEC_ID_REALTEXT
//
//        高级样式文本/矢量类: AV_CODEC_ID_SSA, AV_CODEC_ID_ASS, AV_CODEC_ID_SAMI, AV_CODEC_ID_MOV_TEXT, AV_CODEC_ID_HDMV_TEXT_SUBTITLE, AV_CODEC_ID_TTML, AV_CODEC_ID_JACOSUB, AV_CODEC_ID_STL
//
//        特殊数据类: AV_CODEC_ID_DVB_TELETEXT, AV_CODEC_ID_EIA_608, AV_CODEC_ID_IVTV_VBI (这些通常是嵌入在视频信号中的特殊数据，需要专门的解析器)