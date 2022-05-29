//
// Created by tans on 2022/5/29.
//
#include "media_player_pool.h"
#include "pthread.h"
#include "media_time.h"
#include "map"

pthread_mutex_t *pool_opt_mutex;
std::map<long, MediaPlayerContext*> players;

void check_pool_mutex() {
    if (pool_opt_mutex == nullptr) {
        pool_opt_mutex = new pthread_mutex_t;
        pthread_mutex_init(pool_opt_mutex, nullptr);
    }
}

long PlayerPool::add_player(MediaPlayerContext *p) {
    check_pool_mutex();
    pthread_mutex_lock(pool_opt_mutex);
    long id = get_time_millis();
    players[id] = p;
    pthread_mutex_unlock(pool_opt_mutex);
    return id;
}

MediaPlayerContext* PlayerPool::get_player(long id) {
    check_pool_mutex();
    pthread_mutex_lock(pool_opt_mutex);
    auto player = players[id];
    pthread_mutex_unlock(pool_opt_mutex);
    return player;
}

void PlayerPool::remove_player(long id) {
    check_pool_mutex();
    pthread_mutex_lock(pool_opt_mutex);
    players.erase(id);
    pthread_mutex_unlock(pool_opt_mutex);
}