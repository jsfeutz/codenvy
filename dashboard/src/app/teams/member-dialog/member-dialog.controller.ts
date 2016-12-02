/*
 *  [2015] - [2016] Codenvy, S.A.
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
'use strict';
import {CodenvyUser} from '../../../components/api/codenvy-user.factory';
import {CodenvyTeam} from '../../../components/api/codenvy-team.factory';
import {CodenvyTeamRoles} from '../../../components/api/codenvy-team-roles';

/**
 * @ngdoc controller
 * @name teams.member:MemberDialogController
 * @description This class is handling the controller for adding/editing members dialog.
 * @author Ann Shumilova
 */
export class MemberDialogController {
  /**
   * Team API interaction.
   */
  private codenvyTeam: CodenvyTeam;
  /**
   * User API interaction.
   */
  private codenvyUser: CodenvyUser;
  /**
   * Service for displaying dialogs.
   */
  private $mdDialog: angular.material.IDialogService;
  /**
   * Promises service.
   */
  private $q: ng.IQService;
  /**
   * Processing state of adding member.
   */
  private isProcessing: boolean;
  /**
   * Set of user roles info.
   */
  private roles: Array<any>;
  /**
   * Already added emails.
   */
  private emails: Array<string>;
  /**
   * Existing members.
   */
  private members: Array<any>;
  /**
   * Entered email address.
   */
  private email: string;
  /**
   * Controller that will handle callbacks.
   */
  private callbackController: any;

  /**
   * Member to be displayed, may be <code>null</code> if add new member is needed. (Comes from )
   */
  private member: any;
  /**
   * Dialog window title.
   */
  private title: string;
  /**
   * Title of operation button (Save or Add)
   */
  private buttonTitle: string;
  /**
   * Email validation error message.
   */
  private emailError: string;

  /**
   * Default constructor that is using resource
   * @ngInject for Dependency injection
   */
  constructor($mdDialog: angular.material.IDialogService, codenvyTeam: CodenvyTeam, codenvyUser: CodenvyUser, $q: ng.IQService) {
    this.$mdDialog = $mdDialog;
    this.codenvyTeam = codenvyTeam;
    this.codenvyUser = codenvyUser;
    this.$q = $q;

    this.isProcessing = false;
    this.roles = [];
    if (this.member) {
      this.title = 'Edit ' + this.member.name + ' roles';
      this.buttonTitle = 'Save';
      this.email = this.member.email;
      let roles = this.codenvyTeam.getRolesFromActions(this.member.permissions.actions);
      this.roles.push({'role' : CodenvyTeamRoles.MANAGE_WORKSPACES, 'allowed' : roles.indexOf(CodenvyTeamRoles.MANAGE_WORKSPACES) >= 0});
      this.roles.push({'role' : CodenvyTeamRoles.MANAGE_TEAM, 'allowed' : roles.indexOf(CodenvyTeamRoles.MANAGE_TEAM) >= 0});
      this.roles.push({'role' : CodenvyTeamRoles.CREATE_WORKSPACES, 'allowed' : roles.indexOf(CodenvyTeamRoles.CREATE_WORKSPACES) >= 0});
      this.roles.push({'role' : CodenvyTeamRoles.MANAGE_RESOURCES, 'allowed' : roles.indexOf(CodenvyTeamRoles.MANAGE_RESOURCES) >= 0});
    } else {
      this.email = '';
      this.title = 'Invite user to collaborate';
      this.buttonTitle = 'Add';
      this.roles.push({'role' : CodenvyTeamRoles.MANAGE_WORKSPACES, 'allowed' : true});
      this.roles.push({'role' : CodenvyTeamRoles.MANAGE_TEAM, 'allowed' : false});
      this.roles.push({'role' : CodenvyTeamRoles.CREATE_WORKSPACES, 'allowed' : true});
      this.roles.push({'role' : CodenvyTeamRoles.MANAGE_RESOURCES, 'allowed' : false});
    }
    this.emails = [];
    this.members.forEach((member: any) => {
      this.emails.push(member.email);
    });
  }

  /**
   * Hides the add member dialog.
   */
  hide(): void {
    this.$mdDialog.hide();
  }

  /**
   * Checks whether entered email valid and is unique.
   *
   * @param value value with email(s) to check
   * @returns {boolean} true if pointed email(s) are valid and not in the list yet
   */
  isValidEmail(value: string): boolean {
    // return this.emails.indexOf(email) < 0;
    let emails = value.replace(/ /g, ',').split(',');
    for (let i = 0; i < emails.length; i++) {
      let email = emails[i];
      if (email.length > 0 && !/\S+@\S+\.\S+/.test(email)) {
        this.emailError = email + ' is invalid email address.';
        return false;
      }

      if (this.emails.indexOf(email) >= 0) {
        this.emailError = 'User with email ' + email + ' is already invited.';
        return false;
      }
    }
    return true;
  }

  /**
   * Adds new member.
   */
  addMembers(): void {
    let userRoles = this.getRoles();

    let emails = this.email.replace(/ /g, ',').split(',');
    // form the list of emails without duplicates and empty values:
    let resultEmails = emails.reduce((array: Array<string>, element: string) => {
      if (array.indexOf(element) < 0 && element.length > 0) {
        array.push(element);
      }
      return array;
    }, []);

    let promises = [];
    let users = [];
    resultEmails.forEach((email: string) => {
      promises.push(this.processUser(email, users));
    });

    this.$q.all(promises).then(() => {
      this.finishAdding(users, userRoles);
    });
  }

  processUser(email: string, users : Array<any>): ng.IPromise<any> {
    let deferred = this.$q.defer();
    let user = this.codenvyUser.getUserByAlias(email);
    if (user) {
      users.push(user);
      deferred.resolve();
    } else {
      user = {};
      user.email = email;
      this.isProcessing = true;
      this.codenvyUser.fetchUserByAlias(email).then(() => {
        users.push(this.codenvyUser.getUserByAlias(email));
        deferred.resolve();
      }, (error: any) => {
        users.push(user);
        deferred.resolve();
      });
    }
    return deferred.promise;
  }

  /**
   * Forms the list of user's roles by role's info from page.
   *
   * @returns {Array<any>} roles
   */
  getRoles(): Array<any> {
    let userRoles = [];
    this.roles.forEach((roleInfo: any) => {
      if (roleInfo.allowed) {
        userRoles.push(roleInfo.role);
      }
    });
    return userRoles;
  }

  /**
   * Handle edit member user's action.
   */
  editMember(): void {
    let userRoles = this.getRoles();
    this.member.permissions.actions = this.codenvyTeam.getActionsFromRoles(userRoles);
    this.callbackController.updateMember(this.member);
    this.hide();
  }

  /**
   * Finish adding user state.
   *
   * @param users users to be added
   * @param roles user's roles
   */
  finishAdding(users: Array<any>, roles: any): void {
    this.isProcessing = false;
    this.callbackController.addMembers(users, roles);
    this.hide();
  }

}
