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
package org.eclipse.che.ide.ext.bitbucket.shared;


import org.eclipse.che.dto.shared.DTO;

import java.util.List;

/**
 * Represents a Bitbucket Server pull request.
 *
 * @author Igor Vinokur
 */
@DTO
public interface BitbucketServerPullRequest {

    int getId();

    void setId(int id);

    String getTitle();

    void setTitle(String title);

    BitbucketServerPullRequest withTitle(String title);

    String getDescription();

    void setDescription(String description);

    BitbucketServerPullRequest withDescription(String description);

    State getState();

    void setState(State state);

    BitbucketServerPullRequestLinks getLinks();

    void setLinks(BitbucketServerPullRequestLinks links);

    BitbucketServerAuthor getAuthor();

    void setAuthor(BitbucketServerAuthor author);

    BitbucketServerPullRequestRef getFromRef();

    void setFromRef(BitbucketServerPullRequestRef fromRef);

    BitbucketServerPullRequestRef getToRef();

    void setToRef(BitbucketServerPullRequestRef fromRef);

    enum State {
        OPEN,
        DECLINED,
        MERGED
    }

    @DTO
    interface BitbucketServerPullRequestLinks {
        List<BitbucketLink> getSelf();

        void setSelf(List<BitbucketLink> self);
    }

    @DTO
    interface BitbucketServerAuthor {
        BitbucketServerUser getUser();

        void setUser(BitbucketServerUser user);
    }

    @DTO
    interface BitbucketServerPullRequestRef {
        String getId();

        void setId(String id);

        String getDisplayId();

        void setDisplayId(String displayId);

        String getLatestCommit();

        void setLatestCommit(String latestCommit);

        BitbucketServerRepository getRepository();

        void setRepository(BitbucketServerRepository repository);
    }

}
