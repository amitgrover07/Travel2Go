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

    public String uploadFile(MultipartFile file, String context) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        
        // Generate a unique filename to prevent collisions
        String fileName = UUID.randomUUID().toString() + extension;

        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo.Builder blobInfoBuilder = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType());
                
        if (context != null && !context.trim().isEmpty()) {
            java.util.Map<String, String> metadataMap = new java.util.HashMap<>();
            String[] pairs = context.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split("[:=]", 2);
                if (kv.length == 2) {
                    metadataMap.put(kv[0].trim(), kv[1].trim());
                } else {
                    // Fallback if no key=value format is used, we use a generic key
                    metadataMap.put("context_" + java.util.UUID.randomUUID().toString().substring(0, 4), kv[0].trim());
                }
            }
            blobInfoBuilder.setMetadata(metadataMap);
        }
        
        BlobInfo blobInfo = blobInfoBuilder.build();

        // Upload the file to GCS
        storage.create(blobInfo, file.getBytes());

        // Construct the public URL (assumes the bucket or object is publicly readable)
        return "https://storage.googleapis.com/" + bucketName + "/" + fileName;
    }
}
