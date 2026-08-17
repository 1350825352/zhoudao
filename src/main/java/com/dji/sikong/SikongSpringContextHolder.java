package com.dji.sikong;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring上下文工具类, 用于非Spring容器管理的类获取Bean (Service里兜底取GPS用)
 */
@Component
public class SikongSpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext CTX;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        CTX = applicationContext;
    }

    public static <T> T getBean(Class<T> clz) {
        if (CTX == null) return null;
        return CTX.getBean(clz);
    }

    public static Object getBean(String name) {
        if (CTX == null) return null;
        return CTX.getBean(name);
    }
}
