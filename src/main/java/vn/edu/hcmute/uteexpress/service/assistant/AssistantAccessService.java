package vn.edu.hcmute.uteexpress.service.assistant;

import jakarta.persistence.EntityManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.security.admin.PublicAccountPrincipal;

@Service
public class AssistantAccessService {
    public record Actor(Long id,String username,AppUser.Role role,long accessVersion) { }
    private final AppUserRepository users;
    private final EntityManager entityManager;
    public AssistantAccessService(AppUserRepository users,EntityManager entityManager) { this.users=users; this.entityManager=entityManager; }
    @Transactional(readOnly=true)
    public Actor requireActor(Authentication authentication) {
        if (authentication==null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof PublicAccountPrincipal principal))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var user=users.findByUsername(principal.getUsername()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        // Không dùng entity có thể còn trong cache JPA khi tài khoản vừa bị thu hồi quyền trong lúc chờ AI.
        entityManager.refresh(user);
        if (!principal.matchesAccount(user)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return new Actor(user.getId(),user.getUsername(),user.getRole(),user.getAccessVersion());
    }
}
