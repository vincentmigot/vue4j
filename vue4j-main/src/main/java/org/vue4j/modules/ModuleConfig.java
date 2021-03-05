/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.vue4j.modules;

/**
 *
 * @author vince
 */
public @interface ModuleConfig {
    
    public String id();
    
    public Class<?> cfg();
    
}
