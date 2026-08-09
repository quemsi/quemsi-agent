package com.quemsi.agent.control;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.SpringBeanManager;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.FileNameUtil;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.dto.DataFile;
import com.quemsi.model.dto.DownloadGrantPayload;
import com.quemsi.model.flow.DataPackage;
import com.quemsi.model.flow.Flow;
import com.quemsi.model.flow.FlowContext;
import com.quemsi.model.flow.out.Storage;

@RestController
@RequestMapping("/control")
public class ControlDownloadController {

    @Autowired
    private ApiManager apiManager;
    @Autowired
    private SpringBeanManager beanManager;
    @Autowired
    private FileNameUtil fileNameUtil;

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestParam("ticket") String ticket) throws Exception {
        if (StringUtils.isEmptyOrNull(ticket)) {
            throw Exceptions.badRequest("download-ticket-required").get();
        }

        DownloadGrantPayload payload = redeem(ticket);

        Storage storage;
        try {
            storage = beanManager.findStorage(payload.getStorageName());
        } catch (NoSuchBeanDefinitionException e) {
            throw Exceptions.notFound("storage-not-registered")
                    .withExtra("storageName", payload.getStorageName())
                    .withCause(e)
                    .get();
        }

        Flow flow = new Flow();
        flow.setId(-1L);
        flow.setName("control-download");
        if (!storage.isReady()) {
            storage.init(flow);
        }

        DataFile dataFile = new DataFile();
        dataFile.setDir(payload.getDir());
        dataFile.setName(payload.getFileName());
        dataFile.setVersion(payload.getVersionId());
        dataFile.setContentType(payload.getContentType());
        dataFile.setSize(payload.getSize());

        FlowContext context = new FlowContext(flow, null);
        List<DataPackage> packages = storage.getFiles(context, List.of(dataFile));
        if (packages == null || packages.isEmpty()) {
            throw Exceptions.notFound("file-not-found")
                    .withExtra("fileName", payload.getFileName())
                    .withExtra("dir", payload.getDir())
                    .get();
        }

        DataPackage dataPackage = packages.get(0);
        InputStream raw = dataPackage.getInputStream();
        InputStream inputStream = new FilterInputStream(raw) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    dataPackage.clear();
                }
            }
        };

        long length = dataPackage.getLength() > 0
                ? dataPackage.getLength()
                : (payload.getSize() != null ? payload.getSize() : -1);

        String contentType = payload.getContentType();
        if (StringUtils.isEmptyOrNull(contentType)) {
            contentType = dataPackage.getContentType();
        }
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (!StringUtils.isEmptyOrNull(contentType)) {
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        String filename = payload.getFileName() != null ? payload.getFileName() : "download";
        if (payload.getVersionId() != null && payload.getFileName() != null) {
            filename = fileNameUtil.versionedFileName(payload.getFileName(), payload.getVersionId());
        }
        String finalFileName = filename;
        final long contentLength = length;
        InputStreamResource body = new InputStreamResource(inputStream) {
            @Override
            public String getFilename() {
                return finalFileName;
            }

            @Override
            public long contentLength() {
                return contentLength;
            }
        };

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename))
                .contentType(mediaType);
        if (contentLength >= 0) {
            builder.contentLength(contentLength);
        }
        return builder.body(body);
    }

    private DownloadGrantPayload redeem(String ticket) {
        try {
            return apiManager.redeemDownloadGrant(ticket);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == HttpStatus.GONE.value()) {
                throw Exceptions.create(HttpStatus.GONE, "download-grant-invalid").withCause(e).get();
            }
            if (status == HttpStatus.NOT_FOUND.value()) {
                throw Exceptions.notFound("download-grant-not-found").withCause(e).get();
            }
            if (status == HttpStatus.FORBIDDEN.value() || status == HttpStatus.UNAUTHORIZED.value()) {
                throw Exceptions.forbidden("download-grant-forbidden").withCause(e).get();
            }
            throw Exceptions.server("download-grant-redeem-failed")
                    .withExtra("status", status)
                    .withCause(e)
                    .get();
        } catch (WebClientRequestException e) {
            throw Exceptions.server("unable-to-reach-api").withCause(e).get();
        } catch (BaseRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw Exceptions.server("download-grant-redeem-failed").withCause(e).get();
        }
    }

    private static String contentDisposition(String filename) {
        String safe = filename.replace("\"", "");
        return "attachment; filename=\"" + safe + "\"";
    }
}
