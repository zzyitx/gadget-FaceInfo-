# face-detector

用于人脸检测预览和裁剪生成的本地 Python sidecar。

当前默认检测链路：`MTCNN -> OpenCV Haar -> 连通域后备`。
这与 CompreFace 的思路一致：优先使用预训练检测模型并通过阈值/后端切换做准确率与性能权衡，而不是在业务接入阶段先做模型训练。

## 功能

- `POST /detect`
- 接收 `multipart/form-data` 格式的请求，包含一个 `image` 字段
- 返回：
  - `detection_id`
  - `preview_image`
  - `faces[]`
- 每个人脸包含：
  - `face_id`
  - `bbox`
  - `confidence`
  - `crop_preview`
  - `content_type`

响应保持较小的公共负载，同时内部检测器对象也会保留原始裁剪字节，供 Java 边车交接路径使用。

## 安装

```bash
cd face-detector
python -m pip install -r requirements.txt
```

默认已集成 `opencv-python-headless` 作为轻量但可用的检测后端。
如果您想使用更重的 MTCNN 后端，请在确认环境支持后单独安装 `facenet-pytorch`。

## 运行

在 `face-detector/` 目录下执行：

```bash
.\.venv\Scripts\python -m uvicorn app:app --host 127.0.0.1 --port 8091 --reload
```

如果未安装 `fastapi`，模块仍然会暴露检测器和一个轻量级的进程内路由注册（用于测试），但在 FastAPI 可用之前不会启动 HTTP 服务器。

当前图像输出格式为 PNG。响应中包含 `content_type`，Java 调用方在将裁剪后的人脸转发到后续上传或识别步骤时，应保留该 MIME 类型。

## 测试

```bash
python -m pytest face-detector/tests -q
```
