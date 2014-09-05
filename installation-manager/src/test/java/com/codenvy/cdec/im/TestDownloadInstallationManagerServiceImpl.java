/*
 * CODENVY CONFIDENTIAL
 * __________________
 *
 *  [2012] - [2014] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.cdec.im;

import com.codenvy.cdec.artifacts.Artifact;
import com.codenvy.cdec.artifacts.ArtifactFactory;
import com.codenvy.cdec.artifacts.CDECArtifact;
import com.codenvy.cdec.artifacts.InstallManagerArtifact;
import com.codenvy.cdec.exceptions.ArtifactNotFoundException;
import com.codenvy.cdec.exceptions.AuthenticationException;
import com.codenvy.cdec.restlet.InstallationManager;
import com.codenvy.cdec.restlet.InstallationManagerService;
import com.codenvy.cdec.user.UserCredentials;

import org.restlet.ext.jackson.JacksonRepresentation;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.LinkedHashMap;

import static com.codenvy.cdec.utils.Commons.getPrettyPrintingJson;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

/**
 * @author Dmytro Nochevnov
 */
public class TestDownloadInstallationManagerServiceImpl {
    private InstallationManagerService installationManagerService;

    private InstallationManager        mockInstallationManager;
    private Artifact                   installManagerArtifact;
    private Artifact                   cdecArtifact;
    private UserCredentials            testCredentials;

    @BeforeMethod
    public void init() {
        initMocks();
        installationManagerService = new InstallationManagerServiceImpl(mockInstallationManager);
    }

    public void initMocks() {
        mockInstallationManager = mock(InstallationManagerImpl.class);
        installManagerArtifact = ArtifactFactory.createArtifact(InstallManagerArtifact.NAME);
        cdecArtifact = ArtifactFactory.createArtifact(CDECArtifact.NAME);
        testCredentials = new UserCredentials("auth token");
    }
    
    @Test
    public void testDownload() throws Exception {
        doReturn(new LinkedHashMap<Artifact, String>() {
            {
                put(cdecArtifact, "2.10.5");
                put(installManagerArtifact, "1.0.1");
            }
        }).when(mockInstallationManager).getUpdates(testCredentials.getToken());

        JacksonRepresentation<UserCredentials> userCredentialsRep = new JacksonRepresentation<>(testCredentials);
        String response = installationManagerService.download(userCredentialsRep);
        assertEquals(getPrettyPrintingJson(response), "{\n" +
                                                      "  \"artifacts\": [\n" +
                                                      "    {\n" +
                                                      "      \"artifact\": \"cdec\",\n" +
                                                      "      \"status\": \"SUCCESS\",\n" +
                                                      "      \"version\": \"2.10.5\"\n" +
                                                      "    },\n" +
                                                      "    {\n" +
                                                      "      \"artifact\": \"installation-manager\",\n" +
                                                      "      \"status\": \"SUCCESS\",\n" +
                                                      "      \"version\": \"1.0.1\"\n" +
                                                      "    }\n" +
                                                      "  ],\n" +
                                                      "  \"status\": \"OK\"\n" +
                                                      "}");
    }
    
    @Test
    public void testDownloadSpecificArtifact() throws Exception {
        doReturn(new LinkedHashMap<Artifact, String>() {
            {
                put(cdecArtifact, "2.10.5");
                put(installManagerArtifact, "1.0.1");
            }
        }).when(mockInstallationManager).getUpdates(testCredentials.getToken());

        JacksonRepresentation<UserCredentials> userCredentialsRep = new JacksonRepresentation<>(testCredentials);
        String response = installationManagerService.download("cdec", userCredentialsRep);
        assertEquals(getPrettyPrintingJson(response), "{\n" +
                                                      "  \"artifacts\": [{\n" +
                                                      "    \"artifact\": \"cdec\",\n" +
                                                      "    \"status\": \"SUCCESS\",\n" +
                                                      "    \"version\": \"2.10.5\"\n" +
                                                      "  }],\n" +
                                                      "  \"status\": \"OK\"\n" +
                                                      "}");
    }

    @Test
    public void testDownloadSpecificVersionArtifact() throws Exception {
        doReturn(new LinkedHashMap<Artifact, String>() {
            {
                put(cdecArtifact, "2.10.5");
                put(installManagerArtifact, "1.0.1");
            }
        }).when(mockInstallationManager).getUpdates(testCredentials.getToken());

        JacksonRepresentation<UserCredentials> userCredentialsRep = new JacksonRepresentation<>(testCredentials);
        String response = installationManagerService.download("cdec", "2.10.5", userCredentialsRep);
        assertEquals(getPrettyPrintingJson(response), "{\n" +
                                                      "  \"artifacts\": [{\n" +
                                                      "    \"artifact\": \"cdec\",\n" +
                                                      "    \"status\": \"SUCCESS\",\n" +
                                                      "    \"version\": \"2.10.5\"\n" +
                                                      "  }],\n" +
                                                      "  \"status\": \"OK\"\n" +
                                                      "}");                                                      
    }
    
    @Test
    public void testDownloadErrorIfUpdatesAbsent() throws Exception {
        doReturn(Collections.emptyMap()).when(mockInstallationManager).getUpdates(testCredentials.getToken());

        JacksonRepresentation<UserCredentials> userCredentialsRep = new JacksonRepresentation<>(testCredentials);
        
        String response = installationManagerService.download(userCredentialsRep);
        assertEquals(getPrettyPrintingJson(response), "{\n" +
                                                      "  \"artifacts\": [],\n" +
                                                      "  \"status\": \"OK\"\n" +
                                                      "}");

        response = installationManagerService.download("cdec", userCredentialsRep);
        assertEquals(getPrettyPrintingJson(response), "{\n" +
                                                      "  \"message\": \"There is no any version of artifact 'cdec'\",\n" +
                                                      "  \"status\": \"ERROR\"\n" +
                                                      "}");

        response = installationManagerService.download("unknown", userCredentialsRep);
        assertEquals(getPrettyPrintingJson(response), "{\n" +
                                                      "  \"message\": \"Artifact 'unknown' not found\",\n" +
                                                      "  \"status\": \"ERROR\"\n" +
                                                      "}");
    }
    
    @Test
    public void testDownloadErrorIfSpecificVertionArtifactAbsent() throws Exception {
        doReturn(new LinkedHashMap<Artifact, String>() {
            {
                put(cdecArtifact, "2.10.5");
            }
        }).when(mockInstallationManager).getUpdates(testCredentials.getToken());

        doThrow(new ArtifactNotFoundException("cdec", "2.10.4")).when(mockInstallationManager).download(testCredentials.getToken(), cdecArtifact, "2.10.4");

        JacksonRepresentation<UserCredentials> userCredentialsRep = new JacksonRepresentation<>(testCredentials);
        String response = installationManagerService.download("cdec", "2.10.4", userCredentialsRep);
        assertEquals(getPrettyPrintingJson(response), "{\n" +
                                                      "  \"message\": \"Artifact 'cdec' version '2.10.4' not found\",\n" +
                                                      "  \"status\": \"ERROR\"\n" +
                                                      "}");        
    }
    
    @Test
    public void testDownloadErrorIfAuthenticationFailed() throws Exception {
        when(mockInstallationManager.getUpdates(anyString())).thenThrow(new AuthenticationException());        
        JacksonRepresentation<UserCredentials> userCredentialsRep = new JacksonRepresentation<>(testCredentials);
        
        String response = installationManagerService.download(userCredentialsRep);
        assertEquals(getPrettyPrintingJson(response), "{\n" +
                                                      "  \"message\": \"Authentication error. Authentication token might be expired or invalid.\",\n" +
                                                      "  \"status\": \"ERROR\"\n" +
                                                      "}");
        
        doThrow(new AuthenticationException()).when(mockInstallationManager).download(testCredentials.getToken(), cdecArtifact, "2.10.5");
        response = installationManagerService.download("cdec", "2.10.5", userCredentialsRep);
        assertEquals(getPrettyPrintingJson(response), "{\n" +
                                                      "  \"message\": \"Authentication error. Authentication token might be expired or invalid.\",\n" +
                                                      "  \"status\": \"ERROR\"\n" +
                                                      "}");
    }
    
    @Test
    public void testDownloadErrorIfSubscriptionVerificationFailed() throws Exception {
        when(mockInstallationManager.getUpdates(anyString())).thenThrow(new IllegalStateException("Valid subscription is required to download cdec"));
        
        JacksonRepresentation<UserCredentials> userCredentialsRep = new JacksonRepresentation<>(testCredentials);
        String response = installationManagerService.download("cdec", userCredentialsRep);
        assertEquals(getPrettyPrintingJson(response), "{\n" +
                                                      "  \"message\": \"Valid subscription is required to download cdec\",\n" +
                                                      "  \"status\": \"ERROR\"\n" +
                                                      "}");
    }
}