/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.services;

import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.topology.impl.DefaultTopologyService;
import org.apache.hadoop.gateway.services.security.impl.DefaultAliasService;
import org.apache.hadoop.gateway.services.security.impl.DefaultCryptoService;
import org.apache.hadoop.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.hadoop.gateway.services.security.impl.CLIMasterService;
import org.apache.hadoop.gateway.topology.Provider;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CLIGatewayServices implements GatewayServices {

  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );

  private Map<String,Service> services = new HashMap<String, Service>();
  private CLIMasterService ms = null;
  private DefaultKeystoreService ks = null;

  public CLIGatewayServices() {
    super();
  }

  public void init(GatewayConfig config, Map<String,String> options) throws ServiceLifecycleException {
    ms = new CLIMasterService();
    ms.init(config, options);
    services.put("MasterService", ms);

    ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, options);
    services.put(KEYSTORE_SERVICE, ks);
    
    DefaultAliasService alias = new DefaultAliasService();
    alias.setKeystoreService(ks);
    alias.init(config, options);
    services.put(ALIAS_SERVICE, alias);

    DefaultCryptoService crypto = new DefaultCryptoService();
    crypto.setKeystoreService(ks);
    crypto.setAliasService(alias);
    crypto.init(config, options);
    services.put(CRYPTO_SERVICE, crypto);

    DefaultTopologyService tops = new DefaultTopologyService();
    tops.init(  config, options  );
    services.put(TOPOLOGY_SERVICE, tops);
  }
  
  public void start() throws ServiceLifecycleException {
    ms.start();

    ks.start();

    DefaultAliasService alias = (DefaultAliasService) services.get(ALIAS_SERVICE);
    alias.start();

    DefaultTopologyService tops = (DefaultTopologyService)services.get(TOPOLOGY_SERVICE);
    tops.start();
  }

  public void stop() throws ServiceLifecycleException {
    ms.stop();

    ks.stop();

    DefaultAliasService alias = (DefaultAliasService) services.get(ALIAS_SERVICE);
    alias.stop();

    DefaultTopologyService tops = (DefaultTopologyService)services.get(TOPOLOGY_SERVICE);
    tops.stop();
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.GatewayServices#getServiceNames()
   */
  @Override
  public Collection<String> getServiceNames() {
    return services.keySet();
  }
  
  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.GatewayServices#getService(java.lang.String)
   */
  @Override
  public <T> T getService(String serviceName) {
    return (T)services.get( serviceName );
  }

  @Override
  public String getRole() {
    return "Services";
  }

  @Override
  public String getName() {
    return "GatewayServices";
  }

  @Override
  public void initializeContribution(DeploymentContext context) {
  }

  @Override
  public void contributeProvider(DeploymentContext context, Provider provider) {
  }

  @Override
  public void contributeFilter(DeploymentContext context, Provider provider,
      org.apache.hadoop.gateway.topology.Service service,
      ResourceDescriptor resource, List<FilterParamDescriptor> params) {
  }

  @Override
  public void finalizeContribution(DeploymentContext context) {
  }
}
