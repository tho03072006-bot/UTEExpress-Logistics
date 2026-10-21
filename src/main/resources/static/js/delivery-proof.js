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

    function clearCanvas() {
        context.fillStyle = "#ffffff";
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.strokeStyle = "#0B2540";
        context.lineWidth = 3;
        context.lineCap = "round";
        context.lineJoin = "round";
        signatureData.value = "";
        hasSignature = false;
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
        hasSignature = true;
        canvas.setPointerCapture(event.pointerId);

        const point = pointFromEvent(event);
        context.beginPath();
        context.moveTo(point.x, point.y);
    });

    canvas.addEventListener("pointermove", function (event) {
        if (!drawing) {
            return;
        }

        event.preventDefault();
        const point = pointFromEvent(event);
        context.lineTo(point.x, point.y);
        context.stroke();
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
       trước khi gửi, để tải lên nhanh và không vượt giới hạn 5 MB. Trình duyệt không đọc được ảnh (vd. HEIC) thì giữ nguyên. */
    const MAX_SIDE = 1600;
    let compressing = false;

    function shrink(file) {
        return new Promise(function (resolve) {
            if (!file || !/^image\/(jpeg|png|webp)$/.test(file.type) || typeof DataTransfer === "undefined") {
                resolve(file);
                return;
            }
            const url = URL.createObjectURL(file);
            const image = new Image();
            image.onload = function () {
                URL.revokeObjectURL(url);
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
                    if (!blob || blob.size >= file.size) {
                        resolve(file);
                        return;
                    }
                    const name = file.name.replace(/\.[^.]+$/, "") + ".jpg";
                    resolve(new File([blob], name, {type: "image/jpeg", lastModified: Date.now()}));
                }, "image/jpeg", 0.82);
            };
            image.onerror = function () {
                URL.revokeObjectURL(url);
                resolve(file);
            };
            image.src = url;
        });
    }

    proofImage.addEventListener("change", async function () {
        if (previewUrl) {
            URL.revokeObjectURL(previewUrl);
            previewUrl = null;
        }

        let file = proofImage.files[0];
        if (file && !compressing) {
            compressing = true;
            const smaller = await shrink(file);
            compressing = false;
            if (smaller !== file) {
                const transfer = new DataTransfer();
                transfer.items.add(smaller);
                proofImage.files = transfer.files;
                file = smaller;
            }
        }
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
