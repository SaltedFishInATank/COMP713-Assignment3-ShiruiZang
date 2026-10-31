package nz.ac.aut.comp713.config;
import java.sql.*;
import java.time.Instant;
public final class Seed {
 private Seed(){}
 public static void apply(Connection c) throws SQLException {
  c.setAutoCommit(false);
  try(Statement lock=c.createStatement()) { lock.executeQuery("SELECT version FROM schema_version WHERE version=1 FOR UPDATE").close(); }
  try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT COUNT(*) FROM users")) {
   r.next(); if(r.getInt(1)==0) {
    add(c,"coordinator","Shirui / Coordinator","Coordinator713!","coordinator");
    add(c,"worker","Alex / Field worker","Worker713!","worker");
    add(c,"worker2","Taylor / Field worker","Worker713!","worker");
    String now=Instant.now().toString();
    try(PreparedStatement p=c.prepareStatement("INSERT INTO projects(name,name_key,description,created_at) VALUES('Campus maintenance','campus maintenance','Shared field work around campus',?)")) {p.setString(1,now);p.executeUpdate();}
    try(PreparedStatement p=c.prepareStatement("INSERT INTO tasks(project_id,title,description,status,assigned_to,created_at,updated_at) SELECT p.id,'Inspect library entrance','Check the entrance and submit a location-backed inspection.','todo',u.id,?,? FROM projects p,users u WHERE p.name_key='campus maintenance' AND u.username='worker'")) {p.setString(1,now);p.setString(2,now);p.executeUpdate();}
   }
  }
  c.commit();c.setAutoCommit(true);
 }
 private static void add(Connection c,String username,String name,String password,String role)throws SQLException {
  try(PreparedStatement p=c.prepareStatement("INSERT INTO users(username,display_name,password_hash,role) VALUES(?,?,?,?)")){p.setString(1,username);p.setString(2,name);p.setString(3,Passwords.hash(password));p.setString(4,role);p.executeUpdate();}
 }
}
