package org.eclipse.che.ide.ext.bitbucket.server;

import org.eclipse.che.api.auth.oauth.OAuthAuthorizationHeaderProvider;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketPullRequest;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketRepository;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketRepositoryFork;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerPullRequest;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerPullRequestsPage;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerRepository;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerUser;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketUser;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static javax.security.auth.callback.ConfirmationCallback.OK;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.eclipse.che.commons.json.JsonHelper.toJson;
import static org.eclipse.che.ide.ext.bitbucket.server.BitbucketServerDTOConverter.convertToBitbucketPullRequest;
import static org.eclipse.che.ide.ext.bitbucket.server.BitbucketServerDTOConverter.convertToBitbucketRepository;
import static org.eclipse.che.ide.ext.bitbucket.server.BitbucketServerDTOConverter.convertToBitbucketServerPullRequest;
import static org.eclipse.che.ide.ext.bitbucket.server.BitbucketServerDTOConverter.convertToBitbucketUser;
import static org.eclipse.che.ide.rest.HTTPStatus.CREATED;

public class BitbucketServerConnectionImpl extends BitbucketConnection {

    private final URLTemplates urlTemplates;
    private final String       bitbucketEndpoint;

    @Inject
    private OAuthAuthorizationHeaderProvider headerProvider;

    BitbucketServerConnectionImpl(String bitbucketEndpoint) {
        this.bitbucketEndpoint = bitbucketEndpoint;
        this.urlTemplates = new BitbucketHostedURLTemplates();
    }

    @Override
    BitbucketUser getUser(String username) throws ServerException, IOException, BitbucketException {
        //Need to check if user has permissions to retrieve full information from Bitbucket Server rest API.
        //Other requests will not fail with 403 error, but may show empty data.
        doRequest(GET, bitbucketEndpoint + "/rest/api/latest/users", OK, null, null);
        final String response = getJson(urlTemplates.userUrl(username), OK);
        return convertToBitbucketUser(parseJsonResponse(response, BitbucketServerUser.class));
    }

    @Override
    BitbucketRepository getRepository(@NotNull String owner, @NotNull String repositorySlug)
            throws IOException, BitbucketException, ServerException {
        final String response = getJson(urlTemplates.repositoryUrl(owner, repositorySlug), OK);
        return convertToBitbucketRepository(parseJsonResponse(response, BitbucketServerRepository.class));
    }

    @Override
    List<BitbucketPullRequest> getRepositoryPullRequests(@NotNull String owner, @NotNull String repositorySlug) throws ServerException,
                                                                                                                       IOException,
                                                                                                                       BitbucketException {
        final List<BitbucketPullRequest> pullRequests = new ArrayList<>();
        BitbucketServerPullRequestsPage pullRequestsPage = null;
        do {
            final String url = urlTemplates.pullrequestUrl(owner, repositorySlug) +
                               (pullRequestsPage != null ? "?start=" + String.valueOf(pullRequestsPage.getNextPageStart()) : "");

            pullRequestsPage = getBitbucketPage(url, BitbucketServerPullRequestsPage.class);
            pullRequests.addAll(pullRequestsPage.getValues()
                                                .stream()
                                                .map(BitbucketServerDTOConverter::convertToBitbucketPullRequest)
                                                .collect(Collectors.toList()));

        } while (!pullRequestsPage.isIsLastPage());

        return pullRequests;
    }

    @Override
    BitbucketPullRequest openPullRequest(@NotNull String owner,
                                         @NotNull String repositorySlug,
                                         @NotNull BitbucketPullRequest pullRequest) throws ServerException,
                                                                                           IOException,
                                                                                           BitbucketException {
        final String url = urlTemplates.pullrequestUrl(owner, repositorySlug);
        final String response = postJson(url, CREATED, toJson(convertToBitbucketServerPullRequest(pullRequest)));
        return convertToBitbucketPullRequest(parseJsonResponse(response, BitbucketServerPullRequest.class));
    }

    @Override
    public List<BitbucketRepository> getRepositoryForks(@NotNull String owner,
                                                        @NotNull String repositorySlug) throws IOException,
                                                                                               BitbucketException,
                                                                                               ServerException,
                                                                                               IllegalArgumentException {
        return null;
    }

    @Override
    public BitbucketRepositoryFork forkRepository(@NotNull String owner,
                                                  @NotNull String repositorySlug,
                                                  @NotNull String forkName,
                                                  boolean isForkPrivate) throws IOException,
                                                                                BitbucketException,
                                                                                ServerException {
        return null;
    }

    @Override
    void authorizeRequest(HttpURLConnection http, String requestMethod, String requestUrl) throws IOException {
        String authorizationHeader = headerProvider.getAuthorizationHeader("bitbucket-server",
                                                                           getUserId(),
                                                                           requestMethod,
                                                                           requestUrl,
                                                                           null);
        if (authorizationHeader != null) {
            http.setRequestProperty(AUTHORIZATION, authorizationHeader);
        }
    }
}
