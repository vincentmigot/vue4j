/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.vue4j.services;

import org.vue4j.Vue4J;

/**
 *
 * @author vince
 */
public interface Vue4JService {

    public void configure() throws Exception;

    public void activate() throws Exception;

    public void start() throws Exception;

    public void stop() throws Exception;

    public void deactivate() throws Exception;

    public void destroy() throws Exception;

    public Vue4J getVue4J();
}
