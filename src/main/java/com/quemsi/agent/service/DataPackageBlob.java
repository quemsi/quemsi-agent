package com.quemsi.agent.service;

import java.io.File;
import java.io.InputStream;

import com.azure.storage.blob.BlobClient;
import com.quemsi.model.flow.DataPackage;

import lombok.Data;

@Data
public class DataPackageBlob implements DataPackage {
    private BlobClient blobClient;
    private String name;
    private long length;
    private String contentType;
    
    public DataPackageBlob(BlobClient blobClient, String name, long length, String contentType) {
        this.blobClient = blobClient;
        this.name = name;
        this.length = length;
        this.contentType = contentType;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public void setName(String name) {
        this.name = name;
    }
    
    @Override
    public String getContentType() {
        return contentType;
    }
    
    @Override
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    @Override
    public File getFile(String destName) {
        throw new UnsupportedOperationException("getFile not supported for Azure Blob Storage");
    }
    
    @Override
    public InputStream getInputStream() {
        return blobClient.openInputStream();
    }
    
    @Override
    public void clear() {
        // For Azure Blob Storage, we don't delete the blob when clearing
        // This is typically handled by retention policies or explicit deletion
    }
    
    @Override
    public long getLength() {
        return length;
    }
}
