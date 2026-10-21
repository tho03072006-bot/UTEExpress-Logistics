package vn.edu.hcmute.uteexpress.security;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicAuthRateGuardTest {
    static class Time extends Clock {
        Instant value=Instant.EPOCH;
        public Instant instant(){return value;}
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return Clock.fixed(value,zone);}
    }
    @Test void repeatedMailRequestsAreBoundedPerIpAndExpire(){
        var time=new Time();var guard=new PublicAuthRateGuard(time);
        for(int i=0;i<20;i++) assertEquals(0,guard.checkRequest("/quen-mat-khau","ip-one"));
        assertTrue(guard.checkRequest("/dang-ky","ip-one")>0);
        assertEquals(0,guard.checkRequest("/dang-ky","ip-two"));
        time.value=time.value.plusSeconds(3600);assertEquals(0,guard.checkRequest("/xac-thuc-otp/gui-lai","ip-one"));
    }
    @Test void loginFailuresUseNormalizedAccountAndClearAfterSuccessOrTimeout(){
        var time=new Time();var guard=new PublicAuthRateGuard(time);
        for(int i=0;i<10;i++)guard.loginFailed("  CUSTOMER  ");
        assertTrue(guard.loginBlocked("customer"));assertFalse(guard.loginBlocked("other"));
        guard.loginSucceeded("customer");assertFalse(guard.loginBlocked("customer"));
        for(int i=0;i<10;i++)guard.loginFailed("customer");time.value=time.value.plusSeconds(900);
        assertFalse(guard.loginBlocked("customer"));
    }
    @Test void otpRequestsHaveSeparateMinuteWindowFromMailQuota(){
        var time=new Time();var guard=new PublicAuthRateGuard(time);
        for(int i=0;i<60;i++)assertEquals(0,guard.checkRequest("/xac-thuc-otp","ip"));
        assertTrue(guard.checkRequest("/dat-lai-mat-khau","ip")>0);
        assertEquals(0,guard.checkRequest("/quen-mat-khau","ip"));
        time.value=time.value.plusSeconds(60);assertEquals(0,guard.checkRequest("/xac-thuc-otp","ip"));
    }
}
