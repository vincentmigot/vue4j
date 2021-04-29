/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.vue4j.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.vue4j.Vue4J;

/**
 *
 * @author vince
 */
@OpenAPIDefinition(info = @Info(title = "Open API", version = Vue4J.VERSION))
public class OpenAPI {

    @GET
    @Path("/config")
    @Operation(summary = "Return the current configuration")
    @ApiResponse(responseCode = "200", description = "Front application configuration")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfig() throws Exception {

        return Response.ok("Yo").build();
    }
}
