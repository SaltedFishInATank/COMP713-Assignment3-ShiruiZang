package nz.ac.aut.comp713.api;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import nz.ac.aut.comp713.exception.ApiException;
import nz.ac.aut.comp713.model.User;
import nz.ac.aut.comp713.service.*;
import java.io.InputStream;
@Path("/") @RequestScoped @Produces(MediaType.APPLICATION_JSON) @Consumes({MediaType.APPLICATION_JSON,"application/*+json"})
public class TaskResource {
 @Inject private WorkflowService service;@Inject private AuthService auth;
 @Context private HttpHeaders headers;@Context private UriInfo uri;
 private String bearer(){return headers.getHeaderString("Authorization");}
 private User actor(){return auth.authenticate(bearer());}
 private JsonObject body(InputStream input){JsonValue v=JsonBody.parse(input);if(!(v instanceof JsonObject b))throw ApiException.invalid("The JSON body must be an object.");return b;}
 private Response ok(JsonValue data){return Response.ok(Json.createObjectBuilder().add("data",data).build()).header("Cache-Control","no-store").build();}
 private Response created(JsonObject data,String path){return Response.created(uri.getBaseUriBuilder().path(path).path(data.getJsonNumber("id").toString()).build()).entity(Json.createObjectBuilder().add("data",data).build()).build();}
 @GET @Path("health") public Response health(){service.health();return ok(Json.createObjectBuilder().add("status","ok").add("database","ok").add("application","comp713-a3").build());}
 @POST @Path("auth/login") public Response login(InputStream input){return ok(auth.login(body(input)));}
 @GET @Path("auth/me") public Response me(){return ok(actor().toJson());}
 @POST @Path("auth/logout") public Response logout(){auth.logout(bearer());return Response.noContent().build();}
 @GET @Path("users") public Response users(){return ok(auth.users(actor()));}
 @GET @Path("projects") public Response projects(){return ok(service.projects(actor()));}
 @POST @Path("projects") public Response createProject(InputStream input){User a=actor();return created(service.createProject(body(input),a),"projects");}
 @GET @Path("projects/{id}") public Response project(@PathParam("id")String id){return ok(service.project(WorkflowService.id(id),actor()));}
 @PATCH @Path("projects/{id}") public Response updateProject(@PathParam("id")String id,InputStream input){User a=actor();return ok(service.updateProject(WorkflowService.id(id),body(input),a));}
 @DELETE @Path("projects/{id}") public Response deleteProject(@PathParam("id")String id){service.deleteProject(WorkflowService.id(id),actor());return Response.noContent().build();}
 @GET @Path("projects/{id}/tasks") public Response tasks(@PathParam("id")String id){return ok(service.tasks(WorkflowService.id(id),actor()));}
 @POST @Path("projects/{id}/tasks") public Response createTask(@PathParam("id")String id,InputStream input){User a=actor();return created(service.createTask(WorkflowService.id(id),body(input),a),"tasks");}
 @GET @Path("tasks/{id}") public Response task(@PathParam("id")String id){return ok(service.task(WorkflowService.id(id),actor()));}
 @PATCH @Path("tasks/{id}") public Response updateTask(@PathParam("id")String id,InputStream input){User a=actor();return ok(service.updateTask(WorkflowService.id(id),body(input),a));}
 @DELETE @Path("tasks/{id}") public Response deleteTask(@PathParam("id")String id){service.deleteTask(WorkflowService.id(id),actor());return Response.noContent().build();}
 @GET @Path("events") public Response events(){return ok(service.events(actor()));}
}
