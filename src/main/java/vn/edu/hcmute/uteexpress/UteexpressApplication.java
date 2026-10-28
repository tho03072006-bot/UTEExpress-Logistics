package vn.edu.hcmute.uteexpress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Diem khoi dong (entry point) cua project UTEExpress.
 * Chay class nay (Run As > Spring Boot App trong Eclipse, hoac mvn spring-boot:run)
 * roi mo http://localhost:8080 de xem trang chu Guest.
 */
@SpringBootApplication
public class UteexpressApplication {

    public static void main(String[] args) {
        SpringApplication.run(UteexpressApplication.class, args);
    }

}
