package com.example.face2info.client;

import java.io.InputStream;

public interface MinioClient {

    void putObject(String bucket,
                   String objectName,
                   InputStream stream,
                   long size,
                   String contentType);

    void createBucketIfNotExists(String bucket);
}
