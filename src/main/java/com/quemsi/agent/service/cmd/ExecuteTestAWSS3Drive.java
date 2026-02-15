package com.quemsi.agent.service.cmd;

import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.AgentBatchedLogger;
import com.quemsi.commons.util.LogMessage;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.dto.agent.TestAWSS3Drive;
import com.quemsi.model.dto.agent.onapi.TestAWSS3DriveResult;

import java.time.Duration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

public class ExecuteTestAWSS3Drive {
    @Autowired
    private ApiManager apiManager;
    @Autowired
    private AgentBatchedLogger agentBatchedLogger;
    
    public void execute(TestAWSS3Drive testAWSS3Drive){
        String accessKey = testAWSS3Drive.getAccessKey();
        String secretKey = testAWSS3Drive.getSecretKey();
        String region = testAWSS3Drive.getRegion();
        String bucketName = testAWSS3Drive.getBucketName();

        agentBatchedLogger.logInfo(null, null, LogMessage.info("Executing test AWS S3 drive with access key: {}, secret key: {}, region: {}, bucket name: {}", accessKey, secretKey, region, bucketName));

        TestAWSS3DriveResult.TestAWSS3DriveResultBuilder builder = TestAWSS3DriveResult.builder()
            .correlationId(testAWSS3Drive.getCorrelationId());
            
        if(testAWSS3Drive.isUseEnvVar()){
            accessKey = System.getenv(accessKey);
            secretKey = System.getenv(secretKey);
            if(StringUtils.isEmptyOrNull(accessKey) || StringUtils.isEmptyOrNull(secretKey)){
                TestAWSS3DriveResult result = builder.success(false).errorCode(400).errorMessage("environment-vars-not-set").build();
                apiManager.send(result);
                return;
            }
        }
        
        try{
            AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);
            
            ClientOverrideConfiguration clientConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(10))
                .apiCallAttemptTimeout(Duration.ofSeconds(10))
                .build();
            
            S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .overrideConfiguration(clientConfig)
                .build();
                
            /* Test the connection by checking if we can access the bucket */
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                .bucket(bucketName)
                .build();
                
            s3Client.headBucket(headBucketRequest);
            
            TestAWSS3DriveResult result = builder.success(true).message("AWS S3 connection successful").build();
            apiManager.send(result);
        }catch(Exception ex){
            TestAWSS3DriveResult result = builder.success(false).errorCode(500).errorMessage(ex.getMessage()!=null?ex.getMessage():"aws-s3-drive-test-failed").build();
            apiManager.send(result);
        }
    }
}
