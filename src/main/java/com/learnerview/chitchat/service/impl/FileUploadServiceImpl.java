package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.Attachment;
import com.learnerview.chitchat.exception.FileUploadException;
import com.learnerview.chitchat.repositories.AttachmentRepository;
import com.learnerview.chitchat.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.base-url:http://localhost:8080/api/files}")
    private String baseUrl;

    private static final String[] FOLDERS = {"images", "documents", "files"};

    @Override
    public Attachment uploadFile(MultipartFile file, String fileType, String username) {
        try {
            // Create upload directories if they don't exist
            createUploadDirectories();

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String uniqueFilename = generateUniqueFilename(originalFilename);
            
            // Determine folder based on file type
            String folder = getFolderForType(fileType);
            Path uploadPath = Paths.get(uploadDir, folder);
            Path filePath = uploadPath.resolve(uniqueFilename);

            // Copy file to upload location
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Create attachment entity
            Attachment attachment = Attachment.builder()
                    .id(UUID.randomUUID().toString())
                    .filename(originalFilename)
                    .url(baseUrl + "/download/" + uniqueFilename)
                    .type(determineAttachmentType(file.getContentType(), fileType))
                    .size(file.getSize())
                    .uploadedBy(username)
                    .uploadedAt(LocalDateTime.now())
                    .storagePath(filePath.toString())
                    .build();

            // Save attachment to database
            return attachmentRepository.save(attachment);

        } catch (IOException e) {
            log.error("Failed to upload file: {}", e.getMessage(), e);
            throw new FileUploadException("UPLOAD_FAILED", "Failed to upload file: " + e.getMessage());
        }
    }

    @Override
    public byte[] downloadFile(String fileId, String username) {
        try {
            Attachment attachment = attachmentRepository.findById(fileId)
                    .orElseThrow(() -> new FileUploadException("FILE_NOT_FOUND", "File not found"));

            // Check permission
            if (!hasAccessPermission(fileId, username)) {
                throw new FileUploadException("ACCESS_DENIED", "Access denied to this file");
            }

            // Read file from storage
            Path filePath = Paths.get(attachment.getStoragePath());
            if (!Files.exists(filePath)) {
                throw new FileUploadException("FILE_NOT_FOUND", "File not found on disk");
            }

            return Files.readAllBytes(filePath);

        } catch (IOException e) {
            log.error("Failed to download file: {}", e.getMessage(), e);
            throw new FileUploadException("DOWNLOAD_FAILED", "Failed to download file: " + e.getMessage());
        }
    }

    @Override
    public Attachment getAttachmentInfo(String fileId) {
        return attachmentRepository.findById(fileId)
                .orElseThrow(() -> new FileUploadException("FILE_NOT_FOUND", "File not found"));
    }

    @Override
    public void deleteFile(String fileId, String username) {
        try {
            Attachment attachment = attachmentRepository.findById(fileId)
                    .orElseThrow(() -> new FileUploadException("FILE_NOT_FOUND", "File not found"));

            // Check permission (only uploader can delete)
            if (!attachment.getUploadedBy().equals(username)) {
                throw new FileUploadException("ACCESS_DENIED", "Only file uploader can delete the file");
            }

            // Delete file from storage
            Path filePath = Paths.get(attachment.getStoragePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }

            // Delete from database
            attachmentRepository.deleteById(fileId);

            log.info("File deleted successfully: {} by user: {}", fileId, username);

        } catch (IOException e) {
            log.error("Failed to delete file: {}", e.getMessage(), e);
            throw new FileUploadException("DELETE_FAILED", "Failed to delete file: " + e.getMessage());
        }
    }

    @Override
    public boolean hasAccessPermission(String fileId, String username) {
        try {
            Attachment attachment = attachmentRepository.findById(fileId).orElse(null);
            if (attachment == null) {
                return false;
            }

            // For now, allow access to uploader only
            // In a real app, you might check if user is in the same conversation
            return attachment.getUploadedBy().equals(username);

        } catch (Exception e) {
            log.error("Error checking file access permission: {}", e.getMessage(), e);
            return false;
        }
    }

    private void createUploadDirectories() throws IOException {
        for (String folder : FOLDERS) {
            Path path = Paths.get(uploadDir, folder);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created upload directory: {}", path);
            }
        }
    }

    private String getFolderForType(String fileType) {
        switch (fileType.toUpperCase()) {
            case "IMAGE":
                return "images";
            case "DOCUMENT":
                return "documents";
            default:
                return "files";
        }
    }

    private String determineAttachmentType(String contentType, String fileType) {
        if (contentType != null && contentType.startsWith("image/")) {
            return "IMAGE";
        } else if (contentType != null && contentType.contains("document")) {
            return "DOCUMENT";
        }
        return fileType.toUpperCase();
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }

    private String generateUniqueFilename(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = UUID.randomUUID().toString().substring(0, 8);
        
        return timestamp + "_" + random + extension;
    }

    private String generateFileHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(file.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to generate file hash", e);
            return UUID.randomUUID().toString();
        }
    }
}
