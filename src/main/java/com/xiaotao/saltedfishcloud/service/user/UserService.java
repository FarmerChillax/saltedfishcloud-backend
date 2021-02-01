package com.xiaotao.saltedfishcloud.service.user;

import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.po.User;

public interface UserService {

    User getUserByUser(String user) throws UserNoExistException;


    int modifyPasswd(Integer uid, String oldPassword, String newPassword);

    int addUser(String user, String passwd, Integer type);

    int updateLoginDate(Integer uid);
}