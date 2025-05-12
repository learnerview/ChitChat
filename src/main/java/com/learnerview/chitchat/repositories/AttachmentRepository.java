package com.learnerview.chitchat.repositories;

import com.learnerview.chitchat.entities.Attachment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachmentRepository extends MongoRepository<Attachment, String> {
    
    /**
     * Find attachments by uploader username
     */
    List<Attachment> findByUploadedBy(String uploadedBy);
    
    /**
     * Find attachments by type
     */
    List<Attachment> findByType(String type);
    
    /**
     * Find attachments by uploader and type
     */
    List<Attachment> findByUploadedByAndType(String uploadedBy, String type);
    
    /**
     * Count attachments by uploader
     */
    long countByUploadedBy(String uploadedBy);
}
