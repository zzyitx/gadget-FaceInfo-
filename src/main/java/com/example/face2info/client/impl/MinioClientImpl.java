package com.example.face2info.client.impl;

import com.example.face2info.client.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinioClientImpl implements MinioClient {

    private final io.minio.MinioClient minioClient;

    public MinioClientImpl(io.minio.MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public void putObject(String bucket,
                          String objectName,
                          InputStream stream,
                          long size,
                          String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(stream, size, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("MinIO上传失败", e);
        }
    }

    @Override
    public void createBucketIfNotExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("创建 Bucket 失败", e);
        }
    }
}
