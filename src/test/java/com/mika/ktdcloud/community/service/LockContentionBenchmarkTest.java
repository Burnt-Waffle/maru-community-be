package com.mika.ktdcloud.community.service;

import com.mika.ktdcloud.community.entity.Post;
import com.mika.ktdcloud.community.entity.PostStat;
import com.mika.ktdcloud.community.entity.User;
import com.mika.ktdcloud.community.repository.PostRepository;
import com.mika.ktdcloud.community.repository.PostStatRepository;
import com.mika.ktdcloud.community.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class LockContentionBenchmarkTest {

    @Autowired private PostService postService;
    @Autowired private PostViewService postViewService;
    @Autowired private PostRepository postRepository;
    @Autowired private PostStatRepository postStatRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long savedPostId;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .email("test@test.com")
                .nickname("tester")
                .password("password")
                .build();
        User savedUser = userRepository.save(user);

        Post post = Post.builder()
                .title("Initial Title")
                .content("Initial Content")
                .author(savedUser) // direct set since author field is available
                .build();
        
        Post savedPost = postRepository.save(post);
        savedPostId = savedPost.getId();
        
        PostStat stat = new PostStat(savedPost);
        postStatRepository.save(stat);
    }

    @Test
    @DisplayName("수치화 테스트: 본문 수정 중에도 통계 업데이트가 블로킹되지 않는지 확인")
    void quantifyLockContention() throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong totalLatency = new AtomicLong();

        // Thread 1: 게시글 본문에 대한 무거운 수정 (2초간 트랜잭션 유지)
        executorService.submit(() -> {
            TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
            try {
                Post post = postRepository.findById(savedPostId).get();
                post.updateTitle("Heavy Updated Title");
                postRepository.saveAndFlush(post);
                
                latch.countDown(); // Thread 2 시작 신호
                
                Thread.sleep(2000); // 2초간 락 점유 유지
                transactionManager.commit(status);
            } catch (Exception e) {
                transactionManager.rollback(status);
            }
        });

        latch.await(); // Thread 1이 락을 걸 때까지 대기

        // Thread 2: 통계 데이터(조회수) 업데이트 시도
        long startTime = System.currentTimeMillis();
        executorService.submit(() -> {
            try {
                postViewService.increaseViewCount(savedPostId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).get(); // 완료될 때까지 대기
        
        totalLatency.set(System.currentTimeMillis() - startTime);

        // 결과 출력
        System.out.println("=========================================");
        System.out.println("Result -> Stat Update Latency: " + totalLatency.get() + "ms");
        System.out.println("Wait expected if contended: 2000ms+");
        System.out.println("=========================================");

        // 본문 수정(2000ms 유지) 중에도 통계 업데이트는 즉시 완료되어야 함 (락 범위가 분리되었으므로)
        assertThat(totalLatency.get()).isLessThan(500); // 0.5초 이내 완료 보장
    }
}
