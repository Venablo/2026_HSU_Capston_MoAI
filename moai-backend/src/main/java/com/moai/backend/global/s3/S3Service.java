package com.moai.backend.global.s3;

import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    /**
     * S3에 파일을 업로드하고 공개 URL을 반환한다.
     *
     * @param directory   S3 내 디렉터리 (예: "profiles")
     * @param filename    파일명 (예: "uuid.jpg")
     * @param data        파일 바이트 배열
     * @param contentType MIME 타입 (예: "image/jpeg")
     * @return 업로드된 객체의 S3 URL
     */
    public String upload(String directory, String filename, byte[] data, String contentType) {
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
     * S3 객체에 대한 Presigned GET URL을 생성한다.
     *
     * @param key      S3 객체 키 (예: "profiles/uuid.jpg")
     * @param duration URL 유효 기간
     * @return Presigned URL 문자열
     */
    public String generatePresignedUrl(String key, Duration duration) {
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
