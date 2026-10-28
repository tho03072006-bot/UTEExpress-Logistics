package vn.edu.hcmute.uteexpress.controller.guest;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.service.media.ReviewMediaService;

@Controller
public class ReviewMediaController {
    private final ReviewMediaService mediaService;
    public ReviewMediaController(ReviewMediaService mediaService) { this.mediaService=mediaService; }
    @GetMapping("/tep-danh-gia/{id}") @ResponseBody
    public ResponseEntity<Resource> media(@PathVariable Long id) {
        var file=mediaService.readPublishedMedia(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(file.type())
                .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox")
                .body(new ByteArrayResource(file.bytes()));
    }
}
