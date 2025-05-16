package com.learnerview.chitchat.service;

import com.learnerview.chitchat.entities.Attachment;

public interface FileUploadService {
    
    /**
     * Upload a file and return attachment information
     */
    Attachment uploadFile(org.springframework.web.multipart.MultipartFile file, String fileType, String username);
    
    /**
     * Download file data by file ID
     */
    byte[] downloadFile(String fileId, String username);
    
    /**
     * Get attachment information by file ID
     */
    Attachment getAttachmentInfo(String fileId);
    
    /**
     * Delete a file by ID
     */
    void deleteFile(String fileId, String username);
    
    /**
     * Check if user has permission to access file
     */
    boolean hasAccessPermission(String fileId, String username);
}
