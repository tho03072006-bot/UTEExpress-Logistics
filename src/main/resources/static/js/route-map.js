/*
 * Bản đồ lộ trình trong trang (Shipper): đánh số các điểm theo đúng thứ tự lộ trình và nối thành đường đi.
 * - Chỉ chạy khi Shipper bấm "Hiện bản đồ" (địa chỉ phải gửi tới dịch vụ bản đồ bên ngoài để tìm toạ độ).
 * - Tìm toạ độ: ArcGIS World Geocoding (khớp tốt địa chỉ Việt Nam tới số nhà) rồi dự phòng Photon (dữ liệu
 *   OpenStreetMap). Theo điều khoản ArcGIS, toạ độ chỉ giữ trong bộ nhớ khi trang đang mở, không lưu lâu dài.
 * - Nền bản đồ: Esri World Street Map (không cần khoá); máy chủ ảnh nền của OpenStreetMap bị chặn ở một số mạng Việt Nam.
 * - Điểm không tìm được thì bỏ qua; các nút "Đi tới" Google Maps bên dưới vẫn dùng bình thường.
 */
(function () {
    "use strict";
    const panel = document.querySelector("[data-route-map]");
    if (!panel) return;
    const button = panel.querySelector("[data-route-map-load]");
    const status = panel.querySelector("[data-route-map-status]");
    const box = document.getElementById("routeMap");
    const LEAFLET = "https://unpkg.com/leaflet@1.9.4/dist/";
    const TILES = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}";
    const ATTRIBUTION = "Bản đồ &copy; Esri";
    const COLORS = {pickup: "#D97706", drop: "#16A34A", mixed: "#4F46E5"};
    const MIN_SCORE = 80;
    const memory = new Map();

    function stops() {
        return Array.from(document.querySelectorAll(".sx-stop[data-stop-address]")).map(function (item) {
            return {number: item.dataset.stopNumber, address: item.dataset.stopAddress, kind: item.dataset.stopKind || "drop"};
        });
    }

    function load(tag, attributes) {
        return new Promise(function (resolve, reject) {
            const element = document.createElement(tag);
            Object.keys(attributes).forEach(function (key) { element[key] = attributes[key]; });
            element.onload = resolve;
            element.onerror = function () { reject(new Error("Không tải được thư viện bản đồ. Kiểm tra kết nối mạng.")); };
            document.head.appendChild(element);
        });
    }

    async function getJson(url) {
        const controller = new AbortController();
        const timer = setTimeout(function () { controller.abort(); }, 10000);
        try {
            const response = await fetch(url, {headers: {"Accept": "application/json"}, signal: controller.signal});
            if (!response.ok) throw new Error("HTTP " + response.status);
            return await response.json();
        } finally {
            clearTimeout(timer);
        }
    }

    async function arcgis(address) {
        const url = "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates?f=json"
                + "&maxLocations=1&countryCode=VNM&outFields=Score&SingleLine=" + encodeURIComponent(address);
        const data = await getJson(url);
        const best = data && data.candidates && data.candidates[0];
        return best && best.score >= MIN_SCORE ? [Number(best.location.y), Number(best.location.x)] : null;
    }

    async function photon(address) {
        const data = await getJson("https://photon.komoot.io/api/?limit=1&lat=10.82&lon=106.68&q=" + encodeURIComponent(address));
        const feature = data && data.features && data.features[0];
        if (!feature || (feature.properties && feature.properties.countrycode && feature.properties.countrycode !== "VN")) return null;
        return [Number(feature.geometry.coordinates[1]), Number(feature.geometry.coordinates[0])];
    }

    /** Trả về [lat, lng], null nếu không tìm được, hoặc ném lỗi nếu cả hai dịch vụ đều không kết nối được. */
    async function geocode(address) {
        if (memory.has(address)) return memory.get(address);
        let reachable = false;
        let point = null;
        for (const service of [arcgis, photon]) {
            try {
                point = await service(address);
                reachable = true;
                if (point) break;
            } catch (error) { /* thử dịch vụ tiếp theo */ }
        }
        if (!reachable) throw new Error("offline");
        memory.set(address, point);
        return point;
    }

    function marker(stop, point) {
        const icon = window.L.divIcon({
            className: "sx-map-pin",
            html: '<span style="background:' + (COLORS[stop.kind] || COLORS.drop) + '">' + Number(stop.number) + "</span>",
            iconSize: [30, 30], iconAnchor: [15, 15]
        });
        const label = document.createElement("div");
        label.textContent = "Điểm " + stop.number + ": " + stop.address;
        return window.L.marker(point, {icon: icon, title: "Điểm " + stop.number}).bindPopup(label);
    }

    button.addEventListener("click", async function () {
        button.disabled = true;
        status.textContent = "Đang tải bản đồ…";
        try {
            if (!window.L) {
                await load("link", {rel: "stylesheet", href: LEAFLET + "leaflet.css"});
                await load("script", {src: LEAFLET + "leaflet.js"});
            }
            box.hidden = false;
            const map = window.L.map(box, {scrollWheelZoom: false}).setView([10.85, 106.77], 12);
            window.L.tileLayer(TILES, {maxZoom: 19, attribution: ATTRIBUTION}).addTo(map);
            const list = stops();
            const path = [];
            let missing = 0;
            let offline = 0;
            for (let i = 0; i < list.length; i++) {
                status.textContent = "Đang tìm vị trí " + (i + 1) + "/" + list.length + "…";
                let point = null;
                try { point = await geocode(list[i].address); } catch (error) { offline++; }
                if (!point) { missing++; continue; }
                marker(list[i], point).addTo(map);
                path.push(point);
            }
            if (path.length === 1) map.setView(path[0], 15);
            if (path.length > 1) {
                window.L.polyline(path, {color: "#0072BC", weight: 4, opacity: .7, dashArray: "6 8"}).addTo(map);
                map.fitBounds(window.L.latLngBounds(path), {padding: [30, 30]});
            }
            if (offline === list.length) status.textContent = "Không kết nối được dịch vụ bản đồ; hãy dùng nút Đi tới ở từng điểm.";
            else if (path.length === 0) status.textContent = "Không tìm được vị trí nào; hãy dùng nút Đi tới ở từng điểm.";
            else status.textContent = missing ? "Đã đánh số " + path.length + " điểm; " + missing + " điểm không tìm được vị trí."
                    : "Đã đánh số " + path.length + " điểm theo thứ tự đi.";
            button.hidden = true;
        } catch (error) {
            status.textContent = error.message || "Không tải được bản đồ.";
            box.hidden = true;
            button.disabled = false;
        }
    });
}());
