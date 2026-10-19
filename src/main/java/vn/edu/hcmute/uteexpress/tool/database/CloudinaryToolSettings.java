package vn.edu.hcmute.uteexpress.tool.database;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.Properties;
import vn.edu.hcmute.uteexpress.config.CloudinaryConfig;

/** Chỉ dùng cho CLI chạy tay; không khởi động server, scheduler hay tạo tài khoản demo. */
final class CloudinaryToolSettings {
    private CloudinaryToolSettings() { }
    static CloudinaryConfig.Settings load() throws IOException {
        var values = new Properties();
        Path file=Path.of("config/cloudinary-local.properties");
        if (Files.isRegularFile(file)) try (var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) { values.load(reader); }
        try {
            var settings=new CloudinaryConfig.Settings(Boolean.parseBoolean(value(values,"enabled","true")),
                    value(values,"cloud-name",""),value(values,"api-key",""),value(values,"api-secret",""));
            if (!settings.configured()) throw new IllegalArgumentException("Cloudinary fields invalid: enabled="+settings.enabled()
                    +", cloudName="+settings.cloudName().matches("[a-zA-Z0-9_-]{1,100}")
                    +", apiKey="+settings.apiKey().matches("[0-9]{5,30}")
                    +", apiSecret="+settings.apiSecret().matches("[a-zA-Z0-9_-]{16,256}"));
            return settings;
        } finally { values.clear(); }
    }
    private static String value(Properties values,String key,String defaultValue) {
        String env=System.getenv("APP_CLOUDINARY_"+key.toUpperCase(java.util.Locale.ROOT).replace('-','_'));
        return (env!=null ? env : values.getProperty("app.cloudinary."+key,defaultValue)).strip();
    }
}
