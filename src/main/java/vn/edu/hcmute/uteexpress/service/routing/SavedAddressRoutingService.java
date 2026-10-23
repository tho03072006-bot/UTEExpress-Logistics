package vn.edu.hcmute.uteexpress.service.routing;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.hcmute.uteexpress.dto.SavedAddressRequest;
import vn.edu.hcmute.uteexpress.entity.SavedAddress;
import vn.edu.hcmute.uteexpress.entity.routing.SavedAddressArea;
import vn.edu.hcmute.uteexpress.repository.routing.SavedAddressAreaRepository;

@Service @Transactional(readOnly = true)
public class SavedAddressRoutingService {
    private final SavedAddressAreaRepository addresses;
    private final RoutingCatalogService catalog;
    public SavedAddressRoutingService(SavedAddressAreaRepository addresses, RoutingCatalogService catalog) {
        this.addresses = addresses; this.catalog = catalog;
    }
    public record Selection(String provinceCode, String areaCode, String areaName) { }
    public Map<Long, Selection> selections(String username) {
        return addresses.findAllBySavedAddress_User_Username(username).stream().collect(Collectors.toMap(
                a -> a.getSavedAddress().getId(), a -> new Selection(a.getProvinceCode(), a.getArea().getAreaCode(), a.getArea().getAreaName())));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(SavedAddress ownedAddress, SavedAddressRequest form) {
        var area = catalog.requireArea(form.getProvinceCode(), form.getAreaCode());
        var existing = addresses.findBySavedAddress_Id(ownedAddress.getId());
        var record = existing.orElseGet(() -> new SavedAddressArea(ownedAddress, area));
        record.configure(area); addresses.save(record);
    }
}
