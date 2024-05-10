package com.biddflux.agent.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;

import com.biddflux.agent.api.ApiClientReactive;
import com.biddflux.agent.flow.gdrive.Gstorage;
import com.biddflux.commons.util.FileNameUtil;
import com.biddflux.model.dto.AgentError;
import com.biddflux.model.dto.AgentModel;
import com.biddflux.model.dto.DataFile;
import com.biddflux.model.dto.DataFlows;
import com.biddflux.model.dto.DataGroup;
import com.biddflux.model.dto.DataType;
import com.biddflux.model.dto.DataVersion;
import com.biddflux.model.dto.DataVersionSummary;
import com.biddflux.model.dto.DatasourceType;
import com.biddflux.model.dto.FlowDetail;
import com.biddflux.model.dto.FlowHistory;
import com.biddflux.model.dto.FlowHistoryStatus;
import com.biddflux.model.dto.NamedEntityReference;
import com.biddflux.model.dto.ObjectReference;
import com.biddflux.model.dto.StorageType;
import com.biddflux.model.dto.Tag;
import com.biddflux.model.dto.TagType;
import com.biddflux.model.dto.agent.AgentCommand;
import com.biddflux.model.dto.agent.DelayAgentCommand;
import com.biddflux.model.dto.agent.ExecuteFlow;
import com.biddflux.model.dto.agent.GoogleDriveConnect;
import com.biddflux.model.dto.agent.RetentionExecute;
import com.biddflux.model.dto.agent.UpdateAgentModel;
import com.biddflux.model.dto.agent.onapi.NotifyError;
import com.biddflux.model.dto.agent.onapi.RetentionCompleted;
import com.biddflux.model.dto.agent.onapi.UpdateGoogleDrive;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;

@Configuration
public class AotHints implements RuntimeHintsRegistrar{

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection()
            .registerType(ApiClientReactive.class, t -> t.withField("webClient"))
            .registerType(GoogleClientSecrets.class, MemberCategory.values()).registerType(GoogleClientSecrets.Details.class, MemberCategory.values())
            .registerType(Gstorage.class, MemberCategory.values())
            .registerType(FileNameUtil.class, MemberCategory.values())
            ;
        hints.serialization()
            .registerType(AgentError.class)
            .registerType(AgentModel.class)
                .registerType(AgentModel.Datasource.class).registerType(AgentModel.GoogleDrive.class).registerType(AgentModel.LocalDrive.class).registerType(AgentModel.Storage.class).registerType(AgentModel.Timer.class)
            .registerType(DataFile.class)
            .registerType(DataFlows.class).registerType(DataFlows.FlowSummary.class)
            .registerType(DataGroup.class)
            .registerType(DataType.class)
            .registerType(DataVersion.class)
            .registerType(DataVersionSummary.class)
            .registerType(DatasourceType.class)
            .registerType(FlowDetail.class)
            .registerType(FlowHistory.class)
            .registerType(FlowHistoryStatus.class)
            .registerType(NamedEntityReference.class)
            .registerType(ObjectReference.class)
            .registerType(StorageType.class)
            .registerType(Tag.class)
            .registerType(TagType.class)

            .registerType(AgentCommand.class)
            .registerType(DelayAgentCommand.class)
            .registerType(ExecuteFlow.class)
            .registerType(GoogleDriveConnect.class)
            .registerType(RetentionExecute.class).registerType(RetentionExecute.FileInfo.class)
            .registerType(UpdateAgentModel.class)

            .registerType(NotifyError.class)
            .registerType(RetentionCompleted.class)
            .registerType(UpdateGoogleDrive.class)

            .registerType(TypeReference.of(GoogleClientSecrets.class)).registerType(TypeReference.of(GoogleClientSecrets.Details.class))
            ;
    }

}
