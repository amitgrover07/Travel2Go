package com.travel2go.backend.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class GcsStorageService {

    @Value("${gcp.bucket.name}")
    private String bucketName;

    // We initialize the Storage client using default credentials.
    // In local dev, it uses GOOGLE_APPLICATION_CREDENTIALS env var.
    // In Cloud Run, it automatically uses the attached Service Account.
    private final Storage storage = StorageOptions.getDefaultInstance().getService();

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

        // Upload the file to GCS
        storage.create(blobInfo, file.getBytes());

        // Construct the public URL (assumes the bucket or object is publicly readable)
        return "https://storage.googleapis.com/" + bucketName + "/" + fileName;
    }
}
