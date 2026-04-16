package com.mika.ktdcloud.community.service;

import com.mika.ktdcloud.community.entity.Post;
import com.mika.ktdcloud.community.entity.PostImage;
import com.mika.ktdcloud.community.repository.PostImageRepository;
import com.mika.ktdcloud.community.service.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;


@Service
public class PostImageService {
    private final PostImageRepository postImageRepository;
    private final FileService fileService;

    public PostImageService(
            PostImageRepository postImageRepository,
            @Qualifier("localFileService") FileService fileService // localFileService or s3FileService로 변환가능
    ) {
        this.postImageRepository = postImageRepository;
        this.fileService = fileService;
    }

    // 람다 함수로 온 s3 url을 DB에 저장
    @Transactional
    public PostImage saveImageUrl(String imageUrl, Post post, int imageOrder) {
        PostImage postImage = PostImage.builder()
                .originalUrl(imageUrl)
                .post(post)
                .imageOrder(imageOrder)
                .build();

        return postImageRepository.save(postImage);
    }

    @Transactional
    public PostImage storeFile(MultipartFile file, Post post, int imageOrder) {
        try {
            String imageUrl = fileService.saveFileUrl(file);

            PostImage postImage = PostImage.builder()
                    .originalUrl(imageUrl)
                    .post(post)
                    .imageOrder(imageOrder)
                    .build();

            return postImageRepository.save(postImage);
        } catch (IOException ex) {
            throw new RuntimeException("파일 저장에 실패했습니다.", ex);
        }
    }

    @Transactional
    public void deleteFile(PostImage postImage) {
        if (postImage == null || postImage.getOriginalUrl() == null) {
            return;
        }
        String imageUrl = postImage.getOriginalUrl();
        fileService.deleteFile(imageUrl); // S3에서 먼저 삭제
        postImageRepository.delete(postImage); // DB에서 삭제
    }
}
