package vn.edu.hcmute.uteexpress.config;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import vn.edu.hcmute.uteexpress.service.payment.MomoSettings;

@Configuration(proxyBeanMethods = false)
@PropertySource(value = "file:./config/momo-local.properties", ignoreResourceNotFound = true, encoding = "UTF-8")
public class MomoConfiguration {
    @Bean public MomoSettings momoSettings(Environment environment) { return new MomoSettings(environment); }
}
