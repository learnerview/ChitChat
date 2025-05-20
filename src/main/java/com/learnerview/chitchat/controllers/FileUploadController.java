package com.learnerview.chitchat.controllers;

import com.learnerview.chitchat.entities.Attachment;
import com.learnerview.chitchat.exception.FileUploadException;
import com.learnerview.chitchat.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/files")
@Slf4j
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    // Allowed file types
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );
    
    private static final List<String> ALLOWED_DOCUMENT_TYPES = Arrays.asList(
        "application/pdf", "text/plain", "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    @PostMapping("/upload")
    public ResponseEntity<Attachment> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "FILE") String fileType,
            Authentication auth) {
        
        try {
            // Validate file
            validateFile(file, fileType);
            
            // Upload file
            Attachment attachment = fileUploadService.uploadFile(file, fileType, auth.getName());
            
            log.info("File uploaded successfully: {} by user: {}", 
                    attachment.getFilename(), auth.getName());
            
            return ResponseEntity.ok(attachment);
            
        } catch (FileUploadException e) {
            log.error("File upload failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during file upload", e);
            throw new FileUploadException("UPLOAD_FAILED", "File upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable String fileId,
            Authentication auth) {
        
        try {
            byte[] fileData = fileUploadService.downloadFile(fileId, auth.getName());
            Attachment attachment = fileUploadService.getAttachmentInfo(fileId);
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + attachment.getFilename() + "\"")
                    .header("Content-Type", attachment.getType())
                    .body(fileData);
                    
        } catch (Exception e) {
            log.error("File download failed for fileId: {}", fileId, e);
            throw new FileUploadException("DOWNLOAD_FAILED", "File download failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<String> deleteFile(
            @PathVariable String fileId,
            Authentication auth) {
        
        try {
            fileUploadService.deleteFile(fileId, auth.getName());
            log.info("File deleted successfully: {} by user: {}", fileId, auth.getName());
            
            return ResponseEntity.ok("File deleted successfully");
            
        } catch (Exception e) {
            log.error("File deletion failed for fileId: {}", fileId, e);
            throw new FileUploadException("DELETE_FAILED", "File deletion failed: " + e.getMessage());
        }
    }

    @GetMapping("/info/{fileId}")
    public ResponseEntity<Attachment> getFileInfo(
            @PathVariable String fileId,
            Authentication auth) {
        
        try {
            Attachment attachment = fileUploadService.getAttachmentInfo(fileId);
            return ResponseEntity.ok(attachment);
            
        } catch (Exception e) {
            log.error("Failed to get file info for fileId: {}", fileId, e);
            throw new FileUploadException("INFO_FAILED", "Failed to get file info: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file, String fileType) {
        if (file.isEmpty()) {
            throw new FileUploadException("EMPTY_FILE", "File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileUploadException("FILE_TOO_LARGE", 
                "File size exceeds maximum limit of 50MB");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new FileUploadException("INVALID_FILE_TYPE", "File type cannot be determined");
        }

        // Validate file type based on category
        switch (fileType.toUpperCase()) {
            case "IMAGE":
                if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
                    throw new FileUploadException("INVALID_IMAGE_TYPE", 
                        "Invalid image type. Allowed types: " + ALLOWED_IMAGE_TYPES);
                }
                break;
            case "DOCUMENT":
                if (!ALLOWED_DOCUMENT_TYPES.contains(contentType)) {
                    throw new FileUploadException("INVALID_DOCUMENT_TYPE", 
                        "Invalid document type. Allowed types: " + ALLOWED_DOCUMENT_TYPES);
                }
                break;
            case "FILE":
                // Allow any file type for general files
                break;
            default:
                throw new FileUploadException("INVALID_FILE_CATEGORY", 
                    "Invalid file category. Use IMAGE, DOCUMENT, or FILE");
        }

        // Validate filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new FileUploadException("INVALID_FILENAME", "Filename cannot be empty");
        }

        // Check for dangerous file extensions
        String lowerFilename = filename.toLowerCase();
        List<String> dangerousExtensions = Arrays.asList(
            ".exe", ".bat", ".cmd", ".com", ".pif", ".scr", ".vbs", ".js", ".jar"
        );
        
        boolean hasDangerousExtension = dangerousExtensions.stream()
            .anyMatch(lowerFilename::endsWith);
            
        if (hasDangerousExtension) {
            throw new FileUploadException("DANGEROUS_FILE", 
                "File type not allowed for security reasons");
        }
    }
}
