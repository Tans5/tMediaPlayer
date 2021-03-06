//
// Created by tans on 2022/5/29.
//
#ifndef MEDIA_PLAYER_POOL_H
#define MEDIA_PLAYER_POOL_H

#include "media_player.h"

typedef struct PlayerPool {
    long add_player(MediaPlayerContext* p);
    void remove_player(long id);
    MediaPlayerContext* get_player(long id);
} PlayerPool;
#endif
