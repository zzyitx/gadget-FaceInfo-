package com.example.face2info.entity.internal;

public class SelectedFaceCrop {

    private String filename;
    private String contentType;
    private byte[] bytes = new byte[0];

    public String getFilename() {
        return filename;
    }

    public SelectedFaceCrop setFilename(String filename) {
        this.filename = filename;
        return this;
    }

    public String getContentType() {
        return contentType;
    }

    public SelectedFaceCrop setContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public SelectedFaceCrop setBytes(byte[] bytes) {
        this.bytes = bytes == null ? new byte[0] : bytes;
        return this;
    }
}
