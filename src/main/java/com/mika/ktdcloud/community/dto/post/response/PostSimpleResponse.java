package com.mika.ktdcloud.community.dto.post.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PostSimpleResponse {

    private final Long id;
    private final String title;
    private String thumbnailUrl;
    private final String authorNickname;
    private String authorProfileImageUrl;

    private final Instant createdAt;
    private final Instant updatedAt;

    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;

    @Builder
    public PostSimpleResponse(
            Long id,
            String title,
            String thumbnailUrl,
            String authorNickname,
            String authorProfileImageUrl,
            Instant createdAt,
            Instant updatedAt,
            Integer viewCount,
            Integer likeCount,
            Integer commentCount
    ) {
        this.id = id;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.authorNickname = authorNickname;
        this.authorProfileImageUrl = authorProfileImageUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
    }

    public void updateUrls(String cloudFrontUrl) {
        if (this.authorProfileImageUrl != null && !this.authorProfileImageUrl.startsWith("http")) {
            this.authorProfileImageUrl = cloudFrontUrl + this.authorProfileImageUrl;
        }
        if (this.thumbnailUrl != null && !this.thumbnailUrl.startsWith("http")) {
            this.thumbnailUrl = cloudFrontUrl + this.thumbnailUrl;
        }
    }

    public void updateCounts(int viewCount, int likeCount, int commentCount) {
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
    }

}

