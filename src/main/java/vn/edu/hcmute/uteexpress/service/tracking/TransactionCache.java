package vn.edu.hcmute.uteexpress.service.tracking;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Nhớ kết quả đọc trong phạm vi một giao dịch, giống bộ nhớ đệm cấp 1 của Hibernate nhưng tra theo khóa tùy ý
 * (ví dụ tài khoản theo tên đăng nhập). Một trang Shipper gọi nhiều service cùng chung một giao dịch; mỗi lần
 * đọc lại cùng một dòng tốn thêm một lượt đi về database (~100 ms với Aiven).
 * Ngoài giao dịch (test đơn vị, luồng nền) thì luôn đọc thẳng như cũ.
 */
public final class TransactionCache {
    private static final Object RESOURCE = TransactionCache.class;

    private TransactionCache() { }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> loader) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) return loader.get();
        var cache = (Map<String, Object>) TransactionSynchronizationManager.getResource(RESOURCE);
        if (cache == null) {
            var created = new HashMap<String, Object>();
            TransactionSynchronizationManager.bindResource(RESOURCE, created);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void suspend() {
                    if (TransactionSynchronizationManager.getResource(RESOURCE) == created)
                        TransactionSynchronizationManager.unbindResource(RESOURCE);
                }
                @Override public void resume() {
                    TransactionSynchronizationManager.bindResource(RESOURCE, created);
                }
                @Override public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.getResource(RESOURCE) == created)
                        TransactionSynchronizationManager.unbindResource(RESOURCE);
                }
            });
            cache = created;
        }
        if (cache.containsKey(key)) return (T) cache.get(key);
        T value = loader.get();
        cache.put(key, value);
        return value;
    }
}
