package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.service.async.context.AsyncTackCallback;
import com.xiaotao.saltedfishcloud.service.async.context.EmptyCallback;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.FileNameValidator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseExtractor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class HttpResourceFile extends File {
    @Getter
    private String resourceName;

    public HttpResourceFile(String nativePath, String resourceName) {
        super(nativePath);
        this.resourceName = resourceName;
    }

    public HttpResourceFile(String pathname) {
        super(pathname);
    }
}

@Slf4j
public class DownloadExtractor implements ResponseExtractor<HttpResourceFile>, ProgressExtractor {
    private long total;
    private long loaded;
    private long speed;
    private final Path savePath;
    @Getter
    private boolean interrupted = false;
    @Setter
    private AsyncTackCallback readyCallback;
    @Setter
    private AsyncTackCallback progressCallback = EmptyCallback.get();
    @Getter
    private String resourceName;
    public DownloadExtractor(Path savePath) {
        this.savePath = savePath;
    }
    public DownloadExtractor(String savePath) {
        this.savePath = Paths.get(savePath);
    }
    public DownloadExtractor(File saveFile) {
        this.savePath = saveFile.toPath();
    }

    public void interrupt() {
        interrupted = true;
    }

    @Override
    public HttpResourceFile extractData(ClientHttpResponse response) throws IOException {
        var parent = savePath.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        // ???????????????
        resourceName = response.getHeaders().getContentDisposition().getFilename();
        if (resourceName != null && FileNameValidator.valid(resourceName)) {
            log.debug("?????????????????????????????????" + resourceName);
        } else {
            log.debug("?????????????????????");
        }

        // ??????????????????????????????????????????
        if (Files.exists(savePath) && Files.isDirectory(savePath)) {
            throw new IOException(savePath + "?????????????????????");
        }

        // ????????????
        InputStream body = response.getBody();
        total = response.getHeaders().getContentLength();
        log.debug("??????????????????????????????:{}", savePath);
        OutputStream localFileStream = Files.newOutputStream(savePath);
        byte[] buffer = new byte[8192];
        int cnt;
        int lastProc = 0;
        long lastLoad = 0;
        long lastRecordTime = System.currentTimeMillis();

        readyCallback.action();
        try {
            while ( (cnt = body.read(buffer)) != -1 ) {
                // ??????????????????
                if (interrupted) {
                    body.close();
                    localFileStream.close();
                    if (Files.exists(savePath)) {
                        Files.delete(savePath);
                    }
                    log.debug("???????????????");
                    return null;
                }

                // ??????????????????1s???????????????????????????????????????????????????ProgressCallback
                long curTime = System.currentTimeMillis();
                loaded += cnt;
                if (curTime - lastRecordTime > 1000) {
                    log.debug("????????????{}({}) ?????????{}({}) ?????????{}%",
                            loaded, StringUtils.getFormatSize(loaded),
                            total, StringUtils.getFormatSize(total), (int)(loaded * 100/ total));

                    // ????????????
                    speed = (loaded - lastLoad)/( (curTime - lastRecordTime)/1000 );

                    // ????????????
                    try {
                        progressCallback.action();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }

                    // ????????????
                    lastRecordTime = curTime;
                    lastLoad = loaded;
                }
                // ??????????????????
                localFileStream.write(buffer, 0, cnt);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            body.close();
            localFileStream.close();
        }

        // ???????????????????????????????????????
        var res = new HttpResourceFile(savePath.toString(), resourceName);
        if (res.length() != total) {
            total = res.length();
        }
        return res;
    }

    @Override
    public long getTotal() {
        return total;
    }

    @Override
    public long getLoaded() {
        return loaded;
    }

    @Override
    public long getSpeed() {
        return speed;
    }
}
