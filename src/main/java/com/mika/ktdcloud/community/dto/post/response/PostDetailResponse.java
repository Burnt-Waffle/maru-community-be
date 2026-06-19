package com.mika.ktdcloud.community.dto.post.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
public class PostDetailResponse {

    private final Long id;
    private final String title;
    private final String content;
    private final List<String> imageUrls;
    private final String authorNickname;
    private final String authorProfileImageUrl;

    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant deletedAt;

    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;

    private final boolean isLikedByCurrentUser;
    private final boolean isAuthor;

    @Builder
    public PostDetailResponse(
            Long id,
            String title,
            String content,
            List<String> imageUrls,
            String authorNickname,
            String authorProfileImageUrl,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt,
            Integer viewCount,
            Integer likeCount,
            Integer commentCount,
            boolean isLikedByCurrentUser,
            boolean isAuthor
    ) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.imageUrls = imageUrls;
        this.authorNickname = authorNickname;
        this.authorProfileImageUrl = authorProfileImageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.isLikedByCurrentUser = isLikedByCurrentUser;
        this.isAuthor = isAuthor;
    }

    public void updateCounts(int viewCount, int likeCount, int commentCount) {
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
    }
}