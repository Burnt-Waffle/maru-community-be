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
}
