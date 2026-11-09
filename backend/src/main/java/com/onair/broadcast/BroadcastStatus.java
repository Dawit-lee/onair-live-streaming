package com.onair.broadcast;

/** READY: 생성됨(송출 전), LIVE: 송출 중, ENDED: 종료됨(다시 시작 가능). */
public enum BroadcastStatus {
    READY,
    LIVE,
    ENDED
}
