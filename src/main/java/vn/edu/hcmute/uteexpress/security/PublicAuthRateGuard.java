package vn.edu.hcmute.uteexpress.security;

import java.time.*;
import java.util.*;

/** Giới hạn theo IP dùng bộ nhớ có trần; quota OTP theo tài khoản được lưu riêng trong Aiven. */
public final class PublicAuthRateGuard {
    private record Window(Instant until, int count) { }
    private final Map<String, Window> windows = new LinkedHashMap<>();
    private final Clock clock;
    public PublicAuthRateGuard(Clock clock) { this.clock = clock; }
    private void prune() {
        var now=clock.instant(); windows.values().removeIf(window -> !now.isBefore(window.until()));
    }
    public synchronized long checkRequest(String path, String address) {
        prune();
        boolean mail = path.equals("/dang-ky") || path.equals("/quen-mat-khau") || path.equals("/xac-thuc-otp/gui-lai");
        String key="ip:"+(mail?"mail:":"verify:")+address;
        var now=clock.instant(); var value=windows.get(key);
        int limit=mail?20:60; Duration duration=mail?Duration.ofHours(1):Duration.ofMinutes(1);
        if(value==null && windows.size()>=20000) return 60;
        if(value!=null && value.count()>=limit) return Math.max(1,Duration.between(now,value.until()).toSeconds());
        windows.put(key,new Window(value==null?now.plus(duration):value.until(),value==null?1:value.count()+1)); return 0;
    }
    /** Sai liên tiếp từng này lần thì khóa tên đăng nhập 15 phút. */
    public static final int MAX_LOGIN_FAILURES = 10;
    public synchronized boolean loginBlocked(String username) {
        prune(); var value=windows.get(loginKey(username)); return value!=null && value.count()>=MAX_LOGIN_FAILURES;
    }
    /** Số lần sai còn hiệu lực của tên đăng nhập (để trang đăng nhập hiện "lần N/10"); 0 nếu chưa sai hoặc đã hết hạn. */
    public synchronized int loginFailures(String username) {
        prune(); var value=windows.get(loginKey(username)); return value==null?0:value.count();
    }
    public synchronized void loginFailed(String username) {
        prune(); String key=loginKey(username); var value=windows.get(key);
        if(value==null && windows.size()>=20000) return;
        windows.put(key,new Window(value==null?clock.instant().plusSeconds(900):value.until(),value==null?1:Math.min(MAX_LOGIN_FAILURES,value.count()+1)));
    }
    public synchronized void loginSucceeded(String username) { windows.remove(loginKey(username)); }
    private String loginKey(String username) {
        String value=username==null?"":username.strip().toLowerCase(Locale.ROOT);
        return "login:"+value.substring(0,Math.min(value.length(),50));
    }
}
