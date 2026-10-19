package vn.edu.hcmute.uteexpress.exception;

/** Không đính kèm exception của SDK vì có thể chứa URL ký/mật khẩu. */
public class CloudStorageUnavailableException extends RuntimeException {
    public CloudStorageUnavailableException() {
        super("Kho tệp cloud chưa sẵn sàng hoặc đang quá giới hạn. Không lưu tạm trên máy; hãy thử lại sau.");
    }
}
