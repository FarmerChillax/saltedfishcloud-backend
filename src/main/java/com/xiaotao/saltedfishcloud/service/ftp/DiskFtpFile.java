package com.xiaotao.saltedfishcloud.service.ftp;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.file.exception.DirectoryAlreadyExistsException;
import com.xiaotao.saltedfishcloud.service.ftp.utils.FtpDiskType;
import com.xiaotao.saltedfishcloud.service.ftp.utils.FtpPathInfo;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.ftplet.FtpFile;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DiskFtpFile implements FtpFile {
    private final FtpPathInfo pathInfo;
    private final DiskFtpUser user;
    private File nativeFile;
    private final FileService fileService = SpringContextHolder.getContext().getBean(FileService.class);

    /**
     * 构造一个网盘FTP文件
     * @param path  请求的FTP路径
     * @param user  FTP用户
     */
    public DiskFtpFile(String path, DiskFtpUser user) {
        this.user = user;
        pathInfo = new FtpPathInfo(path);
        if (!pathInfo.isFtpRoot()) {
            nativeFile = new File(pathInfo.toNativePath(user.getName()));
        }
    }
    @Override
    public String getAbsolutePath() {
        return pathInfo.getFullPath();
    }

    @Override
    public String getName() {
        return pathInfo.getName();
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        if (pathInfo.isFtpRoot() || pathInfo.isResourceRoot()) {
            return true;
        } else {
            return nativeFile.isDirectory();
        }
    }

    @Override
    public boolean isFile() {
        return !isDirectory();
    }

    @Override
    public boolean doesExist() {
        return pathInfo.isFtpRoot() || nativeFile.exists();
    }

    @Override
    public boolean isReadable() {
        return true;
    }

    @Override
    public boolean isWritable() {
        if (DiskConfig.getReadOnlyLevel() == ReadOnlyLevel.DATA_MOVING) {
            return false;
        }
        // FTP根目录不可写
        if (pathInfo.isFtpRoot()) {
            log.debug("根目录写入拒绝");
            return false;
        }
        // 公共网盘只允许管理员写入，其他情况均可写入
        if (pathInfo.isPublicArea() && !user.isAdmin()) {
            log.debug("公共网盘目录写入拒绝");
            return false;
        }
        return true;
    }

    @Override
    public boolean isRemovable() {
        return isWritable() && !pathInfo.isResourceRoot();
    }

    @Override
    public String getOwnerName() {
        return pathInfo.isFtpRoot() ? User.SYS_NAME_PUBLIC : user.getName();
    }

    @Override
    public String getGroupName() {
        return pathInfo.isFtpRoot() ? User.SYS_GROUP_NAME_PUBLIC : user.getName();
    }

    @Override
    public int getLinkCount() {
        return 0;
    }

    @Override
    public long getLastModified() {
        return nativeFile.lastModified();
    }

    @Override
    public boolean setLastModified(long time) {
        return false;
    }

    @Override
    public long getSize() {
        return nativeFile.length();
    }

    @Override
    public Object getPhysicalFile() {
        return null;
    }

    @Override
    public boolean mkdir() {
        PathBuilder pb = new PathBuilder();
        pb.append(pathInfo.getResourcePath());
        try {
            fileService.mkdir(
                    pathInfo.isPublicArea() ? 0 : user.getId(),
                    new PathBuilder().append(pathInfo.getResourcePath()).range(-1),
                    pathInfo.getName()
            );
        } catch (NoSuchFileException | FileAlreadyExistsException | DirectoryAlreadyExistsException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public boolean delete() {
        FileService fileService = SpringContextHolder.getContext().getBean(FileService.class);
        try {
            fileService.deleteFile(
                    pathInfo.isPublicArea() ? 0 : user.getId(),
                    (new PathBuilder()).append(pathInfo.getResourcePath()).range(-1),
                    Collections.singletonList(pathInfo.getName())
            );
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 对文件进行移动或重命名，暂不支持跨资源区操作
     * @param destination 目标文件信息
     * @return 成功与否
     */
    @Override
    public boolean move(FtpFile destination) {
        FtpPathInfo pathInfo = new FtpPathInfo(destination.getAbsolutePath());

        // 不支持跨资源区操作
        if (!pathInfo.getResourceArea().equals(this.pathInfo.getResourceArea())) {
            return false;
        }
        int uid = pathInfo.isPublicArea() ? 0 : user.getId();
        try {
            // 资源路径相同表示重命名
            if (pathInfo.getResourceParent().equals(this.pathInfo.getResourceParent()) ) {
                fileService.rename(uid, pathInfo.getResourceParent(), this.pathInfo.getName(), destination.getName());
                return true;
            } else {
                fileService.move(uid, this.pathInfo.getResourceParent(), pathInfo.getResourceParent(), destination.getName(), true);
                return true;
            }
        } catch (NoSuchFileException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<? extends FtpFile> listFiles() {
        if (pathInfo.isFtpRoot()) {
            List<DiskFtpFile> res = new LinkedList<>();
            res.add(new DiskFtpFile(FtpDiskType.PUBLIC, user));
            if (!user.isAnonymousUser()) {
                res.add(new DiskFtpFile(FtpDiskType.PRIVATE, user));
            }
            return res;
        }

        File[] files = nativeFile.listFiles();
        String path = getAbsolutePath();
        if (files == null) {
            return new LinkedList<>();
        }
        return Arrays.stream(files).map(f -> new DiskFtpFile(path + "/" + f.getName(), user)).collect(Collectors.toList());
    }

    /**
     * 创建文件输出流，创建之前会删除原文件，并设置接收文件的临时位置<br>
     *     临时位置：临时文件夹/(uid+md5(FTP文件完整路径))
     */
    @Override
    public OutputStream createOutputStream(long offset) throws IOException {
        log.debug("create output stream");
        int uid = pathInfo.isPublicArea() ? 0 : user.getId();
        if (doesExist()) {
            fileService.deleteFile(
                    uid,
                    pathInfo.getResourceParent(),
                    Collections.singletonList(pathInfo.getName())
            );
        }
        String tmpDir = System.getProperty("java.io.tmpdir");

        String tag = uid + SecureUtils.getMd5(pathInfo.getFullPath());
        if (offset > 0) {
            throw new IOException("Not support random write");
        }
        return new FileOutputStream(new File(tmpDir + File.separator + tag));
    }

    @Override
    public InputStream createInputStream(long offset) throws IOException {
        FileInputStream inputStream = new FileInputStream(nativeFile);
        if (inputStream.skip(offset) != offset) {
            throw new IOException("Out of offset");
        }
        return inputStream;
    }
}
