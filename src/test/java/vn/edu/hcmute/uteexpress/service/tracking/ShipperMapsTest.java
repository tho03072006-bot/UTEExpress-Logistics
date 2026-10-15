package vn.edu.hcmute.uteexpress.service.tracking;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ShipperMapsTest {
    @Test void directionsUseRoadOnlyAndOpenNavigationInVietnam() {
        String url = ShipperMaps.directions("21 Lê Lợi, Buôn Ma Thuột, Đắk Lắk");
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1&travelmode=driving&dir_action=navigate&destination="),
                "chỉ đường bộ, không hiện máy bay/xe khách/tàu");
        assertTrue(url.endsWith("%2C+Vi%E1%BB%87t+Nam"), "luôn kèm quốc gia để Google không chọn nhầm nước khác");
    }

    @Test void placeCleansAddressAndDoesNotRepeatCountry() {
        assertEquals("1 Võ Văn Ngân, Thủ Đức, Việt Nam", ShipperMaps.place("  1  Võ Văn Ngân,\n Thủ Đức | "));
        assertEquals("45 Lý Thường Kiệt, Hà Nội, Việt Nam", ShipperMaps.place("45 Lý Thường Kiệt, Hà Nội, Việt Nam"));
        assertEquals("12 Lê Lợi, HCMC, Vietnam", ShipperMaps.place("12 Lê Lợi, HCMC, Vietnam"));
        assertEquals("", ShipperMaps.place(null));
        assertEquals("", ShipperMaps.place("   "));
    }
}
