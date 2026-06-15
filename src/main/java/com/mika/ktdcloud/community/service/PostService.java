package com.mika.ktdcloud.community.service;

import com.mika.ktdcloud.community.dto.post.request.PostCreateRequest;
import com.mika.ktdcloud.community.dto.post.request.PostUpdateRequest;
import com.mika.ktdcloud.community.dto.post.response.PostDetailResponse;
import com.mika.ktdcloud.community.dto.post.response.PostLikeResponse;
import com.mika.ktdcloud.community.dto.post.response.PostSimpleResponse;
import com.mika.ktdcloud.community.entity.Post;
import com.mika.ktdcloud.community.entity.PostImage;
import com.mika.ktdcloud.community.entity.PostLike;
import com.mika.ktdcloud.community.entity.User;
import com.mika.ktdcloud.community.mapper.PostMapper;
import com.mika.ktdcloud.community.repository.PostImageRepository;
import com.mika.ktdcloud.community.repository.PostLikeRepository;
import com.mika.ktdcloud.community.repository.PostRepository;
import com.mika.ktdcloud.community.repository.PostStatRepository;
import com.mika.ktdcloud.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostImageRepository postImageRepository;
    private final PostViewService postViewService;
    private final PostMapper postMapper;
    private final PostImageService postImageService;
    private final PostStatRepository postStatRepository;
    private final StatCountManager statCountManager;

    // 게시글 생성
    @Transactional
    public PostSimpleResponse createPost(PostCreateRequest request, Long authorId) {
        User author = userRepository.getReferenceById(authorId);
        Post newPost = postMapper.toEntity(request, author);
        Post savedPost = postRepository.save(newPost);

        if (request.getImageUrls() != null && request.getImageUrls().size() > 3) {
            throw new IllegalArgumentException("이미지는 최대 3장까지 첨부할 수 있습니다.");
        }

        List<String> imageUrls = request.getImageUrls();

        if(imageUrls != null && !imageUrls.isEmpty()) {
            for (int i=0; i<imageUrls.size(); i++){
                String imageUrl = imageUrls.get(i);
                PostImage savedImage = postImageService.saveImageUrl(imageUrl, savedPost, i);

                savedPost.addImage(savedImage);
            }
        }

        if(!savedPost.getImages().isEmpty()) {
            PostImage firstImage = savedPost.getImages().getFirst();
            savedPost.setThumbnail(firstImage);
        }

        return postMapper.toSimpleResponse(savedPost);
    }

    // 게시글 목록 조회 (무한 스크롤링)
    @Transactional(readOnly = true)
    public Slice<PostSimpleResponse> getPostList(Pageable pageable) {
        return postRepository.findPostsWithDetails(pageable);
    }

    //게시글 상세 조회
    @Transactional(readOnly = true)
    public PostDetailResponse getDetailPost(Long id, Long currentUserId) {
        Post post = postRepository.findByIdWithImages(id).
                orElseThrow(() -> new IllegalArgumentException("Post not found."));

        boolean isAuthor = post.getAuthor().getId().equals(currentUserId);

        boolean isLiked = false;
        if (currentUserId != null) {
            isLiked = postLikeRepository.existsByPostIdAndUserIdAndDeletedAtIsNull(id, currentUserId);
        }

        postViewService.increaseViewCount(id);
        return postMapper.toDetailResponse(post, isAuthor, isLiked);
    }

    // 게시글 수정
    @Transactional
    public PostSimpleResponse updatePost(
            PostUpdateRequest request,
            Long postId,
            Long currentUserId
    ) throws AccessDeniedException {
        Post post = postRepository.findByIdWithImages(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found."));

        if (!post.getAuthor().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Only author can update post.");
        }

        if (request.getImageUrls() != null && request.getImageUrls().size() > 3) {
            throw new IllegalArgumentException("이미지는 최대 3장까지 첨부할 수 있습니다.");
        }

        List<PostImage> oldImages = post.getImages();
        if (oldImages != null) {
            for (PostImage oldImage : oldImages) {
                oldImage.softDelete();
            }
            postImageRepository.saveAll(oldImages);
        }

        // 새로 추가된 이미지
        List<String> newImageUrls = request.getImageUrls();
        // 기존 이미지 처리
        List<PostImage> newImages = new ArrayList<>();

        if (newImageUrls != null && !newImageUrls.isEmpty()) {
            for (int i = 0; i < newImageUrls.size(); i++) {
                PostImage newImage = postImageService.saveImageUrl(newImageUrls.get(i), post, i);
                newImages.add(newImage);
            }
        }

        post.update(request, newImages);

        return postMapper.toSimpleResponse(post);
    }

    // 게시글 삭제
    @Transactional
    public void deletePost(Long id, Long currentUserId) throws AccessDeniedException {
        Post post = postRepository.findByIdWithImages(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found."));
        if (!post.getAuthor().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Only the author of this post can delete.");
        }
        post.softDelete();
    }

    // 좋아요 토글
    @Transactional
    public PostLikeResponse togglePostLike(Long postId, Long currentUserId) {
        // getReferenceById는 실제 SELECT를 날리지 않고 프록시만 생성함.
        // 이를 통해 부모(Post) 행의 비관적 락(X-Lock)을 전혀 기다리지 않고 통계 데이터만 업데이트 가능.
        Post post = postRepository.getReferenceById(postId);
        User user = userRepository.getReferenceById(currentUserId);

        Optional<PostLike> existingLike = postLikeRepository.findByPostAndUser(post, user);

        boolean isLiked;

        if(existingLike.isPresent()) {
            PostLike like = existingLike.get();
            if (like.getDeletedAt() == null) {
                like.softDelete();
                statCountManager.decrementLikeCount(postId);
                isLiked = false;
            } else {
                like.restore();
                statCountManager.incrementLikeCount(postId);
                isLiked = true;
            }
        } else {
            PostLike newLike = PostLike.create(user, post);
            postLikeRepository.save(newLike);
            statCountManager.incrementLikeCount(postId);
            isLiked = true;
        }

        // 최신 수치는 DB와 메모리를 합산해서 반환할 수 있으나, 
        // 성능을 위해 DB 수치만 반환하거나 클라이언트에서 예측 처리하도록 함.
        int currentLikeCount = postStatRepository.findById(postId)
                .map(com.mika.ktdcloud.community.entity.PostStat::getLikeCount)
                .orElse(0);

        return postMapper.toLikeResponse(currentLikeCount, isLiked);
    }

    // 인기글 조회 (최근 7일, 상위 5개)
    @Transactional(readOnly = true)
    public List<PostSimpleResponse> getPopularPosts() {
        Instant limitInstant = Instant.now().minus(Duration.ofDays(7));
        return postRepository.findPopularPosts(limitInstant, 5);
    }
}
