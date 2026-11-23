package dev.uxlens.analysis.web;

import dev.uxlens.analysis.Analysis;
import dev.uxlens.analysis.AnalysisRunner;
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
    private final AnalysisRunner runner;

    public AnalysisController(AnalysisService service, AnalysisRunner runner) {
        this.service = service;
        this.runner = runner;
    }

    /**
     * Uploads and analyzes a screenshot. Returns 201 with the new analysis (status DONE, or FAILED with an error
     * message), or 200 with the earlier result when the same file was already analyzed. Synchronous until the job
     * queue lands; then this will return 202 and the client will poll.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Submission submission = service.submit(file.getBytes());
        if (submission.reused()) {
            return ResponseEntity.ok(AnalysisResponse.from(submission.analysis()));
        }
        UUID id = submission.analysis().getId();
        runner.run(id);
        AnalysisResponse body = AnalysisResponse.from(load(id));
        return ResponseEntity.created(URI.create("/api/analyses/" + id)).body(body);
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
