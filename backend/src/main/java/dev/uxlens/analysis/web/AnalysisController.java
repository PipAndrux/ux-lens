package dev.uxlens.analysis.web;

import dev.uxlens.analysis.Analysis;
import dev.uxlens.analysis.AnalysisService;
import dev.uxlens.analysis.AnalysisService.Submission;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    /**
     * Uploads a screenshot. Returns 201 with the new analysis (status PENDING), or 200 with the earlier result
     * when the same file was already analyzed.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Submission submission = service.submit(file.getBytes());
        AnalysisResponse body = AnalysisResponse.from(submission.analysis());
        if (submission.reused()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create("/api/analyses/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(AnalysisResponse.from(load(id)));
    }

    /** The normalized screenshot. It never changes for a given analysis, so browsers may cache it for good. */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        Analysis analysis = load(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(analysis.getImageMediaType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(analysis.getImageData());
    }

    private Analysis load(UUID id) {
        return service.find(id).orElseThrow(() -> new AnalysisNotFoundException(id));
    }
}
