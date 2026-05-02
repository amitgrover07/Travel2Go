package com.travel2go.backend.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GcsStorageService {

    @Value("${gcp.bucket.name}")
    private String bucketName;

    private final Storage storage;

    public String uploadFile(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        
        // Generate a unique filename to prevent collisions
        String fileName = UUID.randomUUID().toString() + extension;

        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        try {
            // Upload the file to GCS
            storage.create(blobInfo, file.getBytes());
        } catch (Exception e) {
            throw new IOException("Failed to upload image to Google Cloud Storage. Ensure credentials are valid.", e);
        }

        // Construct the public URL (assumes the bucket or object is publicly readable)
        return "https://storage.googleapis.com/" + bucketName + "/" + fileName;
    }
}
