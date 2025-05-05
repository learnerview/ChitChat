package com.learnerview.chitchat.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "attachments")
public class Attachment {
    @Id
    private String id;
    
    private String filename;
    private String url;
    private String type; // IMAGE, VIDEO, FILE, DOCUMENT
    private long size;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private String storagePath;
    private String mimeType;
    
    public Attachment(String filename, String url, String type, long size) {
        this.filename = filename;
        this.url = url;
        this.type = type;
        this.size = size;
    }
}
