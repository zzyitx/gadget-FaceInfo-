package com.example.face2info.client;

import java.util.OptionalDouble;

public interface CompreFaceVerificationClient {

    OptionalDouble verify(byte[] sourceImage, byte[] targetImage, String contentType);
}
