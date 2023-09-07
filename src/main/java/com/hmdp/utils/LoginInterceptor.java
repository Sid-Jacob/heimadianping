package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {

    // @Resource
    // private StringRedisTemplate stringRedisTemplate;

    // public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
    //     this.stringRedisTemplate = stringRedisTemplate;
    // }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // //1. 获取session
        // HttpSession session = request.getSession();
        // //2.获取session中的用户
        // Object user = session.getAttribute("user");

        // //1. get token from request header
        // String token = request.getHeader("authorization");
        // if (StrUtil.isBlank(token)){
        //     response.setStatus(401);
        //     return false;
        // }
        // //2. get user from redis
        // Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        //
        // //3. 判断用户是否存在
        // if (userMap.isEmpty()){
        //     //4. 不存在，拦截
        //     response.setStatus(401);
        //     return false;
        // }
        //
        // //5. hash 2 map
        // UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //
        // //6. save userDTO
        // UserHolder.saveUser(userDTO);
        //
        // //7. flush the login tiimeout
        // stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // //5. 存在 保存用户信息到ThreadLocal
        // UserHolder.saveUser((UserDTO) user);
        //6. 放行

        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
