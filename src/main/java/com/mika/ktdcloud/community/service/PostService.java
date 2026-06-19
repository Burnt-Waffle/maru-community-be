package com.mika.ktdcloud.community.service;

import com.mika.ktdcloud.community.dto.post.request.PostCreateRequest;
import com.mika.ktdcloud.community.dto.post.request.PostUpdateRequest;
import com.mika.ktdcloud.community.dto.post.response.PostDetailResponse;
import com.mika.ktdcloud.community.dto.post.response.PostLikeResponse;
import com.mika.ktdcloud.community.dto.post.response.PostSimpleResponse;
import com.mika.ktdcloud.community.dto.post.response.RealTimeStat;
import com.mika.ktdcloud.community.entity.Post;
import com.mika.ktdcloud.community.entity.PostImage;
import com.mika.ktdcloud.community.entity.PostLike;
import com.mika.ktdcloud.community.entity.PostStat;
import com.mika.ktdcloud.community.entity.User;
import com.mika.ktdcloud.community.mapper.PostMapper;
import com.mika.ktdcloud.community.repository.PostImageRepository;
import com.mika.ktdcloud.community.repository.PostLikeRepository;
import com.mika.ktdcloud.community.repository.PostRepository;
import com.mika.ktdcloud.community.repository.PostStatRepository;
import com.mika.ktdcloud.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final StringRedisTemplate redisTemplate;


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
        Slice<PostSimpleResponse> postSlice = postRepository.findPostsWithDetails(pageable);
        postSlice.forEach(dto -> {
            RealTimeStat rts =
                    statCountManager.getRealTimeStats(dto.getId(), dto.getViewCount(), dto.getLikeCount(), dto.getCommentCount());
            dto.updateCounts(rts.getViewCount(), rts.getLikeCount(), rts.getCommentCount());
        });
        return postSlice;
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

        PostDetailResponse response = postMapper.toDetailResponse(post, isAuthor, isLiked);
        com.mika.ktdcloud.community.entity.PostStat stat = post.getStat();
        int dbViews = stat != null ? stat.getViewCount() : 0;
        int dbLikes = stat != null ? stat.getLikeCount() : 0;
        int dbComments = stat != null ? stat.getCommentCount() : 0;

        RealTimeStat realTimeStat =
                statCountManager.getRealTimeStats(id, dbViews, dbLikes, dbComments);

        response.updateCounts(realTimeStat.getViewCount(), realTimeStat.getLikeCount(), realTimeStat.getCommentCount());
        return response;
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

        // 최신 수치는 Redis의 실시간 통계 카운트를 적용하여 반환합니다.
        int dbViews = 0;
        int dbLikes = 0;
        int dbComments = 0;

        Optional<PostStat> statOpt = postStatRepository.findById(postId);
        if (statOpt.isPresent()) {
            PostStat stat = statOpt.get();
            dbViews = stat.getViewCount();
            dbLikes = stat.getLikeCount();
            dbComments = stat.getCommentCount();
        }

        RealTimeStat rts =
                statCountManager.getRealTimeStats(postId, dbViews, dbLikes, dbComments);

        return postMapper.toLikeResponse(rts.getLikeCount(), isLiked);
    }

    // 인기글 조회 (최근 7일, 상위 5개)
    @Transactional(readOnly = true)
    public List<PostSimpleResponse> getPopularPosts() {
        Instant limitInstant = Instant.now().minus(Duration.ofDays(7));

        // 1. Redis Sorted Set에서 인기글 ID 목록 가져오기 (상위 100개 조회)
        Set<String> popularPostIds = redisTemplate.opsForZSet().reverseRange("posts:popular", 0, 99);

        if (popularPostIds == null || popularPostIds.isEmpty()) {
            // Redis 랭킹 캐시 콜드스타트 -> DB에서 구한 뒤 캐시 워밍업 수행
            log.info("Popular posts ranking is empty in Redis. Falling back to DB.");
            List<PostSimpleResponse> dbPopular = postRepository.findPopularPosts(limitInstant, 5);

            // Redis ZSET에 스코어를 채워넣음 (동시 워밍업) 및 개별 통계 워밍업
            dbPopular.forEach(post -> {
                int score = post.getLikeCount() * 5 + post.getCommentCount() * 3 + post.getViewCount();
                redisTemplate.opsForZSet().add("posts:popular", String.valueOf(post.getId()), score);

                RealTimeStat realTimeStat =
                        statCountManager.getRealTimeStats(post.getId(), post.getViewCount(), post.getLikeCount(), post.getCommentCount());
                post.updateCounts(realTimeStat.getViewCount(), realTimeStat.getLikeCount(), realTimeStat.getCommentCount());
            });

            return dbPopular;
        }

        // 2. String ID 목록을 Long ID 목록으로 파싱
        List<Long> ids = popularPostIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        // 3. ID와 7일 조건으로 DB에서 상세 정보 조회
        List<PostSimpleResponse> posts = postRepository.findPopularPostsByIds(ids, limitInstant);

        // --- Redis 오버라이드 로직 추가 ---
        posts.forEach(post -> {
            RealTimeStat realTimeStat =
                    statCountManager.getRealTimeStats(post.getId(), post.getViewCount(), post.getLikeCount(), post.getCommentCount());
            post.updateCounts(realTimeStat.getViewCount(), realTimeStat.getLikeCount(), realTimeStat.getCommentCount());
        });

        // 4. Redis의 인기글 정렬 순서대로 DB 조회 결과 정렬 (IN 쿼리는 순서 보장이 안 됨)
        Map<Long, PostSimpleResponse> postMap = posts.stream()
                .collect(Collectors.toMap(PostSimpleResponse::getId, Function.identity()));

        return ids.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .limit(5) // 상위 5개만 최종 슬라이싱
                .collect(Collectors.toList());
    }
}

