package com.quemsi.agent.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.FileNameUtil;
import com.quemsi.commons.util.FileResource;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.dto.DataFile;
import com.quemsi.model.flow.DataPackage;
import com.quemsi.model.flow.DataPackageFileResource;
import com.quemsi.model.flow.Flow;
import com.quemsi.model.flow.FlowContext;
import com.quemsi.model.flow.out.AzureBlobDrive;
import com.quemsi.model.flow.out.Storage;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AzureBlobStorage implements Storage{
    public static final String AZURE_BLOB_ENDPOINT_FORMAT = "https://%s.blob.core.windows.net";
    private AzureBlobDrive azureBlobDrive;
    private BlobServiceClient client;
    @Getter
    @Setter
    private String name;
    @Setter
    private String retentionPolicy;
    @Setter
    private Long usedSize;
    @Setter
    private Long capacity;
    @Getter
    @Setter
    private String rootPath;
    @Setter
    private FileNameUtil util;
    
    public String containerName() {
        String containerName = StringUtils.trim(azureBlobDrive.getStorageRoot(), "/", "/");
        if(containerName.isEmpty()){
            containerName = rootPath;
        }
        return containerName;
    }

    @Builder
    public AzureBlobStorage(AzureBlobDrive azureBlobDrive) {
        this.azureBlobDrive = azureBlobDrive;
    }

    public synchronized BlobServiceClient getBlobServiceClient() {
        if(client == null){
            String endpoint = String.format(AZURE_BLOB_ENDPOINT_FORMAT, azureBlobDrive.getAccountName());
            client = new com.azure.storage.blob.BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new StorageSharedKeyCredential(azureBlobDrive.getAccountName(), azureBlobDrive.getAccountKey()))
                .buildClient();
            client.listBlobContainers().iterator().hasNext();
        }
        return client;
    }

    @Override
    public boolean recordFiles() {
        return true;
    }

    public void createContainer(String containerName) {
        try {
            getBlobServiceClient().createBlobContainer(containerName);
            log.info("Created blob container: {}", containerName);
        } catch (com.azure.storage.blob.models.BlobStorageException e) {
            if (e.getStatusCode() == 409) { /* 409 - Conflict - Container already exists */
                log.debug("Blob container '{}' already exists.", containerName);
            } else {
                log.warn("Failed to create blob container '{}': {}", containerName, e.getMessage());
                throw Exceptions.server("unable-to-create-azure-blob-container").withExtra("containerName", containerName).withCause(e).get();
            }
        }
    }

    @Override
    public void init(Flow f) {
        String containerName = containerName();
        createContainer(containerName);
    }

    @Override
    public void store(FlowContext context, String dataName, List<DataPackage> dataPackages, Long version) {
        if(dataPackages.isEmpty()){
            throw Exceptions.badRequest("datapackages-empty").withExtra("versionId", version).get();
        }
        String containerName = containerName();
        BlobContainerClient containerClient = getBlobServiceClient().getBlobContainerClient(containerName);
        
        dataPackages.forEach(dp -> {
            log.info("Storing file to Azure Blob Storage: {}", dp.getName());
            
            /* Generate versioned filename using FileNameUtil */
            String fileFolder = StringUtils.removePathPrefix(StringUtils.trim(rootPath, "/", "/"), containerName);
            String versionedFileName = util.versionedFileName(dp.getName(), version);
            String blobPath = StringUtils.buildPath("/", fileFolder, dataName, versionedFileName);
            
            log.info("Destination blob path: {}", blobPath);
            
            try {
                BlobClient blobClient = containerClient.getBlobClient(blobPath);
                
                /* Upload the inputstream to Azure Blob Storage */
                blobClient.upload(dp.getInputStream(), dp.getLength(), true);
                
                log.info("Successfully uploaded file {} to Azure Blob Storage at path: {}", dp.getName(), blobPath);
            } catch (Exception e) {
                log.error("Failed to upload file {} to Azure Blob Storage", dp.getName(), e);
                throw Exceptions.server("error-storing-file-to-azure-blob")
                    .withExtra("fileName", dp.getName())
                    .withExtra("blobPath", blobPath)
                    .withExtra("containerName", containerName)
                    .withCause(e)
                    .get();
            }
        });
    }

    @Override
    public List<DataPackage> getFiles(FlowContext context, List<DataFile> files) throws IOException {
        String containerName = containerName();
        BlobContainerClient containerClient = getBlobServiceClient().getBlobContainerClient(containerName);
        return files.stream().<DataPackage>map(f -> {
            try {
                /* Generate versioned filename using FileNameUtil */
                String versionedFileName = util.versionedFileName(f.getName(), f.getVersion());
                String fileFolder = StringUtils.trim(StringUtils.ensureSeperator(azureBlobDrive.getStorageRoot(), rootPath), "/", "/");
                fileFolder = StringUtils.removePathPrefix(fileFolder, containerName);
                String blobPath = StringUtils.buildPath("/", fileFolder, f.getDir(), versionedFileName);
                
                log.info("Retrieving file from Azure Blob Storage at path: {}", blobPath);
                
                BlobClient blobClient = containerClient.getBlobClient(blobPath);
                
                if (!blobClient.exists()) {
                    log.warn("File not found in Azure Blob Storage at path: {}", blobPath);
                    throw Exceptions.notFound("file-not-found").withExtra("containerName", containerName).withExtra("versionedFileName", versionedFileName).get();
                }
                
                /* Get blob properties to determine content type and size */
                com.azure.storage.blob.models.BlobProperties properties = blobClient.getProperties();
                String contentType = f.getContentType();
                if (contentType == null || contentType.isEmpty()) {
                    contentType = properties.getContentType();
                    if (contentType == null || contentType.isEmpty()) {
                        contentType = util.getFileType(versionedFileName);
                    }
                }
                InputStream blobInputStream = blobClient.openInputStream();
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                org.apache.commons.io.IOUtils.copy(blobInputStream, outStream);
                /* Create a DataPackage from the blob */
                FileResource resource = FileResource.builder().name(versionedFileName).contentType(contentType).empty(outStream.size() > 0).originalFilename(versionedFileName).size(outStream.size()).data(outStream.toByteArray()).build();
                return new DataPackageFileResource(f.getName(), resource);
            } catch(BaseRuntimeException bre){
                throw bre;
            } catch (Exception e) {
                throw Exceptions.server("unable-to-reach-azure-blob").withCause(e).get();
            }
        }).filter(dp -> dp != null).toList();
    }

    @Override
    public void deleteFile(String dir, String fileName) throws IOException {
        String containerName = containerName();
        BlobContainerClient containerClient = getBlobServiceClient().getBlobContainerClient(containerName);
        
        String fileFolder = StringUtils.trim(StringUtils.ensureSeperator(azureBlobDrive.getStorageRoot(), rootPath), "/", "/");
        fileFolder = StringUtils.removePathPrefix(fileFolder, containerName);
        String blobPath = StringUtils.buildPath("/", fileFolder, dir, fileName);
        log.debug("Deleting file from Azure Blob Storage at path: {}", blobPath);
        
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobPath);
            
            if (blobClient.exists()) {
                blobClient.delete();
                log.info("Successfully deleted file from Azure Blob Storage at path: {}", blobPath);
            } else {
                log.warn("File not found in Azure Blob Storage at path: {}", blobPath);
            }
        } catch (Exception e) {
            log.error("Failed to delete file from Azure Blob Storage at path: {}", blobPath, e);
            throw Exceptions.server("error-deleting-file-from-azure-blob")
                .withExtra("dir", dir)
                .withExtra("fileName", fileName)
                .withExtra("blobPath", blobPath)
                .withCause(e)
                .get();
        }
    }

    @Override
    public boolean isReady() {
        try {
            getBlobServiceClient().listBlobContainers().iterator().hasNext();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void fillDetails(Map<String, Object> props) {
        props.put("name", getName());
        props.put("type", Storage.class.getSimpleName());
        props.put("accountName", azureBlobDrive.getAccountName());
        props.put("storageRoot", azureBlobDrive.getStorageRoot());
        props.put("rootPath", rootPath);
        props.put("retentionPolicy", retentionPolicy);
        props.put("capacity", azureBlobDrive.getCapacity());
        props.put("usedSize", azureBlobDrive.getUsedSize());
    }
}
