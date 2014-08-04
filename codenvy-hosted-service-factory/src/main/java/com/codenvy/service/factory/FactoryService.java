/*
 * CODENVY CONFIDENTIAL
 * __________________
 * 
 *  [2012] - [2014] Codenvy, S.A. 
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
package com.codenvy.service.factory;

import com.codenvy.api.account.server.SubscriptionService;
import com.codenvy.api.account.server.dao.AccountDao;
import com.codenvy.api.account.server.dao.Subscription;
import com.codenvy.api.core.ApiException;
import com.codenvy.api.core.ConflictException;
import com.codenvy.api.core.ServerException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

/**
 * Subscription of factories
 *
 * @author Sergii Kabashniuk
 * @author Eugene Voevodin
 */
@Singleton
public class FactoryService extends SubscriptionService {

    private final AccountDao accountDao;

    @Inject
    public FactoryService(AccountDao accountDao) {
        super("Factory", "Factory");
        this.accountDao = accountDao;
    }

    //fixme for now Factory supports only 1 active subscription per 1 account
    @Override
    public void beforeCreateSubscription(Subscription subscription) throws ApiException {
        final List<Subscription> allSubscriptions = accountDao.getSubscriptions(subscription.getAccountId());
        for (Subscription current : allSubscriptions) {
            if (getServiceId().equals(current.getServiceId())) {
                throw new ConflictException("Factory subscription already exists");
            }
        }


        final Calendar calendar = Calendar.getInstance();
        subscription.setStartDate(calendar.getTimeInMillis());
        if ("true".equals((subscription.getProperties().get("codenvy:trial")))) {
            calendar.add(Calendar.DAY_OF_YEAR, 7);
            subscription.setEndDate(calendar.getTimeInMillis());
            subscription.setState(Subscription.State.ACTIVE);
        } else {
            String tariffPlan;
            if (null == (tariffPlan = subscription.getProperties().get("TariffPlan"))) {
                throw new ConflictException("TariffPlan property not found");
            }

            switch (tariffPlan) {
                case "yearly":
                    calendar.add(Calendar.YEAR, 1);
                    break;
                case "monthly":
                    calendar.add(Calendar.MONTH, 1);
                    break;
                default:
                    throw new ConflictException("Unknown TariffPlan is used " + tariffPlan);
            }

            subscription.setEndDate(calendar.getTimeInMillis());
        }
    }

    @Override
    public void afterCreateSubscription(Subscription subscription) throws ApiException {
        //nothing to do
    }

    @Override
    public void onRemoveSubscription(Subscription subscription) {
        //nothing to do
    }

    @Override
    public void onCheckSubscription(Subscription subscription) {
        //nothing to do
    }

    @Override
    public void onUpdateSubscription(Subscription oldSubscription, Subscription newSubscription) {
        //nothing to do
    }

    @Override
    public double tarifficate(Subscription subscription) {
        return 0D;
    }
}