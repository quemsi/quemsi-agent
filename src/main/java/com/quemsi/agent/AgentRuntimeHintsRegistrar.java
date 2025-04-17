package com.quemsi.agent;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;

import com.quemsi.agent.flow.TimerImpl;
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
import com.quemsi.model.dto.FlowHistory;
import com.quemsi.model.dto.NamedEntityReference;
import com.quemsi.model.dto.ObjectReference;
import com.quemsi.model.dto.StorageType;
import com.quemsi.model.dto.Tag;
import com.quemsi.model.dto.TagType;
import com.quemsi.model.dto.agent.AgentCommand;
import com.quemsi.model.dto.agent.DelayAgentCommand;
import com.quemsi.model.dto.agent.ExecuteFlow;
import com.quemsi.model.dto.agent.GoogleDriveConnect;
import com.quemsi.model.dto.agent.RetentionExecute;
import com.quemsi.model.dto.agent.UpdateAgentModel;
import com.quemsi.model.dto.agent.onapi.NotifyError;
import com.quemsi.model.dto.agent.onapi.RetentionCompleted;
import com.quemsi.model.dto.agent.onapi.UpdateGoogleDrive;
// import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;

@Configuration
public class AgentRuntimeHintsRegistrar implements RuntimeHintsRegistrar{

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection()
            // .registerType(GoogleClientSecrets.class, MemberCategory.values()).registerType(GoogleClientSecrets.Details.class, MemberCategory.values())
            // .registerType(Gstorage.class, MemberCategory.values())
            .registerType(FileNameUtil.class, MemberCategory.values())
            .registerType(TimerImpl.class, MemberCategory.values())
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
            .registerType(FlowExecutionStatus.class)
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

            // .registerType(TypeReference.of(GoogleClientSecrets.class)).registerType(TypeReference.of(GoogleClientSecrets.Details.class))
            ;
    }

}
