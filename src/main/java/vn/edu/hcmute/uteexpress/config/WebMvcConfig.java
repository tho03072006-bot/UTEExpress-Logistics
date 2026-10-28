package vn.edu.hcmute.uteexpress.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Không ánh xạ /uploads sang ổ đĩa. Tệp được đọc qua controller và Cloudinary chung. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer { }
