package com.xiaotao.saltedfishcloud.service.file.path;

import com.xiaotao.saltedfishcloud.po.FileInfo;

public interface PathHandler {
    /**
     * 获取文件在本地文件系统中的完整存储路径
     * @param uid       用户ID 0表示公共
     * @param targetDir 请求的目标目录（是相对用户网盘根目录的目录）
     * @param fileInfo  新文件信息
     * @return 路径
     */
    public String getStorePath(int uid, String targetDir, FileInfo fileInfo);
}