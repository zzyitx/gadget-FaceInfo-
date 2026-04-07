package com.example.face2info.entity.internal;

public class FaceBoundingBox {

    private int x;
    private int y;
    private int width;
    private int height;

    public int getX() {
        return x;
    }

    public FaceBoundingBox setX(int x) {
        this.x = x;
        return this;
    }

    public int getY() {
        return y;
    }

    public FaceBoundingBox setY(int y) {
        this.y = y;
        return this;
    }

    public int getWidth() {
        return width;
    }

    public FaceBoundingBox setWidth(int width) {
        this.width = width;
        return this;
    }

    public int getHeight() {
        return height;
    }

    public FaceBoundingBox setHeight(int height) {
        this.height = height;
        return this;
    }
}
