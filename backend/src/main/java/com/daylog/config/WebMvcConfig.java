package com.daylog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * <p>把 /files/** 的访问映射到本地磁盘上传目录，
 * 图片 URL 由后端静态资源直接服务，无需另建文件服务器。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${daylog.upload.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // file: 协议前缀，路径必须以 / 结尾
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
