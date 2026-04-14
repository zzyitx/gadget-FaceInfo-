package com.example.face2info.service;

import com.example.face2info.entity.internal.PreparedImageResult;
import org.springframework.web.multipart.MultipartFile;

public interface EnhancedImagePreparationService {

    PreparedImageResult prepare(MultipartFile originalImage);
}
