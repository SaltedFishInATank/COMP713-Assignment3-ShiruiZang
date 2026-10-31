package nz.ac.aut.comp713.repository;
import jakarta.json.*;
import nz.ac.aut.comp713.exception.ApiException;
import nz.ac.aut.comp713.model.User;
import java.sql.*;
public class WorkflowRepository {
 public JsonArray projects(Connection c)throws SQLException {
  JsonArrayBuilder a=Json.createArrayBuilder();try(Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT * FROM projects ORDER BY id DESC")){while(r.next())a.add(project(r));}return a.build();
 }
 public JsonObject project(Connection c,long id)throws SQLException {try(PreparedStatement p=c.prepareStatement("SELECT * FROM projects WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw ApiException.notFound("Project");return project(r);}}}
 public JsonArray tasks(Connection c,long projectId,User actor)throws SQLException {
  project(c,projectId);JsonArrayBuilder a=Json.createArrayBuilder();
  try(PreparedStatement p=c.prepareStatement("SELECT t.*,u.display_name FROM tasks t LEFT JOIN users u ON t.assigned_to=u.id WHERE project_id=?"+(actor.coordinator()?"":" AND assigned_to=?")+" ORDER BY t.id")){p.setLong(1,projectId);if(!actor.coordinator())p.setLong(2,actor.id());try(ResultSet r=p.executeQuery()){while(r.next())a.add(task(r));}}return a.build();
 }
 public JsonObject task(Connection c,long id,boolean lock)throws SQLException {
  // Lock task alone first: outer joins cannot be locked reliably across databases.
  if(lock)try(PreparedStatement p=c.prepareStatement("SELECT id FROM tasks WHERE id=? FOR UPDATE")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw ApiException.notFound("Task");}}
  try(PreparedStatement p=c.prepareStatement("SELECT t.*,u.display_name FROM tasks t LEFT JOIN users u ON t.assigned_to=u.id WHERE t.id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw ApiException.notFound("Task");return task(r);}}
 }
 public long insert(Connection c,String sql,Object...args)throws SQLException {try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){bind(p,args);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("No generated ID");return r.getLong(1);}}}
 public int execute(Connection c,String sql,Object...args)throws SQLException {try(PreparedStatement p=c.prepareStatement(sql)){bind(p,args);return p.executeUpdate();}}
 public void worker(Connection c,long id)throws SQLException {try(PreparedStatement p=c.prepareStatement("SELECT id FROM users WHERE id=? AND role='worker'")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw ApiException.invalid("assignedTo must identify an existing worker.");}}}
 private void bind(PreparedStatement p,Object...args)throws SQLException{for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);}
 private JsonObject project(ResultSet r)throws SQLException {return Json.createObjectBuilder().add("id",r.getLong("id")).add("name",r.getString("name")).add("description",r.getString("description")).add("createdAt",r.getString("created_at")).build();}
 private JsonObject task(ResultSet r)throws SQLException {
  JsonObjectBuilder b=Json.createObjectBuilder().add("id",r.getLong("id")).add("projectId",r.getLong("project_id")).add("title",r.getString("title")).add("description",r.getString("description")).add("status",r.getString("status")).add("completionNote",r.getString("completion_note")).add("version",r.getLong("version")).add("createdAt",r.getString("created_at")).add("updatedAt",r.getString("updated_at"));
  long assigned=r.getLong("assigned_to");if(r.wasNull())b.addNull("assignedTo");else b.add("assignedTo",assigned);
  String name=r.getString("display_name");if(name==null)b.addNull("assigneeName");else b.add("assigneeName",name);
  double lat=r.getDouble("latitude");if(r.wasNull())b.addNull("latitude");else b.add("latitude",lat);
  double lon=r.getDouble("longitude");if(r.wasNull())b.addNull("longitude");else b.add("longitude",lon);
  return b.build();
 }
}
