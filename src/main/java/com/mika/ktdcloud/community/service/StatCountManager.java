package com.mika.ktdcloud.community.service;

import com.mika.ktdcloud.community.dto.post.response.RealTimeStat;
import com.mika.ktdcloud.community.entity.Post;
import com.mika.ktdcloud.community.entity.PostStat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 게시글 조회수 및 좋아요 수치를 Redis에서 관리하는 클래스.
 * DB 락 경합을 피하기 위해 Redis Hash 기반의 Write-behind 패턴을 사용하며,
 * 실시간 인기글 집계를 위해 Redis Sorted Set(ZSET)을 사용합니다.
 * 또한 Redis의 실시간 수치를 원본으로 활용하기 위해 개별 게시글의 현재 총합 수치를 캐싱합니다.
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

    // 개별 게시글 실시간 통계 키 생성 유틸
    private String getPostStatsKey(Long postId) {
        return "post:" + postId + ":stats";
    }

    public void incrementViewCount(Long postId) {
        String postKey = getPostStatsKey(postId);
        // 1. 실시간 개별 수치 오버라이드용 카운터 갱신 (HMGET 대상)
        redisTemplate.opsForHash().increment(postKey, "viewCount", 1);
        redisTemplate.expire(postKey, Duration.ofDays(7)); // TTL 연장

        // 2. RDB 배치 반영용 Hash 카운터 증가
        redisTemplate.opsForHash().increment(VIEW_COUNT_KEY, String.valueOf(postId), 1);
        // 3. 실시간 Sorted Set 랭킹 Score 갱신
        redisTemplate.opsForZSet().incrementScore(POPULAR_RANK_KEY, String.valueOf(postId), VIEW_SCORE_WEIGHT);
    }

    public void incrementLikeCount(Long postId) {
        String postKey = getPostStatsKey(postId);
        // 1. 실시간 개별 수치 오버라이드용 카운터 갱신
        redisTemplate.opsForHash().increment(postKey, "likeCount", 1);
        redisTemplate.expire(postKey, Duration.ofDays(7));

        // 2. RDB 배치 반영용 Hash 카운터 증가
        redisTemplate.opsForHash().increment(LIKE_COUNT_KEY, String.valueOf(postId), 1);
        // 3. 실시간 Sorted Set 랭킹 Score 갱신
        redisTemplate.opsForZSet().incrementScore(POPULAR_RANK_KEY, String.valueOf(postId), LIKE_SCORE_WEIGHT);
    }

    public void decrementLikeCount(Long postId) {
        String postKey = getPostStatsKey(postId);
        // 1. 실시간 개별 수치 오버라이드용 카운터 감산
        redisTemplate.opsForHash().increment(postKey, "likeCount", -1);
        redisTemplate.expire(postKey, Duration.ofDays(7));

        // 2. RDB 배치 반영용 Hash 카운터 증가
        redisTemplate.opsForHash().increment(LIKE_COUNT_KEY, String.valueOf(postId), -1);
        // 3. 실시간 Sorted Set 랭킹 Score 갱신
        redisTemplate.opsForZSet().incrementScore(POPULAR_RANK_KEY, String.valueOf(postId), -LIKE_SCORE_WEIGHT);
    }

    public void incrementCommentCount(Long postId) {
        String postKey = getPostStatsKey(postId);
        // 1. 실시간 개별 수치 오버라이드용 카운터 갱신
        redisTemplate.opsForHash().increment(postKey, "commentCount", 1);
        redisTemplate.expire(postKey, Duration.ofDays(7));

        // 2. 실시간 Sorted Set 랭킹 Score 갱신
        redisTemplate.opsForZSet().incrementScore(POPULAR_RANK_KEY, String.valueOf(postId), COMMENT_SCORE_WEIGHT);
    }

    public void decrementCommentCount(Long postId) {
        String postKey = getPostStatsKey(postId);
        // 1. 실시간 개별 수치 오버라이드용 카운터 감산
        redisTemplate.opsForHash().increment(postKey, "commentCount", -1);
        redisTemplate.expire(postKey, Duration.ofDays(7));

        // 2. 실시간 Sorted Set 랭킹 Score 감산
        redisTemplate.opsForZSet().incrementScore(POPULAR_RANK_KEY, String.valueOf(postId), -COMMENT_SCORE_WEIGHT);
    }

    public RealTimeStat getRealTimeStats(Long postId, int dbViews, int dbLikes, int dbComments) {
        String key = getPostStatsKey(postId);
        Map<Object, Object> rawEntries = redisTemplate.opsForHash().entries(key);

        if (rawEntries.isEmpty() || !rawEntries.containsKey("initialized")) {
            // 캐시 미스 또는 부분 캐시 -> DB 원본 값 + 아직 DB에 반영되지 않은 Redis의 누적 변동량(Delta)으로 Redis 워밍업
            Object unflushedViewObj = redisTemplate.opsForHash().get(VIEW_COUNT_KEY, String.valueOf(postId));
            Object unflushedLikeObj = redisTemplate.opsForHash().get(LIKE_COUNT_KEY, String.valueOf(postId));

            int deltaViews = unflushedViewObj != null ? Integer.parseInt(unflushedViewObj.toString()) : 0;
            int deltaLikes = unflushedLikeObj != null ? Integer.parseInt(unflushedLikeObj.toString()) : 0;
            int deltaComments = 0; // 댓글은 DB에 실시간 반영되므로 delta가 없음

            int realViews = dbViews + deltaViews;
            int realLikes = dbLikes + deltaLikes;
            int realComments = dbComments + deltaComments;

            Map<String, String> initData = new HashMap<>();
            initData.put("viewCount", String.valueOf(realViews));
            initData.put("likeCount", String.valueOf(realLikes));
            initData.put("commentCount", String.valueOf(realComments));
            initData.put("initialized", "true");

            redisTemplate.opsForHash().putAll(key, initData);
            redisTemplate.expire(key, Duration.ofDays(7)); // 7일의 TTL 부여

            return new RealTimeStat(realViews, realLikes, realComments);
        }

        int viewCount = Math.max(0, Integer.parseInt(rawEntries.getOrDefault("viewCount", "0").toString()));
        int likeCount = Math.max(0, Integer.parseInt(rawEntries.getOrDefault("likeCount", "0").toString()));
        int commentCount = Math.max(0, Integer.parseInt(rawEntries.getOrDefault("commentCount", "0").toString()));

        return new RealTimeStat(viewCount, likeCount, commentCount);
    }

    // --- 다건 실시간 통계 조회 및 캐시 워밍업 (Override용) ---

    public Map<Long, RealTimeStat> getRealTimeStatsBulk(List<Long> postIds, List<Post> posts) {
        Map<Long, RealTimeStat> result = new HashMap<>();
        for (Post post : posts) {
            PostStat stat = post.getStat();
            RealTimeStat rts = getRealTimeStats(post.getId(), stat.getViewCount(), stat.getLikeCount(), stat.getCommentCount());
            result.put(post.getId(), rts);
        }
        return result;
    }

    // --- ZSET 랭킹 수동 워밍업 지원 ---
    public void warmUpPopularZSet(Long postId, int score) {
        redisTemplate.opsForZSet().add(POPULAR_RANK_KEY, String.valueOf(postId), score);
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

        // 키가 존재하지 않으면 renameNX를 실행하지 않고 조기 종료 (ERR no such key 에러 방지)
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            return new HashMap<>();
        }

        // 원자적으로 키 이름을 RENAME 하여, 백업 스레드가 처리하는 동안 신규 유입되는 증감량 누수 차단
        Boolean renamed = false;
        try {
            renamed = redisTemplate.renameIfAbsent(key, tempKey);
        } catch (Exception e) {
            // 다른 인스턴스에서 이미 처리하여 키가 사라진 경우(ERR no such key) 등을 안전하게 방어
            if (e.getMessage() != null && e.getMessage().contains("no such key")) {
                log.debug("Source key {} does not exist in Redis (might be processed by other instance). Skipping.", key);
            } else {
                log.error("Failed to rename key: {}", key, e);
            }
            return new HashMap<>();
        }

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
