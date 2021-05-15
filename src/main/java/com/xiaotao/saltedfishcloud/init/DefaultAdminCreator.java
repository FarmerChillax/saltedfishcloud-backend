package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.dao.UserDao;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.service.user.UserType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class DefaultAdminCreator  implements ApplicationRunner {
    @Resource
    private UserDao userDao;
    @Resource
    private UserService userService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int cnt = userDao.getUserCount();
        log.info("当前系统存在用户数：" + cnt);
        if (cnt == 0) {
            userService.addUser("admin", "admin666", UserType.ADMIN);
            log.warn("创建初始管理员用户：admin 密码：admin666，建议及时修改密码");
        }
    }
}