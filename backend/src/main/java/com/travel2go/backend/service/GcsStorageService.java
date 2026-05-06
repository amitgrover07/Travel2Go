package com.travel2go.backend.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.travel2go.backend.dto.MediaFileDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class GcsStorageService {

    @Value("${gcp.bucket.name}")
    private String bucketName;

    private final Storage storage;

    public String uploadFile(MultipartFile file, String context) throws IOException {
        java.util.Map<String, String> metadataMap = new java.util.HashMap<>();
        if (context != null && !context.trim().isEmpty()) {
            String[] pairs = context.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split("[:=]", 2);
                if (kv.length == 2) {
                    metadataMap.put(kv[0].trim(), kv[1].trim());
                } else {
                    metadataMap.put("context_" + java.util.UUID.randomUUID().toString().substring(0, 4), kv[0].trim());
                }
            }
        }
        return uploadFileWithMetadata(file, metadataMap);
    }

    public String uploadFileWithMetadata(MultipartFile file, java.util.Map<String, String> metadata) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        
        String fileName = UUID.randomUUID().toString() + extension;
        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo.Builder blobInfoBuilder = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType());
                
        if (metadata != null && !metadata.isEmpty()) {
            blobInfoBuilder.setMetadata(metadata);
        }
        
        storage.create(blobInfoBuilder.build(), file.getBytes());
        return "https://storage.googleapis.com/" + bucketName + "/" + fileName;
    }

    public boolean deleteFile(String fileName) {
        BlobId blobId = BlobId.of(bucketName, fileName);
        return storage.delete(blobId);
    }

    public List<MediaFileDTO> listAllFiles() {
        com.google.api.gax.paging.Page<Blob> blobs = storage.list(bucketName);
        
        return StreamSupport.stream(blobs.iterateAll().spliterator(), false)
                .map(blob -> MediaFileDTO.builder()
                        .name(blob.getName())
                        .url("https://storage.googleapis.com/" + bucketName + "/" + blob.getName())
                        .metadata(blob.getMetadata())
                        .size(blob.getSize())
                        .updatedAt(blob.getUpdateTime())
                        .build())
                .collect(Collectors.toList());
    }
}
