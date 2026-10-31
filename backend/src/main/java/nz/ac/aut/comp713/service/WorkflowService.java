package nz.ac.aut.comp713.service;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.json.*;
import nz.ac.aut.comp713.exception.ApiException;
import nz.ac.aut.comp713.model.User;
import nz.ac.aut.comp713.persistence.Database;
import nz.ac.aut.comp713.repository.WorkflowRepository;
import nz.ac.aut.comp713.websocket.TaskSocket;
import java.time.Instant;
import java.util.*;
@Dependent
public class WorkflowService {
 private final Database db;private final WorkflowRepository repo=new WorkflowRepository();
 @Inject public WorkflowService(Database db){this.db=db;}
 public void health(){db.read(c->{try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM tasks")){r.next();}return null;});}
 public JsonArray projects(User actor){return db.read(repo::projects);}
 public JsonObject project(long id,User actor){return db.read(c->repo.project(c,id));}
 public JsonObject createProject(JsonObject b,User actor){AuthService.manager(actor);fields(b,Set.of("name","description"));String name=text(b,"name",80,true),desc=text(b,"description",1000,false);JsonObject p=db.transaction(c->{long id=repo.insert(c,"INSERT INTO projects(name,name_key,description,created_at) VALUES(?,?,?,?)",name,name.toLowerCase(Locale.ROOT),desc,now());return repo.project(c,id);});TaskSocket.changed("project.changed");return p;}
 public JsonObject updateProject(long id,JsonObject b,User actor){AuthService.manager(actor);fields(b,Set.of("name","description"));JsonObject p=db.transaction(c->{JsonObject old=repo.project(c,id);String name=b.containsKey("name")?text(b,"name",80,true):old.getString("name");String desc=b.containsKey("description")?text(b,"description",1000,false):old.getString("description");repo.execute(c,"UPDATE projects SET name=?,name_key=?,description=? WHERE id=?",name,name.toLowerCase(Locale.ROOT),desc,id);return repo.project(c,id);});TaskSocket.changed("project.changed");return p;}
 public void deleteProject(long id,User actor){AuthService.manager(actor);db.transaction(c->{repo.project(c,id);repo.execute(c,"DELETE FROM projects WHERE id=?",id);return null;});TaskSocket.changed("project.changed");}
 public JsonArray tasks(long projectId,User actor){return db.read(c->repo.tasks(c,projectId,actor));}
 public JsonObject task(long id,User actor){return db.read(c->{JsonObject t=repo.task(c,id,false);authorize(t,actor);return t;});}
 public JsonObject createTask(long projectId,JsonObject b,User actor){
  AuthService.manager(actor);fields(b,Set.of("title","description","assignedTo"));String title=text(b,"title",120,true),desc=text(b,"description",1000,false);long assigned=integer(b,"assignedTo");
  JsonObject result=db.transaction(c->{repo.project(c,projectId);repo.worker(c,assigned);String timestamp=now();long id=repo.insert(c,"INSERT INTO tasks(project_id,title,description,status,assigned_to,created_at,updated_at) VALUES(?,?,?,'todo',?,?,?)",projectId,title,desc,assigned,timestamp,timestamp);return repo.task(c,id,false);});TaskSocket.changed("task.changed");return result;
 }
 public JsonObject updateTask(long id,JsonObject b,User actor){
  fields(b,actor.coordinator()?Set.of("version","title","description","assignedTo","status"):Set.of("version","status","completionNote","latitude","longitude"));long version=integer(b,"version");
  if(b.size()==1)throw ApiException.invalid("Provide a change as well as version.");
  JsonObject result=db.transaction(c->{
   JsonObject old=repo.task(c,id,true);authorize(old,actor);
   if(old.getJsonNumber("version").longValue()!=version)throw new ApiException(409,"stale_version","This task changed. Reload the current task and try again.");
   String from=old.getString("status"),to=b.containsKey("status")?text(b,"status",20,true):from;
   String title=old.getString("title"),desc=old.getString("description"),note=old.getString("completionNote");
   long assigned=old.getJsonNumber("assignedTo").longValue();Double lat=numberOrNull(old,"latitude"),lon=numberOrNull(old,"longitude");
   if(actor.coordinator()) {
    if(b.containsKey("title"))title=text(b,"title",120,true);
    if(b.containsKey("description"))desc=text(b,"description",1000,false);
    if(b.containsKey("assignedTo")){if(!from.equals("todo"))throw ApiException.invalid("Only todo tasks can be reassigned.");assigned=integer(b,"assignedTo");repo.worker(c,assigned);}
    if(!to.equals(from)&&!(from.equals("submitted")&&(to.equals("done")||to.equals("in_progress"))))throw ApiException.invalid("Review a submitted task by approving done or requesting in_progress rework.");
    if(b.containsKey("status")&&to.equals(from))throw ApiException.invalid("Choose a different review state.");
   } else {
    if(!(from.equals("todo")&&to.equals("in_progress"))&&!(from.equals("in_progress")&&to.equals("submitted")))throw ApiException.invalid("Accept a todo task, then submit an in-progress task.");
    if(to.equals("submitted")){note=text(b,"completionNote",1000,true);lat=coordinate(b,"latitude",90);lon=coordinate(b,"longitude",180);}
    else if(b.containsKey("completionNote")||b.containsKey("latitude")||b.containsKey("longitude"))throw ApiException.invalid("Completion evidence is accepted only when submitting.");
   }
   String timestamp=now();repo.execute(c,"UPDATE tasks SET title=?,description=?,assigned_to=?,status=?,completion_note=?,latitude=?,longitude=?,version=version+1,updated_at=? WHERE id=?",title,desc,assigned,to,note,lat,lon,timestamp,id);
   JsonObject updated=repo.task(c,id,false);
   if(!from.equals(to)&&to.equals("submitted")){
    String eventId=UUID.randomUUID().toString();JsonObject event=Json.createObjectBuilder().add("eventId",eventId).add("type","task.submitted").add("taskId",id).add("projectId",old.getJsonNumber("projectId")).add("title",title).add("workerId",actor.id()).add("completionNote",note).add("occurredAt",timestamp).build();
    repo.execute(c,"INSERT INTO event_outbox(event_id,payload,published,created_at) VALUES(?,?,FALSE,?)",eventId,event.toString(),timestamp);
   }
   return updated;
  });TaskSocket.changed("task.changed");return result;
 }
 public void deleteTask(long id,User actor){AuthService.manager(actor);db.transaction(c->{repo.task(c,id,true);repo.execute(c,"DELETE FROM tasks WHERE id=?",id);return null;});TaskSocket.changed("task.changed");}
 public JsonArray events(User actor){AuthService.manager(actor);return db.read(c->{JsonArrayBuilder a=Json.createArrayBuilder();try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM event_outbox ORDER BY created_at DESC FETCH FIRST 50 ROWS ONLY")){while(r.next())a.add(Json.createObjectBuilder().add("eventId",r.getString("event_id")).add("published",r.getBoolean("published")).add("createdAt",r.getString("created_at")).add("event",Json.createReader(new java.io.StringReader(r.getString("payload"))).readObject()));}return a.build();});}
 private void authorize(JsonObject t,User user){if(!user.coordinator()&&(t.isNull("assignedTo")||t.getJsonNumber("assignedTo").longValue()!=user.id()))throw new ApiException(403,"forbidden","This task is assigned to another worker.");}
 public static long id(String value){try{long id=Long.parseLong(value);if(id<1)throw new NumberFormatException();return id;}catch(NumberFormatException e){throw ApiException.notFound("Resource");}}
 private void fields(JsonObject b,Set<String> allowed){if(b.isEmpty()||!allowed.containsAll(b.keySet()))throw ApiException.invalid("Provide supported, non-empty request fields.");}
 private String text(JsonObject b,String key,int max,boolean required){if(!b.containsKey(key)){if(required)throw ApiException.invalid(key+" is required.");return "";}if(!(b.get(key)instanceof JsonString s))throw ApiException.invalid(key+" must be text.");String v=s.getString().strip();if(v.length()>max||(required&&v.isEmpty())||v.indexOf(0)>=0)throw ApiException.invalid(key+" must contain "+(required?1:0)+" to "+max+" characters.");return v;}
 private long integer(JsonObject b,String key){if(!(b.get(key)instanceof JsonNumber n)||!n.isIntegral())throw ApiException.invalid(key+" must be an integer.");try{return n.longValueExact();}catch(ArithmeticException e){throw ApiException.invalid(key+" is out of range.");}}
 private Double coordinate(JsonObject b,String key,int limit){if(!(b.get(key)instanceof JsonNumber n))throw ApiException.invalid("Provide "+key+" using device location or the manual fallback.");double v=n.doubleValue();if(!Double.isFinite(v)||Math.abs(v)>limit)throw ApiException.invalid(key+" is outside its geographic range.");return v;}
 private Double numberOrNull(JsonObject b,String key){return b.isNull(key)?null:b.getJsonNumber(key).doubleValue();}
 private String now(){return Instant.now().toString();}
}
