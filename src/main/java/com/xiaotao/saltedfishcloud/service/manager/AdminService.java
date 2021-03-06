package com.xiaotao.saltedfishcloud.service.manager;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileAnalyseDao;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminService {
    @Resource
    private FileAnalyseDao fileAnalyseDao;
    public Map<String, Object> getStoreState() {
        LinkedHashMap<String, Object> data = JsonResult.getDataMap();
        File storeRoot = new File(DiskConfig.STORE_ROOT);
        File publicRoot = new File(DiskConfig.PUBLIC_ROOT);
        long userTotalSize = fileAnalyseDao.getUserTotalSize();
        long realTotalUserSize = DiskConfig.STORE_TYPE == StoreType.UNIQUE ? fileAnalyseDao.getRealTotalUserSize() : userTotalSize;
        long publicTotalSize = fileAnalyseDao.getPublicTotalSize();
        data.put("store_type", DiskConfig.STORE_TYPE);
        data.put("file_count", fileAnalyseDao.getFileCount());
        data.put("dir_count", fileAnalyseDao.getDirCount());
        data.put("real_user_size", realTotalUserSize);
        data.put("total_user_size", userTotalSize);
        data.put("total_public_size", publicTotalSize);
        data.put("store_total_space", storeRoot.getTotalSpace());
        data.put("store_free_space", storeRoot.getFreeSpace());
        data.put("public_total_space", publicRoot.getTotalSpace());
        data.put("public_free_space", publicRoot.getFreeSpace());
        data.put("store_root", storeRoot.getPath());
        data.put("public_root", publicRoot.getPath());
        data.put("read_only", DiskConfig.getReadOnlyLevel());
        return data;
    }
}
