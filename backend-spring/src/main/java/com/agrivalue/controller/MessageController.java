package com.agrivalue.controller;

import com.agrivalue.entity.Message;
import com.agrivalue.repository.MessageRepository;
import com.agrivalue.repository.UserRepository;
import com.agrivalue.security.JwtUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageRepository messageRepo;
    private final UserRepository userRepo;

    public MessageController(MessageRepository messageRepo, UserRepository userRepo) {
        this.messageRepo = messageRepo;
        this.userRepo = userRepo;
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(@AuthenticationPrincipal JwtUserPrincipal principal) {
        List<Message> latestMessages = messageRepo.findLatestConversations(principal.getId());

        List<Map<String, Object>> conversations = latestMessages.stream().map(m -> {
            Integer otherUserId = m.getSenderId().equals(principal.getId()) ? m.getReceiverId() : m.getSenderId();
            Map<String, Object> conv = new LinkedHashMap<>();
            conv.put("other_user_id", otherUserId);
            conv.put("last_message", m.getContent());
            conv.put("last_message_time", m.getCreatedAt());
            conv.put("unread_count", messageRepo.countUnread(otherUserId, principal.getId()));
            userRepo.findById(otherUserId).ifPresent(u -> {
                conv.put("other_user_name", u.getName());
                conv.put("other_user_role", u.getRole().name());
                conv.put("profile_photo", u.getProfilePhoto());
            });
            return conv;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("conversations", conversations));
    }

    @GetMapping("/{userId}")
    @Transactional
    public ResponseEntity<?> getMessages(@AuthenticationPrincipal JwtUserPrincipal principal,
                                          @PathVariable Integer userId) {
        List<Message> messages = messageRepo.findConversation(principal.getId(), userId);
        messageRepo.markAsRead(userId, principal.getId());

        List<Map<String, Object>> result = messages.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("sender_id", m.getSenderId());
            map.put("receiver_id", m.getReceiverId());
            map.put("content", m.getContent());
            map.put("is_read", m.getIsRead());
            map.put("created_at", m.getCreatedAt());
            userRepo.findById(m.getSenderId()).ifPresent(u -> map.put("sender_name", u.getName()));
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("messages", result));
    }

    @PostMapping
    public ResponseEntity<?> sendMessage(@AuthenticationPrincipal JwtUserPrincipal principal,
                                          @RequestBody Map<String, Object> body) {
        Integer receiverId = Integer.valueOf(body.get("receiverId").toString());
        String content = body.get("content").toString();

        if (userRepo.findById(receiverId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Message message = Message.builder()
                .senderId(principal.getId()).receiverId(receiverId).content(content).build();
        message = messageRepo.save(message);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", message.getId());
        data.put("sender_id", principal.getId());
        data.put("receiver_id", receiverId);
        data.put("content", content);
        data.put("sender_name", principal.getName());
        data.put("is_read", false);
        data.put("created_at", message.getCreatedAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Message sent.", "data", data));
    }
}
