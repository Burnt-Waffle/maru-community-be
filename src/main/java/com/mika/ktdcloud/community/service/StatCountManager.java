package com.mika.ktdcloud.community.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 게시글 조회수 및 좋아요 수치를 메모리에서 관리하는 클래스.
 * DB 락 경합을 피하기 위해 Write-behind 패턴을 지원.
 */
@Component
public class StatCountManager {
    private final ConcurrentHashMap<Long, AtomicInteger> viewCountIncrements = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> likeCountChanges = new ConcurrentHashMap<>();

    public void incrementViewCount(Long postId) {
        viewCountIncrements.computeIfAbsent(postId, id -> new AtomicInteger(0)).incrementAndGet();
    }

    public void incrementLikeCount(Long postId) {
        likeCountChanges.computeIfAbsent(postId, id -> new AtomicInteger(0)).incrementAndGet();
    }

    public void decrementLikeCount(Long postId) {
        likeCountChanges.computeIfAbsent(postId, id -> new AtomicInteger(0)).decrementAndGet();
    }

    public Map<Long, Integer> getAndResetViewCounts() {
        return getAndReset(viewCountIncrements);
    }

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
