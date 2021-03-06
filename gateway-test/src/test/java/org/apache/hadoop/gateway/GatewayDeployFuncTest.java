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
package org.apache.hadoop.gateway;

import com.jayway.restassured.response.Response;
import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.commons.io.FileUtils;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.security.ldap.SimpleLdapDirectoryServer;
import org.apache.hadoop.gateway.services.DefaultGatewayServices;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.http.HttpStatus;
import org.apache.log4j.Appender;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

public class GatewayDeployFuncTest {

  private static Class RESOURCE_BASE_CLASS = GatewayDeployFuncTest.class;
  private static Logger LOG = LoggerFactory.getLogger( GatewayDeployFuncTest.class );

  public static Enumeration<Appender> appenders;
  public static GatewayConfig config;
  public static GatewayServer gateway;
  public static File gatewayHome;
  public static String gatewayUrl;
  public static String clusterUrl;
  public static SimpleLdapDirectoryServer ldap;
  public static TcpTransport ldapTransport;

  @BeforeClass
  public static void setupSuite() throws Exception {
    //appenders = NoOpAppender.setUp();
    setupLdap();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    ldap.stop( true );
    //FileUtils.deleteQuietly( new File( config.getGatewayHomeDir() ) );
    //NoOpAppender.tearDown( appenders );
  }

  public static void setupLdap() throws Exception {
    URL usersUrl = getResourceUrl( "users.ldif" );
    int port = findFreePort();
    ldapTransport = new TcpTransport( port );
    ldap = new SimpleLdapDirectoryServer( "dc=hadoop,dc=apache,dc=org", new File( usersUrl.toURI() ), ldapTransport );
    ldap.start();
    LOG.info( "LDAP port = " + ldapTransport.getPort() );
  }

  @Before
  public void setupGateway() throws Exception {

    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File gatewayDir = new File( targetDir, "gateway-home-" + UUID.randomUUID() );
    gatewayDir.mkdirs();
    gatewayHome = gatewayDir;

    GatewayTestConfig testConfig = new GatewayTestConfig();
    config = testConfig;
    testConfig.setGatewayHomeDir( gatewayDir.getAbsolutePath() );

    File topoDir = new File( testConfig.getGatewayTopologyDir() );
    topoDir.mkdirs();

    File deployDir = new File( testConfig.getGatewayDeploymentDir() );
    deployDir.mkdirs();

    DefaultGatewayServices srvcs = new DefaultGatewayServices();
    Map<String,String> options = new HashMap<String,String>();
    options.put( "persist-master", "false" );
    options.put( "master", "password" );
    try {
      srvcs.init( testConfig, options );
    } catch ( ServiceLifecycleException e ) {
      e.printStackTrace(); // I18N not required.
    }
    gateway = GatewayServer.startGateway( testConfig, srvcs );
    MatcherAssert.assertThat( "Failed to start gateway.", gateway, notNullValue() );

    LOG.info( "Gateway port = " + gateway.getAddresses()[ 0 ].getPort() );

    gatewayUrl = "http://localhost:" + gateway.getAddresses()[0].getPort() + "/" + config.getGatewayPath();
    clusterUrl = gatewayUrl + "/test-cluster";
  }

  @After
  public void cleanupGateway() throws Exception {
    gateway.stop();
    FileUtils.deleteQuietly( gatewayHome );
  }

  private static XMLTag createTopology() {
    XMLTag xml = XMLDoc.newDocument( true )
        .addRoot( "topology" )
        .addTag( "gateway" )

        .addTag( "provider" )
        .addTag( "role" ).addText( "authentication" )
        .addTag( "name" ).addText( "ShiroProvider" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm" )
        .addTag( "value" ).addText( "org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.userDnTemplate" )
        .addTag( "value" ).addText( "uid={0},ou=people,dc=hadoop,dc=apache,dc=org" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.url" )
        .addTag( "value" ).addText( "ldap://localhost:" + ldapTransport.getPort() ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "main.ldapRealm.contextFactory.authenticationMechanism" )
        .addTag( "value" ).addText( "simple" ).gotoParent()
        .addTag( "param" )
        .addTag( "name" ).addText( "urls./**" )
        .addTag( "value" ).addText( "authcBasic" ).gotoParent().gotoParent()
        .addTag( "provider" )
        .addTag( "role" ).addText( "identity-assertion" )
        .addTag( "enabled" ).addText( "true" )
        .addTag( "name" ).addText( "Default" ).gotoParent()
        .addTag( "provider" )
        .gotoRoot()
        .addTag( "service" )
        .addTag( "role" ).addText( "test-service-role" )
        .gotoRoot();
    return xml;
  }

  private static int findFreePort() throws IOException {
    ServerSocket socket = new ServerSocket(0);
    int port = socket.getLocalPort();
    socket.close();
    return port;
  }

  public static InputStream getResourceStream( String resource ) throws IOException {
    return getResourceUrl( resource ).openStream();
  }

  public static URL getResourceUrl( String resource ) {
    URL url = ClassLoader.getSystemResource( getResourceName( resource ) );
    assertThat( "Failed to find test resource " + resource, url, Matchers.notNullValue() );
    return url;
  }

  public static String getResourceName( String resource ) {
    return getResourceBaseName() + resource;
  }

  public static String getResourceBaseName() {
    return RESOURCE_BASE_CLASS.getName().replaceAll( "\\.", "/" ) + "/";
  }

  //@Test
  public void waitForManualTesting() throws IOException {
    System.in.read();
  }

  @Test( timeout = 30*1000 )
  public void testDeployRedeployUndeploy() throws InterruptedException, IOException {
    long sleep = 200;
    String username = "guest";
    String password = "guest-password";
    String serviceUrl =  clusterUrl + "/test-service-path/test-service-resource";
    long topoTimestampBefore, topoTimestampAfter;

    File topoDir = new File( config.getGatewayTopologyDir() );
    File deployDir = new File( config.getGatewayDeploymentDir() );
    File warDir = null;

    // Make sure deployment directory is empty.
    assertThat( topoDir.listFiles().length, is( 0 ) );
    assertThat( deployDir.listFiles().length, is( 0 ) );

    File descriptor = writeTestTopology( "test-cluster", createTopology() );
    long writeTime = System.currentTimeMillis();

    warDir = waitForFiles( deployDir, "test-cluster.war\\.[0-9A-Fa-f]+", 1, 0, sleep );
    for( File webInfDir : warDir.listFiles() ) {
      waitForFiles( webInfDir, ".*", 4, 0, sleep );
    }
    waitForAccess( serviceUrl, username, password, sleep );

    // Wait to make sure a second has passed to ensure the the file timestamps are different.
    waitForElapsed( writeTime, 1000, 100 );

    // Redeploy and make sure the timestamp is updated.
    topoTimestampBefore = descriptor.lastModified();
    GatewayServer.redeployTopologies( null );
    writeTime = System.currentTimeMillis();
    topoTimestampAfter = descriptor.lastModified();
    assertThat( topoTimestampAfter, greaterThan( topoTimestampBefore ) );

    // Check to make sure there are two war directories with the same root.
    warDir = waitForFiles( deployDir, "test-cluster.war\\.[0-9A-Fa-f]+", 2, 1, sleep );
    for( File webInfDir : warDir.listFiles() ) {
      waitForFiles( webInfDir, ".*", 4, 0, sleep );
    }
    waitForAccess( serviceUrl, username, password, sleep );

    // Wait to make sure a second has passed to ensure the the file timestamps are different.
    waitForElapsed( writeTime, 1000, 100 );

    // Redeploy and make sure the timestamp is updated.
    topoTimestampBefore = descriptor.lastModified();
    GatewayServer.redeployTopologies( "test-cluster" );
    writeTime = System.currentTimeMillis();
    topoTimestampAfter = descriptor.lastModified();
    assertThat( topoTimestampAfter, greaterThan( topoTimestampBefore ) );

    // Check to make sure there are two war directories with the same root.
    warDir = waitForFiles( deployDir, "test-cluster.war\\.[0-9A-Fa-f]+", 3, 2, sleep );
    for( File webInfDir : warDir.listFiles() ) {
      waitForFiles( webInfDir, ".*", 4, 0, sleep );
    }
    waitForAccess( serviceUrl, username, password, sleep );

    // Delete the test topology.
    assertThat( "Failed to delete the topology file.", descriptor.delete(), is( true ) );

    // Wait to make sure a second has passed to ensure the the file timestamps are different.
    waitForElapsed( writeTime, 1000, 100 );

    waitForFiles( deployDir, ".*", 0, -1, sleep );

    // Wait a bit more to make sure undeployment finished.
    Thread.sleep( sleep );

    // Make sure the test topology is not accessible.
    given().auth().preemptive().basic( username, password )
        .expect().statusCode( HttpStatus.SC_NOT_FOUND )
        .when().get( serviceUrl );

    // Make sure deployment directory is empty.
    assertThat( topoDir.listFiles().length, is( 0 ) );
    assertThat( deployDir.listFiles().length, is( 0 ) );
  }

  private void waitForElapsed( long from, long total, long sleep ) throws InterruptedException {
    while( System.currentTimeMillis() - from < total ) {
      Thread.sleep( sleep );
    }
  }

  private File writeTestTopology( String name, XMLTag xml ) throws IOException {
    // Create the test topology.
    File tempFile = new File( config.getGatewayTopologyDir(), name + ".xml." + UUID.randomUUID() );
    FileOutputStream stream = new FileOutputStream( tempFile );
    xml.toStream( stream );
    stream.close();
    File descriptor = new File( config.getGatewayTopologyDir(), name + ".xml" );
    tempFile.renameTo( descriptor );
    return descriptor;
  }

  private File waitForFiles( File dir, String pattern, int count, int index, long sleep ) throws InterruptedException {
    RegexDirFilter filter = new RegexDirFilter( pattern );
    while( true ) {
      File[] files = dir.listFiles( filter );
      if( files.length == count ) {
        return ( index < 0 ) ? null : files[ index ];
      }
      Thread.sleep( sleep );
    }
  }

  private void waitForAccess( String url, String username, String password, long sleep ) throws InterruptedException {
    while( true ) {
      Response response = given()
          .auth().preemptive().basic( username, password )
          .when().get( url ).andReturn();
      if( response.getStatusCode() == HttpStatus.SC_NOT_FOUND ) {
        Thread.sleep( sleep );
        continue;
      }
      assertThat( response.getContentType(), containsString( "text/plain" ) );
      assertThat( response.getBody().asString(), is( "test-service-response" ) );
      break;
    }
  }

  private class RegexDirFilter implements FilenameFilter {

    Pattern pattern;

    RegexDirFilter( String regex ) {
      pattern = Pattern.compile( regex );
    }

    @Override
    public boolean accept( File dir, String name ) {
      return pattern.matcher( name ).matches();
    }
  }

}
