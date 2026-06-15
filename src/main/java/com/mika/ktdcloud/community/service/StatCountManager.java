package com.mika.ktdcloud.community.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 게시글 조회수 및 좋아요 수치를 메모리에서 관리하는 클래스.
 * DB 락 경합을 피하기 위해 Write-behind 패턴을 지원합니다.
 */
@Component
public class StatCountManager {
    // postId를 키로, 조회수 증가량을 값으로 저장
    private final ConcurrentHashMap<Long, AtomicInteger> viewCountIncrements = new ConcurrentHashMap<>();
    
    // postId를 키로, 좋아요 수 변화량을 값으로 저장 (+1, -1)
    private final ConcurrentHashMap<Long, AtomicInteger> likeCountChanges = new ConcurrentHashMap<>();

    /**
     * 조회수를 1 증가시킵니다.
     */
    public void incrementViewCount(Long postId) {
        viewCountIncrements.computeIfAbsent(postId, id -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * 좋아요 수를 1 증가시킵니다.
     */
    public void incrementLikeCount(Long postId) {
        likeCountChanges.computeIfAbsent(postId, id -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * 좋아요 수를 1 감소시킵니다.
     */
    public void decrementLikeCount(Long postId) {
        likeCountChanges.computeIfAbsent(postId, id -> new AtomicInteger(0)).decrementAndGet();
    }

    /**
     * 현재 메모리에 쌓인 조회수 변화량을 반환하고 리셋합니다.
     */
    public Map<Long, Integer> getAndResetViewCounts() {
        return getAndReset(viewCountIncrements);
    }

    /**
     * 현재 메모리에 쌓인 좋아요 수 변화량을 반환하고 리셋합니다.
     */
    public Map<Long, Integer> getAndResetLikeCounts() {
        return getAndReset(likeCountChanges);
    }

    private Map<Long, Integer> getAndReset(ConcurrentHashMap<Long, AtomicInteger> targetMap) {
        Map<Long, Integer> snapshot = new HashMap<>();
        targetMap.forEach((postId, atomicVal) -> {
            int val = atomicVal.getAndSet(0);
            if (val != 0) {
                snapshot.put(postId, val);
            }
        });
        return snapshot;
    }
}
