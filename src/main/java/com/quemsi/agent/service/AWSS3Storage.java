package com.quemsi.agent.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.FileNameUtil;
import com.quemsi.commons.util.FileResource;
import com.quemsi.commons.util.LogMessage;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.dto.DataFile;
import com.quemsi.model.flow.DataPackage;
import com.quemsi.model.flow.DataPackageFileResource;
import com.quemsi.model.flow.Flow;
import com.quemsi.model.flow.FlowContext;
import com.quemsi.model.flow.out.AWSS3Drive;
import com.quemsi.model.flow.out.Storage;

import lombok.Builder;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
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

public class AWSS3Storage implements Storage {
    private AWSS3Drive awsS3Drive;
    private S3Client s3Client;
    @Setter
    private String retentionPolicy;
    @Setter
    private Long usedSize;
    @Setter
    private Long capacity;
    @Setter
    private String rootPath;
    @Setter
    private FileNameUtil util;
    @Setter
    private AgentBatchedLogger agentBatchedLogger;
    
    private Long getFlowExecutionId(FlowContext context) {
        return context != null && context.getExecution() != null ? context.getExecution().getId() : null;
    }
    
    private Long getFlowExecutionStepId(FlowContext context) {
        return context != null && context.getCurrentStep() != null ? context.getCurrentStep().getId() : null;
    }

    @Builder
    public AWSS3Storage(AWSS3Drive awsS3Drive) {
        this.awsS3Drive = awsS3Drive;
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
                
            /* Test the connection */
            try {
                s3Client.headBucket(builder -> builder.bucket(awsS3Drive.getBucketName()));
            } catch (Exception e) {
                if (agentBatchedLogger != null) {
                    agentBatchedLogger.logWarn(null, null, LogMessage.warn("Failed to connect to S3 bucket: {}", e.getMessage()));
                }
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
        return StringUtils.ensureSeperator(awsS3Drive.getStorageRoot(), rootPath);
    }

    @Override
    public void init(Flow f) {
        String bucketName = awsS3Drive.getBucketName();
        try {
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();
            getS3Client().createBucket(createBucketRequest);
            if (agentBatchedLogger != null) {
                agentBatchedLogger.logInfo(null, null, LogMessage.info("Created S3 bucket: {}", bucketName));
            }
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            if (agentBatchedLogger != null) {
                agentBatchedLogger.logDebug(null, null, LogMessage.debug("S3 bucket '{}' already exists.", bucketName));
            }
        } catch (S3Exception e) {
            if (agentBatchedLogger != null) {
                agentBatchedLogger.logWarn(null, null, LogMessage.warn("Failed to create S3 bucket '{}': {}", bucketName, e.getMessage()));
            }
            throw Exceptions.server("unable-to-create-s3-bucket").withExtra("bucketName", bucketName).withCause(e).get();
        }
    }

    @Override
    public void store(FlowContext context, String dataName, List<DataPackage> dataPackages, Long version) {
        if (dataPackages.isEmpty()) {
            throw Exceptions.badRequest("datapackages-empty").withExtra("versionId", version).get();
        }
        
        String bucketName = awsS3Drive.getBucketName();
        Long flowExecutionId = getFlowExecutionId(context);
        Long flowExecutionStepId = getFlowExecutionStepId(context);
        context.logStepInfo(context.getCurrentStep(), LogMessage.info("Storing {} files to AWS S3", dataPackages.size()));
        dataPackages.forEach(dp -> {
            if (agentBatchedLogger != null) {
                agentBatchedLogger.logInfo(flowExecutionId, flowExecutionStepId, LogMessage.info("Storing file to AWS S3: {}", dp.getName()));
            }
            
            /* Generate versioned filename using FileNameUtil */
            String fileFolder = StringUtils.trim(StringUtils.ensureSeperator(awsS3Drive.getStorageRoot(), rootPath), "/", null);
            String versionedFileName = util.versionedFileName(dp.getName(), version);
            String s3Key = fileFolder + "/" + dataName + "/" + versionedFileName;
            
            if (agentBatchedLogger != null) {
                agentBatchedLogger.logInfo(flowExecutionId, flowExecutionStepId, LogMessage.info("Destination S3 key: {}", s3Key));
            }
            
            try {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(dp.getContentType())
                    .build();
                
                RequestBody requestBody = RequestBody.fromInputStream(dp.getInputStream(), dp.getLength());
                getS3Client().putObject(putObjectRequest, requestBody);
                
                if (agentBatchedLogger != null) {
                    agentBatchedLogger.logInfo(flowExecutionId, flowExecutionStepId, LogMessage.info("Successfully uploaded file {} to AWS S3 at key: {}", dp.getName(), s3Key));
                }
            } catch (Exception e) {
                if (agentBatchedLogger != null) {
                    agentBatchedLogger.logError(flowExecutionId, flowExecutionStepId, LogMessage.error("Failed to upload file {} to AWS S3", dp.getName(), e));
                }
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
    public List<DataPackage> getFiles(FlowContext context, List<DataFile> files) throws IOException {
        String bucketName = awsS3Drive.getBucketName();
        Long flowExecutionId = getFlowExecutionId(context);
        Long flowExecutionStepId = getFlowExecutionStepId(context);
        
        return files.stream().<DataPackage>map(f -> {
            try {
                /* Generate versioned filename using FileNameUtil */
                String fileFolder = StringUtils.trim(StringUtils.ensureSeperator(awsS3Drive.getStorageRoot(), rootPath), "/", null);
                String versionedFileName = util.versionedFileName(f.getName(), f.getVersion());
                String s3Key = StringUtils.buildPath("/", fileFolder ,f.getDir(), versionedFileName);
                
                if (agentBatchedLogger != null) {
                    agentBatchedLogger.logInfo(flowExecutionId, flowExecutionStepId, LogMessage.info("Retrieving file from AWS S3 at key: {}", s3Key));
                }
                
                /* Check if object exists */
                try {
                    HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .build();
                    getS3Client().headObject(headObjectRequest);
                } catch (NoSuchKeyException e) {
                    if (agentBatchedLogger != null) {
                        agentBatchedLogger.logWarn(flowExecutionId, flowExecutionStepId, LogMessage.warn("File not found in AWS S3 at key: {}", s3Key));
                    }
                    throw Exceptions.notFound("file-not-found").withExtra("bucketName", bucketName).withExtra("versionedFileName", versionedFileName).get();
                }
                
                /* Get object */
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
                
                InputStream s3InputStream = getS3Client().getObject(getObjectRequest);
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                org.apache.commons.io.IOUtils.copy(s3InputStream, outStream);
                
                /* Determine content type */
                String contentType = f.getContentType();
                if (contentType == null || contentType.isEmpty()) {
                    contentType = util.getFileType(versionedFileName);
                }
                
                /* Create a DataPackage from the S3 object */
                FileResource resource = FileResource.builder()
                    .name(versionedFileName)
                    .contentType(contentType)
                    .empty(outStream.size() > 0)
                    .originalFilename(versionedFileName)
                    .size(outStream.size())
                    .data(outStream.toByteArray())
                    .build();
                return new DataPackageFileResource(f.getName(), resource);
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
        
        String s3Key = StringUtils.trim(StringUtils.ensureSeperator(awsS3Drive.getStorageRoot(), rootPath) + "/" + dir + "/" + fileName, "/", null);
        if (agentBatchedLogger != null) {
            agentBatchedLogger.logDebug(null, null, LogMessage.debug("Deleting file from AWS S3 at key: {}", s3Key));
        }
        
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
            
            getS3Client().deleteObject(deleteObjectRequest);
            if (agentBatchedLogger != null) {
                agentBatchedLogger.logInfo(null, null, LogMessage.info("Successfully deleted file from AWS S3 at key: {}", s3Key));
            }
        } catch (Exception e) {
            if (agentBatchedLogger != null) {
                agentBatchedLogger.logError(null, null, LogMessage.error("Failed to delete file from AWS S3 at key: {}", s3Key, e));
            }
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
        props.put("rootPath", rootPath);
        props.put("retentionPolicy", retentionPolicy);
        props.put("capacity", awsS3Drive.getCapacity());
        props.put("usedSize", awsS3Drive.getUsedSize());
    }
}
