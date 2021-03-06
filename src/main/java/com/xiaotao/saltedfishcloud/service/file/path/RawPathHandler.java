package com.xiaotao.saltedfishcloud.service.file.path;


import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import org.springframework.stereotype.Component;

/**
 * 原始路径操作器，用户请求什么路径，就返回什么路径
 */
@Component
public class RawPathHandler implements PathHandler{
    public String getStorePath(String username, String targetDir, BasicFileInfo fileInfo) {
        PathBuilder pb = new PathBuilder();
        pb.append(DiskConfig.getUserPrivateDiskRoot(username))
                .append("/")
                .append(targetDir);
        if (fileInfo != null) {
            pb.append(fileInfo.getName());
        }
        return PathBuilder.formatPath(pb.toString());
    }
    /**
     *
     * @param uid       用户ID 0表示公共
     * @param targetDir 请求的目标目录（是相对用户网盘根目录的目录）
     * @param fileInfo  目标文件信息
     * @return          目标文件在本地的存储路径
     */
    @Override
    public String getStorePath(int uid, String targetDir, BasicFileInfo fileInfo) {
        PathBuilder pathBuilder = new PathBuilder();
        if (fileInfo != null) {
            pathBuilder.append(DiskConfig.getRawFileStoreRootPath(uid))
                    .append(targetDir)
                    .append(fileInfo.getName());
        } else {
            pathBuilder.append(DiskConfig.getRawFileStoreRootPath(uid)).append(targetDir);
        }
        return pathBuilder.toString();
    }
}
