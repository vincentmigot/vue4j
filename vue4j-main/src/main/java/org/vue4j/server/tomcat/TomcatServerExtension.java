/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.vue4j.server.tomcat;

/**
 *
 * @author vince
 */
public interface TomcatServerExtension {

    public void serverInit(TomcatServer server) throws Exception;
    
    public void serverStop(TomcatServer server) throws Exception;
    
}
