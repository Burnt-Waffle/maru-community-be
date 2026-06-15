package com.mika.ktdcloud.community.service;

import com.mika.ktdcloud.community.repository.PostStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 메모리에 쌓인 조회수 및 좋아요 수치를 주기적으로 DB에 반영하는 스케줄러.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostStatScheduler {
    private final StatCountManager statCountManager;
    private final PostStatRepository postStatRepository;

    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    @Transactional
    public void flushStats() {
        // 1. 조회수 벌크 업데이트
        Map<Long, Integer> views = statCountManager.getAndResetViewCounts();
        if (!views.isEmpty()) {
            log.info("Batch updating view counts for {} posts", views.size());
            views.forEach(postStatRepository::addViewCount);
        }

        // 2. 좋아요 벌크 업데이트
        Map<Long, Integer> likes = statCountManager.getAndResetLikeCounts();
        if (!likes.isEmpty()) {
            log.info("Batch updating like counts for {} posts", likes.size());
            likes.forEach(postStatRepository::addLikeCount);
        }
    }
}
