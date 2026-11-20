package dev.uxlens.analysis.web;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.uxlens.TestcontainersConfiguration;
import dev.uxlens.analysis.Analysis;
import dev.uxlens.analysis.AnalysisRepository;
import dev.uxlens.image.ImageProcessor;
import dev.uxlens.image.TestImages;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnalysisControllerTest {

    @Autowired MockMvc mvc;
    @Autowired AnalysisRepository analyses;
    @Autowired ImageProcessor imageProcessor;

    @Test
    void uploadCreatesAPendingAnalysisWithANormalizedImage() throws Exception {
        String location = mvc.perform(multipart("/api/analyses").file(file(TestImages.jpeg(3136, 1960))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/analyses/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.image.mediaType").value("image/png"))
                .andExpect(jsonPath("$.image.width").value(1568))
                .andExpect(jsonPath("$.image.height").value(980))
                .andExpect(jsonPath("$.image.url", endsWith("/image")))
                .andExpect(jsonPath("$.issues").isEmpty())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(get(location + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", startsWith("max-age=")));
    }

    @Test
    void reusesAFinishedAnalysisOfTheSameFile() throws Exception {
        byte[] screenshot = TestImages.png(640, 480);
        Analysis done = new Analysis(imageProcessor.process(screenshot).sha256(), "image/png", 640, 480, screenshot);
        done.markRunning();
        done.complete("mock", "mock-1", "Looks good.", (short) 95, (short) 90, (short) 92, List.of());
        analyses.save(done);

        mvc.perform(multipart("/api/analyses").file(file(screenshot)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(done.getId().toString()))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.scores.accessibility").value(95));
    }

    @Test
    void rejectsAFileThatIsNotAnImageWithAProblemDetail() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "screen.png", "image/png", "not an image".getBytes());

        mvc.perform(multipart("/api/analyses").file(fake))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid image"))
                .andExpect(jsonPath("$.detail").value("Unsupported file type. Upload a PNG, JPEG or WebP image."));
    }

    @Test
    void rejectsAFileOverFiveMegabytes() throws Exception {
        byte[] big = new byte[ImageProcessor.MAX_FILE_BYTES + 1];

        mvc.perform(multipart("/api/analyses").file(file(big)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.title").value("File too large"));
    }

    @Test
    void explainsAMissingFileField() throws Exception {
        mvc.perform(multipart("/api/analyses"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing file"));
    }

    @Test
    void returns404ForAnUnknownAnalysis() throws Exception {
        mvc.perform(get("/api/analyses/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Analysis not found"));
        mvc.perform(get("/api/analyses/" + UUID.randomUUID() + "/image"))
                .andExpect(status().isNotFound());
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "screenshot", MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
    }
}
