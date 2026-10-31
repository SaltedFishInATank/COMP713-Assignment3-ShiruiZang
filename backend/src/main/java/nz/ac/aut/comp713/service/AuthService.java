package nz.ac.aut.comp713.service;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.*;
import nz.ac.aut.comp713.config.Passwords;
import nz.ac.aut.comp713.exception.ApiException;
import nz.ac.aut.comp713.model.User;
import nz.ac.aut.comp713.persistence.Database;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;
@ApplicationScoped
public class AuthService {
 private Database db;
 public AuthService(){}
 @Inject public AuthService(Database db){this.db=db;}
 public JsonObject login(JsonObject body) {
  String username=text(body,"username"),password=text(body,"password");
  return db.transaction(c->{
   try(PreparedStatement p=c.prepareStatement("SELECT * FROM users WHERE username=?")) {
    p.setString(1,username);
    try(ResultSet r=p.executeQuery()) {
     if(!r.next()||!Passwords.matches(password,r.getString("password_hash"))) throw unauthorized();
     User user=map(r);byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
     String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
     try(PreparedStatement s=c.prepareStatement("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)")) {s.setString(1,Passwords.tokenHash(token));s.setLong(2,user.id());s.setLong(3,System.currentTimeMillis()+86400000);s.executeUpdate();}
     return Json.createObjectBuilder().add("token",token).add("user",user.toJson()).build();
    }
   }
  });
 }
 public User authenticate(String header) {
  if(header==null||!header.startsWith("Bearer ")||header.length()>200) throw unauthorized();
  return db.read(c->{try(PreparedStatement p=c.prepareStatement("SELECT u.* FROM users u JOIN sessions s ON s.user_id=u.id WHERE s.token_hash=? AND s.expires_at>?")){p.setString(1,Passwords.tokenHash(header.substring(7)));p.setLong(2,System.currentTimeMillis());try(ResultSet r=p.executeQuery()){if(!r.next())throw unauthorized();return map(r);}}});
 }
 public void logout(String header){authenticate(header);db.transaction(c->{try(PreparedStatement p=c.prepareStatement("DELETE FROM sessions WHERE token_hash=?")){p.setString(1,Passwords.tokenHash(header.substring(7)));p.executeUpdate();}return null;});}
 public JsonArray users(User user){manager(user);return db.read(c->{JsonArrayBuilder a=Json.createArrayBuilder();try(Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT * FROM users WHERE role='worker' ORDER BY id")){while(r.next())a.add(map(r).toJson());}return a.build();});}
 public static void manager(User user){if(!user.coordinator())throw new ApiException(403,"forbidden","This action requires the coordinator role.");}
 private User map(ResultSet r)throws SQLException{return new User(r.getLong("id"),r.getString("username"),r.getString("display_name"),r.getString("role"));}
 private String text(JsonObject b,String k){JsonValue v=b.get(k);if(!(v instanceof JsonString s)||s.getString().isBlank()||s.getString().length()>120)throw ApiException.invalid(k+" is required (maximum 120 characters).");return s.getString();}
 private ApiException unauthorized(){return new ApiException(401,"unauthorized","Sign in with a valid account. Your session may have expired.");}
}
