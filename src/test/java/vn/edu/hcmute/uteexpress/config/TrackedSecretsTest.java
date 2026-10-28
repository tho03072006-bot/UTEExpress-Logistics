package vn.edu.hcmute.uteexpress.config;

import java.nio.file.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackedSecretsTest {
    @Test void sharedApplicationConfigContainsNoLiteralCredential()throws Exception{
        var values=new Properties();try(var reader=Files.newBufferedReader(Path.of("src/main/resources/application.properties"))){values.load(reader);}
        for(String key:values.stringPropertyNames()){
            if(!key.matches("(?i).*(password|secret|api[-_.]?key|signing[-_.]?key).*"))continue;
            String value=values.getProperty(key);
            assertTrue(value.isBlank() || value.startsWith("${") || value.startsWith("CHANGE_ME"),"Credential must be private/environment-backed; key="+key);
        }
    }
    @Test void privateApplicationConfigIsExcludedFromPackaging()throws Exception{
        String pom=Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<exclude>application-local.properties*</exclude>"));
        assertTrue(Files.readString(Path.of(".gitignore")).contains("application-local.properties"));
    }
}
