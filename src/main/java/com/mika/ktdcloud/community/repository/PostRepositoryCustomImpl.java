package com.mika.ktdcloud.community.repository;

import com.mika.ktdcloud.community.dto.post.response.PostSimpleResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.core.types.dsl.Expressions; 
import com.querydsl.core.types.dsl.NumberExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.time.Instant;

import static com.mika.ktdcloud.community.entity.QPost.post;
import static com.mika.ktdcloud.community.entity.QPostStat.postStat;
import static com.mika.ktdcloud.community.entity.QUser.user;

public class PostRepositoryCustomImpl implements PostRepositoryCustom {

    private final String cloudFrontUrl;
    private final JPAQueryFactory queryFactory;

    public PostRepositoryCustomImpl(
            JPAQueryFactory queryFactory,
            @Value("${aws.cloud-front.url}") String cloudFrontUrl
    ) {
        this.queryFactory = queryFactory;
        this.cloudFrontUrl = cloudFrontUrl;
    }

    @Override
    public Slice<PostSimpleResponse> findPostsWithDetails(Pageable pageable) {
        // 게시글 목록 조회 쿼리
        List<PostSimpleResponse> content = queryFactory
                .select(Projections.constructor(PostSimpleResponse.class,
                        post.id,
                        post.title,
                        post.thumbnailUrl,
                        user.nickname,
                        user.profileImageUrl,
                        post.createdAt,
                        post.updatedAt,
                        postStat.viewCount,
                        postStat.likeCount,
                        postStat.commentCount
                ))
                .from(post)
                .join(post.author, user)
                .join(post.stat, postStat)
                .where(post.deletedAt.isNull())
                .orderBy(post.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1) // 요청한 페이지 크기보다 하나 더 많이 조회
                .fetch();

        content.forEach(dto -> dto.updateUrls(cloudFrontUrl));

        // 다음 페이지 존재 여부 확인
        boolean hasNextPage = false;
        if (content.size() > pageable.getPageSize()) { // 조회된 데이터 수가 요청한 페이지 수보다 많다면 다음 페이지가 있다고 판단함
            content.remove(pageable.getPageSize());
            hasNextPage = true;
        }

        return new SliceImpl<>(content, pageable, hasNextPage);
    }

    @Override
    public List<PostSimpleResponse> findPopularPosts(java.time.Instant limitInstant, int limitSize) {
        NumberExpression<Integer> score = postStat.likeCount.multiply(5)
                .add(postStat.commentCount.multiply(3))
                .add(postStat.viewCount);

        List<PostSimpleResponse> content = queryFactory
                .select(Projections.constructor(PostSimpleResponse.class,
                        post.id,
                        post.title,
                        post.thumbnailUrl,
                        user.nickname,
                        user.profileImageUrl,
                        post.createdAt,
                        post.updatedAt,
                        postStat.viewCount,
                        postStat.likeCount,
                        postStat.commentCount
                ))
                .from(post)
                .join(post.author, user)
                .join(post.stat, postStat)
                .where(
                        post.createdAt.goe(limitInstant),
                        post.deletedAt.isNull()
                )
                .orderBy(
                        score.desc(),
                        post.createdAt.desc()
                )
                .limit(limitSize)
                .fetch();

        content.forEach(dto -> dto.updateUrls(cloudFrontUrl));

        return content;
    }

    @Override
    public List<PostSimpleResponse> findPopularPostsByIds(List<Long> ids, java.time.Instant limitInstant) {
        if (ids == null || ids.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<PostSimpleResponse> content = queryFactory
                .select(Projections.constructor(PostSimpleResponse.class,
                        post.id,
                        post.title,
                        post.thumbnailUrl,
                        user.nickname,
                        user.profileImageUrl,
                        post.createdAt,
                        post.updatedAt,
                        postStat.viewCount,
                        postStat.likeCount,
                        postStat.commentCount
                ))
                .from(post)
                .join(post.author, user)
                .join(post.stat, postStat)
                .where(
                        post.id.in(ids),
                        post.createdAt.goe(limitInstant),
                        post.deletedAt.isNull()
                )
                .fetch();

        content.forEach(dto -> dto.updateUrls(cloudFrontUrl));

        return content;
    }
}
