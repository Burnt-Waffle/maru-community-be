package com.mika.ktdcloud.community.service;

import com.mika.ktdcloud.community.entity.Post;
import com.mika.ktdcloud.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostViewService {
    private final StatCountManager statCountManager;

    public void increaseViewCount(Long postId) {
        statCountManager.incrementViewCount(postId);
    }
}
