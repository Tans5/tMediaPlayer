#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <ass/ass_types.h>
#include <ass/ass.h>
#include "libavcodec/avcodec.h"
#include "libavfilter/buffersrc.h"

// 将AVSubtitle渲染为RGBA位图
uint8_t* render_subtitle_to_rgba(
        AVSubtitle* sub,          // FFmpeg解码的字幕
        int frame_w, int frame_h // 视频帧尺寸
) {
    // 1. 初始化libass
    ASS_Library* library = ass_library_init();
    ASS_Renderer* renderer = ass_renderer_init(library);
    ass_set_frame_size(renderer, frame_w, frame_h);
    // 设置字体（示例：使用默认字体）
    ass_set_fonts(renderer, NULL, NULL, ASS_FONTPROVIDER_AUTODETECT, NULL, 1);

    // 2. 创建ASS轨道并添加事件
    ASS_Track* track = ass_new_track(library);
    for (int i = 0; i < sub->num_rects; i++) {
        AVSubtitleRect* rect = sub->rects[i];
        if (rect->type == SUBTITLE_ASS) {
            // 添加ASS事件（时间戳为0，由事件自带时间控制）
            ass_process_data(track, rect->ass, strlen(rect->ass));
        }
    }
    for (int i = 0; i < track->n_events; i ++) {
        auto event = track->events[i];
        event.Start = 0;
        event.Duration = 10000;
    }

    // 3. 渲染为ASS_Image链表
    ASS_Image* img = ass_render_frame(renderer, track, 1, NULL);

    // 4. 创建RGBA缓冲区（初始透明）
    int stride = frame_w * 4; // RGBA=4字节/像素
    uint8_t* rgba = (uint8_t*)calloc(1, frame_h * stride);

    // 5. 遍历图像链表并混合到RGBA
    ASS_Image* cur = img;
    while (cur) {
        // 提取颜色分量（RGBA格式）
        uint32_t color = cur->color;
        uint8_t global_alpha = (color) & 0xFF;
        uint8_t b = (color >> 8) & 0xFF;
        uint8_t g = (color >> 16) & 0xFF;
        uint8_t r = (color >> 24) & 0xFF;

        // 逐像素处理
        for (int y = 0; y < cur->h; y++) {
            for (int x = 0; x < cur->w; x++) {
                uint8_t alpha_val = cur->bitmap[y * cur->stride + x];
                if (alpha_val == 0) continue; // 跳过透明像素

                uint8_t final_alpha = (global_alpha * alpha_val) / 255;
                if (final_alpha == 0) continue;

                int tgt_x = cur->dst_x + x;
                int tgt_y = cur->dst_y + y;
                if (tgt_x < 0 || tgt_x >= frame_w || tgt_y < 0 || tgt_y >= frame_h) {
                    continue; // 边界检查
                }

                // 目标像素位置（RGBA缓冲区）
                uint8_t* pixel = &rgba[(tgt_y * stride) + (tgt_x * 4)];
                uint8_t bg_alpha = pixel[3]; // 原Alpha分量

                // ---- Alpha混合算法（预乘模式）----
                if (bg_alpha == 0) {
                    // 背景透明：直接覆盖
                    pixel[0] = r;
                    pixel[1] = g;
                    pixel[2] = b;
                    pixel[3] = final_alpha;
                } else {
                    // 混合计算
                    uint8_t out_alpha = final_alpha + (bg_alpha * (255 - final_alpha) / 255);
                    float blend = final_alpha / 255.0f;

                    pixel[0] = (r * blend) + (pixel[0] * (1.0f - blend));
                    pixel[1] = (g * blend) + (pixel[1] * (1.0f - blend));
                    pixel[2] = (b * blend) + (pixel[2] * (1.0f - blend));
                    pixel[3] = out_alpha;
                }
            }
        }
        cur = cur->next;
    }

    // 6. 清理资源
    ass_free_track(track);
    ass_renderer_done(renderer);
    ass_library_done(library);
    return rgba; // 返回RGBA缓冲区（需由调用者释放）
}