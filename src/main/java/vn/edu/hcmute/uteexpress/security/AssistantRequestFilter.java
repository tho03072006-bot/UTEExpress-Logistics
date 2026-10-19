package vn.edu.hcmute.uteexpress.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/** Sau phân quyền/CSRF: giới hạn JSON trước Jackson, không đọc toàn bộ body không giới hạn vào bộ nhớ. */
public class AssistantRequestFilter extends OncePerRequestFilter {
    private static final int MAX_BODY_BYTES=16*1024;
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals("/api/assistant/messages") || !request.getMethod().equals("POST")) { chain.doFilter(request,response); return; }
        String mediaType=request.getContentType()==null ? "" : request.getContentType().split(";",2)[0].strip();
        if (!mediaType.equalsIgnoreCase("application/json")) {
            reject(response,415); return;
        }
        if (request.getContentLengthLong()>MAX_BODY_BYTES) { reject(response,413); return; }
        byte[] bytes=request.getInputStream().readNBytes(MAX_BODY_BYTES+1);
        if (bytes.length>MAX_BODY_BYTES) { reject(response,413); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] buffer,int offset,int length) { return input.read(buffer,offset,length); }
                    @Override public boolean isFinished() { return input.available()==0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous JSON endpoint."); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
            @Override public int getContentLength() { return bytes.length; }
            @Override public long getContentLengthLong() { return bytes.length; }
        },response);
    }
    private void reject(HttpServletResponse response,int status) throws IOException {
        response.setStatus(status); response.setHeader("Cache-Control","no-store"); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"INVALID_INPUT\",\"message\":\"Chỉ nhận JSON tối đa 16 KiB; câu hỏi tối đa 1200 ký tự.\"}");
    }
}
