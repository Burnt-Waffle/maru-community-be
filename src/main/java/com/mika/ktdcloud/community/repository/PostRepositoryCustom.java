package com.mika.ktdcloud.community.repository;

import com.mika.ktdcloud.community.dto.post.response.PostSimpleResponse;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

// 사용자 정의 리포지토리
public interface PostRepositoryCustom {
    Slice<PostSimpleResponse> findPostsWithDetails(Pageable pageable);
    List<PostSimpleResponse> findPopularPosts(Instant limitInstant, int limitSize);
}
