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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import com.codenvy.plugin.webhooks.AuthConnection;
import com.codenvy.plugin.webhooks.FactoryConnection;
import com.codenvy.plugin.webhooks.BaseWebhookService;
import com.codenvy.plugin.webhooks.bitbucket.shared.BitbucketPushEvent;
import com.codenvy.plugin.webhooks.bitbucket.shared.BitbucketWebhookEvent;
import com.codenvy.plugin.webhooks.connectors.Connector;

import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.shared.dto.Link;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.dto.server.DtoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Api(value = "/bitbucket-webhook", description = "Bitbucket webhooks handler")
@Path("/bitbucket-webhook")
public class BitbucketWebhookService extends BaseWebhookService {

    private static final Logger LOG = LoggerFactory.getLogger(BitbucketWebhookService.class);

    private static final String BITBUCKET_WEBHOOKS_PROPERTIES_FILENAME = "bitbucket-webhooks.properties";
    private final String bitbucketEndpoint;

    @Inject
    public BitbucketWebhookService(final AuthConnection authConnection,
                                   final FactoryConnection factoryConnection,
                                   @Named("bitbucket.endpoint") String bitbucketEndpoint) {
        super(authConnection, factoryConnection);
        this.bitbucketEndpoint = bitbucketEndpoint.endsWith("/") ? bitbucketEndpoint.substring(0, bitbucketEndpoint.length() - 1)
                                                                 : bitbucketEndpoint;
    }

    @ApiOperation(value = "Handle GitHub webhook events", response = Response.class)
    @ApiResponses({@ApiResponse(code = 200, message = "OK"),
                   @ApiResponse(code = 202, message = "The request has been accepted for processing, but the processing has not been completed."),
                   @ApiResponse(code = 500, message = "Internal Server Error")})
    @POST
    @Consumes(APPLICATION_JSON)
    public Response handleBitbucketWebhookEvent(@ApiParam(value = "New contribution", required = true)
                                                @Context HttpServletRequest request) throws ServerException {
        Response response = Response.ok().build();
        try (ServletInputStream inputStream = request.getInputStream()) {
            if (inputStream != null) {
                final BitbucketPushEvent event = DtoFactory.getInstance().createDtoFromJson(inputStream, BitbucketPushEvent.class);
                String action = event.getRefChanges().get(0).getType();
                switch (action.toLowerCase()) {
                    case "update":
                        handlePushEvent(event);
                        break;
                    case "merged":
                        handlePullRequestMergedEvent(event);
                        break;
                    default:
                        response = Response.accepted(new GenericEntity<>("Bitbucket message \'" + action +
                                                                         "\' received. It isn't intended to be processed.", String.class))
                                           .build();
                        break;
                }
            }
        } catch (IOException e) {
            LOG.error(e.getLocalizedMessage());
            throw new ServerException(e.getLocalizedMessage());
        }

        return response;
    }

    /**
     * Handle Bitbucket {@link BitbucketWebhookEvent}
     *
     * @param event
     *         the push event to handle
     * @return HTTP 200 response if event was processed successfully
     * HTTP 202 response if event was processed partially
     * @throws ServerException
     */
    private void handlePushEvent(BitbucketPushEvent event) throws ServerException {
        LOG.debug("{}", event);

        // Set current Codenvy user
        EnvironmentContext.getCurrent().setSubject(new TokenSubject());

        // Get event data
        BitbucketPushEvent.BitbucketServerRepository repository = event.getRepository();
        final String htmlUrl = bitbucketEndpoint + "/users/" +
                               repository.getProject().getOwner().getName() +
                               "/repos/" + repository.getName();
        final String branch = event.getRefChanges().get(0).getRefId().substring(11);

        // Get factories id's that are configured in a webhook
        final Set<String> factoriesIDs = getWebhookConfiguredFactoriesIDs(htmlUrl);

        // Get factories that contain a project for given repository and branch
        final List<FactoryDto> factories = getFactoriesForRepositoryAndBranch(factoriesIDs, htmlUrl, branch);
        if (factories.isEmpty()) {
            throw new ServerException("No factory found for repository " + htmlUrl + " and branch " + branch);
        }

        for (FactoryDto f : factories) {
            // Get 'open factory' URL
            final Link factoryLink = f.getLink(FACTORY_URL_REL);
            if (factoryLink == null) {
                throw new ServerException("Factory " + f.getId() + " do not contain mandatory \'" + FACTORY_URL_REL + "\' link");
            }

            // Get connectors configured for the factory
            final List<Connector> connectors = getConnectors(f.getId());

            // Add factory link within third-party services
            connectors.forEach(connector -> connector.addFactoryLink(factoryLink.getHref()));
        }
    }

    /**
     * Handle GitHub {@link BitbucketWebhookEvent}
     *
     * @param event
     *         the pull request event to handle
     * @return HTTP 200 response if event was processed successfully
     * HTTP 202 response if event was processed partially
     * @throws ServerException
     */
    private void handlePullRequestMergedEvent(BitbucketPushEvent event) throws ServerException {
        LOG.debug("{}", event);

        // Set current Codenvy user
        EnvironmentContext.getCurrent().setSubject(new TokenSubject());

        // Get head repository data
        final String branch = event.getRefChanges().get(0).getRefId().substring(11);
        final String prHeadCommitId = event.getRefChanges().get(0).getFromHash();

        // Get base repository data
        BitbucketPushEvent.BitbucketServerRepository repository = event.getRepository();
        final String htmlUrl = bitbucketEndpoint + "/users/" +
                               repository.getProject().getOwner().getName() +
                               "/repos/" + repository.getName();

        // Get factories id's that are configured in a webhook
        final Set<String> factoriesIDs = getWebhookConfiguredFactoriesIDs(htmlUrl);

        // Get factories that contain a project for given repository and branch
        final List<FactoryDto> factories = getFactoriesForRepositoryAndBranch(factoriesIDs, htmlUrl, branch);
        if (factories.isEmpty()) {
            throw new ServerException("No factory found for branch " + branch);
        }

        for (FactoryDto f : factories) {
            // Update project into the factory with given repository and branch
            final FactoryDto updatedfactory =
                    updateProjectInFactory(f, htmlUrl, branch, htmlUrl, prHeadCommitId);

            // Persist updated factory
            updateFactory(updatedfactory);
        }
    }

    /**
     * Get factories configured in a webhook for given base repository
     * and contain a project for given head repository and head branch
     *
     * @param baseRepositoryHtmlUrl
     *         the URL of the repository for which a webhook is configured
     * @return the factories configured in a webhook and that contain a project that matches given repo and branch
     * @throws ServerException
     */
    private Set<String> getWebhookConfiguredFactoriesIDs(final String baseRepositoryHtmlUrl)
            throws ServerException {

        // Get webhook configured for given repository
        final Optional<BitbucketWebhook> webhook = getBitbucketWebhook(baseRepositoryHtmlUrl);

        final BitbucketWebhook w = webhook.orElseThrow(
                () -> new ServerException("No webhook configured for repository " + baseRepositoryHtmlUrl));

        // Get factory id's listed into the webhook
        return w.getFactoriesIds();
    }

    /**
     * Get webhook configured for a given repository
     *
     * @param repositoryUrl
     *         the URL of the repository
     * @return the webhook configured for the repository or null if no webhook is configured for this repository
     * @throws ServerException
     */
    private Optional<BitbucketWebhook> getBitbucketWebhook(String repositoryUrl) throws ServerException {
        List<BitbucketWebhook> webhooks = getBitbucketWebhooks();
        BitbucketWebhook webhook = null;
        for (BitbucketWebhook w : webhooks) {
            String webhookRepositoryUrl = w.getRepositoryUrl();
            if (repositoryUrl.equals(webhookRepositoryUrl)) {
                webhook = w;
            }
        }
        return Optional.ofNullable(webhook);
    }

    /**
     * Get all configured webhooks
     * <p>
     * GitHub webhook: [webhook-name]=[webhook-type],[repository-url],[factory-id];[factory-id];...;[factory-id]
     *
     * @return the list of all webhooks contained in GITHUB_WEBHOOKS_PROPERTIES_FILENAME properties fil
     */
    private static List<BitbucketWebhook> getBitbucketWebhooks() throws ServerException {
        List<BitbucketWebhook> webhooks = new ArrayList<>();
        Properties webhooksProperties = getProperties(BITBUCKET_WEBHOOKS_PROPERTIES_FILENAME);
        Set<String> keySet = webhooksProperties.stringPropertyNames();
        keySet.forEach(key -> {
            String value = webhooksProperties.getProperty(key);
            if (!isNullOrEmpty(value)) {
                String[] valueSplit = value.split(",");
                if (valueSplit.length == 3
                    && valueSplit[0].equals("bitbucket")) {
                    String[] factoriesIDs = valueSplit[2].split(";");
                    BitbucketWebhook bitbucketWebhook = new BitbucketWebhook(valueSplit[1], factoriesIDs);
                    webhooks.add(bitbucketWebhook);
                    LOG.debug("new BitbucketWebhook({})", value);
                }
            }
        });
        return webhooks;
    }
}
