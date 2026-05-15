package com.moai.backend.global.s3;

import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class S3Service {

    @Nullable
    @Autowired(required = false)
    private S3Client s3Client;

    @Nullable
    @Autowired(required = false)
    private S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket:}")
    private String bucket;

    @Value("${cloud.aws.region.static:ap-northeast-2}")
    private String region;

    @Value("${cloud.aws.s3.local-fallback:false}")
    private boolean localFallback;

    @Value("${moai.files.local-root:../data}")
    private String localRoot;

    /**
     * S3에 파일을 업로드하고 공개 URL을 반환한다.
     * S3가 비활성화되고 local-fallback이 true이면 로컬 디스크에 저장 후 로컬 URL을 반환한다.
     *
     * @param directory   S3 내 디렉터리 (예: "materials/roomId")
     * @param filename    파일명 (예: "uuid.pdf")
     * @param data        파일 바이트 배열
     * @param contentType MIME 타입 (예: "application/pdf")
     * @return 업로드된 객체의 URL (S3 또는 로컬)
     */
    public String upload(String directory, String filename, byte[] data, String contentType) {
        if (s3Client == null) {
            if (localFallback) {
                return saveLocally(directory, filename, data);
            }
            log.warn("S3 비활성화 상태 — 파일 업로드 스킵 (cloud.aws.s3.enabled=false): {}/{}", directory, filename);
            return null;
        }

        String key = directory + "/" + filename;
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(data));

            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);
        } catch (Exception e) {
            log.error("S3 업로드 실패: key={}", key, e);
            throw new CustomException(ErrorCode.S3_UPLOAD_FAILED);
        }
    }

    /**
     * 로컬 디스크에 파일을 저장하고 접근 가능한 상대 URL을 반환한다.
     * S3 비활성화 시 로컬 개발 환경 전용 폴백.
     */
    private String saveLocally(String directory, String filename, byte[] data) {
        try {
            Path dir = resolveLocalRoot().resolve(directory).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path filePath = dir.resolve(filename);
            Files.write(filePath, data);
            String url = "/api/files/" + directory + "/" + filename;
            log.info("로컬 파일 저장 완료: {} ({}bytes)", filePath, data.length);
            return url;
        } catch (IOException e) {
            log.warn("로컬 파일 저장 실패: {}/{} — {}", directory, filename, e.getMessage());
            return null;
        }
    }

    private Path resolveLocalRoot() {
        Path configured = Paths.get(localRoot).toAbsolutePath().normalize();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                configured,
                cwd.resolve("data").toAbsolutePath().normalize(),
                cwd.resolve("../data").toAbsolutePath().normalize(),
                cwd.resolve("../../data").toAbsolutePath().normalize()
        );

        return candidates.stream()
                .filter(path -> Files.exists(path.resolve("materials")) || Files.exists(path))
                .findFirst()
                .orElse(configured);
    }

    /**
     * URL에서 파일 내용을 텍스트로 읽어 반환한다.
     * 로컬 폴백 URL(/api/files/...)은 디스크에서, S3 URL은 S3Client로 읽는다.
     * 읽기에 실패하면 null을 반환한다.
     */
    public String fetchTextContent(String url) {
        if (url == null || url.isBlank()) return null;

        if (url.startsWith("/api/files/")) {
            String relativePath = url.substring("/api/files/".length());
            try {
                Path filePath = resolveLocalRoot().resolve(relativePath).toAbsolutePath().normalize();
                return Files.readString(filePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("로컬 파일 읽기 실패: {}", url, e);
                return null;
            }
        }

        if (s3Client != null && url.contains("amazonaws.com")) {
            try {
                String key = URI.create(url).getPath().substring(1);
                byte[] bytes = s3Client.getObjectAsBytes(
                        b -> b.bucket(bucket).key(key)
                ).asByteArray();
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("S3 파일 읽기 실패: {}", url, e);
                return null;
            }
        }

        return null;
    }

    /**
     * S3 객체에 대한 Presigned GET URL을 생성한다.
     *
     * @param key      S3 객체 키 (예: "profiles/uuid.jpg")
     * @param duration URL 유효 기간
     * @return Presigned URL 문자열
     */
    public String generatePresignedUrl(String key, Duration duration) {
        if (s3Presigner == null) {
            log.warn("S3 비활성화 상태 — Presigned URL 생성 스킵: {}", key);
            return null;
        }

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .getObjectRequest(getObjectRequest)
                    .signatureDuration(duration)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.error("Presigned URL 생성 실패: key={}", key, e);
            throw new CustomException(ErrorCode.S3_PRESIGN_FAILED);
        }
    }
}
