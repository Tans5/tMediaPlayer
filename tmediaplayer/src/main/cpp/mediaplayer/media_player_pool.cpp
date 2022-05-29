//
// Created by tans on 2022/5/29.
//
#include "media_player_pool.h"
#include "pthread.h"
#include "media_time.h"

pthread_mutex_t *pool_opt_mutex;

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
    PlayerNode* move_node = header;
    while (true) {
        if (move_node->next == nullptr) {
            break;
        } else {
            move_node = move_node->next;
        }
    }
    PlayerNode* new_node = new PlayerNode;
    new_node->id = id;
    new_node->player = p;
    move_node->next = new_node;
    pthread_mutex_unlock(pool_opt_mutex);
    return id;
}

MediaPlayerContext* PlayerPool::get_player(long id) {
    check_pool_mutex();
    pthread_mutex_lock(pool_opt_mutex);
    PlayerNode *target = nullptr;
    PlayerNode *move_node = header;
    while (move_node != nullptr) {
        if (move_node->id == id) {
            target = move_node;
            break;
        }
        move_node = move_node->next;
    }
    pthread_mutex_unlock(pool_opt_mutex);
    if (target != nullptr) {
        return target->player;
    } else {
        return nullptr;
    }
}

void PlayerPool::remove_player(long id) {
    check_pool_mutex();
    pthread_mutex_lock(pool_opt_mutex);
    PlayerNode* move_node = header;
    PlayerNode* target_node = nullptr;
    PlayerNode* pre_target_node = header;
    while (move_node != nullptr) {
        if (move_node->id == id) {
            target_node = move_node;
            break;
        }
        pre_target_node = move_node;
        move_node = move_node->next;
    }
    if (target_node != nullptr) {
        pre_target_node->next = target_node->next;
        free(target_node);
    }
    pthread_mutex_unlock(pool_opt_mutex);
}