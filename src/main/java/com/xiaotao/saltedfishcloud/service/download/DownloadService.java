package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.dao.jpa.DownloadTaskRepository;
import com.xiaotao.saltedfishcloud.dao.mybatis.ProxyDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.po.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.po.param.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.po.param.TaskType;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContext;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManager;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

@Service
@Slf4j
public class DownloadService {
    static final private Collection<DownloadTaskInfo.State> FINISH_TYPE = Arrays.asList(
            DownloadTaskInfo.State.FINISH,
            DownloadTaskInfo.State.CANCEL,
            DownloadTaskInfo.State.FAILED
    );
    static final private Collection<DownloadTaskInfo.State> DOWNLOADING_TYPE = Collections.singleton(DownloadTaskInfo.State.DOWNLOADING);
    @Resource
    private DownloadTaskRepository downloadDao;
    @Resource
    private ProxyDao proxyDao;
    @Resource
    private TaskContextFactory factory;
    @Resource
    private NodeService nodeService;
    @Resource
    private FileService fileService;
    private final TaskManager taskManager;

    /**
     * ????????????????????????
     */
    public DownloadService(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public TaskContext<DownloadTask> getTaskContext(String taskId) {
        return taskManager.getContext(taskId, DownloadTask.class);
    }

    public void interrupt(String id) {
        var context = taskManager.getContext(id, DownloadTask.class);
        if (context == null) {
            throw new JsonException(404, id + "?????????");
        } else {
            context.interrupt();
            taskManager.remove(context);
        }
    }

    /**
     * ?????????????????????????????????
     * @param uid   ??????????????????ID
     */
    public Page<DownloadTaskInfo> getTaskList(int uid, int page, int size, TaskType type) {
        Page<DownloadTaskInfo> tasks;
        var pageRequest = PageRequest.of(page, size);
        if (type == null || type == TaskType.ALL) {
            tasks = downloadDao.findByUidOrderByCreatedAtDesc(uid, pageRequest);
        } else if (type == TaskType.DOWNLOADING) {
            tasks = downloadDao.findByUidAndStateInOrderByCreatedAtDesc(uid, DOWNLOADING_TYPE, pageRequest);
        } else {
            tasks = downloadDao.findByUidAndStateInOrderByCreatedAtDesc(uid, FINISH_TYPE, pageRequest);
        }
        tasks.forEach(e -> {
            if (e.state == DownloadTaskInfo.State.DOWNLOADING ) {
                var context = taskManager.getContext(e.id, DownloadTask.class);
                if (context == null) {
                    e.state = DownloadTaskInfo.State.FAILED;
                    e.message = "interrupt";
                    downloadDao.save(e);
                } else {
                    var task = context.getTask();
                    e.loaded = task.getStatus().loaded;
                    e.speed = task.getStatus().speed;
                }
            }
        });
        return tasks;
    }

    /**
     * ????????????????????????
     * @param params ????????????
     * @TODO ??????????????????????????????????????????
     * @return ????????????ID
     */
    public String createTask(DownloadTaskParams params, int creator) throws NoSuchFileException {
        // ?????????????????????????????????
        var builder = DownloadTaskBuilder.create(params.url);
        builder.setHeaders(params.headers);
        builder.setMethod(params.method);
        if (params.proxy != null && params.proxy.length() != 0) {
            ProxyInfo proxy = proxyDao.getProxyByName(params.proxy);
            if (proxy == null) {
                throw new JsonException(400, "??????????????????" + params.proxy);
            }
            builder.setProxy(proxy.toProxy());
            log.debug("?????????????????????????????????" + proxy);
        }

        // ?????????????????????
        nodeService.getPathNodeByPath(params.uid, params.savePath);

        DownloadTask task = builder.build();
        TaskContext<DownloadTask> context = factory.createContextFromAsyncTask(task);
        // ?????????????????????????????????????????????
        var info = new DownloadTaskInfo();
        task.bindingInfo = info;
        info.id = context.getId();
        info.url = params.url;
        info.proxy = params.proxy;
        info.uid = params.uid;
        info.state = DownloadTaskInfo.State.DOWNLOADING;
        info.createdBy = creator;
        info.savePath = params.savePath;
        info.createdAt = new Date();
        downloadDao.save(info);

        // ??????????????????
        task.onReady(() -> {
            info.size = task.getStatus().total;
            info.name = task.getStatus().name;
            downloadDao.save(info);
            log.debug("Task ON Ready");
        });

        context.onSuccess(() -> {
            info.state = DownloadTaskInfo.State.FINISH;
            // ???????????????????????????md5???
            var tempFile = Paths.get(task.getSavePath());
            var fileInfo = FileInfo.getLocal(tempFile.toString());
            try {
                // ??????????????????????????????????????????????????????????????????????????????
                fileService.mkdirs(params.uid, params.savePath);


                // ??????????????????????????????????????????
                if (task.getStatus().name != null) {
                    fileInfo.setName(task.getStatus().name);
                    info.name = task.getStatus().name;
                } else {
                    info.name = fileInfo.getName();
                }

                // ???????????????????????????
                fileService.moveToSaveFile(params.uid, tempFile, params.savePath, fileInfo);
            } catch (FileAlreadyExistsException e) {
                // ??????????????????????????????????????????????????????????????????????????????
                info.savePath = "/download" + System.currentTimeMillis() + info.savePath;
                try {
                    fileService.mkdirs(params.uid, info.savePath);
                    fileService.moveToSaveFile(params.uid, tempFile, params.savePath, fileInfo);
                    info.state = DownloadTaskInfo.State.FINISH;
                } catch (IOException ex) {
                    // ??????????????????????????????
                    info.message = e.getMessage();
                    info.state = DownloadTaskInfo.State.FAILED;
                }
            } catch (Exception e) {
                // ??????????????????
                e.printStackTrace();
                info.message = e.getMessage();
                info.state = DownloadTaskInfo.State.FAILED;
            }
            info.finishAt = new Date();
            info.size = task.getStatus().total;
            info.loaded = info.size;
            downloadDao.save(info);
        });
        context.onFailed(() -> {
            if (task.isInterrupted()) {
                info.state = DownloadTaskInfo.State.CANCEL;
                info.message = "has been interrupted";
            } else {
                info.state = DownloadTaskInfo.State.FAILED;
                info.message = task.getStatus().error;
            }
            info.loaded = task.getStatus().loaded;
            info.size = task.getStatus().total != -1 ? task.getStatus().total : task.getStatus().loaded;
            info.finishAt = new Date();
            downloadDao.save(info);
        });

        // ??????????????????
        factory.getManager().submit(context);

        return context.getId();
    }
}
