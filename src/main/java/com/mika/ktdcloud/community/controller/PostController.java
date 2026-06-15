package com.mika.ktdcloud.community.controller;

import com.mika.ktdcloud.community.dto.post.request.PostCreateRequest;
import com.mika.ktdcloud.community.dto.post.request.PostUpdateRequest;
import com.mika.ktdcloud.community.dto.post.response.PostDetailResponse;
import com.mika.ktdcloud.community.dto.post.response.PostLikeResponse;
import com.mika.ktdcloud.community.dto.post.response.PostSimpleResponse;
import com.mika.ktdcloud.community.service.PostService;
import com.mika.ktdcloud.community.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // 게시글 생성
    @PostMapping
    public ResponseEntity<PostSimpleResponse> createPost(
            @RequestBody @Valid PostCreateRequest request,
            HttpServletRequest httpServletRequest
    ) {
        Long authorId = SecurityUtil.getCurrentUserId(httpServletRequest);
        PostSimpleResponse response = postService.createPost(request, authorId);
        URI location = URI.create("/api/v1/posts/" + response.getId());
        return ResponseEntity.created(location).body(response);
    }

    // 게시글 목록 조회 (무한 스크롤링)
    @GetMapping
    public ResponseEntity<Slice<PostSimpleResponse>> getPostList(
            @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Slice<PostSimpleResponse> responseSlice = postService.getPostList(pageable);
        return ResponseEntity.ok(responseSlice);
    }

    // 게시글 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<PostDetailResponse> getPost(@PathVariable Long id, HttpServletRequest httpServletRequest) {
        Long currentUserId = SecurityUtil.getCurrentUserId(httpServletRequest);
        PostDetailResponse response = postService.getDetailPost(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    // 게시글 수정
    @PatchMapping("/{id}")
    public ResponseEntity<PostSimpleResponse> updatePost(
            @PathVariable("id") Long postId,
            @RequestBody @Valid PostUpdateRequest request,
            HttpServletRequest httpServletRequest
    ) throws AccessDeniedException {
        Long currentUserId = SecurityUtil.getCurrentUserId(httpServletRequest);
        PostSimpleResponse response = postService.updatePost(request, postId, currentUserId);
        return ResponseEntity.ok(response);
    }

    // 게시글 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id, HttpServletRequest httpServletRequest) throws AccessDeniedException {
        Long currentUser = SecurityUtil.getCurrentUserId(httpServletRequest);
        postService.deletePost(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    // 좋아요 토글
    @PostMapping("/{postId}/like")
    public ResponseEntity<PostLikeResponse> togglePostLike(
            @PathVariable Long postId,
            HttpServletRequest httpServletRequest
    ) {
        Long currentUserId = SecurityUtil.getCurrentUserId(httpServletRequest);
        PostLikeResponse response = postService.togglePostLike(postId,currentUserId);
        return ResponseEntity.ok(response);
    }

    // 인기글 목록 조회
    @GetMapping("/popular")
    public ResponseEntity<List<PostSimpleResponse>> getPopularPosts() {
        List<PostSimpleResponse> response = postService.getPopularPosts();
        return ResponseEntity.ok(response);
    }
}
