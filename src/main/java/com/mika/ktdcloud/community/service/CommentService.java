package com.mika.ktdcloud.community.service;

import com.mika.ktdcloud.community.dto.comment.request.CommentCreateRequest;
import com.mika.ktdcloud.community.dto.comment.request.CommentUpdateRequest;
import com.mika.ktdcloud.community.dto.comment.response.CommentResponse;
import com.mika.ktdcloud.community.entity.Comment;
import com.mika.ktdcloud.community.entity.Post;
import com.mika.ktdcloud.community.entity.User;
import com.mika.ktdcloud.community.mapper.CommentMapper;
import com.mika.ktdcloud.community.repository.CommentRepository;
import com.mika.ktdcloud.community.repository.PostRepository;
import com.mika.ktdcloud.community.repository.PostStatRepository;
import com.mika.ktdcloud.community.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;
    private final PostStatRepository postStatRepository; // PostStatRepository 주입
    private final EntityManager entityManager;

    // 댓글 생성
    @Transactional
    public CommentResponse createComment(CommentCreateRequest request, Long postId, Long authorId) {
        Post post = postRepository.findWithLockById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found."));
        User author = userRepository.getReferenceById(authorId);
        Comment newComment = commentMapper.toEntity(request, post, author);
        Comment savedComment = commentRepository.save(newComment);

        post.getStat().increaseCommentCount();
        postStatRepository.save(post.getStat()); // 변경된 PostStat을 명시적으로 저장

        return commentMapper.toResponse(savedComment, true);
    }

    @Transactional(readOnly = true)
    public Slice<CommentResponse> getComment(Long postId, Long currentUserId, Pageable pageable) {
        Slice<Comment> commentSlice = commentRepository.findTopCommentsByPostIdWithAuthor(postId, pageable);

        return commentSlice.map(comment -> {
            boolean isAuthor = comment.getAuthor().getId().equals(currentUserId);
            return commentMapper.toResponse(comment, isAuthor);
        });
    }

    // 댓글 수정
    @Transactional
    public CommentResponse updateComment(CommentUpdateRequest request, Long commentId, Long currentUserId) throws AccessDeniedException {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found."));
        if (!comment.getAuthor().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Only author can update comment.");
        }
        comment.update(request.getContent());
        return commentMapper.toResponse(comment, true);
    }

    // 댓글 삭제
    @Transactional
    public void deleteComment(Long id, Long currentUserId) throws AccessDeniedException {
        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("comment not found."));

        if (!comment.getAuthor().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Only the author of this comment can delete.");
        }

        Long postId = comment.getPost().getId();
        Post post = postRepository.findWithLockById(postId)
                        .orElseThrow(() -> new IllegalArgumentException("Post not found."));

        entityManager.refresh(comment);

        if (comment.getDeletedAt() != null) {
            return;
        }

        comment.softDelete();
        post.getStat().decreaseCommentCount();
        postStatRepository.save(post.getStat()); // 변경된 PostStat을 명시적으로 저장
    }
}
