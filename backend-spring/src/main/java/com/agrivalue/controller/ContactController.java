package com.agrivalue.controller;

import com.agrivalue.entity.ContactMessage;
import com.agrivalue.repository.ContactMessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactMessageRepository contactRepo;

    public ContactController(ContactMessageRepository contactRepo) {
        this.contactRepo = contactRepo;
    }

    @PostMapping
    public ResponseEntity<?> submitContact(@RequestBody Map<String, String> body) {
        contactRepo.save(ContactMessage.builder()
                .name(body.get("name")).email(body.get("email"))
                .subject(body.get("subject")).message(body.get("message"))
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Message saved successfully."));
    }
}
