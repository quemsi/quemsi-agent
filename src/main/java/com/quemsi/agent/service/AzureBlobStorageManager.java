package com.quemsi.agent.service;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.model.flow.out.AzureBlobDrive;

import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AzureBlobStorageManager {
    private AzureBlobDrive azureBlobDrive;
    private BlobServiceClient client;
    
    @Builder
    public AzureBlobStorageManager(AzureBlobDrive azureBlobDrive) {
        this.azureBlobDrive = azureBlobDrive;
    }

    @PostConstruct
    public void init() {
        String containerName = azureBlobDrive.getStorageRoot();
        try {
            client.createBlobContainer(containerName);
            log.info("Created blob container: {}", containerName);
        } catch (com.azure.storage.blob.models.BlobStorageException e) {
            if (e.getStatusCode() == 409) { // Container already exists
                log.debug("Blob container '{}' already exists.", containerName);
            } else {
                log.warn("Failed to create blob container '{}': {}", containerName, e.getMessage());
                throw Exceptions.server("unable-to-create-azure-blob-container").withExtra("containerName", containerName).withCause(e).get();
            }
        }
    }

    public synchronized BlobServiceClient getBlobServiceClient() {
        if(client == null){
            String endpoint = String.format("https://%s.blob.core.windows.net", azureBlobDrive.getAccountName());
            client = new com.azure.storage.blob.BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new StorageSharedKeyCredential(azureBlobDrive.getAccountName(), azureBlobDrive.getAccountKey()))
                .buildClient();
            client.listBlobContainers().iterator().hasNext();
        }
        return client;
    }

    public boolean isConnected() {
        try {
            getBlobServiceClient().listBlobContainers().iterator().hasNext();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
