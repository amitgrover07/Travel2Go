package com.travel2go.backend.controller;

import com.travel2go.backend.dto.MediaFileDTO;
import com.travel2go.backend.service.GcsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final GcsStorageService gcsStorageService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "context", required = false) String context) {
        try {
            String fileUrl = gcsStorageService.uploadFile(file, context);
            Map<String, String> response = new HashMap<>();
            response.put("url", fileUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to upload image: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<MediaFileDTO>> listAllMedia() {
        return ResponseEntity.ok(gcsStorageService.listAllFiles());
    }
}
