package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnableOverwriteException;
import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.po.file.DirCollection;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.exception.DirectoryAlreadyExistsException;
import com.xiaotao.saltedfishcloud.service.file.path.PathHandler;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地文件存储服务，用于管理本地文件系统中的文件的创建，复制，删除，移动等操作
 */
@Service
@Slf4j
public class StoreService {
    /**
     * 通过文件移动的方式存储文件到网盘系统，相对于{@link #store}方法，避免了文件的重复写入操作。对本地文件操作后，原路径文件不再存在<br><br>
     * 如果是UNIQUE存储模式，则会先将文件移动到存储仓库（若仓库已存在文件则忽略该操作），随后再在目标网盘目录创建文件链接<br><br>
     * 如果是RAW存储模式，则会直接移动到目标位置。若本地文件路径与网盘路径对应的本地路径相同，操作将忽略。
     * @param uid           用户ID
     * @param nativePath    本地文件路径
     * @param diskPath      网盘路径
     * @param fileInfo      文件信息
     */
    public void moveToSave(int uid, Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {
        Path sourcePath = nativePath; // 本地源文件
        Path targetPath = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, diskPath, fileInfo)); // 被移动到的目标位置
        if (DiskConfig.STORE_TYPE == StoreType.UNIQUE) {
            // 唯一文件仓库中的路径
            sourcePath = Paths.get(DiskConfig.uniquePathHandler.getStorePath(uid, diskPath, fileInfo)); // 文件仓库源文件路径
            if (Files.exists(sourcePath)) {
                // 已存在相同文件时，直接删除本地文件
                log.debug("file md5 HIT: {}", fileInfo.getMd5());
                Files.delete(nativePath);
                if (Files.exists(targetPath)) {
                    Files.delete(targetPath);
                }
            } else {
                // 将本地文件移动到唯一仓库
                log.debug("file md5 NOT HIT: {}", fileInfo.getMd5());
                FileUtils.createParentDirectory(sourcePath);
                Files.move(nativePath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
            }
            // 在目标网盘位置创建文件仓库中的文件链接
            log.debug("Create file link: {} <==> {}", targetPath, sourcePath);
            Files.createLink(targetPath, sourcePath);
        } else {
            // 非唯一模式，直接将文件移动到目标位置
            if (!sourcePath.equals(targetPath)) {
                log.debug("File move {} => {}", sourcePath, targetPath);
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * 在本地存储中复制用户网盘文件
     * @param uid     用户ID
     * @param source  所在网盘路径
     * @param target  目的地网盘路径
     * @param sourceName    文件名
     * @param overwrite 是否覆盖，若非true，则跳过该文件
     */
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, Boolean overwrite) throws IOException {
        BasicFileInfo fileInfo = new BasicFileInfo(sourceName, null);
        String localSource = DiskConfig.getPathHandler().getStorePath(uid, source, fileInfo);
        String localTarget = DiskConfig.getPathHandler().getStorePath(targetId, target, null);

        fileInfo = FileInfo.getLocal(localSource);
        Path sourcePath = Paths.get(localSource);

        //  判断源与目标是否存在
        if (!Files.exists(sourcePath)) {
            throw new NoSuchFileException("资源 \"" + source + "/" + sourceName + "\" 不存在");
        }
        if (!Files.exists(Paths.get(localTarget))) {
            throw new NoSuchFileException("目标目录 " + target + " 不存在");
        }

        CopyOption[] option;
        if (overwrite) {
            option = new CopyOption[]{StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING};
        } else  {
            option = new CopyOption[]{StandardCopyOption.COPY_ATTRIBUTES};
        }

        if (fileInfo.isFile()) {
            Files.copy(sourcePath, Paths.get(localTarget + "/" + targetName), option);
        }

        if (fileInfo.isDir()) {
            DirCollection dirCollection = FileUtils.scanDir(Paths.get(localSource));
            Path targetDir = Paths.get(localTarget + "/" + targetName);
            if (!Files.exists(targetDir)) {
                Files.createDirectory(targetDir);
            }
            //  先创建文件夹
            for(File dir: dirCollection.getDirList()) {
                String src = dir.getPath().substring(localSource.length());
                String dest = targetDir + "/" + src;
                log.debug("local filesystem mkdir: " + dest);
                try { Files.createDirectory(Paths.get(dest)); } catch (FileAlreadyExistsException ignored) {}
            }

            //  复制文件
            for(File file: dirCollection.getFileList()) {
                String src = file.getPath().substring(localSource.length());
                String dest = localTarget + "/" + targetName + src;
                if (DiskConfig.STORE_TYPE == StoreType.UNIQUE) {
                    log.debug("create hard link: " + file + " ==> " + dest);
                    Files.createLink(Paths.get(dest), Paths.get(file.getPath()));
                } else {
                    log.debug("local filesystem copy: " + file + " ==> " + dest);
                    try { Files.copy(Paths.get(file.getPath()), Paths.get(dest), option); }
                    catch (FileAlreadyExistsException ignored) {}
                }
            }
        }
    }

    /**
     * 向用户网盘目录中保存一个文件
     * @param uid   用户ID 0表示公共
     * @param input 输入的文件
     * @param targetDir    保存到的目标网盘目录位置（注意：不是本地真是路径）
     * @param fileInfo 文件信息
     * @throws JsonException 存储文件出错
     * @throws DuplicateKeyException UNIQUE模式下两个不相同的文件发生MD5碰撞
     * @throws UnableOverwriteException 保存位置存在同名的目录
     */
    public void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws JsonException, IOException {
        Path md5Target = Paths.get(DiskConfig.uniquePathHandler.getStorePath(uid, targetDir, fileInfo));
        if (DiskConfig.STORE_TYPE == StoreType.UNIQUE) {
            if (Files.exists(md5Target)) {
                log.debug("file md5 HIT:" + fileInfo.getMd5());
                if (Files.size(md5Target) != fileInfo.getSize()) {
                    throw new DuplicateKeyException("文件MD5冲突");
                }
            } else {
                log.debug("file md5 NOT HIT, saving:" + fileInfo.getMd5());
                FileUtils.createParentDirectory(md5Target);
                Files.copy(input, md5Target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path rawTarget = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, targetDir, fileInfo));
        if (Files.exists(rawTarget) && Files.isDirectory(rawTarget)) {
            throw new UnableOverwriteException(409, "已存在同名目录: " + targetDir + "/" + fileInfo.getName());
        }
        FileUtils.createParentDirectory(rawTarget);
        if (DiskConfig.STORE_TYPE == StoreType.UNIQUE) {
            log.info("create hard link:" + md5Target + " <==> "  + rawTarget);
            if (Files.exists(rawTarget)) Files.delete(rawTarget);
            Files.createLink(rawTarget, md5Target);
        } else {
            log.info("save file:" + rawTarget);
            Files.copy(input, rawTarget, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 在本地存储中移动用户网盘文件
     * @param uid     用户ID
     * @param source  所在网盘路径
     * @param target  目的地网盘路径
     * @param name    文件名
     * @param overwrite 是否覆盖原文件
     */
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        PathHandler pathHandler = DiskConfig.getPathHandler();
        BasicFileInfo fileInfo = new BasicFileInfo(name, null);
        Path sourcePath = Paths.get(pathHandler.getStorePath(uid, source, fileInfo));
        Path targetPath = Paths.get(pathHandler.getStorePath(uid, target, fileInfo));
        if (Files.exists(targetPath)) {
            if (Files.isDirectory(sourcePath) != Files.isDirectory(targetPath)) {
                throw new UnsupportedOperationException("文件类型不一致，无法移动");
            }

            if (Files.isDirectory(sourcePath)) {
                // 目录则合并
                FileUtils.mergeDir(sourcePath.toString(), targetPath.toString(), overwrite);
            } else if (overwrite){
                // 文件则替换移动（仅当overwrite为true时）
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                // 为了与数据库记录保持一致，原文件还是要删滴
                Files.delete(sourcePath);
            }
        } else {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 文件重命名
     * @param uid   用户ID 0表示公共
     * @param path  文件所在路径
     * @param oldName 旧文件名
     * @param newName 新文件名
     */
    public void rename(int uid, String path, String oldName, String newName) throws JsonException {
        String base = DiskConfig.getRawFileStoreRootPath(uid);
        File origin = new File(base + "/" + path + "/" + oldName);
        File dist = new File(base + "/" + path + "/" + newName);
        if (!origin.exists()) {
            throw new JsonException("原文件不存在");
        }
        if (dist.exists()) {
            throw new JsonException("文件名冲突");
        }
        if (!origin.renameTo(dist)) {
            throw new JsonException("移动失败");
        }

    }

    /**
     * 在本地文件系统中创建文件夹
     * @param uid   用户ID
     * @param path  所在路径
     * @param name  文件夹名
     * @throws FileAlreadyExistsException 目标已存在时抛出
     * @return 是否创建成功
     */
    public boolean mkdir(int uid, String path, String name) throws FileAlreadyExistsException, DirectoryAlreadyExistsException {
        String localFilePath = DiskConfig.getRawFileStoreRootPath(uid) + "/" + path + "/" + name;
        File file = new File(localFilePath);
        if (file.mkdir()) {
            return true;
        } else {
            if (file.exists()) {
                if (file.isDirectory()) {
                    throw new DirectoryAlreadyExistsException(file + "/" + name);
                } else {
                    throw new FileAlreadyExistsException(file + "/" + name);
                }
            }
            log.error("在本地路径\"" + localFilePath + "\"创建文件夹失败");
            return false;
        }
    }

    /**
     * 删除一个唯一存储类型的文件
     * @param md5   文件MD5
     * @return      删除的文件和目录数
     */
    public int delete(String md5) throws IOException {
        int res = 1;
        Path filePath = Paths.get(DiskConfig.getUniqueStoreRoot() + "/" + StringUtils.getUniquePath(md5));
        Files.delete(filePath);
        log.debug("删除本地文件：" + filePath);
        DirectoryStream<Path> paths = Files.newDirectoryStream(filePath.getParent());
        // 最里层目录
        if (  !paths.iterator().hasNext() ) {
            log.debug("删除本地目录：" + filePath.getParent());
            res++;
            paths.close();
            Files.delete(filePath.getParent());
            paths = Files.newDirectoryStream(filePath.getParent().getParent());

            // 外层目录
            if ( !paths.iterator().hasNext()) {
                log.debug("删除本地目录：" + filePath.getParent().getParent());
                res++;
                Files.delete(filePath.getParent().getParent());
                paths.close();
            }
            paths.close();
        } else {
            paths.close();
        }
        return res;
    }

    /**
     * 删除本地文件（文件夹会连同所有子文件和目录）
     * @param uid 用户ID
     * @param path 文件所在网盘目录的路径
     * @param files 文件名
     * @return 删除的文件和文件夹总数
     */
    public long delete(int uid, String path, Collection<String> files) {
        AtomicLong cnt = new AtomicLong();
        // 本地物理基础路径
        String basePath = DiskConfig.getRawFileStoreRootPath(uid)  + "/" + path;
        files.forEach(fileName -> {

            // 本地完整路径
            String local = basePath + "/" + fileName;
            File file = new File(local);
            if (file.isDirectory()) {
                Path path1 = Paths.get(local);
                try {
                    Files.walkFileTree(path1, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            log.debug("删除文件 " + file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            log.debug("删除目录 " + dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    throw new JsonException(500, e.getMessage());
                }
            } else {
                if (!file.delete()){
                    log.error("文件删除失败：" + file.getPath());
                } else {
                    cnt.incrementAndGet();
                }
            }
        });
        return cnt.longValue();
    }

}
