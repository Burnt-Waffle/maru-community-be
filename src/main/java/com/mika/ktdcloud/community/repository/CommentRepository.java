package com.mika.ktdcloud.community.repository;

import com.mika.ktdcloud.community.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {
    Optional<Comment> findByIdAndDeletedAtIsNull(Long id);
}
