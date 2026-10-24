(function () {
    "use strict";

    const canvas = document.getElementById("signatureCanvas");
    const form = document.getElementById("deliveryProofForm");

    if (!canvas || !form) {
        return;
    }

    const context = canvas.getContext("2d");
    const signatureData = document.getElementById("signatureData");
    const clearButton = document.getElementById("clearSignature");
    const proofImage = document.getElementById("proofImage");
    const proofPreview = document.getElementById("proofPreview");

    let drawing = false;
    let hasSignature = false;
    let previewUrl = null;
    let lastPoint = null;

    function clearCanvas() {
        context.fillStyle = "#ffffff";
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.strokeStyle = "#0B2540";
        context.lineWidth = 3;
        context.lineCap = "round";
        context.lineJoin = "round";
        signatureData.value = "";
        hasSignature = false;
        drawing = false;
        lastPoint = null;
    }

    function pointFromEvent(event) {
        const rectangle = canvas.getBoundingClientRect();

        return {
            x: (event.clientX - rectangle.left)
                    * (canvas.width / rectangle.width),
            y: (event.clientY - rectangle.top)
                    * (canvas.height / rectangle.height)
        };
    }

    canvas.addEventListener("pointerdown", function (event) {
        event.preventDefault();
        drawing = true;
        canvas.setPointerCapture(event.pointerId);

        const point = pointFromEvent(event);
        lastPoint = point;
        context.beginPath();
        context.moveTo(point.x, point.y);
    });

    canvas.addEventListener("pointermove", function (event) {
        if (!drawing) {
            return;
        }

        event.preventDefault();
        const point = pointFromEvent(event);
        if (!lastPoint || Math.hypot(point.x - lastPoint.x, point.y - lastPoint.y) < 0.5) return;
        context.lineTo(point.x, point.y);
        context.stroke();
        hasSignature = true;
        lastPoint = point;
    });

    function stopDrawing(event) {
        if (!drawing) {
            return;
        }

        drawing = false;
        context.closePath();

        if (event.pointerId !== undefined
                && canvas.hasPointerCapture(event.pointerId)) {
            canvas.releasePointerCapture(event.pointerId);
        }
    }

    canvas.addEventListener("pointerup", stopDrawing);
    canvas.addEventListener("pointercancel", stopDrawing);

    clearButton.addEventListener("click", clearCanvas);

    /* Ảnh chụp từ điện thoại thường 4-10 MB: thu nhỏ còn cạnh dài 1600 px, JPEG chất lượng 0.82 ngay trên máy
       trước khi gửi, để tải lên nhanh và không vượt giới hạn 5 MB. Không nhận ảnh hỏng hoặc định dạng ngoài JPG/PNG. */
    const MAX_SIDE = 1600;
    let compressing = false;
    let imageSequence = 0;

    function shrink(file) {
        return new Promise(function (resolve, reject) {
            if (!file || !/^image\/(jpeg|png|webp)$/.test(file.type) || typeof DataTransfer === "undefined") {
                resolve(file);
                return;
            }
            const url = URL.createObjectURL(file);
            const image = new Image();
            image.onload = function () {
                URL.revokeObjectURL(url);
                try {
                    if (!image.naturalWidth || !image.naturalHeight) throw new Error('Ảnh không hợp lệ. Hãy chọn lại ảnh JPG/PNG.');
                    const scale = Math.min(1, MAX_SIDE / Math.max(image.naturalWidth, image.naturalHeight));
                    if (scale === 1 && file.size <= 1.5 * 1024 * 1024) {
                        resolve(file);
                        return;
                    }
                    const board = document.createElement("canvas");
                    board.width = Math.round(image.naturalWidth * scale);
                    board.height = Math.round(image.naturalHeight * scale);
                    const draw = board.getContext("2d");
                    draw.fillStyle = "#ffffff";
                    draw.fillRect(0, 0, board.width, board.height);
                    draw.drawImage(image, 0, 0, board.width, board.height);
                    board.toBlob(function (blob) {
                        try {
                            if (!blob || blob.size >= file.size) {
                                resolve(file);
                                return;
                            }
                            const name = file.name.replace(/\.[^.]+$/, "") + ".jpg";
                            resolve(new File([blob], name, {type: "image/jpeg", lastModified: Date.now()}));
                        } catch (error) { reject(error); }
                    }, "image/jpeg", 0.82);
                } catch (error) { reject(error); }
            };
            image.onerror = function () {
                URL.revokeObjectURL(url);
                reject(new Error('Không đọc được ảnh. Hãy chọn lại ảnh JPG/PNG.'));
            };
            image.src = url;
        });
    }

    proofImage.addEventListener("change", async function () {
        const run = ++imageSequence;
        if (previewUrl) {
            URL.revokeObjectURL(previewUrl);
            previewUrl = null;
        }

        proofPreview.hidden = true;
        proofPreview.removeAttribute("src");
        let file = proofImage.files && proofImage.files[0];
        compressing = Boolean(file);
        try {
            if (file) {
                if (!/^image\/(jpeg|png)$/.test(file.type) || file.size < 1) throw new Error('Chỉ nhận ảnh JPG hoặc PNG không rỗng.');
                if (file.size > 25 * 1024 * 1024) throw new Error('Ảnh gốc tối đa 25 MB trước khi thu nhỏ; ảnh gửi lên tối đa 5 MB.');
                const smaller = await shrink(file);
                if (run !== imageSequence) return;
                if (smaller !== file) {
                    const transfer = new DataTransfer();
                    transfer.items.add(smaller);
                    proofImage.files = transfer.files;
                    file = smaller;
                }
            }
        } catch (error) {
            if (run === imageSequence) {
                proofImage.setCustomValidity(error.message || 'Không xử lý được ảnh. Hãy chọn lại ảnh JPG/PNG.');
                proofImage.reportValidity();
            }
            return;
        } finally {
            if (run === imageSequence) compressing = false;
        }
        if (run !== imageSequence) return;
        const tooBig = Boolean(file && file.size > 5 * 1024 * 1024);
        proofImage.setCustomValidity(tooBig ? "Ảnh tối đa 5 MB. Hãy chọn ảnh nhỏ hơn." : "");
        proofImage.classList.toggle("is-invalid", tooBig);
        if (tooBig) proofImage.reportValidity();

        if (!file || file.size > 5 * 1024 * 1024) {
            proofPreview.hidden = true;
            proofPreview.removeAttribute("src");
            return;
        }

        previewUrl = URL.createObjectURL(file);
        proofPreview.src = previewUrl;
        proofPreview.hidden = false;
    });

    form.addEventListener("submit", function (event) {
        if (compressing) {
            event.preventDefault();
            window.alert("Đang thu nhỏ ảnh, vui lòng chờ một giây rồi lưu lại.");
            return;
        }
        if (!hasSignature) {
            event.preventDefault();
            window.alert("Vui lòng ký xác nhận trước khi lưu.");
            return;
        }

        signatureData.value = canvas.toDataURL("image/png");
    });

    clearCanvas();
}());
