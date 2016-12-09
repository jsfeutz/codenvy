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
package org.eclipse.che.ide.ext.bitbucket.server;

import static java.net.URLDecoder.decode;
import static java.net.URLEncoder.encode;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static org.eclipse.che.commons.json.JsonHelper.toJson;
import static org.eclipse.che.commons.json.JsonNameConventions.CAMEL_UNDERSCORE;
import static org.eclipse.che.ide.MimeType.APPLICATION_FORM_URLENCODED;
import static org.eclipse.che.ide.MimeType.APPLICATION_JSON;
import static org.eclipse.che.ide.ext.bitbucket.server.BitbucketServerDTOConverter.*;
import static org.eclipse.che.ide.ext.bitbucket.server.BitbucketServerDTOConverter.convertToBitbucketUser;
import static org.eclipse.che.ide.ext.bitbucket.shared.Preconditions.checkArgument;
import static org.eclipse.che.ide.ext.bitbucket.shared.StringHelper.isNullOrEmpty;
import static org.eclipse.che.ide.rest.HTTPHeader.AUTHORIZATION;
import static org.eclipse.che.ide.rest.HTTPHeader.CONTENT_TYPE;
import static org.eclipse.che.ide.rest.HTTPMethod.GET;
import static org.eclipse.che.ide.rest.HTTPMethod.POST;
import static org.eclipse.che.ide.rest.HTTPStatus.CREATED;
import static org.eclipse.che.ide.rest.HTTPStatus.OK;

import org.eclipse.che.api.auth.oauth.OAuthAuthorizationHeaderProvider;
import org.eclipse.che.api.auth.oauth.OAuthTokenProvider;
import org.eclipse.che.api.auth.shared.dto.OAuthToken;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.HttpJsonRequestFactory;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.json.JsonHelper;
import org.eclipse.che.commons.json.JsonParseException;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketPullRequest;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketPullRequestsPage;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketRepositoriesPage;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketRepository;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketRepositoryFork;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerPullRequest;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerPullRequestsPage;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerRepository;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerUser;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketUser;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contains methods for retrieving data from BITBUCKET and processing it before sending to client side.
 *
 * @author Kevin Pollet
 * @author Igor Vinokur
 */
@Singleton
public class Bitbucket {

    private final OAuthTokenProvider               tokenProvider;
    private final OAuthAuthorizationHeaderProvider headerProvider;

    private String       bitbucketEndpoint;
    private URLTemplates urlTemplates;

    @Inject
    public Bitbucket(OAuthTokenProvider tokenProvider,
                     OAuthAuthorizationHeaderProvider headerProvider,
                     HttpJsonRequestFactory requestFactory,
                     @Named("che.api") String apiEndpoint) {

        this.tokenProvider = tokenProvider;
        this.headerProvider = headerProvider;

        String bitbucketEndpoint = "https://bitbucket.org";
        try {
            bitbucketEndpoint = requestFactory.fromUrl(apiEndpoint + "/bitbucket/endpoint")
                                              .useGetMethod()
                                              .request()
                                              .asString();
        } catch (Exception ignored) {
        }
        bitbucketEndpoint = bitbucketEndpoint.endsWith("/") ? bitbucketEndpoint.substring(0, bitbucketEndpoint.length() - 1)
                                                            : bitbucketEndpoint;

        this.bitbucketEndpoint = bitbucketEndpoint;

        this.urlTemplates = isBitbucketServer() ? new BitbucketServerURLTemplates(bitbucketEndpoint) : new BitbucketHostedURLTemplates();
    }

    /**
     * Get host url of bitbucket.
     */
    public String getEndpoint() throws ServerException {
        return bitbucketEndpoint;
    }

    /**
     * Get authorized user information.
     *
     * @return the {@link org.eclipse.che.ide.ext.bitbucket.shared.BitbucketUser}.
     * @throws java.io.IOException
     *         if any i/o errors occurs.
     * @throws BitbucketException
     *         if Bitbucket server return unexpected or error status for request.
     * @throws ServerException
     *         if any error occurs when parse.
     */
    public BitbucketUser getUser(String username) throws IOException, BitbucketException, ServerException {
        if (isBitbucketServer()) {
            //Need to check if user has permissions to retrieve full information from Bitbucket Server rest API.
            //Other requests will not fail with 403 error, but may show empty data.
            doRequest(GET, bitbucketEndpoint + "/rest/api/latest/users", OK, null, null);
            final String response = getJson(urlTemplates.userUrl(username), OK);
            return convertToBitbucketUser(parseJsonResponse(response, BitbucketServerUser.class));
        } else {
            final String response = getJson(urlTemplates.userUrl(null), OK);
            return parseJsonResponse(response, BitbucketUser.class);
        }
    }

    /**
     * Get Bitbucket repository information.
     *
     * @param owner
     *         the repository owner, cannot be {@code null}.
     * @param repositorySlug
     *         the repository name, cannot be {@code null}.
     * @return the {@link org.eclipse.che.ide.ext.bitbucket.shared.BitbucketRepository}.
     * @throws java.io.IOException
     *         if any i/o errors occurs.
     * @throws BitbucketException
     *         if Bitbucket server return unexpected or error status for request.
     * @throws ServerException
     *         if any error occurs when parse.
     * @throws java.lang.IllegalArgumentException
     *         if one parameter is not valid.
     */
    public BitbucketRepository getRepository(@NotNull final String owner, @NotNull final String repositorySlug)
            throws IOException, BitbucketException, ServerException, IllegalArgumentException {
        checkArgument(!isNullOrEmpty(owner), "owner");
        checkArgument(!isNullOrEmpty(repositorySlug), "repositorySlug");

        final String response = getJson(urlTemplates.repositoryUrl(owner, repositorySlug), OK);
        if (isBitbucketServer()) {
            return convertToBitbucketRepository(parseJsonResponse(response, BitbucketServerRepository.class));
        } else {
            return parseJsonResponse(response, BitbucketRepository.class);
        }
    }

    /**
     * Get Bitbucket repository forks.
     *
     * @param owner
     *         the repository owner, cannot be {@code null}.
     * @param repositorySlug
     *         the repository name, cannot be {@code null}.
     * @return the fork {@link org.eclipse.che.ide.ext.bitbucket.shared.BitbucketRepositories}.
     * @throws java.io.IOException
     *         if any i/o errors occurs.
     * @throws BitbucketException
     *         if Bitbucket server return unexpected or error status for request.
     * @throws ServerException
     *         if any error occurs when parse.
     * @throws java.lang.IllegalArgumentException
     *         if one parameter is not valid.
     */
    public List<BitbucketRepository> getRepositoryForks(@NotNull final String owner, @NotNull final String repositorySlug)
            throws IOException, BitbucketException, ServerException, IllegalArgumentException {
        checkArgument(!isNullOrEmpty(owner), "owner");
        checkArgument(!isNullOrEmpty(repositorySlug), "repositorySlug");

        final List<BitbucketRepository> repositories = new ArrayList<>();
        BitbucketRepositoriesPage repositoryPage = DtoFactory.getInstance().createDto(BitbucketRepositoriesPage.class);

        do {

            final String nextPageUrl = repositoryPage.getNext();
            final String url =
                    nextPageUrl == null ? urlTemplates.forksUrl(owner, repositorySlug) : nextPageUrl;
            repositoryPage = getBitbucketPage(url, BitbucketRepositoriesPage.class);
            repositories.addAll(repositoryPage.getValues());

        } while (repositoryPage.getNext() != null);

        return repositories;
    }

    /**
     * Fork a Bitbucket repository.
     *
     * @param owner
     *         the repository owner, cannot be {@code null}.
     * @param repositorySlug
     *         the repository name, cannot be {@code null}.
     * @param forkName
     *         the fork name, cannot be {@code null}.
     * @param isForkPrivate
     *         if the fork must be private.
     * @return the fork {@link org.eclipse.che.ide.ext.bitbucket.shared.BitbucketRepositoryFork}.
     * @throws IOException
     *         if any i/o errors occurs.
     * @throws BitbucketException
     *         if Bitbucket server return unexpected or error status for request.
     * @throws ServerException
     *         if any error occurs when parse.
     * @throws java.lang.IllegalArgumentException
     *         if one parameter is not valid.
     */
    public BitbucketRepositoryFork forkRepository(@NotNull final String owner,
                                                  @NotNull final String repositorySlug,
                                                  @NotNull final String forkName,
                                                  final boolean isForkPrivate) throws IOException, BitbucketException, ServerException {
        checkArgument(!isNullOrEmpty(owner), "owner");
        checkArgument(!isNullOrEmpty(repositorySlug), "repositorySlug");
        checkArgument(!isNullOrEmpty(forkName), "forkName");

        final String url = urlTemplates.forkRepositoryUrl(owner, repositorySlug);
        final String data = "name=" + encode(forkName, "UTF-8") + "&is_private=" + isForkPrivate;
        final String response = doRequest(POST, url, OK, APPLICATION_FORM_URLENCODED, data);
        return parseJsonResponse(response, BitbucketRepositoryFork.class);
    }


    /**
     * Get Bitbucket repository pull requests.
     *
     * @param owner
     *         the repositories owner, cannot be {@code null}.
     * @param repositorySlug
     *         the repository name, cannot be {@code null}.
     * @return the {@link org.eclipse.che.ide.ext.bitbucket.shared.BitbucketPullRequests}.
     * @throws IOException
     *         if any i/o errors occurs.
     * @throws BitbucketException
     *         if Bitbucket server return unexpected or error status for request.
     * @throws ServerException
     *         if any error occurs when parse.
     * @throws IllegalArgumentException
     *         if one parameter is not valid.
     */
    public List<BitbucketPullRequest> getRepositoryPullRequests(@NotNull final String owner, @NotNull final String repositorySlug)
            throws ServerException, IOException, BitbucketException {
        checkArgument(!isNullOrEmpty(owner), "owner");
        checkArgument(!isNullOrEmpty(repositorySlug), "repositorySlug");

        final List<BitbucketPullRequest> pullRequests = new ArrayList<>();
        if (isBitbucketServer()) {
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
        } else {
            BitbucketPullRequestsPage pullRequestsPage = DtoFactory.getInstance().createDto(BitbucketPullRequestsPage.class);

            do {
                final String nextPageUrl = pullRequestsPage.getNext();
                final String url = nextPageUrl == null ? urlTemplates.pullrequestUrl(owner, repositorySlug) : nextPageUrl;

                pullRequestsPage = getBitbucketPage(url, BitbucketPullRequestsPage.class);
                pullRequests.addAll(pullRequestsPage.getValues());

            } while (pullRequestsPage.getNext() != null);
        }

        return pullRequests;

    }

    /**
     * Open the given {@link org.eclipse.che.ide.ext.bitbucket.shared.BitbucketPullRequest}.
     *
     * @param owner
     *         the repository owner, cannot be {@code null}.
     * @param repositorySlug
     *         the repository name, cannot be {@code null}.
     * @param pullRequest
     *         the {@link org.eclipse.che.ide.ext.bitbucket.shared.BitbucketPullRequest} to open, cannot be {@code null}.
     * @return the opened {@link org.eclipse.che.ide.ext.bitbucket.shared.BitbucketPullRequest}.
     */
    public BitbucketPullRequest openPullRequest(@NotNull final String owner,
                                                @NotNull final String repositorySlug,
                                                @NotNull final BitbucketPullRequest pullRequest)
            throws ServerException, IOException, BitbucketException {
        checkArgument(!isNullOrEmpty(owner), "owner");
        checkArgument(!isNullOrEmpty(repositorySlug), "repositorySlug");
        checkArgument(pullRequest != null, "pullRequest");

        final String url = urlTemplates.pullrequestUrl(owner, repositorySlug);
        if (isBitbucketServer()) {
            final String response = postJson(url, CREATED, toJson(convertToBitbucketServerPullRequest(pullRequest)));
            return convertToBitbucketPullRequest(parseJsonResponse(response, BitbucketServerPullRequest.class));
        } else {
            final String response = postJson(url, CREATED, toJson(pullRequest, CAMEL_UNDERSCORE));
            return parseJsonResponse(response, BitbucketPullRequest.class);
        }
    }

    private boolean isBitbucketServer() {
        return !"https://bitbucket.org".equals(bitbucketEndpoint);
    }

    private <T> T getBitbucketPage(final String url,
                                   final Class<T> pageClass) throws IOException, BitbucketException, ServerException {
        final String response = getJson(url, OK);
        return parseJsonResponse(response, pageClass);
    }

    private String getJson(final String url, final int success) throws IOException, BitbucketException {
        return doRequest(GET, url, success, null, null);
    }

    private String postJson(final String url, final int success, final String data) throws IOException, BitbucketException {
        return doRequest(POST, url, success, APPLICATION_JSON, data);
    }

    private String doRequest(final String requestMethod,
                             final String requestUrl,
                             final int success,
                             final String contentType,
                             final String data) throws IOException, BitbucketException {
        HttpURLConnection http = null;

        try {

            http = (HttpURLConnection)new URL(requestUrl).openConnection();
            http.setInstanceFollowRedirects(false);
            http.setRequestMethod(requestMethod);

            final Map<String, String> requestParameters = new HashMap<>();
            if (data != null && APPLICATION_FORM_URLENCODED.equals(contentType)) {
                final String[] parameters = data.split("&");

                for (final String oneParameter : parameters) {
                    final String[] oneParameterKeyAndValue = oneParameter.split("=");
                    if (oneParameterKeyAndValue.length == 2) {
                        requestParameters.put(oneParameterKeyAndValue[0], decode(oneParameterKeyAndValue[1], "UTF-8"));
                    }
                }
            }

            if (isBitbucketServer()) {
                String authorizationHeader = headerProvider.getAuthorizationHeader("bitbucket-server",
                                                                                   getUserId(),
                                                                                   requestMethod,
                                                                                   requestUrl,
                                                                                   null);
                if (authorizationHeader != null) {
                    http.setRequestProperty(AUTHORIZATION, authorizationHeader);
                }
            } else {
                final OAuthToken token = tokenProvider.getToken("bitbucket", getUserId());
                if (token != null) {
                    http.setRequestProperty(AUTHORIZATION, "Bearer " + token.getToken());
                }
                http.setRequestProperty(ACCEPT, APPLICATION_JSON);
            }

            if (data != null && !data.isEmpty()) {
                http.setRequestProperty(CONTENT_TYPE, contentType);
                http.setDoOutput(true);

                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(http.getOutputStream()))) {
                    writer.write(data);
                }
            }

            if (http.getResponseCode() != success) {
                throw fault(http);
            }

            String result;
            try (InputStream input = http.getInputStream()) {
                result = readBody(input, http.getContentLength());
            }

            return result;

        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            return null;
        } finally {
            if (http != null) {
                http.disconnect();
            }
        }
    }

    private <O> O parseJsonResponse(final String json, final Class<O> clazz) throws ServerException {
        try {

            return JsonHelper.fromJson(json, clazz, null, CAMEL_UNDERSCORE);

        } catch (JsonParseException e) {
            throw new ServerException(e);
        }
    }

    private BitbucketException fault(final HttpURLConnection http) throws IOException {
        final int responseCode = http.getResponseCode();

        try (final InputStream stream = (responseCode >= 400 ? http.getErrorStream() : http.getInputStream())) {

            String body = null;
            if (stream != null) {
                final int length = http.getContentLength();
                body = readBody(stream, length);
            }

            return new BitbucketException(responseCode, body, http.getContentType());
        }
    }

    private String readBody(final InputStream input, final int contentLength) throws IOException {
        String body = null;
        if (contentLength > 0) {
            byte[] b = new byte[contentLength];
            int off = 0;
            int i;
            while ((i = input.read(b, off, contentLength - off)) > 0) {
                off += i;
            }
            body = new String(b);
        } else if (contentLength < 0) {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int point;
            while ((point = input.read(buf)) != -1) {
                bout.write(buf, 0, point);
            }
            body = bout.toString();
        }
        return body;
    }

    private String getUserId() {
        return EnvironmentContext.getCurrent().getSubject().getUserId();
    }
}
