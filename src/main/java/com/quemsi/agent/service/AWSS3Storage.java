package com.quemsi.agent.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.model.dto.DataFile;
import com.quemsi.model.flow.DataPackage;
import com.quemsi.model.flow.DataPackageFileResource;
import com.quemsi.model.flow.Flow;
import com.quemsi.model.flow.out.AWSS3Drive;
import com.quemsi.model.flow.out.Storage;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import com.quemsi.commons.util.FileNameUtil;
import com.quemsi.commons.util.FileResource;

@Slf4j
public class AWSS3Storage implements Storage {
    private AWSS3Drive awsS3Drive;
    private S3Client s3Client;
    private FileNameUtil fileNameUtil;
    
    @Builder
    public AWSS3Storage(AWSS3Drive awsS3Drive) {
        this.awsS3Drive = awsS3Drive;
        this.fileNameUtil = new FileNameUtil();
    }

    public synchronized S3Client getS3Client() {
        if (s3Client == null) {
            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
                awsS3Drive.getAccessKey(), 
                awsS3Drive.getSecretKey()
            );
            
            s3Client = S3Client.builder()
                .region(Region.of(awsS3Drive.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
                
            // Test the connection
            try {
                s3Client.headBucket(builder -> builder.bucket(awsS3Drive.getBucketName()));
            } catch (Exception e) {
                log.warn("Failed to connect to S3 bucket: {}", e.getMessage());
            }
        }
        return s3Client;
    }

    @Override
    public String getName() {
        return awsS3Drive.getName();
    }

    @Override
    public boolean recordFiles() {
        return true;
    }

    @Override
    public String getRootPath() {
        return awsS3Drive.getStorageRoot();
    }

    @Override
    public void init(Flow f) {
        String bucketName = awsS3Drive.getBucketName();
        try {
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();
            getS3Client().createBucket(createBucketRequest);
            log.info("Created S3 bucket: {}", bucketName);
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            log.debug("S3 bucket '{}' already exists.", bucketName);
        } catch (S3Exception e) {
            log.warn("Failed to create S3 bucket '{}': {}", bucketName, e.getMessage());
            throw Exceptions.server("unable-to-create-s3-bucket").withExtra("bucketName", bucketName).withCause(e).get();
        }
    }

    @Override
    public void store(String dataName, List<DataPackage> dataPackages, Long version) {
        if (dataPackages.isEmpty()) {
            throw Exceptions.badRequest("datapackages-empty").withExtra("versionId", version).get();
        }
        
        String bucketName = awsS3Drive.getBucketName();
        
        dataPackages.forEach(dp -> {
            log.info("Storing file to AWS S3: {}", dp.getName());
            
            // Generate versioned filename using FileNameUtil
            String versionedFileName = fileNameUtil.versionedFileName(dp.getName(), version);
            String s3Key = dataName + "/" + versionedFileName;
            
            log.info("Destination S3 key: {}", s3Key);
            
            try {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(dp.getContentType())
                    .build();
                
                RequestBody requestBody = RequestBody.fromInputStream(dp.getInputStream(), dp.getLength());
                getS3Client().putObject(putObjectRequest, requestBody);
                
                log.info("Successfully uploaded file {} to AWS S3 at key: {}", dp.getName(), s3Key);
            } catch (Exception e) {
                log.error("Failed to upload file {} to AWS S3", dp.getName(), e);
                throw Exceptions.server("error-storing-file-to-s3")
                    .withExtra("fileName", dp.getName())
                    .withExtra("s3Key", s3Key)
                    .withExtra("bucketName", bucketName)
                    .withCause(e)
                    .get();
            }
        });
    }

    @Override
    public List<DataPackage> getFiles(List<DataFile> files) throws IOException {
        String bucketName = awsS3Drive.getBucketName();
        
        return files.stream().<DataPackage>map(f -> {
            try {
                // Generate versioned filename using FileNameUtil
                String versionedFileName = fileNameUtil.versionedFileName(f.getName(), f.getVersion());
                String s3Key = f.getDir() + "/" + versionedFileName;
                
                log.info("Retrieving file from AWS S3 at key: {}", s3Key);
                
                // Check if object exists
                try {
                    HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .build();
                    getS3Client().headObject(headObjectRequest);
                } catch (NoSuchKeyException e) {
                    log.warn("File not found in AWS S3 at key: {}", s3Key);
                    throw Exceptions.notFound("file-not-found").withExtra("bucketName", bucketName).withExtra("versionedFileName", versionedFileName).get();
                }
                
                // Get object
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
                
                InputStream s3InputStream = getS3Client().getObject(getObjectRequest);
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                org.apache.commons.io.IOUtils.copy(s3InputStream, outStream);
                
                // Determine content type
                String contentType = f.getContentType();
                if (contentType == null || contentType.isEmpty()) {
                    contentType = fileNameUtil.getFileType(versionedFileName);
                }
                
                // Create a DataPackage from the S3 object
                FileResource resource = FileResource.builder()
                    .name(versionedFileName)
                    .contentType(contentType)
                    .empty(outStream.size() > 0)
                    .originalFilename(versionedFileName)
                    .size(outStream.size())
                    .data(outStream.toByteArray())
                    .build();
                return new DataPackageFileResource(resource);
            } catch (BaseRuntimeException bre) {
                throw bre;
            } catch (Exception e) {
                throw Exceptions.server("unable-to-reach-s3").withCause(e).get();
            }
        }).filter(dp -> dp != null).toList();
    }

    @Override
    public void deleteFile(String dir, String fileName) throws IOException {
        String bucketName = awsS3Drive.getBucketName();
        
        String s3Key = dir + "/" + fileName;
        log.debug("Deleting file from AWS S3 at key: {}", s3Key);
        
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
            
            getS3Client().deleteObject(deleteObjectRequest);
            log.info("Successfully deleted file from AWS S3 at key: {}", s3Key);
        } catch (Exception e) {
            log.error("Failed to delete file from AWS S3 at key: {}", s3Key, e);
            throw Exceptions.server("error-deleting-file-from-s3")
                .withExtra("dir", dir)
                .withExtra("fileName", fileName)
                .withExtra("s3Key", s3Key)
                .withCause(e)
                .get();
        }
    }

    @Override
    public boolean isReady() {
        try {
            getS3Client().headBucket(builder -> builder.bucket(awsS3Drive.getBucketName()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void fillDetails(Map<String, Object> props) {
        props.put("name", getName());
        props.put("type", Storage.class.getSimpleName());
        props.put("accessKey", awsS3Drive.getAccessKey());
        props.put("region", awsS3Drive.getRegion());
        props.put("bucketName", awsS3Drive.getBucketName());
        props.put("storageRoot", awsS3Drive.getStorageRoot());
        props.put("capacity", awsS3Drive.getCapacity());
        props.put("usedSize", awsS3Drive.getUsedSize());
    }
}
