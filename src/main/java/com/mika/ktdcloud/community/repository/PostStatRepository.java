package com.mika.ktdcloud.community.repository;

import com.mika.ktdcloud.community.entity.PostStat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostStatRepository extends JpaRepository<PostStat, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PostStat s WHERE s.id = :postId")
    Optional<PostStat> findWithLockByPostId(@Param("postId") Long postId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE PostStat s SET s.likeCount = s.likeCount + :count WHERE s.id = :postId")
    void addLikeCount(@Param("postId") Long postId, @Param("count") int count);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE PostStat s SET s.viewCount = s.viewCount + :count WHERE s.id = :postId")
    void addViewCount(@Param("postId") Long postId, @Param("count") int count);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE PostStat s SET s.viewCount = s.viewCount + 1 WHERE s.id = :postId")
    void incrementViewCount(@Param("postId") Long postId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE PostStat s SET s.likeCount = s.likeCount + 1 WHERE s.id = :postId")
    void incrementLikeCount(@Param("postId") Long postId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE PostStat s SET s.likeCount = CASE WHEN s.likeCount > 0 THEN s.likeCount - 1 ELSE 0 END WHERE s.id = :postId")
    void decrementLikeCount(@Param("postId") Long postId);
}
