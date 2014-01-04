/*
 *
 * CODENVY CONFIDENTIAL
 * ________________
 *
 * [2012] - [2013] Codenvy, S.A.
 * All Rights Reserved.
 * NOTICE: All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any. The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */

DEFINE MongoStorage com.codenvy.analytics.pig.udf.MongoStorage('$STORAGE_USER', '$STORAGE_PASSWORD');
DEFINE UUID com.codenvy.analytics.pig.udf.UUID;

IMPORT 'macros.pig';

l = loadResources('$LOG', '$FROM_DATE', '$TO_DATE', '$USER', '$WS');

a1 = filterByEvent(l, 'factory-created');
a2 = extractUrlParam(a1, 'FACTORY-URL', 'factory');
a3 = extractParam(a2, 'TYPE', 'projectType');
a4 = extractUrlParam(a3, 'REPO-URL', 'repository');
a5 = extractUrlParam(a4, 'ORG-ID', 'orgId');
a6 = extractUrlParam(a5, 'AFFILIATE-ID', 'affiliateId');

a = FOREACH a6 GENERATE dt, ws, user, factory, repository, (orgId == '}' ? '' : orgId) AS orgId,
                (affiliateId == '}' ? '' : affiliateId) AS affiliateId, projectType;

r1 = FOREACH a GENERATE dt, ws, user, factory, repository,
                orgId, affiliateId, projectType;
result = FOREACH r1 GENERATE UUID(), TOTUPLE('date', ToMilliSeconds(dt)), TOTUPLE('ws', ws), TOTUPLE('user', user),
                    TOTUPLE('orgId', orgId), TOTUPLE('affiliateId', affiliateId),
                    TOTUPLE('repository', repository), TOTUPLE('project_type', projectType), TOTUPLE('factory', factory);
STORE result INTO '$STORAGE_URL.$STORAGE_TABLE' USING MongoStorage;

