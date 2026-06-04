package com.moai.backend.global.material;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/api/files")
public class MaterialFileController {

    private final List<Path> baseDirs;
    private final MaterialGeneratorService materialGeneratorService;

    public MaterialFileController(
            @Value("${moai.files.local-root:../data}") String localRoot,
            MaterialGeneratorService materialGeneratorService
    ) {
        this.materialGeneratorService = materialGeneratorService;
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        this.baseDirs = Stream.of(
                        Paths.get(localRoot),
                        cwd.resolve("data"),
                        cwd.resolve("../data"),
                        cwd.resolve("../../data")
                )
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .toList();
        log.info("Local material file roots: {}", baseDirs);
    }

    @GetMapping("/**")
    public ResponseEntity<Resource> serveFile(
            HttpServletRequest request,
            @RequestParam(name = "download", defaultValue = "false") boolean download
    ) {
        String uri = request.getRequestURI();
        String relativePath = URLDecoder.decode(uri.substring("/api/files/".length()), StandardCharsets.UTF_8);

        if (isUnsafePath(relativePath)) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = findFile(relativePath);
        if (filePath == null) {
            log.warn("Local material file not found. relativePath={}, roots={}", relativePath, baseDirs);
            return ResponseEntity.notFound().build();
        }

        try {
            if (isMarkdown(filePath)) {
                return serveMarkdown(filePath, download);
            }

            Resource resource = new FileSystemResource(filePath);
            String filename = filePath.getFileName().toString();
            ContentDisposition disposition = (download
                    ? ContentDisposition.attachment()
                    : ContentDisposition.inline())
                    .filename(filename, StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .contentType(resolveMediaType(filePath))
                    .contentLength(Files.size(filePath))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(resource);
        } catch (IOException e) {
            log.error("Failed to serve local material file: {}", filePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<Resource> serveMarkdown(Path filePath, boolean download) throws IOException {
        String markdown = Files.readString(filePath, StandardCharsets.UTF_8);
        String baseName = stripExtension(filePath.getFileName().toString());

        if (download) {
            byte[] mdBytes = markdown.getBytes(StandardCharsets.UTF_8);
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(baseName + ".md", StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/markdown"))
                    .contentLength(mdBytes.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(new ByteArrayResource(mdBytes));
        }

        byte[] htmlBytes = materialGeneratorService.renderMarkdownHtml(markdown, baseName)
                .getBytes(StandardCharsets.UTF_8);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(baseName + ".html", StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .contentLength(htmlBytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new ByteArrayResource(htmlBytes));
    }

    private MediaType resolveMediaType(Path filePath) throws IOException {
        String filename = filePath.getFileName().toString().toLowerCase();
        if (filename.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }

        String contentType = Files.probeContentType(filePath);
        return contentType == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
    }

    private boolean isMarkdown(Path filePath) {
        String filename = filePath.getFileName().toString().toLowerCase();
        return filename.endsWith(".md") || filename.endsWith(".markdown");
    }

    private String stripExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private boolean isUnsafePath(String relativePath) {
        Path normalized = Paths.get(relativePath).normalize();
        return normalized.isAbsolute() || normalized.startsWith("..");
    }

    private Path findFile(String relativePath) {
        for (Path baseDir : baseDirs) {
            Path filePath = baseDir.resolve(relativePath).normalize();
            if (filePath.startsWith(baseDir) && Files.exists(filePath) && Files.isRegularFile(filePath)) {
                return filePath;
            }
        }
        return null;
    }
}
