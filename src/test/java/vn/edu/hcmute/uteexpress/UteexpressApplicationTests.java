package vn.edu.hcmute.uteexpress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Kiem tra khoi dong voi Aiven that va validate schema, khong sua schema.
 * Chay chu dong bang mvn -Paiven-integration verify sau khi cau hinh TLS local.
 * mvn test mac dinh khong can database/SMTP hay bi mat cua nhom.
 */
@SpringBootTest
@Tag("aiven")
class UteexpressApplicationTests {

    @Test
    void contextLoads() {
    }

}
