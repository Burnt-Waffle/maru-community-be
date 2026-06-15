package com.mika.ktdcloud.community.service;

import com.mika.ktdcloud.community.dto.post.request.PostCreateRequest;
import com.mika.ktdcloud.community.dto.post.response.PostDetailResponse;
import com.mika.ktdcloud.community.dto.post.response.PostSimpleResponse;
import com.mika.ktdcloud.community.entity.Post;
import com.mika.ktdcloud.community.entity.User;
import com.mika.ktdcloud.community.mapper.PostMapper;
import com.mika.ktdcloud.community.repository.PostImageRepository;
import com.mika.ktdcloud.community.repository.PostLikeRepository;
import com.mika.ktdcloud.community.repository.PostRepository;
import com.mika.ktdcloud.community.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class PostServiceTest {

    @InjectMocks
    private PostService postService;

    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private PostImageRepository postImageRepository;
    @Mock private PostMapper postMapper;
    @Mock private PostImageService postImageService;
    @Mock private PostViewService postViewService;


    @Test
    @DisplayName("Create Post Test")
    void createPost_Success() {
        // given
        Long authorId = 1L;
        PostCreateRequest request = PostCreateRequest.builder()
                .title("test post title")
                .content("test post content")
                .build();

        User mockUser = Mockito.mock(User.class);
        Post mockPost = Mockito.mock(Post.class);

        PostSimpleResponse mockResponse = PostSimpleResponse.builder()
                .id(1L)
                .title("test post title")
                .build();

        given(userRepository.getReferenceById(authorId)).willReturn(mockUser);
        given(postMapper.toEntity(request, mockUser)).willReturn(mockPost);
        given(postRepository.save(any(Post.class))).willReturn(mockPost);
        given(postMapper.toSimpleResponse(any(Post.class))).willReturn(mockResponse);

        // when
        PostSimpleResponse response = postService.createPost(request, authorId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("test post title");

        // verify
        verify(postRepository).save(any(Post.class));
    }

    @Test
    @DisplayName("Get Post Detail Test")
    void getDetailPost_Success() {
        // given
        Long postId=1L;
        Long currentUserId = 1L;

        User mockAuthor = Mockito.mock(User.class);
        given(mockAuthor.getId()).willReturn(currentUserId);

        Post mockPost = Mockito.mock(Post.class);
        given(mockPost.getAuthor()).willReturn(mockAuthor);

        PostDetailResponse mockResponse = PostDetailResponse.builder()
                .id(postId)
                .isAuthor(true)
                .build();

        given(postRepository.findByIdWithImages(postId)).willReturn(Optional.of(mockPost));
        given(postLikeRepository.existsByPostIdAndUserIdAndDeletedAtIsNull(postId, currentUserId)).willReturn(false);
        given(postMapper.toDetailResponse(mockPost, true, false)).willReturn(mockResponse);

        // when
        PostDetailResponse response = postService.getDetailPost(postId, currentUserId);

        // then
        assertThat(response.getId()).isEqualTo(postId);
        assertThat(response.isAuthor()).isTrue();

        verify(postViewService).increaseViewCount(postId);
    }

    @Test
    @DisplayName("Get Popular Posts Test")
    void getPopularPosts_Success() {
        // given
        java.util.List<PostSimpleResponse> mockList = new java.util.ArrayList<>();
        mockList.add(PostSimpleResponse.builder().id(1L).title("popular post 1").build());
        mockList.add(PostSimpleResponse.builder().id(2L).title("popular post 2").build());

        given(postRepository.findPopularPosts(any(java.time.Instant.class), Mockito.eq(5))).willReturn(mockList);

        // when
        java.util.List<PostSimpleResponse> responses = postService.getPopularPosts();

        // then
        assertThat(responses).isNotNull();
        assertThat(responses.size()).isEqualTo(2);
        assertThat(responses.get(0).getTitle()).isEqualTo("popular post 1");

        verify(postRepository).findPopularPosts(any(java.time.Instant.class), Mockito.eq(5));
    }
}

