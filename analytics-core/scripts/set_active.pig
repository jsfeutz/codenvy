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

IMPORT 'macros.pig';

f1 = loadResources('$LOG', '$FROM_DATE', '$TO_DATE', '$USER', '$WS');
f2 = filterByEvent(f1, '$EVENT');

-- extracts additional parameters
f = extractUrlParam(f2, 'FACTORY-URL', 'url');

c1 = FOREACH f GENERATE $FIELD AS targetField;
c = removeEmptyField(c1, 'targetField');

result = DISTINCT c;
