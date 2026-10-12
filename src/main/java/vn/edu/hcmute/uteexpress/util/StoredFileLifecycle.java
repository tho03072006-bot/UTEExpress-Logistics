package vn.edu.hcmute.uteexpress.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class StoredFileLifecycle {
    private StoredFileLifecycle() { }
    public static void removeOnRollback(Path path) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) remove(path);
                }
            });
        }
    }
    public static void remove(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ex) {
            org.slf4j.LoggerFactory.getLogger(StoredFileLifecycle.class).warn("Không dọn được tệp đã lưu.");
        }
    }
}
