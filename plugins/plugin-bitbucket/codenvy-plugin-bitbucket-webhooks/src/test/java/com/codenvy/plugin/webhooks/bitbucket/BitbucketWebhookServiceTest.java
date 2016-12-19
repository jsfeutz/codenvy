/*
 *  [2012] - [2016] Codenvy, S.A.
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
package com.codenvy.plugin.webhooks.bitbucket;

import com.codenvy.plugin.webhooks.AuthConnection;
import com.codenvy.plugin.webhooks.FactoryConnection;
import com.codenvy.plugin.webhooks.UserConnection;
import com.codenvy.plugin.webhooks.bitbucket.shared.BitbucketPushEvent;
import com.codenvy.plugin.webhooks.bitbucket.shared.BitbucketPushEvent.BitbucketServerProject;
import com.codenvy.plugin.webhooks.bitbucket.shared.BitbucketPushEvent.BitbucketServerRepository;
import com.codenvy.plugin.webhooks.bitbucket.shared.BitbucketPushEvent.Changesets;
import com.codenvy.plugin.webhooks.bitbucket.shared.BitbucketPushEvent.RefChanges;
import com.codenvy.plugin.webhooks.bitbucket.shared.BitbucketPushEvent.Value;

import org.eclipse.che.api.auth.shared.dto.Token;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.user.shared.dto.UserDto;
import org.eclipse.che.dto.server.DtoFactory;
import org.everrest.assured.EverrestJetty;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static com.jayway.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.everrest.assured.JettyHttpServer.UNSECURE_PATH_SPEC;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Listeners(value = {EverrestJetty.class, MockitoTestNGListener.class})
public class BitbucketWebhookServiceTest {

    BitbucketWebhookService service;

    @BeforeClass
    public void setup() throws Exception {

        AuthConnection authConnection = mock(AuthConnection.class);
        FactoryConnection factoryConnection = mock(FactoryConnection.class);

        // Prepare userConnection
        UserConnection mockUserConnection = mock(UserConnection.class);
        UserDto mockUser = mock(UserDto.class);
        when(mockUser.getId()).thenReturn("userId");
        when(mockUserConnection.getCurrentUser()).thenReturn(mockUser);

        when(authConnection.authenticateUser("somebody@somemail.com", "somepwd")).thenReturn(newDto(Token.class).withValue("fakeToken"));

        service = spy(new BitbucketWebhookService(authConnection, factoryConnection));
    }

    @Test
    public void Tessdghdfhsdht() throws Exception {
        BitbucketPushEvent event = newDto(BitbucketPushEvent.class)
                .withRefChanges(singletonList(newDto(RefChanges.class).withToHash("hash commit")
                                                                      .withType("UPDATE")))
                .withChangesets(newDto(Changesets.class).withValues(
                        singletonList(newDto(Value.class).withToCommit(newDto(BitbucketPushEvent.ToCommit.class).withId("hash commit")
                                                                                                                .withMessage("message")))));
        service.handleBitbucketWebhookEvent(prepareRequest(event));

        verify(service).handlePushEvent(anyObject());
    }

    private HttpServletRequest prepareRequest(BitbucketPushEvent event) throws Exception {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(event.toString().getBytes(StandardCharsets.UTF_8));
        ServletInputStream fakeInputStream = new ServletInputStream() {
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }
        };
        when(mockRequest.getInputStream()).thenReturn(fakeInputStream);

        return mockRequest;
    }
}