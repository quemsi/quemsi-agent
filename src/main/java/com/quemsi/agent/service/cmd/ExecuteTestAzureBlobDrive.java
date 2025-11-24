package com.quemsi.agent.service.cmd;

import org.springframework.beans.factory.annotation.Autowired;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.AzureBlobStorage;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.dto.agent.TestAzureBlobDrive;
import com.quemsi.model.dto.agent.onapi.TestAzureBlobDriveResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecuteTestAzureBlobDrive {
    @Autowired
    private ApiManager apiManager;
    
    public void execute(TestAzureBlobDrive testAzureBlobDrive){
        String endpoint = String.format(AzureBlobStorage.AZURE_BLOB_ENDPOINT_FORMAT, testAzureBlobDrive.getAccountName());
        String accountName = testAzureBlobDrive.getAccountName();
        String accountKey = testAzureBlobDrive.getAccountKey();
        TestAzureBlobDriveResult.TestAzureBlobDriveResultBuilder builder = TestAzureBlobDriveResult.builder().correlationId(testAzureBlobDrive.getCorrelationId());
        if(testAzureBlobDrive.isUseEnvVar()){
            accountKey = System.getenv(accountKey);
            if(StringUtils.isEmptyOrNull(accountKey)){
                TestAzureBlobDriveResult result = builder.success(false).errorCode(400).errorMessage("environment-vars-not-set").build();
                apiManager.send(result);
                return;
            }
        }
        try{
            com.azure.core.http.policy.HttpPipelinePolicy timeoutPolicy = (context, next) -> {
                context.setHttpRequest(context.getHttpRequest());
                return next.process().timeout(java.time.Duration.ofSeconds(3L)).retry(2);
            };
            
            BlobServiceClient client = new com.azure.storage.blob.BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new StorageSharedKeyCredential(accountName, accountKey))
                .addPolicy(timeoutPolicy)
                .buildClient();
            client.listBlobContainers().iterator().hasNext();
            TestAzureBlobDriveResult result = builder.success(true).build();
            apiManager.send(result);
        }catch(Exception ex){
            log.error("azure-blob-drive-failed", ex);
            TestAzureBlobDriveResult result = builder.success(false).errorCode(500).errorMessage(ex.getMessage()!=null?ex.getMessage():"azure-blob-drive-failed").build();
            apiManager.send(result);
        }
    }
}
