package com.quemsi.agent.service;

import com.quemsi.model.flow.out.AWSS3Drive;

/**
 * Example usage of AWSS3Storage
 * 
 * This class demonstrates how to use the AWSS3Storage implementation
 * to store files in AWS S3 storage services.
 */
public class AWSS3StorageExample {
    
    /**
     * Example of creating and using AWSS3Storage
     */
    public void exampleUsage() {
        // Create AWSS3Drive configuration
        AWSS3Drive s3Drive = new AWSS3Drive();
        s3Drive.setName("my-s3-storage");
        s3Drive.setAccessKey("your-access-key");
        s3Drive.setSecretKey("your-secret-key");
        s3Drive.setRegion("us-east-1");
        s3Drive.setBucketName("my-bucket");
        s3Drive.setStorageRoot("/data");
        s3Drive.setCapacity(1000000000L); // 1GB
        s3Drive.setUsedSize(0L);
        s3Drive.setUseEnvVar(false);
        
        // Create AWSS3Storage instance
        AWSS3Storage s3Storage = AWSS3Storage.builder()
            .awsS3Drive(s3Drive)
            .build();
        
        // Check if storage is ready
        if (s3Storage.isReady()) {
            System.out.println("AWS S3 storage is ready!");
            
            // Get storage details
            java.util.Map<String, Object> details = new java.util.HashMap<>();
            s3Storage.fillDetails(details);
            System.out.println("Storage details: " + details);
        } else {
            System.out.println("AWS S3 storage is not ready!");
        }
    }
    
    /**
     * Example of using environment variables for credentials
     */
    public void exampleWithEnvVars() {
        // Create AWSS3Drive configuration with environment variables
        AWSS3Drive s3Drive = new AWSS3Drive();
        s3Drive.setName("my-s3-storage-env");
        s3Drive.setAccessKey("AWS_ACCESS_KEY_ID"); // Environment variable name
        s3Drive.setSecretKey("AWS_SECRET_ACCESS_KEY"); // Environment variable name
        s3Drive.setRegion("us-west-2");
        s3Drive.setBucketName("my-bucket");
        s3Drive.setStorageRoot("/data");
        s3Drive.setCapacity(2000000000L); // 2GB
        s3Drive.setUsedSize(0L);
        s3Drive.setUseEnvVar(true); // Use environment variables
        
        // Create AWSS3Storage instance
        AWSS3Storage s3Storage = AWSS3Storage.builder()
            .awsS3Drive(s3Drive)
            .build();
        
        // The storage will automatically read credentials from environment variables
        // AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
        
        // Example usage of the storage
        System.out.println("AWS S3 storage configured with environment variables: " + s3Storage.getName());
    }
}
