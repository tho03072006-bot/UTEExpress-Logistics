package vn.edu.hcmute.uteexpress.security;

import java.net.URI;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/** Only browser page navigation may become the destination after public login. */
public final class PageRequestCache extends HttpSessionRequestCache {
    public PageRequestCache() {
        setRequestMatcher(request -> "GET".equals(request.getMethod())
                && acceptsHtml(request.getHeader("Accept")) && !isBackgroundRequest(request));
    }

    public static boolean isBackgroundRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return isBackgroundPath(path)
                || "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || "empty".equalsIgnoreCase(request.getHeader("Sec-Fetch-Dest"))
                || (acceptsJson(request.getHeader("Accept")) && !acceptsHtml(request.getHeader("Accept")));
    }

    @Override
    public SavedRequest getRequest(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = super.getRequest(request, response);
        if (saved != null && !isSavedPage(saved, request.getContextPath())) {
            // Also discard background requests saved by an earlier application version.
            super.removeRequest(request, response);
            return null;
        }
        return saved;
    }

    private static boolean isSavedPage(SavedRequest saved, String contextPath) {
        try {
            String path = URI.create(saved.getRedirectUrl()).getPath();
            if (!contextPath.isEmpty() && path.startsWith(contextPath + "/")) {
                path = path.substring(contextPath.length());
            }
            return "GET".equals(saved.getMethod()) && !isBackgroundPath(path)
                    && saved.getHeaderValues("Accept").stream().anyMatch(PageRequestCache::acceptsHtml)
                    && saved.getHeaderValues("X-Requested-With").stream().noneMatch("XMLHttpRequest"::equalsIgnoreCase)
                    && saved.getHeaderValues("Sec-Fetch-Dest").stream().noneMatch("empty"::equalsIgnoreCase);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isBackgroundPath(String path) {
        return path.equals("/shipper/thong-bao/chua-doc") || path.startsWith("/api/")
                || path.equals("/ws-tracking") || path.startsWith("/ws-tracking/")
                || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/uploads/") || path.startsWith("/private-proof/")
                || path.endsWith(".csv") || path.endsWith(".png") || path.contains("/bang-chung/");
    }

    private static boolean acceptsHtml(String accept) {
        return accept != null && accept.toLowerCase(Locale.ROOT).contains("text/html");
    }

    private static boolean acceptsJson(String accept) {
        return accept != null && accept.toLowerCase(Locale.ROOT).contains("application/json");
    }
}
