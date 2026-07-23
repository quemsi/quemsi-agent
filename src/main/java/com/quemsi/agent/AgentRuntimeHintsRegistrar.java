package com.quemsi.agent;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;

import com.quemsi.agent.flow.TimerImpl;
import com.quemsi.agent.config.TrustStoreSupport;
import com.quemsi.commons.util.FileNameUtil;
import com.quemsi.model.dto.AgentError;
import com.quemsi.model.dto.AgentModel;
import com.quemsi.model.dto.DataFile;
import com.quemsi.model.dto.DataFlows;
import com.quemsi.model.dto.DataGroup;
import com.quemsi.model.dto.DataType;
import com.quemsi.model.dto.DataVersion;
import com.quemsi.model.dto.DataVersionSummary;
import com.quemsi.model.dto.DatasourceType;
import com.quemsi.model.dto.FlowDetail;
import com.quemsi.model.dto.FlowExecutionStatus;
import com.quemsi.model.dto.MaskColumn;
import com.quemsi.model.dto.MaskType;
import com.quemsi.model.dto.NamedEntityReference;
import com.quemsi.model.dto.ObjectReference;
import com.quemsi.model.dto.StorageType;
import com.quemsi.model.dto.Tag;
import com.quemsi.model.dto.TagType;
import com.quemsi.model.dto.UpdateSchemaConfig;
import com.quemsi.model.dto.UpdateSequences;
import com.quemsi.model.dto.agent.AgentCommand;
import com.quemsi.model.dto.agent.AgentCommandSync;
import com.quemsi.model.dto.agent.DelayAgentCommand;
import com.quemsi.model.dto.agent.ExecuteFlow;
import com.quemsi.model.dto.agent.RetentionExecute;
import com.quemsi.model.dto.agent.TestAWSS3Drive;
import com.quemsi.model.dto.agent.TestAzureBlobDrive;
import com.quemsi.model.dto.agent.TestDatasource;
import com.quemsi.model.dto.agent.TestFolderAccess;
import com.quemsi.model.dto.agent.UpdateAgentModel;
import com.quemsi.model.dto.agent.VersionDeleteRequest;
import com.quemsi.model.dto.agent.onapi.NotifyError;
import com.quemsi.model.dto.agent.onapi.RetentionCompleted;
import com.quemsi.model.dto.agent.onapi.TestAWSS3DriveResult;
import com.quemsi.model.dto.agent.onapi.TestAzureBlobDriveResult;
import com.quemsi.model.dto.agent.onapi.TestDatasourceResult;
import com.quemsi.model.dto.agent.onapi.TestFolderAccessResult;
import com.quemsi.model.dto.agent.onapi.VersionDeleted;
import com.quemsi.model.flow.db.mongodb.DDLServiceMongo;
import com.quemsi.model.flow.db.mongodb.DMLServiceMongo;
import com.quemsi.model.flow.db.mongodb.DatasourceFactoryMongo;
import com.quemsi.model.flow.db.mongodb.MongoTypeMapper;
import com.quemsi.model.flow.db.oracle.DDLServiceOracle;
import com.quemsi.model.flow.db.oracle.DMLServiceOracle;
import com.quemsi.model.flow.db.oracle.DatasourceFactoryOracle;
import com.quemsi.model.flow.db.sql.DbColumn;
import com.quemsi.model.flow.db.sql.DbDomainType;
import com.quemsi.model.flow.db.sql.DbEnumType;
import com.quemsi.model.flow.db.sql.DbFullTextCatalog;
import com.quemsi.model.flow.db.sql.DbFullTextIndex;
import com.quemsi.model.flow.db.sql.DbFunction;
import com.quemsi.model.flow.db.sql.DbModel;
import com.quemsi.model.flow.db.sql.DbSequence;
import com.quemsi.model.flow.db.sql.DbTable;
import com.quemsi.model.flow.db.sql.DbTrigger;
import com.quemsi.model.flow.db.sql.DbView;
import com.quemsi.model.flow.db.sql.DbXmlSchemaCollection;
import com.quemsi.model.flow.in.CustomSerializedColumn;
import com.quemsi.model.flow.in.TableData;
import com.quemsi.model.flow.in.TableDataMeta;
import com.quemsi.model.flow.in.TableDataPage;

@Configuration
public class AgentRuntimeHintsRegistrar implements RuntimeHintsRegistrar{

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection()
            .registerType(FileNameUtil.class, MemberCategory.values())
            .registerType(TimerImpl.class, MemberCategory.values())
            /* Quartz job instantiation hints */
            .registerType(org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean.class, MemberCategory.values())
            .registerType(org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean.MethodInvokingJob.class, MemberCategory.values())
            .registerType(org.springframework.scheduling.quartz.AdaptableJobFactory.class, MemberCategory.values())
            /* DbModel and related classes for Jackson serialization */
            .registerType(DbModel.class, MemberCategory.values())
            .registerType(DbModel.TableReference.class, MemberCategory.values())
            .registerType(DbModel.ReferenceInfo.class, MemberCategory.values())
            .registerType(DbModel.IndexInfo.class, MemberCategory.values())
            .registerType(DbModel.ContraintInfo.class, MemberCategory.values())
            .registerType(DbModel.CheckConstraint.class, MemberCategory.values())
            .registerType(DbTable.class, MemberCategory.values())
            .registerType(DbColumn.class, MemberCategory.values())
            .registerType(DbSequence.class, MemberCategory.values())
            .registerType(DbView.class, MemberCategory.values())
            .registerType(DbFunction.class, MemberCategory.values())
            .registerType(DbEnumType.class, MemberCategory.values())
            .registerType(DbDomainType.class, MemberCategory.values())
            .registerType(DbTrigger.class, MemberCategory.values())
            .registerType(DbFullTextCatalog.class, MemberCategory.values())
            .registerType(DbFullTextIndex.class, MemberCategory.values())
            .registerType(DbFullTextIndex.Column.class, MemberCategory.values())
            .registerType(DbXmlSchemaCollection.class, MemberCategory.values())
            .registerType(DatasourceFactoryMongo.class, MemberCategory.values())
            .registerType(DDLServiceMongo.class, MemberCategory.values())
            .registerType(DMLServiceMongo.class, MemberCategory.values())
            .registerType(MongoTypeMapper.class, MemberCategory.values())
            .registerType(DatasourceFactoryOracle.class, MemberCategory.values())
            .registerType(DDLServiceOracle.class, MemberCategory.values())
            .registerType(DMLServiceOracle.class, MemberCategory.values())
            /* TableData and related classes for Jackson serialization */
            .registerType(TableData.class, MemberCategory.values())
            .registerType(TableData.DataPage.class, MemberCategory.values())
            .registerType(TableDataMeta.class, MemberCategory.values())
            .registerType(TableDataPage.class, MemberCategory.values())
            .registerType(TableDataPage.Request.class, MemberCategory.values())
            .registerType(CustomSerializedColumn.BinaryColumn.class, MemberCategory.values())
            /* All AgentCommand subclasses for Jackson serialization */
            .registerType(AgentCommand.class, MemberCategory.values())
            .registerType(AgentCommandSync.class, MemberCategory.values())
            .registerType(ExecuteFlow.class, MemberCategory.values())
            .registerType(DelayAgentCommand.class, MemberCategory.values())
            .registerType(UpdateAgentModel.class, MemberCategory.values())
            .registerType(RetentionExecute.class, MemberCategory.values())
            .registerType(RetentionExecute.FileInfo.class, MemberCategory.values())
            .registerType(TestDatasource.class, MemberCategory.values())
            .registerType(TestAWSS3Drive.class, MemberCategory.values())
            .registerType(TestAzureBlobDrive.class, MemberCategory.values())
            .registerType(VersionDeleteRequest.class, MemberCategory.values())
            .registerType(NotifyError.class, MemberCategory.values())
            .registerType(RetentionCompleted.class, MemberCategory.values())
            .registerType(TestAWSS3DriveResult.class, MemberCategory.values())
            .registerType(TestAzureBlobDriveResult.class, MemberCategory.values())
            .registerType(TestDatasourceResult.class, MemberCategory.values())
            .registerType(VersionDeleted.class, MemberCategory.values())
            .registerType(TestFolderAccess.class, MemberCategory.values())
            .registerType(TestFolderAccessResult.class, MemberCategory.values())
            /* Flow step config types for Jackson convertValue */
            .registerType(UpdateSchemaConfig.class, MemberCategory.values())
            .registerType(UpdateSequences.class, MemberCategory.values())
            .registerType(UpdateSequences.SequenceMapping.class, MemberCategory.values())
            .registerType(MaskColumn.class, MemberCategory.values())
            .registerType(MaskColumn.MaskColumnConfig.class, MemberCategory.values())
            .registerType(MaskType.class, MemberCategory.values())
            .registerType(TrustStoreSupport.class, MemberCategory.values())
            .registerType(java.security.KeyStore.class, MemberCategory.values())
            .registerType(javax.net.ssl.TrustManagerFactory.class, MemberCategory.values())
            ;
        hints.serialization()
            .registerType(AgentError.class)
            .registerType(AgentModel.class)
            .registerType(AgentModel.Datasource.class)
            .registerType(AgentModel.LocalDrive.class)
            .registerType(AgentModel.Storage.class)
            .registerType(AgentModel.Timer.class)
            .registerType(AgentModel.AzureBlobDrive.class)
            .registerType(AgentModel.AWSS3Drive.class)
            .registerType(DataFile.class)
            .registerType(DataFlows.class).registerType(DataFlows.FlowSummary.class)
            .registerType(DataGroup.class)
            .registerType(DataType.class)
            .registerType(DataVersion.class)
            .registerType(DataVersionSummary.class)
            .registerType(DatasourceType.class)
            .registerType(FlowDetail.class)
            .registerType(FlowExecutionStatus.class)
            .registerType(NamedEntityReference.class)
            .registerType(ObjectReference.class)
            .registerType(StorageType.class)
            .registerType(Tag.class)
            .registerType(TagType.class)

            .registerType(AgentCommand.class)
            .registerType(AgentCommandSync.class)
            .registerType(DelayAgentCommand.class)
            .registerType(ExecuteFlow.class)
            .registerType(RetentionExecute.class).registerType(RetentionExecute.FileInfo.class)
            .registerType(UpdateAgentModel.class)
            .registerType(VersionDeleteRequest.class)
            .registerType(TestDatasource.class)
            .registerType(TestAWSS3Drive.class)
            .registerType(TestAzureBlobDrive.class)
            .registerType(TestDatasourceResult.class)
            .registerType(TestAWSS3DriveResult.class)
            .registerType(TestAzureBlobDriveResult.class)
            .registerType(TestFolderAccess.class)
            .registerType(TestFolderAccessResult.class)
            .registerType(NotifyError.class)
            .registerType(RetentionCompleted.class)
            .registerType(VersionDeleted.class)
            .registerType(UpdateSchemaConfig.class)
            .registerType(UpdateSequences.class)
            .registerType(UpdateSequences.SequenceMapping.class)
            .registerType(MaskColumn.class)
            .registerType(MaskColumn.MaskColumnConfig.class)
            .registerType(MaskType.class)
            ;
    }

}
