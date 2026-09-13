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

    proofImage.addEventListener("change", function () {
        if (previewUrl) {
            URL.revokeObjectURL(previewUrl);
            previewUrl = null;
        }

        const file = proofImage.files[0];

        if (!file) {
            proofPreview.hidden = true;
            proofPreview.removeAttribute("src");
            return;
        }

        previewUrl = URL.createObjectURL(file);
        proofPreview.src = previewUrl;
        proofPreview.hidden = false;
    });

    form.addEventListener("submit", function (event) {
        if (!hasSignature) {
            event.preventDefault();
            window.alert("Vui lòng ký xác nhận trước khi lưu.");
            return;
        }

        signatureData.value = canvas.toDataURL("image/png");
    });

    clearCanvas();
}());
