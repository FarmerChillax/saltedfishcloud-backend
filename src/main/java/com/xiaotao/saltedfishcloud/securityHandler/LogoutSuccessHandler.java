package com.xiaotao.saltedfishcloud.securityHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class LogoutSuccessHandler implements org.springframework.security.web.authentication.logout.LogoutSuccessHandler {
    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        ObjectMapper om = new ObjectMapper();
        try {
            response.getWriter().print(om.writeValueAsString(JsonResult.getInstance()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
