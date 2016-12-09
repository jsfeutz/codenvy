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

import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketLink;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketPullRequest;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketRepository;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerPullRequest;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerPullRequest.BitbucketServerPullRequestRef;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerRepository;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerUser;
import org.eclipse.che.ide.ext.bitbucket.shared.BitbucketUser;

import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.eclipse.che.ide.ext.bitbucket.shared.BitbucketServerRepository.*;

/**
 * Adapter for BitbucketServer DTOs.
 *
 * @author Igor Vinokur
 */
public class BitbucketServerDTOConverter {

    static BitbucketUser convertToBitbucketUser(BitbucketServerUser bitbucketServerUser) {
        BitbucketUser bitbucketUser = newDto(BitbucketUser.class);
        BitbucketUser.BitbucketUserLinks bitbucketUserLinks = newDto(BitbucketUser.BitbucketUserLinks.class);
        BitbucketLink bitbucketLink = newDto(BitbucketLink.class);

        bitbucketUser.setUsername(bitbucketServerUser.getName());
        bitbucketUser.setDisplayName(bitbucketServerUser.getDisplayName());
        bitbucketUser.setUuid(bitbucketServerUser.getId());

        bitbucketLink.setHref(bitbucketServerUser.getLinks().getSelf().get(0).getHref());
        bitbucketLink.setName(bitbucketServerUser.getLinks().getSelf().get(0).getName());

        bitbucketUserLinks.setSelf(bitbucketLink);
        bitbucketUser.setLinks(bitbucketUserLinks);

        return bitbucketUser;
    }

    static BitbucketRepository convertToBitbucketRepository(BitbucketServerRepository bitbucketServerRepository) {
        bitbucketServerRepository.setFullName(bitbucketServerRepository.getName());
        bitbucketServerRepository.setParent(bitbucketServerRepository.getOrigin() == null ? null :
                                            convertToBitbucketRepository(bitbucketServerRepository.getOrigin()));
        BitbucketServerUser owner = bitbucketServerRepository.getProject().getOwner();
        bitbucketServerRepository.setOwner(owner == null ? null : convertToBitbucketUser(owner));

        return bitbucketServerRepository;
    }

    static BitbucketPullRequest convertToBitbucketPullRequest(BitbucketServerPullRequest pullRequest) {
        BitbucketPullRequest bitbucketPullRequest = newDto(BitbucketPullRequest.class);
        bitbucketPullRequest.setDescription(pullRequest.getDescription());
        bitbucketPullRequest.setTitle(pullRequest.getTitle());
        bitbucketPullRequest.setAuthor(convertToBitbucketUser(pullRequest.getAuthor().getUser()));
        bitbucketPullRequest.setId(pullRequest.getId());
        BitbucketPullRequest.BitbucketPullRequestLocation location = newDto(BitbucketPullRequest.BitbucketPullRequestLocation.class);
        BitbucketPullRequest.BitbucketPullRequestBranch branch = newDto(BitbucketPullRequest.BitbucketPullRequestBranch.class);
        branch.setName(pullRequest.getFromRef().getDisplayId());
        branch.setName(pullRequest.getFromRef().getDisplayId());
        location.setBranch(branch);
        bitbucketPullRequest.setSource(location);
        bitbucketPullRequest.setState(BitbucketPullRequest.State.valueOf(pullRequest.getState().toString()));

        return bitbucketPullRequest;
    }

    static BitbucketServerPullRequest convertToBitbucketServerPullRequest(BitbucketPullRequest pullRequest) {

        BitbucketServerPullRequest bitbucketServerPullRequest = newDto(BitbucketServerPullRequest.class);
        bitbucketServerPullRequest.setId(pullRequest.getId());
        bitbucketServerPullRequest.setTitle(pullRequest.getTitle());
        bitbucketServerPullRequest.setDescription(pullRequest.getDescription());

        String[] source = pullRequest.getSource().getRepository().getFullName().split("/");
        BitbucketServerPullRequestRef pullRequestFromRef = newDto(BitbucketServerPullRequestRef.class);
        pullRequestFromRef.setId("refs/heads/" + pullRequest.getSource().getBranch().getName());
        BitbucketServerRepository fromRepository = newDto(BitbucketServerRepository.class);
        fromRepository.setSlug(source[1]);

        BitbucketServerProject projectFrom = newDto(BitbucketServerProject.class);
        projectFrom.setKey("~" + source[0]);
        fromRepository.setProject(projectFrom);
        pullRequestFromRef.setRepository(fromRepository);
        bitbucketServerPullRequest.setFromRef(pullRequestFromRef);

        String[] destination = pullRequest.getDestination().getRepository().getFullName().split("/");
        BitbucketServerPullRequestRef pullRequestToRef = newDto(BitbucketServerPullRequestRef.class);
        pullRequestToRef.setId("refs/heads/" + pullRequest.getDestination().getBranch().getName());
        BitbucketServerRepository toRepository = newDto(BitbucketServerRepository.class);
        toRepository.setSlug(destination[1]);

        BitbucketServerProject projectTo = newDto(BitbucketServerProject.class);
        projectTo.setKey("~" + destination[0]);
        toRepository.setProject(projectTo);
        pullRequestToRef.setRepository(toRepository);
        bitbucketServerPullRequest.setToRef(pullRequestToRef);

        return bitbucketServerPullRequest;
    }
}
