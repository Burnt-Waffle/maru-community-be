package com.mika.ktdcloud.community.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class DummyDataService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void createDummyPosts(int totalCount, int recentCount) {
        if (recentCount > totalCount) {
            throw new IllegalArgumentException("recentCount cannot be greater than totalCount");
        }

        log.info("Starting dummy post creation: total={}, recent={}", totalCount, recentCount);

        // 1. 기존 유저 확인 및 더미 유저 생성
        Long userId;
        List<Long> userIds = jdbcTemplate.queryForList("SELECT user_id FROM users LIMIT 1", Long.class);
        if (userIds.isEmpty()) {
            log.info("No user found in DB. Creating a dummy user.");
            jdbcTemplate.update(
                    "INSERT INTO users (email, password, nickname, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    "dummy_author@test.com",
                    "$2a$10$DUMMY_ENCRYPTED_PASSWORD_VALUE_1234567890",
                    "DummyAuthor",
                    Instant.now(),
                    Instant.now()
            );
            userId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            userId = userIds.get(0);
        }

        // 2. 현재 posts 테이블의 최대 post_id 조회 (Explicit PK 매핑을 위함)
        Long maxPostId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(post_id), 0) FROM posts", Long.class);

        // 3. Batch Insert 데이터 준비
        String postSql = "INSERT INTO posts (post_id, title, content, user_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        String statSql = "INSERT INTO post_stats (post_id, view_count, like_count, comment_count) VALUES (?, ?, ?, ?)";

        List<Object[]> postBatch = new ArrayList<>();
        List<Object[]> statBatch = new ArrayList<>();

        Random random = new Random();
        int batchSize = 1000;

        for (int i = 1; i <= totalCount; i++) {
            Long currentPostId = maxPostId + i;
            String title = "Dummy Post Title " + currentPostId;
            String content = "This is dummy content for post " + currentPostId + ". Generated for load testing purposes.";

            // 4. 생성 시점 분리 (recentCount만큼은 7일 이내, 나머지는 7일 이전)
            Instant createdAt;
            if (i <= recentCount) {
                // 1 ~ 6일 전 랜덤
                createdAt = Instant.now().minus(Duration.ofHours(24 + random.nextInt(24 * 5)));
            } else {
                // 8 ~ 30일 전 랜덤
                createdAt = Instant.now().minus(Duration.ofHours(24 * 8 + random.nextInt(24 * 22)));
            }

            postBatch.add(new Object[]{currentPostId, title, content, userId, createdAt, createdAt});

            // 5. 통계 데이터 랜덤 부여 (성능 쿼리에 다양성을 줌)
            int viewCount = random.nextInt(1000);
            int likeCount = random.nextInt(100);
            int commentCount = random.nextInt(20);

            statBatch.add(new Object[]{currentPostId, viewCount, likeCount, commentCount});

            // 6. batchSize 단위로 분할 삽입
            if (i % batchSize == 0 || i == totalCount) {
                jdbcTemplate.batchUpdate(postSql, postBatch);
                jdbcTemplate.batchUpdate(statSql, statBatch);
                postBatch.clear();
                statBatch.clear();
                log.info("Inserted dummy data batch: {}/{}", i, totalCount);
            }
        }

        log.info("Successfully finished dummy post creation.");
    }

    @Transactional
    public void createDummyUsers(int count) {
        log.info("Starting dummy user creation: count={}", count);
        String sql = "INSERT IGNORE INTO users (user_id, email, password, nickname, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        List<Object[]> batch = new ArrayList<>();
        int batchSize = 1000;
        Instant now = Instant.now();

        for (int i = 1; i <= count; i++) {
            Long userId = (long) i;
            String email = "dummy_user_" + userId + "@test.com";
            String password = "$2a$10$DUMMY_ENCRYPTED_PASSWORD_VALUE_1234567890";
            String nickname = "User" + userId;

            batch.add(new Object[]{userId, email, password, nickname, now, now});

            if (i % batchSize == 0 || i == count) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
                log.info("Inserted dummy users batch: {}/{}", i, count);
            }
        }
        log.info("Successfully finished dummy user creation.");
    }
}

