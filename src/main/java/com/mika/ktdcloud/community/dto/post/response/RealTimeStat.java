package com.mika.ktdcloud.community.dto.post.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RealTimeStat {
    private final int viewCount;
    private final int likeCount;
    private final int commentCount;
}
