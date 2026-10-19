package vn.edu.hcmute.uteexpress.service.media.impl;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.cloudinary.Cloudinary;
import org.springframework.stereotype.Component;
import vn.edu.hcmute.uteexpress.config.CloudinaryConfig;
import vn.edu.hcmute.uteexpress.service.media.CloudMediaReference;
import vn.edu.hcmute.uteexpress.exception.CloudStorageUnavailableException;
import vn.edu.hcmute.uteexpress.service.media.CloudMediaGateway;

@Component
public class CloudinaryMediaGatewayImpl implements CloudMediaGateway {
    private final CloudinaryConfig.Settings settings;
    private final Cloudinary sdk;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public CloudinaryMediaGatewayImpl(CloudinaryConfig.Settings settings) {
        this.settings = settings;
        sdk = settings.configured() ? new Cloudinary(Map.of("cloud_name",settings.cloudName(),"api_key",settings.apiKey(),
                "api_secret",settings.apiSecret(),"secure",true,"timeout",15000,"signature_algorithm","SHA256")) : null;
    }
    private Cloudinary client() {
        if (sdk == null) throw new CloudStorageUnavailableException();
        return sdk;
    }
    @Override public String cloudName() { client(); return settings.cloudName(); }
    @Override public UploadResult upload(CloudMediaReference ref,byte[] bytes) {
        try {
            var result = client().uploader().upload(bytes,Map.of("public_id",ref.publicId(),"resource_type",ref.resourceType(),
                    "type","authenticated","overwrite",false,"unique_filename",false,"allowed_formats",List.of(ref.format()),
                    "asset_folder","uteexpress/"+ref.purpose().folder(),"return_error",true));
            if (result.get("error") instanceof Map<?,?> error) {
                long status=number(error,"http_code");
                if (status==400 || status==413) throw new IllegalArgumentException("Tệp không được cloud chấp nhận.");
                throw new CloudStorageUnavailableException();
            }
            return new UploadResult(text(result,"public_id"),text(result,"resource_type"),text(result,"type"),text(result,"format"),
                    number(result,"bytes"),number(result,"width"),number(result,"height"),
                    result.get("duration") instanceof Number duration ? duration.doubleValue() : 0);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Cloud không giải mã được tệp. Hãy chọn ảnh/video hợp lệ.");
        } catch (Exception ex) { throw new CloudStorageUnavailableException(); }
    }
    @Override public byte[] download(CloudMediaReference ref) {
        try {
            if (!ref.cloudName().equals(settings.cloudName())) throw new CloudStorageUnavailableException();
            String signed = client().privateDownload(ref.publicId(),ref.format(),Map.of("type","authenticated",
                    "resource_type",ref.resourceType(),"expires_at",java.time.Instant.now().getEpochSecond()+60));
            URI uri = URI.create(signed);
            if (!"https".equals(uri.getScheme()) || !"api.cloudinary.com".equals(uri.getHost()) || uri.getUserInfo()!=null
                    || !uri.getPath().equals("/v1_1/"+settings.cloudName()+"/"+ref.resourceType()+"/download"))
                throw new CloudStorageUnavailableException();
            // Giới hạn cả bộ nhớ lẫn tổng thời gian tải, kể cả máy chủ gửi body rất chậm.
            var response = http.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),
                    info -> new BoundedBodySubscriber(ref.purpose().maximumBytes()));
            HttpResponse<byte[]> completed;
            try { completed = response.get(20,TimeUnit.SECONDS); }
            finally { if (!response.isDone()) response.cancel(true); }
            if (completed.statusCode()==404) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Tệp cloud không tồn tại.");
            if (completed.statusCode()!=200 || completed.body().length==0) throw new CloudStorageUnavailableException();
            return completed.body();
        } catch (org.springframework.web.server.ResponseStatusException ex) { throw ex; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new CloudStorageUnavailableException(); }
        catch (Exception ex) { throw new CloudStorageUnavailableException(); }
    }
    @Override public void deleteNewUpload(CloudMediaReference ref) {
        if (!ref.cloudName().equals(settings.cloudName())) throw new CloudStorageUnavailableException();
        try {
            var result = client().uploader().destroy(ref.publicId(),Map.of("resource_type",ref.resourceType(),"type","authenticated","invalidate",true));
            if (!Set.of("ok","not found").contains(text(result,"result"))) throw new CloudStorageUnavailableException();
        }
        catch (Exception ex) { throw new CloudStorageUnavailableException(); }
    }
    private static String text(Map<?,?> result,String key) { return result.get(key) instanceof String value ? value : ""; }
    private static long number(Map<?,?> result,String key) { return result.get(key) instanceof Number value ? value.longValue() : -1; }

    static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int maximum;
        private Flow.Subscription subscription;
        private long size;
        private boolean failed;
        BoundedBodySubscriber(int maximum) { this.maximum = maximum; }
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription=value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            for (var buffer : buffers) size+=buffer.remaining();
            if (size>maximum) {
                failed=true; subscription.cancel(); delegate.onError(new IOException("Cloud response exceeds file limit."));
            } else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { if (!failed) delegate.onError(error); }
        @Override public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
