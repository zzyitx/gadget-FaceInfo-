package com.example.face2info.controller;

import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.FaceBoundingBox;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.FaceSelectionPayload;
import com.example.face2info.entity.response.DetectedFaceResponse;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.exception.FaceDetectionException;
import com.example.face2info.exception.GlobalExceptionHandler;
import com.example.face2info.service.Face2InfoService;
import com.example.face2info.service.FaceDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaceInfoController.class)
@Import({GlobalExceptionHandler.class, FaceInfoControllerTest.TestConfig.class})
class FaceInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubFace2InfoService face2InfoService;

    @Autowired
    private StubFaceDetectionService faceDetectionService;

    @Test
    void shouldReturnSuccessfulPayload() throws Exception {
        face2InfoService.setProcessResponse(new FaceInfoResponse().setStatus("success"));

        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/face2info").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void shouldReturnSelectionRequiredPayload() throws Exception {
        face2InfoService.setProcessResponse(new FaceInfoResponse()
                .setStatus("selection_required")
                .setSelection(new FaceSelectionPayload()
                        .setDetectionId("det-1")
                        .setPreviewImage("data:image/jpeg;base64,preview")
                        .setFaces(java.util.List.of(new DetectedFaceResponse()
                                .setFaceId("face-1")
                                .setConfidence(0.98)))));

        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/face2info").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("selection_required"))
                .andExpect(jsonPath("$.selection.detection_id").value("det-1"))
                .andExpect(jsonPath("$.selection.preview_image").value("data:image/jpeg;base64,preview"))
                .andExpect(jsonPath("$.selection.faces[0].face_id").value("face-1"));
    }

    @Test
    void shouldDetectFacesAndExposeStructuredResponse() throws Exception {
        SelectedFaceCrop crop = new SelectedFaceCrop()
                .setFilename("face-1.jpg")
                .setContentType("image/jpeg")
                .setBytes("FACE-1".getBytes(StandardCharsets.UTF_8));
        DetectedFace face = new DetectedFace()
                .setFaceId("face-1")
                .setConfidence(0.98)
                .setFaceBoundingBox(new FaceBoundingBox().setX(12).setY(24).setWidth(100).setHeight(120))
                .setSelectedFaceCrop(crop);

        faceDetectionService.setDetectResponse(new DetectionSession()
                .setDetectionId("det-1")
                .setPreviewImage("data:image/jpeg;base64,preview")
                .setFaces(java.util.List.of(face)));

        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/face2info/detect").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detection_id").value("det-1"))
                .andExpect(jsonPath("$.preview_image").value("data:image/jpeg;base64,preview"))
                .andExpect(jsonPath("$.faces[0].face_id").value("face-1"))
                .andExpect(jsonPath("$.faces[0].confidence").value(0.98))
                .andExpect(jsonPath("$.faces[0].bbox.x").value(12))
                .andExpect(jsonPath("$.faces[0].crop_preview").value(
                        "data:image/jpeg;base64," + Base64.getEncoder().encodeToString("FACE-1".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void shouldProcessSelectedFaceThroughMainFlow() throws Exception {
        face2InfoService.setProcessSelectedResponse(new FaceInfoResponse().setStatus("success"));

        mockMvc.perform(post("/api/face2info/process-selected")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"detection_id":"det-1","face_id":"face-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        org.assertj.core.api.Assertions.assertThat(face2InfoService.getLastDetectionId()).isEqualTo("det-1");
        org.assertj.core.api.Assertions.assertThat(face2InfoService.getLastFaceId()).isEqualTo("face-1");
    }

    @Test
    void shouldMapFaceDetectionExceptionToBadRequest() throws Exception {
        faceDetectionService.setDetectException(new FaceDetectionException("no face detected"));

        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/face2info/detect").file(image))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.error").value("no face detected"));
    }

    @Test
    void shouldMapDetectorUnavailableToServiceUnavailable() throws Exception {
        faceDetectionService.setDetectException(new ApiCallException("人脸检测服务不可用：http://127.0.0.1:8091/detect，请先启动本地检测服务后重试。"));

        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/face2info/detect").file(image))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.error").value("人脸检测服务不可用：http://127.0.0.1:8091/detect，请先启动本地检测服务后重试。"));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        StubFace2InfoService face2InfoService() {
            return new StubFace2InfoService();
        }

        @Bean
        StubFaceDetectionService faceDetectionService() {
            return new StubFaceDetectionService();
        }
    }

    static class StubFace2InfoService implements Face2InfoService {
        private FaceInfoResponse processResponse = new FaceInfoResponse();
        private FaceInfoResponse processSelectedResponse = new FaceInfoResponse();
        private String lastDetectionId;
        private String lastFaceId;

        void setProcessResponse(FaceInfoResponse response) {
            this.processResponse = response;
        }

        void setProcessSelectedResponse(FaceInfoResponse response) {
            this.processSelectedResponse = response;
        }

        String getLastDetectionId() {
            return lastDetectionId;
        }

        String getLastFaceId() {
            return lastFaceId;
        }

        @Override
        public FaceInfoResponse process(org.springframework.web.multipart.MultipartFile image) {
            return processResponse;
        }

        @Override
        public FaceInfoResponse processSelectedFace(String detectionId, String faceId) {
            this.lastDetectionId = detectionId;
            this.lastFaceId = faceId;
            return processSelectedResponse;
        }
    }

    static class StubFaceDetectionService implements FaceDetectionService {
        private DetectionSession detectResponse;
        private RuntimeException detectException;

        void setDetectResponse(DetectionSession detectResponse) {
            this.detectResponse = detectResponse;
            this.detectException = null;
        }

        void setDetectException(RuntimeException detectException) {
            this.detectException = detectException;
            this.detectResponse = null;
        }

        @Override
        public DetectionSession detect(org.springframework.web.multipart.MultipartFile image) {
            if (detectException != null) {
                throw detectException;
            }
            return detectResponse;
        }

        @Override
        public SelectedFaceCrop getSelectedFaceCrop(String detectionId, String faceId) {
            return null;
        }
    }
}
