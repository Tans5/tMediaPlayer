#include "media_player.h"
extern "C" {
#include "libavformat/avformat.h"
}

void set_media_file_path(const char * file_path) {
    LOGD("File path: %s", file_path);
}