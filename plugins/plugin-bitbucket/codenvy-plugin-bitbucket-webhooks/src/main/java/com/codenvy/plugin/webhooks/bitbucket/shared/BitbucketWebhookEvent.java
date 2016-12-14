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

@DTO
public interface BitbucketWebhookEvent {

    String getHttpUrl();

    void setHttpUrl(String httpUrl);

    BitbucketWebhookEvent withHttpUrl(String httpUrl);

    String getBranch();

    void setBranch(String branch);

    BitbucketWebhookEvent withBranch(String branch);

    String getCommitId();

    void setCommitId(String commitId);

    BitbucketWebhookEvent withCommitId(String commitId);

    String getAction();

    void setAction(String action);

    BitbucketWebhookEvent withAction(String action);
}
