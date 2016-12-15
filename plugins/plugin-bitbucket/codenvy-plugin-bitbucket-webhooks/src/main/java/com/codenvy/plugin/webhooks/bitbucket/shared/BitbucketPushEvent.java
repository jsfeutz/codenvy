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
package com.codenvy.plugin.webhooks.bitbucket.shared;

import org.eclipse.che.dto.shared.DTO;

import java.util.List;

@DTO
public interface BitbucketPushEvent {

    BitbucketServerRepository getRepository();

    void setRepository(BitbucketServerRepository repository);

    BitbucketPushEvent withRepository(BitbucketServerRepository repository);

    List<RefChanges> getRefChanges();

    void setRefChanges(List<RefChanges> refChanges);

    BitbucketPushEvent withRefChanges(List<RefChanges> refChanges);

    @DTO
    interface BitbucketServerRepository {
        String getName();

        void setName(String name);

        BitbucketServerProject getProject();

        void setProject(BitbucketServerProject project);

        BitbucketServerRepository withProject(BitbucketServerProject project);
    }

    @DTO
    interface BitbucketServerProject {
        BitbucketServerOwner getOwner();

        void setOwner(BitbucketServerOwner owner);

        BitbucketServerProject withOwner(BitbucketServerOwner owner);
    }

    @DTO
    interface BitbucketServerOwner {
        String getName();

        void setName(String name);

        BitbucketServerOwner withName(String name);
    }

    @DTO
    interface RefChanges {
        String getRefId();

        void setRefId(String refId);

        String getFromHash();

        void setFromHash(String fromHash);

        String getToHash();

        void setToHash(String toHash);

        String getType();

        void setType(String type);
    }
}


