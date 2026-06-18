package com.mika.ktdcloud.community.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 게시글 조회수 및 좋아요 수치를 Redis에서 관리하는 클래스.
 * DB 락 경합을 피하기 위해 Redis Hash 기반의 Write-behind 패턴을 사용하며,
 * 실시간 인기글 집계를 위해 Redis Sorted Set(ZSET)을 사용합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatCountManager {

    private final StringRedisTemplate redisTemplate;

    private static final String VIEW_COUNT_KEY = "post:view_count_changes";
    private static final String LIKE_COUNT_KEY = "post:like_count_changes";
    private static final String POPULAR_RANK_KEY = "posts:popular";

    // 인기 점수 가중치 (좋아요 5점, 댓글 3점, 조회수 1점)
    private static final int LIKE_SCORE_WEIGHT = 5;
    private static final int COMMENT_SCORE_WEIGHT = 3;
    private static final int VIEW_SCORE_WEIGHT = 1;

    public void incrementViewCount(Long postId) {
        // 1. RDB 배치 반영용 Hash 카운터 증가
        redisTemplate.opsForHash().increment(VIEW_COUNT_KEY, String.valueOf(postId), 1);
        // 2. 실시간 Sorted Set 랭킹 Score 갱신
        redisTemplate.opsForZSet().incrementScore(POPULAR_RANK_KEY, String.valueOf(postId), VIEW_SCORE_WEIGHT);
    }

    public void incrementLikeCount(Long postId) {
        // 1. RDB 배치 반영용 Hash 카운터 증가
        redisTemplate.opsForHash().increment(LIKE_COUNT_KEY, String.valueOf(postId), 1);
        // 2. 실시간 Sorted Set 랭킹 Score 갱신
        redisTemplate.opsForZSet().incrementScore(POPULAR_RANK_KEY, String.valueOf(postId), LIKE_SCORE_WEIGHT);
    }

    public void decrementLikeCount(Long postId) {
        // 1. RDB 배치 반영용 Hash 카운터 증가
        redisTemplate.opsForHash().increment(LIKE_COUNT_KEY, String.valueOf(postId), -1);
        // 2. 실시간 Sorted Set 랭킹 Score 갱신
        redisTemplate.opsForZSet().incrementScore(POPULAR_RANK_KEY, String.valueOf(postId), -LIKE_SCORE_WEIGHT);
    }

    public void incrementCommentCount(Long postId) {
        // 댓글 추가 시 실시간 ZSET Score 갱신
        redisTemplate.opsForZSet().incrementScore(POPULAR_RANK_KEY, String.valueOf(postId), COMMENT_SCORE_WEIGHT);
    }

    public void decrementCommentCount(Long postId) {
        // 댓글 삭제 시 실시간 ZSET Score 감산
        redisTemplate.opsForZSet().incrementScore(POPULAR_RANK_KEY, String.valueOf(postId), -COMMENT_SCORE_WEIGHT);
    }

    // --- RDB 반영용 스냅샷 조회 및 리셋 (Atomic RENAME 기법) ---

    public Map<Long, Integer> getAndResetViewCounts() {
        return getAndReset(VIEW_COUNT_KEY);
    }

    public Map<Long, Integer> getAndResetLikeCounts() {
        return getAndReset(LIKE_COUNT_KEY);
    }

    private Map<Long, Integer> getAndReset(String key) {
        String tempKey = key + ":temp";

        // 원자적으로 키 이름을 RENAME 하여, 백업 스레드가 처리하는 동안 신규 유입되는 증감량 누수 차단
        Boolean renamed = redisTemplate.renameIfAbsent(key, tempKey);
        if (Boolean.FALSE.equals(renamed)) {
            // tempKey가 이미 존재하여 이름 변경에 실패한 경우 (이전 백업 배치가 다소 밀린 상태)
            log.warn("Temp key {} already exists. Skipping this flush cycle.", tempKey);
            return new HashMap<>();
        }

        Map<Object, Object> rawEntries = redisTemplate.opsForHash().entries(tempKey);
        Map<Long, Integer> result = new HashMap<>();

        rawEntries.forEach((field, val) -> {
            try {
                Long postId = Long.valueOf(field.toString());
                Integer count = Integer.valueOf(val.toString());
                if (count != 0) {
                    result.put(postId, count);
                }
            } catch (NumberFormatException e) {
                log.error("Invalid key format in Redis Hash: field={}, value={}", field, val);
            }
        });

        // 처리가 끝난 임시 키는 완전히 제거
        redisTemplate.delete(tempKey);
        return result;
    }

    // --- 테스트용 실시간 스냅샷 조회 (리셋 없음) ---

    public Map<Long, Integer> getLikeCountChangesSnapshot() {
        return getSnapshot(LIKE_COUNT_KEY);
    }

    public Map<Long, Integer> getViewCountChangesSnapshot() {
        return getSnapshot(VIEW_COUNT_KEY);
    }

    private Map<Long, Integer> getSnapshot(String key) {
        Map<Object, Object> rawEntries = redisTemplate.opsForHash().entries(key);
        Map<Long, Integer> result = new HashMap<>();
        rawEntries.forEach((field, val) -> {
            try {
                Long postId = Long.valueOf(field.toString());
                Integer count = Integer.valueOf(val.toString());
                if (count != 0) {
                    result.put(postId, count);
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        });
        return result;
    }
}
