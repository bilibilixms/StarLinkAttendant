package com.intcaf.rag.minerU;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MinerUUtil {
    private static final String BASE_URL = "http://127.0.0.1:8000";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build();

    /** 本模块根目录（cafe-module-Rag），从 class 文件位置自动推导，不依赖 IDEA 启动工作目录 */
    private static final Path MODULE_ROOT = resolveModuleRoot();

    /** 测试输入目录：本模块 minerU 包下的 input-dir */
    private static final String INPUT_DIR = MODULE_ROOT.resolve("src/main/java/com/intcaf/rag/minerU/input-dir").toString();

    /** 转换输出目录：cafe-module-ai 的 ai-context，应用启动时被 AiContextLoader 扫描并注入 AI 上下文 */
    private static final String OUTPUT_DIR = MODULE_ROOT.getParent().resolve("cafe-module-ai/src/main/resources/ai-context").toString();

    /**
     * 由 class 文件所在位置反推模块根目录：
     * MinerUUtil.class 编译后位于 cafe-module-Rag/target/classes/com/...，回退两级即模块根目录
     */
    private static Path resolveModuleRoot() {
        try {
            Path classesDir = Path.of(MinerUUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return classesDir.resolveSibling("..").normalize();
        } catch (Exception e) {
            // 兜底：退回到当前进程工作目录
            return Path.of("").toAbsolutePath();
        }
    }

    /** 走 MinerU API 的文档类型（txt 无需解析，直接拷贝为 md） */
    private static final Set<String> API_SUPPORTED_EXTS = Set.of("pdf", "doc", "docx");

    /** 根据文件扩展名返回上传 mime 类型 */
    private static String mimeType(String fileName) {
        switch (extension(fileName)) {
            case "pdf":  return "application/pdf";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc":  return "application/msword";
            default:     return "application/octet-stream";
        }
    }

    private static String extension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 步骤1 POST /v1/uploads 创建上传会话
     */
    public static String createUploadSession(File pdfFile, String mimeType) throws IOException {
        long fileSize = pdfFile.length();
        JSONObject reqJson = new JSONObject();
        reqJson.put("filename", pdfFile.getName());
        reqJson.put("bytes", fileSize);
        reqJson.put("mime_type", mimeType);
        RequestBody body = RequestBody.create(reqJson.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/v1/uploads")
                .post(body)
                .build();
        try (Response resp = HTTP_CLIENT.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("创建上传会话失败 code=" + resp.code() + " body=" + resp.body().string());
            }
            String respBody = resp.body().string();
            System.out.println("[createUploadSession response]:" + respBody);
            JSONObject json = JSON.parseObject(respBody);
            return json.getString("id");
        }
    }

    /**
     * 步骤2 PUT /v1/uploads/{uploadId}/content 上传pdf二进制
     */
    public static void putUploadContent(String uploadId, File pdfFile) throws IOException {
        System.out.println("[putUploadContent uploadId]:" + uploadId);
        byte[] rawBytes = Files.readAllBytes(pdfFile.toPath());
        // 按文件扩展名传对应 mime（doc/docx 不能写死 application/pdf）
        MediaType fileMediaType = MediaType.parse(mimeType(pdfFile.getName()));
        RequestBody body = RequestBody.create(rawBytes, fileMediaType);
        Request request = new Request.Builder()
                .url(BASE_URL + "/v1/uploads/" + uploadId + "/content")
                .put(body)
                .build();
        try (Response resp = HTTP_CLIENT.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("PUT上传二进制失败 code=" + resp.code() + " body=" + resp.body().string());
            }
        }
    }

    /**
     * 步骤3 POST /v1/uploads/{uploadId}/complete 完成上传，获取file.id
     */
    public static String completeUpload(String uploadId) throws IOException {
        RequestBody emptyBody = RequestBody.create("{}", MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/v1/uploads/" + uploadId + "/complete")
                .post(emptyBody)
                .build();
        try (Response resp = HTTP_CLIENT.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("complete上传失败 code=" + resp.code() + " body=" + resp.body().string());
            }
            String respBody = resp.body().string();
            System.out.println("[completeUpload response]:" + respBody);
            JSONObject json = JSON.parseObject(respBody);
            JSONObject fileObj = json.getJSONObject("file");
            return fileObj.getString("id");
        }
    }

    /**
     * 步骤4 POST /v1/parse/jobs
     */
    public static String createParseJob(String fileId, boolean isOcr) throws IOException {
        JSONObject reqJson = new JSONObject();
        JSONArray filesArr = new JSONArray();

        JSONObject fileItem = new JSONObject();
        JSONObject sourceObj = new JSONObject();
        sourceObj.put("type", "file_id");
        sourceObj.put("file_id", fileId);
        fileItem.put("source", sourceObj);
        filesArr.add(fileItem);

        reqJson.put("files", filesArr);
        reqJson.put("tier", "flash");
        // MinerU 解析接口 ocr_mode 取值：auto / txt / ocr（扫描件/图片型 PDF 用 ocr 强制识别）
        reqJson.put("ocr_mode", isOcr ? "ocr" : "auto");
        JSONArray outputFormats = new JSONArray();
        outputFormats.add("markdown");
        reqJson.put("output_formats", outputFormats);

        RequestBody body = RequestBody.create(reqJson.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/v1/parse/jobs")
                .post(body)
                .build();
        try (Response resp = HTTP_CLIENT.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("创建解析任务失败 code=" + resp.code() + " body=" + resp.body().string());
            }
            String respBody = resp.body().string();
            System.out.println("[createParseJob response]:" + respBody);
            JSONObject json = JSON.parseObject(respBody);
            return json.getString("job_id");
        }
    }

    /**
     * 轮询任务状态，MinerU完成状态是 completed
     */
    public static JSONObject waitJobResult(String jobId, int maxSeconds) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < maxSeconds * 1000L) {
            Request request = new Request.Builder()
                    .url(BASE_URL + "/v1/parse/jobs/" + jobId)
                    .get()
                    .build();
            try (Response resp = HTTP_CLIENT.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    throw new IOException("查询任务失败 code=" + resp.code());
                }
                String respBody = resp.body().string();
                JSONObject json = JSON.parseObject(respBody);
                String status = json.getString("status");
                System.out.println("【job状态】" + status);
                if ("completed".equals(status)) {
                    return json;
                }
                if ("failed".equals(status)) {
                    throw new RuntimeException("任务执行失败：" + json.getString("error"));
                }
            }
            Thread.sleep(800);
        }
        throw new RuntimeException("任务超时");
    }

    /**
     * 下载输出文件内容 GET /v1/files/{fileId}/content
     */
    public static String downloadFileContent(String mdFileId) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "/v1/files/" + mdFileId + "/content")
                .get()
                .build();
        try (Response resp = HTTP_CLIENT.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("下载markdown文件失败 code=" + resp.code());
            }
            return resp.body().string();
        }
    }

    public static void pdfToMarkdown(String pdfFilePath, String outMdPath, boolean ocr) throws IOException, InterruptedException {
        File pdfFile = new File(pdfFilePath);
        if (!pdfFile.exists()) {
            throw new IOException("pdf文件不存在：" + pdfFilePath);
        }
        //自动创建输出目录
        File outFile = new File(outMdPath);
        File parentDir = outFile.getParentFile();
        if(!parentDir.exists()){
            parentDir.mkdirs();
        }

        String uploadId = createUploadSession(pdfFile, mimeType(pdfFile.getName()));
        putUploadContent(uploadId, pdfFile);
        String fileId = completeUpload(uploadId);
        String jobId = createParseJob(fileId, ocr);
        JSONObject resultJson = waitJobResult(jobId, 300);

        //取markdown file_id
        JSONObject fileItem = resultJson.getJSONArray("files").getJSONObject(0);
        JSONObject outputFiles = fileItem.getJSONObject("output_files");
        JSONObject markdownObj = outputFiles.getJSONObject("markdown");
        String mdFileId = markdownObj.getString("file_id");
        String mdText = downloadFileContent(mdFileId);

        try (FileOutputStream fos = new FileOutputStream(outMdPath)) {
            fos.write(mdText.getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("✅转换完成，输出文件：" + outMdPath);
    }

    /**
     * 批量转换：扫描 inputDir 下所有支持的文档（pdf/doc/docx 走 MinerU API，txt 直接拷贝为 md），
     * 生成的 md 输出到 outputDir，文件名与输入同名（.md 后缀）。
     */
    public static void convertDirectory(String inputDir, String outputDir, boolean ocr) throws IOException {
        File in = new File(inputDir);
        File out = new File(outputDir);
        if (!in.isDirectory()) {
            throw new IOException("输入目录不存在：" + inputDir);
        }
        if (!out.exists() && !out.mkdirs()) {
            throw new IOException("无法创建输出目录：" + outputDir);
        }
        File[] files = in.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("输入目录为空：" + inputDir);
            return;
        }
        int ok = 0, skip = 0;
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName();
            String ext = extension(name);
            if (ext.isEmpty()) { skip++; continue; }
            String base = name.substring(0, name.length() - ext.length() - 1);
            String outPath = new File(out, base + ".md").getPath();
            try {
                if ("txt".equals(ext)) {
                    // txt 是纯文本，无需解析，直接拷贝为 md（保持 UTF-8）
                    Files.copy(f.toPath(), Path.of(outPath), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[txt 直拷] " + name + " -> " + outPath);
                } else if (API_SUPPORTED_EXTS.contains(ext)) {
                    pdfToMarkdown(f.getPath(), outPath, ocr);
                } else {
                    System.out.println("[跳过] 不支持的类型: " + name);
                    skip++;
                    continue;
                }
                ok++;
            } catch (Exception e) {
                System.err.println("[失败] " + name + " : " + e.getMessage());
            }
        }
        System.out.println("批量转换完成：成功 " + ok + " 个，跳过 " + skip + " 个，输出目录 " + out.getPath());
    }

    public static void main(String[] args) {
        try {
            convertDirectory(INPUT_DIR, OUTPUT_DIR, false);
            System.out.println("程序结束");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
