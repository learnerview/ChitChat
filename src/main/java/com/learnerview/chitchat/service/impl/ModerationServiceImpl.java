package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.service.ModerationService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ModerationServiceImpl implements ModerationService {

    // Simple profanity filter - in production, use a more sophisticated solution
    private static final Set<String> PROFANITY_WORDS = Set.of(
            "badword1", "badword2", "badword3", "curse1", "curse2"
    );
    
    // Patterns for detecting personal information
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    
    private final Set<String> flaggedMessages = new HashSet<>();
    private final Set<String> bannedUsers = new HashSet<>();
    private final List<String> reports = new ArrayList<>();

    @Override
    public boolean containsProfanity(String content) {
        if (content == null) return false;
        
        String lowerContent = content.toLowerCase();
        return PROFANITY_WORDS.stream().anyMatch(lowerContent::contains);
    }

    @Override
    public boolean containsSpam(String content) {
        if (content == null) return false;
        
        // Simple spam detection - repetitive characters, excessive caps, etc.
        String lowerContent = content.toLowerCase();
        
        // Check for excessive repetitive characters
        if (lowerContent.matches(".*(.)\\1{4,}.*")) {
            return true;
        }
        
        // Check for excessive capitalization
        int upperCount = 0;
        for (char c : content.toCharArray()) {
            if (Character.isUpperCase(c)) {
                upperCount++;
            }
        }
        if (content.length() > 10 && upperCount > content.length() * 0.7) {
            return true;
        }
        
        return false;
    }

    @Override
    public boolean containsPersonalInfo(String content) {
        if (content == null) return false;
        
        return EMAIL_PATTERN.matcher(content).find() ||
               PHONE_PATTERN.matcher(content).find() ||
               SSN_PATTERN.matcher(content).find() ||
               CREDIT_CARD_PATTERN.matcher(content).find();
    }

    @Override
    public boolean containsInappropriateContent(String content) {
        return containsProfanity(content) || 
               containsSpam(content) || 
               containsPersonalInfo(content);
    }

    @Override
    public String filterContent(String content) {
        if (content == null) return null;
        
        String filtered = content;
        
        // Filter profanity
        for (String profanity : PROFANITY_WORDS) {
            filtered = filtered.replaceAll("(?i)" + Pattern.quote(profanity), "***");
        }
        
        // Filter personal information
        filtered = EMAIL_PATTERN.matcher(filtered).replaceAll("[EMAIL HIDDEN]");
        filtered = PHONE_PATTERN.matcher(filtered).replaceAll("[PHONE HIDDEN]");
        filtered = SSN_PATTERN.matcher(filtered).replaceAll("[SSN HIDDEN]");
        filtered = CREDIT_CARD_PATTERN.matcher(filtered).replaceAll("[CARD HIDDEN]");
        
        return filtered;
    }

    @Override
    public void flagMessage(String messageId, String reason) {
        flaggedMessages.add(messageId + ":" + reason);
    }

    @Override
    public List<String> getFlaggedMessages() {
        return new ArrayList<>(flaggedMessages);
    }

    @Override
    public void moderateMessage(String messageId, boolean approve) {
        if (approve) {
            flaggedMessages.removeIf(entry -> entry.startsWith(messageId + ":"));
        }
        // If not approved, keep it flagged for further action
    }

    @Override
    public void banUser(String username, String reason) {
        bannedUsers.add(username + ":" + reason);
    }

    @Override
    public void unbanUser(String username) {
        bannedUsers.removeIf(entry -> entry.startsWith(username + ":"));
    }

    @Override
    public boolean isUserBanned(String username) {
        return bannedUsers.stream().anyMatch(entry -> entry.startsWith(username + ":"));
    }

    @Override
    public void reportContent(String type, String contentId, String reporterUsername, String reason) {
        String report = String.format("[%s] %s reported %s: %s", 
                new Date(), reporterUsername, type, reason);
        reports.add(report);
    }

    @Override
    public List<String> getReports() {
        return new ArrayList<>(reports);
    }
}
