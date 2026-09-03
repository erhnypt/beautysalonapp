package com.beautysalonapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA yönlendirmesi: derlenmiş React uygulaması tek sayfadır; bilinmeyen (API olmayan)
 * yollar {@code index.html}'e düşer ki tarayıcı tarafı router çalışsın.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // /api, /actuator, /v3, /swagger-ui ve nokta içeren (dosya) yollar hariç her şey index.html
        registry.addViewController("/{path:^(?!api|actuator|v3|swagger-ui|assets)[^\\.]*}")
                .setViewName("forward:/index.html");
        registry.addViewController("/{path:^(?!api|actuator|v3|swagger-ui|assets)[^\\.]*}/{sub:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}
