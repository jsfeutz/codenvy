#!/bin/bash
# Copyright (c) 2016 Codenvy, S.A.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#

cli_pre_init() {
  GLOBAL_HOST_IP=${GLOBAL_HOST_IP:=$(docker_run --net host eclipse/che-ip:nightly)}
  DEFAULT_CODENVY_HOST=$GLOBAL_HOST_IP
  CODENVY_HOST=${CODENVY_HOST:-${DEFAULT_CODENVY_HOST}}
  CODENVY_PORT=80
}

cli_post_init() {
  CHE_SERVER_CONTAINER_NAME="${CHE_MINI_PRODUCT_NAME}_${CHE_MINI_PRODUCT_NAME}_1"
}

cli_parse () {
  debug $FUNCNAME
  COMMAND="cmd_$1"

  case $1 in
      init|config|start|stop|restart|backup|restore|info|offline|add-node|list-nodes|remove-node|destroy|download|rmi|upgrade|version|ssh|mount|action|test|compile|help)
      ;;
      *)
         error "You passed an unknown command."
         usage
         return 2
      ;;
  esac
}

get_boot_url() {
  echo "$CODENVY_HOST/api/"
}

get_display_url() {
  if ! is_docker_for_mac; then
    echo "http://${CODENVY_HOST}"
  else
    echo "http://localhost"
  fi
}


cmd_backup_extra_args() {
  # if windows we backup data volume
  if has_docker_for_windows_client; then
    echo " -v codenvy-postgresql-volume:/root${CHE_CONTAINER_ROOT}/data/postgres "
  else
    echo ""
  fi
}

cmd_destroy_post_action() {
  if has_docker_for_windows_client; then
    docker volume rm codenvy-postgresql-volume > /dev/null 2>&1  || true
  fi
}

cmd_restore_pre_action() {
  if has_docker_for_windows_client; then
    log "docker volume rm codenvy-postgresql-volume >> \"${LOGS}\" 2>&1 || true"
    docker volume rm codenvy-postgresql-volume >> "${LOGS}" 2>&1 || true
    log "docker volume create --name=codenvy-postgresql-volume >> \"${LOGS}\""
    docker volume create --name=codenvy-postgresql-volume >> "${LOGS}"
  fi
}

cmd_restore_extra_args() {
  if has_docker_for_windows_client; then
    echo " -v codenvy-postgresql-volume:/root${CHE_CONTAINER_ROOT}/instance/data/postgres "
  else
    echo ""
  fi
}

server_is_booted_extra_check() {
  # Total hack - having to restart haproxy for some reason on windows
  if is_docker_for_windows || is_docker_for_mac; then
    log "docker restart codenvy_haproxy_1 >> \"${LOGS}\" 2>&1"
    docker restart codenvy_haproxy_1 >> "${LOGS}" 2>&1
  fi
}

cmd_init_reinit_pre_action() {
  sed -i'.bak' "s|#CODENVY_HOST=.*|CODENVY_HOST=${CODENVY_HOST}|" "${REFERENCE_CONTAINER_ENVIRONMENT_FILE}"
  sed -i'.bak' "s|#CODENVY_SWARM_NODES=.*|CODENVY_SWARM_NODES=${CODENVY_HOST}:23750|" "${REFERENCE_CONTAINER_ENVIRONMENT_FILE}"

  # For testing purposes only
  #HTTP_PROXY=8.8.8.8
  #HTTPS_PROXY=http://4.4.4.4:9090
  #NO_PROXY="locahost,127.0.0.1"

  if [[ ! ${HTTP_PROXY} = "" ]]; then
    sed -i'.bak' "s|#${CHE_PRODUCT_NAME}_HTTP_PROXY_FOR_${CHE_PRODUCT_NAME}=.*|${CHE_PRODUCT_NAME}_HTTP_PROXY_FOR_${CHE_PRODUCT_NAME}=${HTTP_PROXY}|" "${REFERENCE_CONTAINER_ENVIRONMENT_FILE}"
    sed -i'.bak' "s|#${CHE_PRODUCT_NAME}_HTTP_PROXY_FOR_${CHE_PRODUCT_NAME}_WORKSPACES=.*|${CHE_PRODUCT_NAME}_HTTP_PROXY_FOR_${CHE_PRODUCT_NAME}_WORKSPACES=${HTTP_PROXY}|" "${REFERENCE_CONTAINER_ENVIRONMENT_FILE}"
  fi
  if [[ ! ${HTTPS_PROXY} = "" ]]; then
    sed -i'.bak' "s|#${CHE_PRODUCT_NAME}_HTTPS_PROXY_FOR_${CHE_PRODUCT_NAME}=.*|${CHE_PRODUCT_NAME}_HTTPS_PROXY_FOR_${CHE_PRODUCT_NAME}=${HTTPS_PROXY}|" "${REFERENCE_CONTAINER_ENVIRONMENT_FILE}"
    sed -i'.bak' "s|#${CHE_PRODUCT_NAME}_HTTPS_PROXY_FOR_${CHE_PRODUCT_NAME}_WORKSPACES=.*|${CHE_PRODUCT_NAME}_HTTPS_PROXY_FOR_${CHE_PRODUCT_NAME}_WORKSPACES=${HTTPS_PROXY}|" "${REFERENCE_CONTAINER_ENVIRONMENT_FILE}"
  fi
  if [[ ! ${HTTP_PROXY} = "" ]] ||
     [[ ! ${HTTPS_PROXY} = "" ]]; then
    #
    # NOTE --- Notice that if no proxy is set, we must append 'codenvy-swarm' to this for docker networking
    #
    sed -i'.bak' "s|#${CHE_PRODUCT_NAME}_NO_PROXY_FOR_${CHE_PRODUCT_NAME}=.*|${CHE_PRODUCT_NAME}_NO_PROXY_FOR_${CHE_PRODUCT_NAME}=127.0.0.1,localhost,${NO_PROXY},codenvy-swarm,${CODENVY_HOST}|" "${REFERENCE_CONTAINER_ENVIRONMENT_FILE}"
    sed -i'.bak' "s|#${CHE_PRODUCT_NAME}_NO_PROXY_FOR_${CHE_PRODUCT_NAME}_WORKSPACES=.*|${CHE_PRODUCT_NAME}_NO_PROXY_FOR_${CHE_PRODUCT_NAME}_WORKSPACES=127.0.0.1,localhost,${NO_PROXY},${CODENVY_HOST}|" "${REFERENCE_CONTAINER_ENVIRONMENT_FILE}"
  fi
}

cmd_start_check_ports() {

  # If dev mode is on, then we also need to check the debug port set by the user for availability
  if [ "${CHE_DEVELOPMENT_MODE}" = "on" ]; then
    USER_DEBUG_PORT=$(docker_run --env-file="${REFERENCE_CONTAINER_ENVIRONMENT_FILE}" alpine sh -c 'echo $CODENVY_DEBUG_PORT')

    if [[ "$USER_DEBUG_PORT" = "" ]]; then
      # If the user has not set a debug port, then use the default
      CODENVY_DEBUG_PORT=8000
    else 
      # Otherwise, this is the value set by the user
      CODENVY_DEBUG_PORT=$USER_DEBUG_PORT
    fi
  fi

  text   "         port 80 (http):       $(port_open 80 && echo "${GREEN}[AVAILABLE]${NC}" || echo "${RED}[ALREADY IN USE]${NC}") \n"
  text   "         port 443 (https):     $(port_open 443 && echo "${GREEN}[AVAILABLE]${NC}" || echo "${RED}[ALREADY IN USE]${NC}") \n"
  text   "         port 5000 (registry): $(port_open 5000 && echo "${GREEN}[AVAILABLE]${NC}" || echo "${RED}[ALREADY IN USE]${NC}") \n"
  if [ "${CHE_DEVELOPMENT_MODE}" = "on" ]; then
    text   "         port ${CODENVY_DEBUG_PORT} (debug):       $(port_open ${CODENVY_DEBUG_PORT} && echo "${GREEN}[AVAILABLE]${NC}" || echo "${RED}[ALREADY IN USE]${NC}") \n"
    text   "         port 9000 (lighttpd): $(port_open 9000 && echo "${GREEN}[AVAILABLE]${NC}" || echo "${RED}[ALREADY IN USE]${NC}") \n"
  fi

  if ! $(port_open 80) || ! $(port_open 443) || ! $(port_open 5000); then
    echo ""
    error "Ports required to run $CHE_MINI_PRODUCT_NAME are used by another program."
    return 1;
  fi
  if [ "${CHE_DEVELOPMENT_MODE}" = "on" ]; then
    if ! $(port_open ${CODENVY_DEBUG_PORT}); then
      echo ""
      error "Ports required to run $CHE_MINI_PRODUCT_NAME are used by another program."
      return 1;
    fi
  fi
}

cmd_config_post_action() {
  # If this is windows, we need to add a special volume for postgres
  if has_docker_for_windows_client; then
    sed "s|^.*postgresql\/data.*$|\ \ \ \ \ \ \-\ \'codenvy-postgresql-volume\:\/var\/lib\/postgresql\/data\:Z\'|" -i "${REFERENCE_CONTAINER_COMPOSE_FILE}"

    echo "" >> "${REFERENCE_CONTAINER_COMPOSE_FILE}"
    echo "volumes:" >> "${REFERENCE_CONTAINER_COMPOSE_FILE}"
    echo "  codenvy-postgresql-volume:" >> "${REFERENCE_CONTAINER_COMPOSE_FILE}"
    echo "     external: true" >> "${REFERENCE_CONTAINER_COMPOSE_FILE}"

    # This is a post-config creation, so we should also do this to the host version of the file
    sed "s|^.*postgresql\/data.*$|\ \ \ \ \ \ \-\ \'codenvy-postgresql-volume\:\/var\/lib\/postgresql\/data\:Z\'|" -i "${REFERENCE_CONTAINER_COMPOSE_HOST_FILE}"

    echo "" >> "${REFERENCE_CONTAINER_COMPOSE_HOST_FILE}"
    echo "volumes:" >> "${REFERENCE_CONTAINER_COMPOSE_HOST_FILE}"
    echo "  codenvy-postgresql-volume:" >> "${REFERENCE_CONTAINER_COMPOSE_HOST_FILE}"
    echo "     external: true" >> "${REFERENCE_CONTAINER_COMPOSE_HOST_FILE}"

    # On Windows, it is not possible to volume mount postgres data folder directly
    # This creates a named volume which will store postgres data in docker for win VM
    # TODO - in future, we can write synchronizer utility to copy data from win VM to host
    log "docker volume create --name=codenvy-postgresql-volume >> \"${LOGS}\""
    docker volume create --name=codenvy-postgresql-volume >> "${LOGS}"
  fi
}

# Runs puppet image to generate ${CHE_FORMAL_PRODUCT_NAME} configuration
generate_configuration_with_puppet() {
  debug $FUNCNAME

  if is_docker_for_windows; then
    POSTGRES_ENV_FILE=$(convert_posix_to_windows "${CHE_HOST_INSTANCE}/config/postgres/postgres.env")
    CODENVY_ENV_FILE=$(convert_posix_to_windows "${CHE_HOST_INSTANCE}/config/codenvy/$CHE_MINI_PRODUCT_NAME.env")
  else
    POSTGRES_ENV_FILE="${CHE_HOST_INSTANCE}/config/postgres/postgres.env"
    CODENVY_ENV_FILE="${CHE_HOST_INSTANCE}/config/codenvy/$CHE_MINI_PRODUCT_NAME.env"
  fi

  if [ "${CHE_DEVELOPMENT_MODE}" = "on" ]; then
  # Note - bug in docker requires relative path for env, not absolute
  GENERATE_CONFIG_COMMAND="docker_run \
                  --env-file=\"${REFERENCE_CONTAINER_ENVIRONMENT_FILE}\" \
                  --env-file=/version/$CHE_VERSION/images \
                  -v \"${CHE_HOST_INSTANCE}\":/opt/${CHE_MINI_PRODUCT_NAME}:rw \
                  -v \"${CHE_HOST_DEVELOPMENT_REPO}/dockerfiles/init/manifests\":/etc/puppet/manifests:ro \
                  -v \"${CHE_HOST_DEVELOPMENT_REPO}/dockerfiles/init/modules\":/etc/puppet/modules:ro \
                  -e \"POSTGRES_ENV_FILE=${POSTGRES_ENV_FILE}\" \
                  -e \"CHE_CONTAINER_ROOT=${CHE_CONTAINER_ROOT}\" \
                  -e \"CODENVY_ENV_FILE=${CODENVY_ENV_FILE}\" \
                  -e \"CHE_ENVIRONMENT=development\" \
                  -e \"CHE_CONFIG=${CHE_HOST_INSTANCE}\" \
                  -e \"CHE_INSTANCE=${CHE_HOST_INSTANCE}\" \
                  -e \"CHE_DEVELOPMENT_REPO=${CHE_HOST_DEVELOPMENT_REPO}\" \
                  -e \"CHE_ASSEMBLY=${CHE_ASSEMBLY}\" \
                  --entrypoint=/usr/bin/puppet \
                      $IMAGE_INIT \
                          apply --modulepath \
                                /etc/puppet/modules/ \
                                /etc/puppet/manifests/${CHE_MINI_PRODUCT_NAME}.pp --show_diff"
  else
  GENERATE_CONFIG_COMMAND="docker_run \
                  --env-file=\"${REFERENCE_CONTAINER_ENVIRONMENT_FILE}\" \
                  --env-file=/version/$CHE_VERSION/images \
                  -v \"${CHE_HOST_INSTANCE}\":/opt/${CHE_MINI_PRODUCT_NAME}:rw \
                  -e \"POSTGRES_ENV_FILE=${POSTGRES_ENV_FILE}\" \
                  -e \"CHE_CONTAINER_ROOT=${CHE_CONTAINER_ROOT}\" \
                  -e \"CODENVY_ENV_FILE=${CODENVY_ENV_FILE}\" \
                  -e \"CHE_ENVIRONMENT=production\" \
                  -e \"CHE_CONFIG=${CHE_HOST_INSTANCE}\" \
                  -e \"CHE_INSTANCE=${CHE_HOST_INSTANCE}\" \
                  --entrypoint=/usr/bin/puppet \
                      $IMAGE_INIT \
                          apply --modulepath \
                                /etc/puppet/modules/ \
                                /etc/puppet/manifests/${CHE_MINI_PRODUCT_NAME}.pp --show_diff >> \"${LOGS}\""
  fi

  log ${GENERATE_CONFIG_COMMAND}
  eval ${GENERATE_CONFIG_COMMAND}
}



