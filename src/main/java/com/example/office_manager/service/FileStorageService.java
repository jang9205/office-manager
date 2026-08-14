package com.example.office_manager.service;

import com.example.office_manager.common.BusinessException;
import com.example.office_manager.mapper.OfficeMapper;
import com.example.office_manager.security.OfficePrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FileStorageService {

    private final OfficeMapper officeMapper;
    private final Path storageRoot;
    private final Set<String> allowedExtensions;

    public FileStorageService(OfficeMapper officeMapper,
                              @Value("${app.storage.root}") String storageRoot,
                              @Value("${app.storage.allowed-extensions}") String allowedExtensions) {
        this.officeMapper = officeMapper;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.allowedExtensions = Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public StoredFile store(OfficePrincipal actor, MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException("업로드할 파일을 선택해 주세요.");
        }
        String originalName = StringUtils.cleanPath(
                multipartFile.getOriginalFilename() == null ? "file" : multipartFile.getOriginalFilename());
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BusinessException("올바르지 않은 파일 이름입니다.");
        }
        String extension = extension(originalName);
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException("허용되지 않은 파일 형식입니다: " + extension);
        }

        LocalDate now = LocalDate.now();
        String storedName = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);
        Path relative = Path.of(String.valueOf(now.getYear()), String.format("%02d", now.getMonthValue()), storedName);
        Path target = storageRoot.resolve(relative).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BusinessException("파일 저장 경로가 올바르지 않습니다.");
        }

        try {
            byte[] bytes = multipartFile.getBytes();
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);

            int width = 0;
            int height = 0;
            if (multipartFile.getContentType() != null && multipartFile.getContentType().startsWith("image/")) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image != null) {
                    width = image.getWidth();
                    height = image.getHeight();
                }
            }

            Map<String, Object> values = new HashMap<>();
            values.put("companyId", actor.companyId());
            values.put("uploaderAccountId", actor.accountId());
            values.put("originalName", originalName);
            values.put("storedName", storedName);
            values.put("storageKey", relative.toString().replace('\\', '/'));
            values.put("contentType", multipartFile.getContentType() == null
                    ? "application/octet-stream" : multipartFile.getContentType());
            values.put("extension", extension);
            values.put("sizeBytes", bytes.length);
            values.put("checksumSha256", HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)));
            values.put("imageWidth", width == 0 ? null : width);
            values.put("imageHeight", height == 0 ? null : height);
            officeMapper.insertFileAsset(values);
            return new StoredFile(((Number) values.get("fileId")).longValue(), originalName,
                    values.get("contentType").toString(), bytes.length);
        } catch (Exception exception) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // The database transaction still rolls back; orphan cleanup can run separately.
            }
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("파일 저장에 실패했습니다.");
        }
    }

    public DownloadFile load(OfficePrincipal actor, Long fileId) {
        Map<String, Object> asset = officeMapper.findFileAsset(fileId);
        if (asset == null || ((Number) asset.get("companyId")).longValue() != actor.companyId()) {
            throw new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "파일을 찾을 수 없습니다.");
        }
        Path target = storageRoot.resolve(String.valueOf(asset.get("storageKey"))).normalize();
        if (!target.startsWith(storageRoot) || !Files.isRegularFile(target)) {
            throw new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "저장된 파일을 찾을 수 없습니다.");
        }
        Resource resource = new FileSystemResource(target);
        return new DownloadFile(resource, String.valueOf(asset.get("originalName")),
                String.valueOf(asset.get("contentType")));
    }

    @Transactional
    public void recordDownload(OfficePrincipal actor, Long fileId, String ip) {
        officeMapper.recordFileDownload(fileId, actor.accountId(), ip);
        officeMapper.incrementResourceDownload(fileId);
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredFile(Long fileId, String originalName, String contentType, long sizeBytes) {
    }

    public record DownloadFile(Resource resource, String originalName, String contentType) {
    }
}
