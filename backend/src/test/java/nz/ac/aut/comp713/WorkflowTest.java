package nz.ac.aut.comp713;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import jakarta.json.*;
import nz.ac.aut.comp713.persistence.Database;
import nz.ac.aut.comp713.service.*;
import nz.ac.aut.comp713.model.User;
import nz.ac.aut.comp713.exception.ApiException;
import java.nio.file.Path;
class WorkflowTest {
 @TempDir Path temp;Database db;AuthService auth;WorkflowService service;User manager,worker,other;
 @BeforeEach void setup(){db=new Database(temp.resolve("state"));auth=new AuthService(db);service=new WorkflowService(db);manager=login("coordinator","Coordinator713!");worker=login("worker","Worker713!");other=login("worker2","Worker713!");}
 @AfterEach void shutdown(){db.close();}
 User login(String name,String pass){JsonObject result=auth.login(Json.createObjectBuilder().add("username",name).add("password",pass).build());return auth.authenticate("Bearer "+result.getString("token"));}
 JsonObject object(String s){return Json.createReader(new java.io.StringReader(s)).readObject();}
 long project(){return service.projects(manager).getJsonObject(0).getJsonNumber("id").longValue();}
 JsonObject create(){return service.createTask(project(),Json.createObjectBuilder().add("title","Inspect laboratory").add("assignedTo",worker.id()).build(),manager);}
 long id(JsonObject t){return t.getJsonNumber("id").longValue();}
 void status(int expected,Runnable r){assertEquals(expected,assertThrows(ApiException.class,r::run).status());}
 @Test void sharedAcceptSubmitReviewAndOutbox(){JsonObject t=create();assertEquals(1,service.tasks(project(),worker).stream().filter(v->((JsonObject)v).getJsonNumber("id").longValue()==id(t)).count());service.updateTask(id(t),object("{\"version\":0,\"status\":\"in_progress\"}"),worker);service.updateTask(id(t),object("{\"version\":1,\"status\":\"submitted\",\"completionNote\":\"Checked\",\"latitude\":-36.85,\"longitude\":174.76}"),worker);assertEquals(1,service.events(manager).size());assertEquals("done",service.updateTask(id(t),object("{\"version\":2,\"status\":\"done\"}"),manager).getString("status"));}
 @Test void rolesAndAssignmentEnforced(){JsonObject t=create();status(403,()->service.task(id(t),other));status(403,()->service.createTask(project(),object("{\"title\":\"X\",\"assignedTo\":2}"),worker));}
 @Test void staleVersionRejected(){JsonObject t=create();service.updateTask(id(t),object("{\"version\":0,\"status\":\"in_progress\"}"),worker);status(409,()->service.updateTask(id(t),object("{\"version\":0,\"status\":\"submitted\"}"),worker));}
 @Test void invalidLocationRollsBackWithoutOutbox(){JsonObject t=create();service.updateTask(id(t),object("{\"version\":0,\"status\":\"in_progress\"}"),worker);status(400,()->service.updateTask(id(t),object("{\"version\":1,\"status\":\"submitted\",\"completionNote\":\"Checked\",\"latitude\":91,\"longitude\":1}"),worker));assertEquals("in_progress",service.task(id(t),worker).getString("status"));assertEquals(0,service.events(manager).size());}
 @Test void cannotApproveOrSkipAcceptance(){JsonObject t=create();status(400,()->service.updateTask(id(t),object("{\"version\":0,\"status\":\"done\"}"),worker));status(400,()->service.updateTask(id(t),object("{\"version\":0,\"status\":\"submitted\"}"),worker));}
 @Test void invalidAssigneeAndUnknownFields(){status(400,()->service.createTask(project(),object("{\"title\":\"Test\",\"assignedTo\":9999}"),manager));status(400,()->service.createProject(object("{\"name\":\"Valid\",\"admin\":true}"),manager));}
 @Test void sessionRevocation(){JsonObject result=auth.login(object("{\"username\":\"worker\",\"password\":\"Worker713!\"}"));String header="Bearer "+result.getString("token");auth.logout(header);status(401,()->auth.authenticate(header));status(401,()->auth.login(object("{\"username\":\"worker\",\"password\":\"wrong\"}")));}
 @Test void persistedAcrossDatabaseObjectRestart(){JsonObject t=create();WorkflowService restarted=new WorkflowService(new Database(temp.resolve("state")));assertEquals(t,restarted.task(id(t),manager));}
 @Test void duplicateProjectAndDeleteCascade(){JsonObject p=service.createProject(object("{\"name\":\"Unique project\"}"),manager);status(409,()->service.createProject(object("{\"name\":\"unique project\"}"),manager));long pId=p.getJsonNumber("id").longValue();JsonObject t=service.createTask(pId,Json.createObjectBuilder().add("title","Child").add("assignedTo",worker.id()).build(),manager);service.deleteProject(pId,manager);status(404,()->service.task(id(t),manager));}
}
